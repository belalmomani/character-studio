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
