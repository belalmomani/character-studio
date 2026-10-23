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
