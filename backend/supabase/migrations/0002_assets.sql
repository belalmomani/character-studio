-- المرحلة 4 (الخادم): مكتبة الأصول. يعتمد على 0001. لم يُشغَّل على قاعدة بيانات بعد.
-- الصور نفسها في Cloudinary؛ هنا البيانات الوصفية فقط (public_id وليس أسرارًا).

create table public.asset_categories (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique check (slug ~ '^[a-z0-9_]+$'),
  parent_id uuid references public.asset_categories(id) on delete restrict,
  name_ar text not null,
  sort_order int not null default 0,
  active boolean not null default true
);

create table public.asset_licenses (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  source_url text,
  author text,
  commercial_use boolean not null default false,
  attribution_required boolean not null default false,
  notes text,
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  reviewed_by uuid references auth.users(id),
  reviewed_at timestamptz
);

create table public.assets (
  id uuid primary key default gen_random_uuid(),
  category_id uuid not null references public.asset_categories(id),
  name_ar text not null check (char_length(name_ar) between 1 and 80),
  art_style text not null check (art_style in ('realistic_2d','cartoon','anime','simple_game')),
  angle text not null default 'front' check (angle in ('front','side','back','three_quarter')),
  access_level text not null default 'free' check (access_level in ('free','paid')),
  points_cost int not null default 0 check (points_cost >= 0),
  status text not null default 'draft' check (status in ('draft','published','hidden','archived')),
  license_id uuid references public.asset_licenses(id),
  current_version_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check ((access_level = 'free' and points_cost = 0) or (access_level = 'paid' and points_cost > 0))
);
create index assets_sync_idx on public.assets(updated_at);
create index assets_browse_idx on public.assets(status, category_id, art_style);

create table public.asset_versions (
  id uuid primary key default gen_random_uuid(),
  asset_id uuid not null references public.assets(id) on delete cascade,
  version int not null check (version > 0),
  file_type text not null check (file_type in ('png','webp')),
  file_public_id text not null,
  thumb_public_id text not null,
  width int not null check (width between 16 and 4096),
  height int not null check (height between 16 and 4096),
  file_bytes int not null check (file_bytes between 1 and 5242880),
  sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
  anchors jsonb not null default '[]'::jsonb,       -- نقاط التثبيت الخاصة بالأصل
  compatibility jsonb not null default '{}'::jsonb, -- قواعد التوافق مع أصول أخرى
  min_app_version int not null default 1,
  created_at timestamptz not null default now(),
  unique (asset_id, version)
);

alter table public.assets
  add constraint assets_current_version_fk foreign key (current_version_id)
  references public.asset_versions(id) deferrable initially deferred;

create table public.asset_unlocks (
  user_id uuid not null references auth.users(id) on delete cascade,
  asset_id uuid not null references public.assets(id),
  unlocked_at timestamptz not null default now(),
  primary key (user_id, asset_id)
);

-- منع نشر أصل بلا ترخيص معتمد للاستخدام التجاري أو بلا نسخة حالية
create or replace function public.assets_enforce_rules() returns trigger
language plpgsql as $$
begin
  new.updated_at := now();
  if new.status = 'published' then
    if new.current_version_id is null then raise exception 'publish_requires_version'; end if;
    if not exists (select 1 from public.asset_licenses l
                   where l.id = new.license_id and l.status = 'approved' and l.commercial_use) then
      raise exception 'publish_requires_approved_commercial_license';
    end if;
  end if;
  return new;
end $$;
create trigger assets_rules before insert or update on public.assets
  for each row execute function public.assets_enforce_rules();

-- سجل تدقيق لتعديلات الأصول الإدارية
create or replace function public.assets_audit() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.admin_audit_logs(admin_id, action, target, details)
  values (auth.uid(), 'asset_' || lower(tg_op), coalesce(new.id, old.id)::text,
          jsonb_build_object('status', coalesce(new.status, old.status)));
  return coalesce(new, old);
end $$;
create trigger assets_audit_trg after insert or update or delete on public.assets
  for each row when (auth.uid() is not null) execute function public.assets_audit();

-- RLS
alter table public.asset_categories enable row level security;
alter table public.asset_licenses enable row level security;
alter table public.assets enable row level security;
alter table public.asset_versions enable row level security;
alter table public.asset_unlocks enable row level security;

create policy cat_read on public.asset_categories for select to authenticated using (active or public.is_admin());
create policy cat_admin on public.asset_categories for all to authenticated using (public.is_admin()) with check (public.is_admin());

create policy lic_admin on public.asset_licenses for all to authenticated using (public.is_admin()) with check (public.is_admin());

create policy assets_read on public.assets for select to authenticated using (status = 'published' or public.is_admin());
create policy assets_admin on public.assets for all to authenticated using (public.is_admin()) with check (public.is_admin());

create policy ver_read on public.asset_versions for select to authenticated using (
  public.is_admin() or exists (select 1 from public.assets a where a.id = asset_id and a.status = 'published'));
create policy ver_admin on public.asset_versions for all to authenticated using (public.is_admin()) with check (public.is_admin());

create policy unlock_read on public.asset_unlocks for select to authenticated using (user_id = auth.uid() or public.is_admin());
revoke insert, update, delete on public.asset_unlocks from anon, authenticated;

-- مزامنة تزايدية: كل ما تغيّر منذ آخر مزامنة (تشمل hidden/archived ليعرفها العميل
-- دون حذف نسخ المشاريع المحفوظة محليًا)، وتستبعد ما يتطلب إصدار تطبيق أحدث.
create or replace function public.list_assets_since(p_since timestamptz, p_app_version int)
returns table (
  asset_id uuid, category_id uuid, name_ar text, art_style text, angle text, access_level text,
  points_cost int, status text, updated_at timestamptz, version_id uuid, version int, file_type text,
  file_public_id text, thumb_public_id text, width int, height int, file_bytes int, sha256 text,
  anchors jsonb, compatibility jsonb, unlocked boolean
) language sql stable security invoker set search_path = public as $$
  select a.id, a.category_id, a.name_ar, a.art_style, a.angle, a.access_level, a.points_cost, a.status, a.updated_at,
         v.id, v.version, v.file_type, v.file_public_id, v.thumb_public_id, v.width, v.height, v.file_bytes, v.sha256,
         v.anchors, v.compatibility,
         (a.access_level = 'free' or exists (select 1 from public.asset_unlocks u where u.user_id = auth.uid() and u.asset_id = a.id))
  from public.assets a
  join public.asset_versions v on v.id = a.current_version_id
  where a.updated_at > p_since and v.min_app_version <= p_app_version
  order by a.updated_at
  limit 500;
$$;
grant execute on function public.list_assets_since(timestamptz, int) to authenticated;

-- فتح أصل مدفوع بالنقاط: ذري (الخصم + الفتح في معاملة واحدة)، ومكرر الطلب لا يخصم مرتين
create or replace function public.unlock_asset(p_asset_id uuid, p_key text)
returns int language plpgsql security definer set search_path = public as $$
declare v_uid uuid := auth.uid(); v_cost int; v_balance int;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  select points_cost into v_cost from assets where id = p_asset_id and status = 'published' and access_level = 'paid';
  if not found then raise exception 'asset_not_purchasable'; end if;
  if exists (select 1 from asset_unlocks where user_id = v_uid and asset_id = p_asset_id) then
    return (select balance from user_wallets where user_id = v_uid);
  end if;
  v_balance := public.spend_points(v_cost, 'unlock_asset:' || p_asset_id::text, p_key);
  insert into asset_unlocks(user_id, asset_id) values (v_uid, p_asset_id) on conflict do nothing;
  return v_balance;
end $$;
revoke all on function public.unlock_asset(uuid, text) from public, anon;
grant execute on function public.unlock_asset(uuid, text) to authenticated;
