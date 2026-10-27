-- ============================================================================
-- Character Studio — كل قاعدة البيانات في ملف واحد (0001 إلى 0007 + المسؤول)
-- شغّله كاملًا مرة واحدة في Supabase → SQL Editor.
--
-- تحذير: القسم (أ) يحذف أي نسخة سابقة من جداول ودوال المشروع (المشاريع المتزامنة، الأرصدة، سجل النقاط،
-- الأصول، الطلبات، الإعدادات…) ثم يعيد إنشاءها نظيفة. لا يحذف حسابات المستخدمين (auth.users).
-- لا تشغّله بعد وجود بيانات حقيقية تريد الاحتفاظ بها.
-- ============================================================================

-- (أ) تنظيف شامل لأي نسخة سابقة
drop trigger if exists on_auth_user_created on auth.users;

drop table if exists
  public.user_subscriptions, public.bank_transfer_submissions, public.payment_events, public.payment_orders,
  public.bank_transfer_settings, public.payment_settings, public.point_packages, public.subscription_plans,
  public.reward_ad_events, public.app_settings,
  public.asset_unlocks, public.asset_versions, public.assets, public.asset_licenses, public.asset_categories,
  public.admin_audit_logs, public.point_transactions, public.user_wallets, public.projects,
  public.user_roles, public.profiles
  cascade;

drop function if exists public.iban_valid(text) cascade;
drop function if exists public.create_order(text, uuid, text, text) cascade;
drop function if exists public.mark_order_paid(uuid, text) cascade;
drop function if exists public.process_provider_payment(text, text, uuid, text, int, text, text, jsonb) cascade;
drop function if exists public.get_bank_transfer_details(uuid) cascade;
drop function if exists public.submit_bank_transfer(uuid, text, text) cascade;
drop function if exists public.admin_review_bank_transfer(uuid, boolean, text) cascade;
drop function if exists public.current_subscription() cascade;
drop function if exists public.expire_stale_orders() cascade;
drop function if exists public.admin_config_audit() cascade;
drop function if exists public.admin_stats() cascade;
drop function if exists public.settings_validate() cascade;
drop function if exists public.settings_audit() cascade;
drop function if exists public.credit_points(uuid, int, text, text, text) cascade;
drop function if exists public.start_reward_ad() cascade;
drop function if exists public.claim_reward_ad(uuid) cascade;
drop function if exists public.unlock_asset(uuid, text) cascade;
drop function if exists public.list_assets_since(timestamptz, int) cascade;
drop function if exists public.list_assets_since(bigint, uuid, int) cascade;
drop function if exists public.assets_enforce_rules() cascade;
drop function if exists public.assets_audit() cascade;
drop function if exists public._insert_asset_version(uuid, int, jsonb) cascade;
drop function if exists public.admin_create_asset(uuid, text, text, text, text, int, uuid, jsonb) cascade;
drop function if exists public.admin_add_asset_version(uuid, jsonb) cascade;
drop function if exists public.admin_review_license(uuid, boolean, text) cascade;
drop function if exists public.spend_points(int, text, text) cascade;
drop function if exists public.handle_new_user() cascade;
drop function if exists public.is_admin() cascade;


-- ======================================================================
-- (ب) 0001_core_identity_projects_points.sql
-- ======================================================================
-- المرحلة 2: الهوية، الأدوار، المشاريع السحابية، المحفظة وسجل النقاط.
-- يفترض Supabase Auth (auth.users). عند اعتماد Firebase كمزود هوية يُعدَّل نوع user_id إلى text.
-- لم يُشغَّل هذا الملف على قاعدة بيانات بعد.

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  disabled boolean not null default false,
  created_at timestamptz not null default now()
);

create table public.user_roles (
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('administrator')),
  granted_at timestamptz not null default now(),
  primary key (user_id, role)
);

create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.user_roles where user_id = auth.uid() and role = 'administrator');
$$;

create table public.projects (
  id uuid primary key,
  owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 60),
  art_style text not null,
  format_version int not null,
  document jsonb not null default '{}'::jsonb,
  revision int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index projects_owner_idx on public.projects(owner_id, updated_at desc);

create table public.user_wallets (
  user_id uuid primary key references auth.users(id) on delete cascade,
  balance int not null default 0 check (balance >= 0),
  updated_at timestamptz not null default now()
);

create table public.point_transactions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  delta int not null check (delta <> 0),
  balance_after int not null check (balance_after >= 0),
  source text not null check (source in ('spend','reward_ad','purchase','admin_grant','admin_correction')),
  reason text not null,
  idempotency_key text not null,
  status text not null default 'completed',
  created_at timestamptz not null default now(),
  unique (user_id, idempotency_key)
);

create table public.admin_audit_logs (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid not null references auth.users(id),
  action text not null,
  target text,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- إنشاء الملف الشخصي والمحفظة عند تسجيل مستخدم جديد
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles(id) values (new.id);
  insert into public.user_wallets(user_id) values (new.id);
  return new;
end $$;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- RLS
alter table public.profiles enable row level security;
alter table public.user_roles enable row level security;
alter table public.projects enable row level security;
alter table public.user_wallets enable row level security;
alter table public.point_transactions enable row level security;
alter table public.admin_audit_logs enable row level security;

create policy profiles_self_read on public.profiles for select using (id = auth.uid() or public.is_admin());
create policy profiles_self_update on public.profiles for update using (id = auth.uid()) with check (id = auth.uid());

create policy roles_self_read on public.user_roles for select using (user_id = auth.uid() or public.is_admin());

create policy projects_owner_all on public.projects for all
  using (owner_id = auth.uid()) with check (owner_id = auth.uid());

-- المحفظة والسجل: قراءة فقط للمالك؛ لا كتابة من العميل إطلاقًا
create policy wallets_owner_read on public.user_wallets for select using (user_id = auth.uid() or public.is_admin());
create policy tx_owner_read on public.point_transactions for select using (user_id = auth.uid() or public.is_admin());
create policy audit_admin_read on public.admin_audit_logs for select using (public.is_admin());

revoke insert, update, delete on public.user_wallets, public.point_transactions,
  public.user_roles, public.admin_audit_logs from anon, authenticated;
-- profiles: لا يستطيع المستخدم تعديل disabled
revoke update on public.profiles from authenticated;
grant update (display_name) on public.profiles to authenticated;

-- إنفاق النقاط: ذري، بمفتاح idempotency، ولا يسمح برصيد سالب
create or replace function public.spend_points(p_amount int, p_reason text, p_key text)
returns int language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_balance int;
  v_existing int;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if p_amount is null or p_amount <= 0 then raise exception 'invalid_amount'; end if;
  if p_key is null or length(p_key) < 8 then raise exception 'invalid_key'; end if;

  select balance into v_balance from public.user_wallets where user_id = v_uid for update;
  if not found then raise exception 'wallet_missing'; end if;

  select balance_after into v_existing from public.point_transactions
    where user_id = v_uid and idempotency_key = p_key;
  if found then return v_existing; end if;   -- طلب مكرر: نُرجع نفس النتيجة دون خصم ثانٍ

  if v_balance < p_amount then raise exception 'insufficient_points'; end if;

  update public.user_wallets set balance = balance - p_amount, updated_at = now() where user_id = v_uid;
  insert into public.point_transactions(user_id, delta, balance_after, source, reason, idempotency_key)
    values (v_uid, -p_amount, v_balance - p_amount, 'spend', p_reason, p_key);
  return v_balance - p_amount;
end $$;

revoke all on function public.spend_points(int, text, text) from public, anon;
grant execute on function public.spend_points(int, text, text) to authenticated;


-- ======================================================================
-- (ب) 0002_assets.sql
-- ======================================================================
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

-- مزامنة تزايدية بترقيم مفتاحي (updated_at, id): لا تفقد صفوفًا تتشارك الطابع الزمني نفسه
-- (الاستيراد الجماعي في معاملة واحدة يعطي كل الصفوف now() نفسها). الطابع الزمني بالميكروثانية.
-- تُرجع المخفي والمؤرشف أيضًا ليعرفها العميل دون حذف نسخ المشاريع المحفوظة،
-- وتستبعد ما يحتاج إصدار تطبيق أحدث. حالة الفتح للمستخدم تُقرأ من asset_unlocks مباشرة.
create or replace function public.list_assets_since(p_since_us bigint, p_after_id uuid, p_app_version int)
returns table (
  asset_id uuid, category_id uuid, name_ar text, art_style text, angle text, access_level text,
  points_cost int, status text, updated_at_us bigint, version_id uuid, version int, file_type text,
  file_public_id text, thumb_public_id text, width int, height int, file_bytes int, sha256 text,
  anchors jsonb, compatibility jsonb
) language sql stable security invoker set search_path = public as $$
  select a.id, a.category_id, a.name_ar, a.art_style, a.angle, a.access_level, a.points_cost, a.status,
         (extract(epoch from a.updated_at) * 1000000)::bigint,
         v.id, v.version, v.file_type, v.file_public_id, v.thumb_public_id, v.width, v.height, v.file_bytes, v.sha256,
         v.anchors, v.compatibility
  from public.assets a
  join public.asset_versions v on v.id = a.current_version_id
  where (a.updated_at, a.id) > (
          'epoch'::timestamptz + p_since_us * interval '1 microsecond',
          coalesce(p_after_id, '00000000-0000-0000-0000-000000000000'::uuid))
    and v.min_app_version <= p_app_version
  order by a.updated_at, a.id
  limit 500;
$$;
grant execute on function public.list_assets_since(bigint, uuid, int) to authenticated;

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


-- ======================================================================
-- (ب) 0003_rewarded_ads.sql
-- ======================================================================
-- المرحلة 7 (الخادم): الإعلانات المكافأة + إعدادات التطبيق + دالة منح نقاط داخلية.
-- يعتمد على 0001 و0002. لم يُشغَّل على قاعدة بيانات بعد.
--
-- تنبيه صريح: لم أجد في توثيق Start.io ما يثبت وجود تحقق خادمي (SSV) لإكمال الإعلان المكافأ.
-- لذلك هذا النظام "تذاكر" لا يثبت أن الإعلان شوهد فعلًا؛ هو يمنع فقط: التكرار، تجاوز الحد اليومي،
-- المطالبة السريعة جدًا، وانتهاء الصلاحية. مستخدم متلاعب يستطيع تقليد الاستدعاء بعد مرور الحد الأدنى للزمن.
-- إن وفّر المزود لاحقًا تحققًا خادميًا موقّعًا يُستبدل به شرط الزمن في claim_reward_ad.

create table public.app_settings (
  key text primary key,
  value jsonb not null,
  updated_at timestamptz not null default now()
);
alter table public.app_settings enable row level security;
create policy settings_read on public.app_settings for select to authenticated using (true);
create policy settings_admin on public.app_settings for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- افتراضيًا معطّل وبلا نقاط: المسؤول يحدد القيم من لوحة التحكم
insert into public.app_settings(key, value) values
  ('rewarded_ads', '{"enabled": false, "points_per_ad": 0, "daily_limit": 0, "min_watch_seconds": 15, "ticket_ttl_seconds": 600}')
on conflict (key) do nothing;

create or replace function public.settings_audit() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.admin_audit_logs(admin_id, action, target, details)
  values (auth.uid(), 'setting_update', new.key, jsonb_build_object('value', new.value));
  new.updated_at := now();
  return new;
end $$;
create trigger settings_audit_trg before insert or update on public.app_settings
  for each row when (auth.uid() is not null) execute function public.settings_audit();

create table public.reward_ad_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'issued'
    check (status in ('issued','granted','rejected_too_fast','rejected_limit','expired')),
  points int not null check (points > 0),         -- المبلغ المتفق عليه وقت إصدار التذكرة
  issued_at timestamptz not null default now(),
  claimed_at timestamptz
);
create index reward_ad_events_user_idx on public.reward_ad_events(user_id, issued_at desc);
alter table public.reward_ad_events enable row level security;
create policy rae_read on public.reward_ad_events for select to authenticated
  using (user_id = auth.uid() or public.is_admin());
revoke insert, update, delete on public.reward_ad_events from anon, authenticated;

-- دالة داخلية لمنح النقاط (لا يستدعيها العميل أبدًا)؛ ذرية ومحمية من التكرار بمفتاح idempotency
create or replace function public.credit_points(p_uid uuid, p_amount int, p_source text, p_reason text, p_key text)
returns int language plpgsql security definer set search_path = public as $$
declare v_balance int; v_existing int;
begin
  if p_amount is null or p_amount <= 0 then raise exception 'invalid_amount'; end if;
  select balance into v_balance from public.user_wallets where user_id = p_uid for update;
  if not found then raise exception 'wallet_missing'; end if;
  select balance_after into v_existing from public.point_transactions
    where user_id = p_uid and idempotency_key = p_key;
  if found then return v_existing; end if;
  update public.user_wallets set balance = balance + p_amount, updated_at = now() where user_id = p_uid;
  insert into public.point_transactions(user_id, delta, balance_after, source, reason, idempotency_key)
    values (p_uid, p_amount, v_balance + p_amount, p_source, p_reason, p_key);
  return v_balance + p_amount;
end $$;
revoke all on function public.credit_points(uuid, int, text, text, text) from public, anon, authenticated;

-- 1) قبل عرض الإعلان: يطلب التطبيق تذكرة
create or replace function public.start_reward_ad() returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  s jsonb; v_points int; v_limit int; v_ttl int; v_today int; v_open uuid;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  select value into s from public.app_settings where key = 'rewarded_ads';
  v_points := coalesce((s->>'points_per_ad')::int, 0);
  v_limit := coalesce((s->>'daily_limit')::int, 0);
  v_ttl := coalesce((s->>'ticket_ttl_seconds')::int, 600);
  if s is null or coalesce((s->>'enabled')::boolean, false) = false or v_points <= 0 then
    return jsonb_build_object('ok', false, 'reason', 'ads_disabled');
  end if;

  select count(*) into v_today from public.reward_ad_events
    where user_id = v_uid and status = 'granted'
      and (claimed_at at time zone 'utc')::date = (now() at time zone 'utc')::date;
  if v_limit > 0 and v_today >= v_limit then
    return jsonb_build_object('ok', false, 'reason', 'daily_limit_reached');
  end if;

  -- تذكرة واحدة مفتوحة في كل مرة (تمنع تكديس التذاكر)
  select id into v_open from public.reward_ad_events
    where user_id = v_uid and status = 'issued' and issued_at > now() - make_interval(secs => v_ttl)
    order by issued_at desc limit 1;
  if v_open is not null then
    return jsonb_build_object('ok', true, 'ticket', v_open, 'points', v_points);
  end if;

  insert into public.reward_ad_events(user_id, points) values (v_uid, v_points) returning id into v_open;
  return jsonb_build_object('ok', true, 'ticket', v_open, 'points', v_points);
end $$;
revoke all on function public.start_reward_ad() from public, anon;
grant execute on function public.start_reward_ad() to authenticated;

-- 2) بعد اكتمال الإعلان (حسب callback المزود): يطالب التطبيق بالمكافأة مرة واحدة
create or replace function public.claim_reward_ad(p_ticket uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  e public.reward_ad_events%rowtype;
  s jsonb; v_min int; v_ttl int; v_limit int; v_today int; v_balance int;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  select * into e from public.reward_ad_events where id = p_ticket and user_id = v_uid for update;
  if not found then return jsonb_build_object('ok', false, 'reason', 'ticket_not_found'); end if;

  if e.status = 'granted' then   -- طلب مكرر: لا منح ثانٍ، نُرجع الرصيد الحالي
    select balance into v_balance from public.user_wallets where user_id = v_uid;
    return jsonb_build_object('ok', true, 'duplicate', true, 'balance', v_balance);
  end if;
  if e.status <> 'issued' then return jsonb_build_object('ok', false, 'reason', 'ticket_not_claimable'); end if;

  select value into s from public.app_settings where key = 'rewarded_ads';
  v_min := coalesce((s->>'min_watch_seconds')::int, 15);
  v_ttl := coalesce((s->>'ticket_ttl_seconds')::int, 600);
  v_limit := coalesce((s->>'daily_limit')::int, 0);

  if now() > e.issued_at + make_interval(secs => v_ttl) then
    update public.reward_ad_events set status = 'expired', claimed_at = now() where id = e.id;
    return jsonb_build_object('ok', false, 'reason', 'ticket_expired');
  end if;
  if now() < e.issued_at + make_interval(secs => v_min) then
    update public.reward_ad_events set status = 'rejected_too_fast', claimed_at = now() where id = e.id;
    return jsonb_build_object('ok', false, 'reason', 'too_fast');
  end if;

  select count(*) into v_today from public.reward_ad_events
    where user_id = v_uid and status = 'granted'
      and (claimed_at at time zone 'utc')::date = (now() at time zone 'utc')::date;
  if v_limit > 0 and v_today >= v_limit then
    update public.reward_ad_events set status = 'rejected_limit', claimed_at = now() where id = e.id;
    return jsonb_build_object('ok', false, 'reason', 'daily_limit_reached');
  end if;

  v_balance := public.credit_points(v_uid, e.points, 'reward_ad', 'rewarded_ad', 'ad:' || e.id::text);
  update public.reward_ad_events set status = 'granted', claimed_at = now() where id = e.id;
  return jsonb_build_object('ok', true, 'balance', v_balance, 'points', e.points);
end $$;
revoke all on function public.claim_reward_ad(uuid) from public, anon;
grant execute on function public.claim_reward_ad(uuid) to authenticated;


-- ======================================================================
-- (ب) 0004_admin_stats_and_validation.sql
-- ======================================================================
-- يعتمد على 0001-0003. لم يُشغَّل على قاعدة بيانات بعد.

-- إحصاءات لوحة التحكم: أرقام حقيقية من الجداول، للمسؤول فقط (يتحقق الخادم من الدور)
create or replace function public.admin_stats() returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  return jsonb_build_object(
    'users', (select count(*) from public.profiles),
    'disabled_users', (select count(*) from public.profiles where disabled),
    'projects', (select count(*) from public.projects),
    'assets_total', (select count(*) from public.assets),
    'assets_published', (select count(*) from public.assets where status = 'published'),
    'points_granted', coalesce((select sum(delta) from public.point_transactions where delta > 0), 0),
    'points_spent', coalesce((select -sum(delta) from public.point_transactions where delta < 0), 0),
    'ads_granted_total', (select count(*) from public.reward_ad_events where status = 'granted'),
    'ads_granted_today', (select count(*) from public.reward_ad_events
       where status = 'granted' and (claimed_at at time zone 'utc')::date = (now() at time zone 'utc')::date),
    'ads_rejected_total', (select count(*) from public.reward_ad_events where status like 'rejected%')
  );
end $$;
revoke all on function public.admin_stats() from public, anon;
grant execute on function public.admin_stats() to authenticated;

-- تحقق خادمي من قيم إعداد الإعلانات (لا نثق بالواجهة)
create or replace function public.settings_validate() returns trigger
language plpgsql as $$
declare v jsonb := new.value;
begin
  if new.key = 'rewarded_ads' then
    if jsonb_typeof(v->'enabled') is distinct from 'boolean' then raise exception 'invalid_enabled'; end if;
    if coalesce((v->>'points_per_ad')::int, -1) not between 0 and 1000 then raise exception 'invalid_points_per_ad'; end if;
    if coalesce((v->>'daily_limit')::int, -1) not between 0 and 100 then raise exception 'invalid_daily_limit'; end if;
    if coalesce((v->>'min_watch_seconds')::int, -1) not between 5 and 120 then raise exception 'invalid_min_watch_seconds'; end if;
    if coalesce((v->>'ticket_ttl_seconds')::int, -1) not between 60 and 3600 then raise exception 'invalid_ticket_ttl_seconds'; end if;
  end if;
  return new;
end $$;
create trigger settings_validate_trg before insert or update on public.app_settings
  for each row execute function public.settings_validate();


-- ======================================================================
-- (ب) 0005_billing.sql
-- ======================================================================
-- المرحلتان 8-9 (الخادم): الاشتراكات، باقات النقاط، الطلبات، أحداث المزود، التحويل البنكي.
-- مستقل عن مزود الدفع: لا يوجد مزود مفعّل ولا أسرار. يعتمد على 0001-0004. لم يُشغَّل على قاعدة بيانات بعد.
-- المبالغ بأصغر وحدة (سنت). السعر يُحسب ويُجمَّد في الطلب على الخادم ولا يقبل أي رقم من العميل.

create or replace function public.iban_valid(p text) returns boolean
language plpgsql immutable as $$
declare s text; r bigint := 0; ch text; i int;
begin
  if p is null then return false; end if;
  s := upper(regexp_replace(p, '\s', '', 'g'));
  if s !~ '^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$' then return false; end if;
  s := substr(s, 5) || substr(s, 1, 4);
  for i in 1..length(s) loop
    ch := substr(s, i, 1);
    if ch ~ '[0-9]' then r := (r * 10 + ch::int) % 97;
    else r := (r * 100 + (ascii(ch) - 55)) % 97; end if;
  end loop;
  return r = 1;
end $$;

create table public.subscription_plans (
  id uuid primary key default gen_random_uuid(),
  name_ar text not null,
  description_ar text not null default '',
  price_cents int not null check (price_cents > 0),
  currency text not null default 'USD' check (currency ~ '^[A-Z]{3}$'),
  duration_days int not null check (duration_days > 0),
  discount_percent int not null default 0 check (discount_percent between 0 and 100),
  features jsonb not null default '[]'::jsonb,       -- المزايا الحقيقية فقط؛ تُملأ من لوحة التحكم
  usage_limits jsonb not null default '{}'::jsonb,
  renewal_terms_ar text not null default '',
  cancellable boolean not null default false,
  published boolean not null default false,
  sort_order int not null default 0,
  created_at timestamptz not null default now()
);

-- الأسعار الأولية المطلوبة، غير منشورة حتى تحدد مزاياها وتنشرها من لوحة التحكم
insert into public.subscription_plans(name_ar, price_cents, currency, duration_days, sort_order) values
  ('اشتراك أسبوعي', 500, 'USD', 7, 1),
  ('اشتراك شهري', 1500, 'USD', 30, 2),
  ('اشتراك سنوي', 10000, 'USD', 365, 3);

create table public.point_packages (
  id uuid primary key default gen_random_uuid(),
  name_ar text not null,
  description_ar text not null default '',
  points int not null check (points > 0),
  price_cents int not null check (price_cents > 0),
  currency text not null default 'USD' check (currency ~ '^[A-Z]{3}$'),
  bonus_note_ar text not null default '',
  active boolean not null default false,
  sort_order int not null default 0
);  -- لا بيانات أولية: الباقات يحددها المسؤول

create table public.payment_settings (
  id int primary key check (id = 1),
  card_enabled boolean not null default false,
  card_provider text,                       -- اسم المزود فقط؛ لا أسرار في قاعدة البيانات
  sandbox boolean not null default true,
  check (not card_enabled or card_provider is not null)
);
insert into public.payment_settings(id) values (1);

create table public.bank_transfer_settings (
  id int primary key check (id = 1),
  enabled boolean not null default false,
  beneficiary_name text not null default '',
  bank_name text not null default '',
  iban text not null default '',
  instructions_ar text not null default '',
  updated_at timestamptz not null default now(),
  -- لا يُفعَّل الخيار إلا ببيانات كاملة وIBAN صحيح (مجموع تحقق MOD-97)
  check (not enabled or (beneficiary_name <> '' and bank_name <> '' and public.iban_valid(iban)))
);
insert into public.bank_transfer_settings(id) values (1);

create table public.payment_orders (
  id uuid primary key default gen_random_uuid(),
  reference text generated always as ('CS-' || upper(substr(replace(id::text, '-', ''), 1, 10))) stored,
  user_id uuid not null references auth.users(id),
  product_type text not null check (product_type in ('subscription','points')),
  product_id uuid not null,
  snapshot jsonb not null,                  -- اسم المنتج ومدته/نقاطه وقت الطلب
  amount_cents int not null check (amount_cents > 0),
  currency text not null,
  method text not null check (method in ('card','bank_transfer')),
  provider text not null,
  status text not null default 'created'
    check (status in ('created','pending','paid','failed','canceled','expired','refunded','needs_review')),
  provider_ref text,
  idempotency_key text not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  paid_at timestamptz,
  unique (user_id, idempotency_key),
  unique (reference)
);
create index payment_orders_user_idx on public.payment_orders(user_id, created_at desc);
create index payment_orders_status_idx on public.payment_orders(status, created_at desc);

create table public.payment_events (
  id uuid primary key default gen_random_uuid(),
  provider text not null,
  event_id text not null,
  order_id uuid references public.payment_orders(id),
  payload jsonb not null,
  result text,
  received_at timestamptz not null default now(),
  unique (provider, event_id)               -- يمنع معالجة Webhook مكرر
);

create table public.bank_transfer_submissions (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.payment_orders(id),
  user_id uuid not null references auth.users(id),
  receipt_path text,
  note text check (char_length(note) <= 500),
  status text not null default 'awaiting_review' check (status in ('awaiting_review','approved','rejected')),
  reject_reason text,
  reviewed_by uuid references auth.users(id),
  reviewed_at timestamptz,
  created_at timestamptz not null default now()
);
create unique index one_open_submission_per_order on public.bank_transfer_submissions(order_id)
  where status = 'awaiting_review';

create table public.user_subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  plan_id uuid not null references public.subscription_plans(id),
  order_id uuid references public.payment_orders(id),
  status text not null default 'active' check (status in ('active','canceled','refunded','needs_attention')),
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  auto_renew boolean not null default false,
  created_at timestamptz not null default now(),
  unique (order_id)
);
create index user_subscriptions_user_idx on public.user_subscriptions(user_id, ends_at desc);

-- RLS
alter table public.subscription_plans enable row level security;
alter table public.point_packages enable row level security;
alter table public.payment_settings enable row level security;
alter table public.bank_transfer_settings enable row level security;
alter table public.payment_orders enable row level security;
alter table public.payment_events enable row level security;
alter table public.bank_transfer_submissions enable row level security;
alter table public.user_subscriptions enable row level security;

create policy plans_read on public.subscription_plans for select to authenticated using (published or public.is_admin());
create policy plans_admin on public.subscription_plans for all to authenticated using (public.is_admin()) with check (public.is_admin());
create policy pkg_read on public.point_packages for select to authenticated using (active or public.is_admin());
create policy pkg_admin on public.point_packages for all to authenticated using (public.is_admin()) with check (public.is_admin());
create policy paysettings_admin on public.payment_settings for all to authenticated using (public.is_admin()) with check (public.is_admin());
-- بيانات IBAN: للمسؤول فقط مباشرة؛ المستخدم يحصل عليها عبر get_bank_transfer_details بعد إنشاء طلب
create policy bank_admin on public.bank_transfer_settings for all to authenticated using (public.is_admin()) with check (public.is_admin());
create policy orders_read on public.payment_orders for select to authenticated using (user_id = auth.uid() or public.is_admin());
create policy events_admin_read on public.payment_events for select to authenticated using (public.is_admin());
create policy subs_read on public.bank_transfer_submissions for select to authenticated using (user_id = auth.uid() or public.is_admin());
create policy usersubs_read on public.user_subscriptions for select to authenticated using (user_id = auth.uid() or public.is_admin());
revoke insert, update, delete on public.payment_orders, public.payment_events,
  public.bank_transfer_submissions, public.user_subscriptions from anon, authenticated;

-- تدقيق تعديلات الإعدادات الإدارية (دون تسجيل IBAN)
create or replace function public.admin_config_audit() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.admin_audit_logs(admin_id, action, target, details)
  values (auth.uid(), tg_table_name || '_' || lower(tg_op), to_jsonb(new)->>'id',
    case when tg_table_name = 'bank_transfer_settings'
         then jsonb_build_object('enabled', new.enabled)
         else jsonb_build_object('row', to_jsonb(new)) end);
  return new;
end $$;
create trigger plans_audit after insert or update on public.subscription_plans
  for each row when (auth.uid() is not null) execute function public.admin_config_audit();
create trigger pkg_audit after insert or update on public.point_packages
  for each row when (auth.uid() is not null) execute function public.admin_config_audit();
create trigger paysettings_audit after insert or update on public.payment_settings
  for each row when (auth.uid() is not null) execute function public.admin_config_audit();
create trigger bank_audit after insert or update on public.bank_transfer_settings
  for each row when (auth.uid() is not null) execute function public.admin_config_audit();

-- إنشاء طلب: السعر والعملة من قاعدة البيانات فقط؛ مكرر المفتاح يرجع الطلب نفسه
create or replace function public.create_order(p_product_type text, p_product_id uuid, p_method text, p_key text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  o public.payment_orders%rowtype;
  v_amount int; v_cur text; v_snap jsonb; v_provider text; v_ttl interval;
  ps public.payment_settings%rowtype; bs public.bank_transfer_settings%rowtype;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if p_key is null or length(p_key) < 8 then raise exception 'invalid_key'; end if;
  if exists (select 1 from public.profiles where id = v_uid and disabled) then raise exception 'account_disabled'; end if;

  select * into o from public.payment_orders where user_id = v_uid and idempotency_key = p_key;
  if found then return to_jsonb(o); end if;

  if p_method = 'card' then
    select * into ps from public.payment_settings where id = 1;
    if not ps.card_enabled or ps.card_provider is null then raise exception 'card_not_configured'; end if;
    v_provider := ps.card_provider; v_ttl := interval '1 hour';
  elsif p_method = 'bank_transfer' then
    select * into bs from public.bank_transfer_settings where id = 1;
    if not bs.enabled then raise exception 'bank_transfer_disabled'; end if;
    v_provider := 'bank_transfer'; v_ttl := interval '7 days';
  else
    raise exception 'invalid_method';
  end if;

  if p_product_type = 'subscription' then
    select round(p.price_cents * (100 - p.discount_percent) / 100.0)::int, p.currency,
           jsonb_build_object('name', p.name_ar, 'duration_days', p.duration_days)
      into v_amount, v_cur, v_snap
      from public.subscription_plans p where p.id = p_product_id and p.published;
  elsif p_product_type = 'points' then
    select k.price_cents, k.currency, jsonb_build_object('name', k.name_ar, 'points', k.points)
      into v_amount, v_cur, v_snap
      from public.point_packages k where k.id = p_product_id and k.active;
  else
    raise exception 'invalid_product_type';
  end if;
  if v_amount is null or v_amount <= 0 then raise exception 'product_not_available'; end if;

  insert into public.payment_orders(user_id, product_type, product_id, snapshot, amount_cents, currency,
                                    method, provider, idempotency_key, expires_at)
  values (v_uid, p_product_type, p_product_id, v_snap, v_amount, v_cur, p_method, v_provider, p_key, now() + v_ttl)
  returning * into o;
  return to_jsonb(o);
end $$;
revoke all on function public.create_order(text, uuid, text, text) from public, anon;
grant execute on function public.create_order(text, uuid, text, text) to authenticated;

-- تفعيل الطلب المدفوع: داخلي فقط، ذري، ويُستدعى مرة فعلية واحدة مهما تكرر الاستدعاء
create or replace function public.mark_order_paid(p_order uuid, p_provider_ref text) returns void
language plpgsql security definer set search_path = public as $$
declare o public.payment_orders%rowtype; v_end timestamptz;
begin
  select * into o from public.payment_orders where id = p_order for update;
  if not found then raise exception 'order_not_found'; end if;
  if o.status = 'paid' then return; end if;
  if o.status not in ('created','pending','needs_review') then raise exception 'order_not_payable'; end if;

  update public.payment_orders
    set status = 'paid', paid_at = now(), provider_ref = coalesce(p_provider_ref, provider_ref) where id = o.id;

  if o.product_type = 'points' then
    perform public.credit_points(o.user_id, (o.snapshot->>'points')::int, 'purchase', 'points_package', 'order:' || o.id::text);
  else
    select max(ends_at) into v_end from public.user_subscriptions
      where user_id = o.user_id and status = 'active' and ends_at > now();
    insert into public.user_subscriptions(user_id, plan_id, order_id, status, starts_at, ends_at)
    values (o.user_id, o.product_id, o.id, 'active', coalesce(v_end, now()),
            coalesce(v_end, now()) + make_interval(days => (o.snapshot->>'duration_days')::int));
  end if;
end $$;
revoke all on function public.mark_order_paid(uuid, text) from public, anon, authenticated;

-- نقطة دخول لحدث موثَّق من المزود (تستدعيها Edge Function بعد التحقق من التوقيع بمفتاح service_role).
create or replace function public.process_provider_payment(
  p_provider text, p_event_id text, p_order uuid, p_status text,
  p_amount_cents int, p_currency text, p_provider_ref text, p_payload jsonb
) returns text language plpgsql security definer set search_path = public as $$
declare o public.payment_orders%rowtype; v_result text;
begin
  insert into public.payment_events(provider, event_id, order_id, payload)
  values (p_provider, p_event_id, p_order, coalesce(p_payload, '{}'::jsonb))
  on conflict (provider, event_id) do nothing;
  if not found then return 'duplicate'; end if;

  select * into o from public.payment_orders where id = p_order for update;
  if not found then v_result := 'order_not_found';
  elsif o.provider is distinct from p_provider then v_result := 'provider_mismatch';
  elsif p_status = 'paid' then
    if o.amount_cents <> p_amount_cents or o.currency <> upper(p_currency) then
      update public.payment_orders set status = 'needs_review' where id = o.id and status in ('created','pending');
      v_result := 'amount_mismatch';
    else
      perform public.mark_order_paid(o.id, p_provider_ref);
      v_result := 'paid';
    end if;
  elsif p_status in ('failed','canceled','expired') then
    update public.payment_orders set status = p_status where id = o.id and status in ('created','pending'); -- لا يُخفَّض طلب مدفوع
    v_result := p_status;
  elsif p_status = 'refunded' then
    if o.product_type = 'subscription' then
      update public.payment_orders set status = 'refunded' where id = o.id and status = 'paid';
      update public.user_subscriptions set status = 'refunded' where order_id = o.id;
    else
      update public.payment_orders set status = 'needs_review' where id = o.id and status = 'paid'; -- استرداد نقاط: مراجعة يدوية
    end if;
    v_result := 'refunded';
  else
    v_result := 'ignored_status';
  end if;
  update public.payment_events set result = v_result where provider = p_provider and event_id = p_event_id;
  return v_result;
end $$;
revoke all on function public.process_provider_payment(text, text, uuid, text, int, text, text, jsonb)
  from public, anon, authenticated;

-- بيانات التحويل البنكي: لصاحب الطلب فقط، وفقط إن كان الخيار مفعّلًا وكامل البيانات
create or replace function public.get_bank_transfer_details(p_order uuid) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare o public.payment_orders%rowtype; b public.bank_transfer_settings%rowtype;
begin
  select * into o from public.payment_orders where id = p_order and user_id = auth.uid() and method = 'bank_transfer';
  if not found then raise exception 'order_not_found'; end if;
  select * into b from public.bank_transfer_settings where id = 1;
  if not b.enabled then raise exception 'bank_transfer_disabled'; end if;
  return jsonb_build_object('beneficiary', b.beneficiary_name, 'bank', b.bank_name, 'iban', b.iban,
    'instructions', b.instructions_ar, 'amount_cents', o.amount_cents, 'currency', o.currency,
    'reference', o.reference, 'status', o.status, 'expires_at', o.expires_at);
end $$;
revoke all on function public.get_bank_transfer_details(uuid) from public, anon;
grant execute on function public.get_bank_transfer_details(uuid) to authenticated;

-- إشعار المستخدم بالتحويل: يضع الطلب بانتظار المراجعة، ولا يفعّل شيئًا
create or replace function public.submit_bank_transfer(p_order uuid, p_receipt_path text, p_note text)
returns uuid language plpgsql security definer set search_path = public as $$
declare o public.payment_orders%rowtype; v_id uuid;
begin
  select * into o from public.payment_orders where id = p_order and user_id = auth.uid() and method = 'bank_transfer' for update;
  if not found then raise exception 'order_not_found'; end if;
  if o.status not in ('created','pending') or o.expires_at < now() then raise exception 'order_not_open'; end if;
  insert into public.bank_transfer_submissions(order_id, user_id, receipt_path, note)
  values (o.id, o.user_id, p_receipt_path, left(p_note, 500)) returning id into v_id;
  update public.payment_orders set status = 'pending' where id = o.id;
  return v_id;
end $$;
revoke all on function public.submit_bank_transfer(uuid, text, text) from public, anon;
grant execute on function public.submit_bank_transfer(uuid, text, text) to authenticated;

-- مراجعة المسؤول: لا تفعيل إلا باعتماد صريح بعد التأكد من وصول المبلغ؛ الرفض يتطلب سببًا
create or replace function public.admin_review_bank_transfer(p_order uuid, p_approve boolean, p_reason text)
returns text language plpgsql security definer set search_path = public as $$
declare s public.bank_transfer_submissions%rowtype;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  select * into s from public.bank_transfer_submissions where order_id = p_order and status = 'awaiting_review' for update;
  if not found then raise exception 'no_pending_submission'; end if;
  if p_approve then
    perform public.mark_order_paid(p_order, 'bank:' || s.id::text);
    update public.bank_transfer_submissions set status = 'approved', reviewed_by = auth.uid(), reviewed_at = now() where id = s.id;
  else
    if p_reason is null or length(trim(p_reason)) = 0 then raise exception 'reason_required'; end if;
    update public.bank_transfer_submissions
      set status = 'rejected', reject_reason = left(p_reason, 300), reviewed_by = auth.uid(), reviewed_at = now() where id = s.id;
    update public.payment_orders set status = 'failed' where id = p_order and status in ('created','pending');
  end if;
  insert into public.admin_audit_logs(admin_id, action, target, details)
  values (auth.uid(), case when p_approve then 'bank_transfer_approved' else 'bank_transfer_rejected' end,
          p_order::text, jsonb_build_object('reason', p_reason));
  return case when p_approve then 'approved' else 'rejected' end;
end $$;
revoke all on function public.admin_review_bank_transfer(uuid, boolean, text) from public, anon;
grant execute on function public.admin_review_bank_transfer(uuid, boolean, text) to authenticated;

-- حالة اشتراك المستخدم الفعلية (تُحسب من التواريخ؛ لا تعتمد على مهمة مجدولة)
create or replace function public.current_subscription() returns jsonb
language sql stable security invoker set search_path = public as $$
  select coalesce((
    select jsonb_build_object(
      'status', case when s.status in ('canceled','refunded','needs_attention') then s.status
                     when s.starts_at <= now() and s.ends_at > now() then 'active'
                     when s.ends_at <= now() then 'expired' else 'active' end,
      'plan_id', s.plan_id, 'starts_at', s.starts_at, 'ends_at', s.ends_at, 'auto_renew', s.auto_renew)
    from public.user_subscriptions s where s.user_id = auth.uid() order by s.ends_at desc limit 1
  ), jsonb_build_object('status', 'none'));
$$;
grant execute on function public.current_subscription() to authenticated;

-- ينهي الطلبات المنتهية (يُستدعى من لوحة المسؤول أو مهمة مجدولة)
create or replace function public.expire_stale_orders() returns int
language plpgsql security definer set search_path = public as $$
declare n int;
begin
  if auth.uid() is not null and not public.is_admin() then raise exception 'forbidden'; end if;
  update public.payment_orders set status = 'expired' where status in ('created','pending') and expires_at < now();
  get diagnostics n = row_count;
  return n;
end $$;
revoke all on function public.expire_stale_orders() from public, anon;
grant execute on function public.expire_stale_orders() to authenticated;


-- ======================================================================
-- (ب) 0006_admin_assets.sql
-- ======================================================================
-- يعتمد على 0001-0005 (خاصة 0002). لم يُشغَّل على قاعدة بيانات بعد.
-- دوال إدارية ذرية لإضافة الأصول: الأصل ونسخته الأولى ونقاط تثبيته في معاملة واحدة، ومراجعة التراخيص.

create or replace function public._insert_asset_version(p_asset uuid, p_version int, j jsonb) returns uuid
language plpgsql security definer set search_path = public as $$
declare v_id uuid; w int := (j->>'width')::int; ht int := (j->>'height')::int; a jsonb; el jsonb;
begin
  a := coalesce(j->'anchors', '[]'::jsonb);
  if jsonb_typeof(a) is distinct from 'array' then raise exception 'invalid_anchors'; end if;
  for el in select * from jsonb_array_elements(a) loop
    if jsonb_typeof(el->'id') is distinct from 'string'
       or jsonb_typeof(el->'x') is distinct from 'number'
       or jsonb_typeof(el->'y') is distinct from 'number'
       or (el->>'x')::numeric not between 0 and w
       or (el->>'y')::numeric not between 0 and ht then
      raise exception 'invalid_anchors';
    end if;
  end loop;
  insert into public.asset_versions(asset_id, version, file_type, file_public_id, thumb_public_id,
      width, height, file_bytes, sha256, anchors, compatibility, min_app_version)
  values (p_asset, p_version, j->>'file_type', j->>'file_public_id', j->>'thumb_public_id',
      w, ht, (j->>'file_bytes')::int, j->>'sha256', a,
      coalesce(j->'compatibility', '{}'::jsonb), coalesce((j->>'min_app_version')::int, 1))
  returning id into v_id;
  return v_id;
end $$;
revoke all on function public._insert_asset_version(uuid, int, jsonb) from public, anon, authenticated;

create or replace function public.admin_create_asset(
  p_category uuid, p_name text, p_style text, p_angle text, p_access text, p_points int,
  p_license uuid, p_version jsonb
) returns uuid language plpgsql security definer set search_path = public as $$
declare v_asset uuid; v_ver uuid;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  insert into public.assets(category_id, name_ar, art_style, angle, access_level, points_cost, license_id)
  values (p_category, trim(p_name), p_style, p_angle, p_access, coalesce(p_points, 0), p_license)
  returning id into v_asset;
  v_ver := public._insert_asset_version(v_asset, 1, p_version);
  update public.assets set current_version_id = v_ver where id = v_asset;   -- يبقى مسودة حتى النشر
  return v_asset;
end $$;
revoke all on function public.admin_create_asset(uuid, text, text, text, text, int, uuid, jsonb) from public, anon;
grant execute on function public.admin_create_asset(uuid, text, text, text, text, int, uuid, jsonb) to authenticated;

create or replace function public.admin_add_asset_version(p_asset uuid, p_version jsonb) returns int
language plpgsql security definer set search_path = public as $$
declare v_next int; v_ver uuid;
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  perform 1 from public.assets where id = p_asset for update;
  if not found then raise exception 'asset_not_found'; end if;
  select coalesce(max(version), 0) + 1 into v_next from public.asset_versions where asset_id = p_asset;
  v_ver := public._insert_asset_version(p_asset, v_next, p_version);
  update public.assets set current_version_id = v_ver where id = p_asset;
  return v_next;
end $$;
revoke all on function public.admin_add_asset_version(uuid, jsonb) from public, anon;
grant execute on function public.admin_add_asset_version(uuid, jsonb) to authenticated;

-- مراجعة ترخيص: الرفض يُخفي تلقائيًا كل الأصول المنشورة المرتبطة به
create or replace function public.admin_review_license(p_id uuid, p_approve boolean, p_note text) returns text
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  update public.asset_licenses
    set status = case when p_approve then 'approved' else 'rejected' end,
        reviewed_by = auth.uid(), reviewed_at = now(), notes = coalesce(nullif(trim(p_note), ''), notes)
    where id = p_id;
  if not found then raise exception 'license_not_found'; end if;
  if not p_approve then
    update public.assets set status = 'hidden' where license_id = p_id and status = 'published';
  end if;
  insert into public.admin_audit_logs(admin_id, action, target, details)
  values (auth.uid(), case when p_approve then 'license_approved' else 'license_rejected' end, p_id::text,
          jsonb_build_object('note', p_note));
  return case when p_approve then 'approved' else 'rejected' end;
end $$;
revoke all on function public.admin_review_license(uuid, boolean, text) from public, anon;
grant execute on function public.admin_review_license(uuid, boolean, text) to authenticated;


-- ======================================================================
-- (ب) 0007_public_library.sql
-- ======================================================================
-- يعتمد على 0001-0006. لم يُشغَّل على قاعدة بيانات بعد.
-- 1) قراءة عامة (بلا تسجيل دخول) لفئات الأصول المنشورة وقائمة الأصول المنشورة، حتى تعمل المكتبة قبل بناء الحسابات.
-- 2) تعديل المزامنة: الأصل الذي أُخفي أو أُرشف بعد نشره يصل للعميل بحالته الجديدة (فلا يبقى ظاهرًا بنسخة قديمة)،
--    بينما المسودات التي لم تُنشر قط لا تُكشف أبدًا.

alter table public.assets add column if not exists first_published_at timestamptz;
update public.assets set first_published_at = coalesce(first_published_at, now()) where status = 'published';

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
    if new.first_published_at is null then new.first_published_at := now(); end if;
  end if;
  return new;
end $$;

drop policy if exists cat_read_anon on public.asset_categories;
create policy cat_read_anon on public.asset_categories for select to anon using (active);
grant select on public.asset_categories to anon;

create or replace function public.list_assets_since(p_since_us bigint, p_after_id uuid, p_app_version int)
returns table (
  asset_id uuid, category_id uuid, name_ar text, art_style text, angle text, access_level text,
  points_cost int, status text, updated_at_us bigint, version_id uuid, version int, file_type text,
  file_public_id text, thumb_public_id text, width int, height int, file_bytes int, sha256 text,
  anchors jsonb, compatibility jsonb
) language sql stable security definer set search_path = public as $$
  select a.id, a.category_id, a.name_ar, a.art_style, a.angle, a.access_level, a.points_cost, a.status,
         (extract(epoch from a.updated_at) * 1000000)::bigint,
         v.id, v.version, v.file_type, v.file_public_id, v.thumb_public_id, v.width, v.height, v.file_bytes, v.sha256,
         v.anchors, v.compatibility
  from public.assets a
  join public.asset_versions v on v.id = a.current_version_id
  where (a.status = 'published' or a.first_published_at is not null)   -- لا مسودات لم تُنشر قط
    and (a.updated_at, a.id) > (
          'epoch'::timestamptz + p_since_us * interval '1 microsecond',
          coalesce(p_after_id, '00000000-0000-0000-0000-000000000000'::uuid))
    and v.min_app_version <= p_app_version
  order by a.updated_at, a.id
  limit 500;
$$;
revoke all on function public.list_assets_since(bigint, uuid, int) from public;
grant execute on function public.list_assets_since(bigint, uuid, int) to anon, authenticated;


-- ======================================================================
-- (ج) حسابات موجودة مسبقًا + منح دور المسؤول
-- ======================================================================
-- المشغِّل on_auth_user_created يعمل للحسابات الجديدة فقط؛ نكمل هنا الملف الشخصي والمحفظة لمن سُجِّل قبل التشغيل.
insert into public.profiles (id) select id from auth.users on conflict do nothing;
insert into public.user_wallets (user_id) select id from auth.users on conflict do nothing;

-- منح دور المسؤول (قرار خادمي): يعمل فقط إن كان الحساب موجودًا وبريده مؤكَّدًا.
insert into public.user_roles (user_id, role)
select id, 'administrator' from auth.users
where email = 'bilalalmomani90@gmail.com' and email_confirmed_at is not null
on conflict do nothing;

-- ======================================================================
-- (د) تحقق: يجب أن يظهر tables_created = 21، وسطر المسؤول إن كان حسابك مؤكَّدًا
-- ======================================================================
select count(*) as tables_created from information_schema.tables where table_schema = 'public' and table_name in (
  'profiles','user_roles','projects','user_wallets','point_transactions','admin_audit_logs',
  'asset_categories','asset_licenses','assets','asset_versions','asset_unlocks','app_settings','reward_ad_events',
  'subscription_plans','point_packages','payment_settings','bank_transfer_settings','payment_orders','payment_events',
  'bank_transfer_submissions','user_subscriptions');

select u.email, r.role from public.user_roles r join auth.users u on u.id = r.user_id;
