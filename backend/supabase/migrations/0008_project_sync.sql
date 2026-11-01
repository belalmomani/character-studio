-- يعتمد على 0001 (جدول projects وسياسة المالك). لم يُشغَّل على قاعدة بيانات بعد.
-- مزامنة المشاريع: كتابة بتحقق من رقم النسخة (optimistic concurrency) ضمن RLS؛ لا يكتب المستخدم إلا في مشاريعه.

alter table public.projects drop constraint if exists projects_document_size;
alter table public.projects add constraint projects_document_size check (pg_column_size(document) < 2000000);

-- expected_revision = 0 → إنشاء مشروع جديد (revision = 1).
-- غير ذلك → تحديث بشرط أن تكون النسخة الحالية في السحابة هي المتوقعة، وإلا يرجع revision_conflict دون أي تعديل.
create or replace function public.sync_push_project(
  p_id uuid, p_name text, p_art_style text, p_format_version int, p_document jsonb,
  p_created_at timestamptz, p_updated_at timestamptz, p_expected_revision int
) returns jsonb language plpgsql security invoker set search_path = public as $$
declare v_uid uuid := auth.uid(); v_rev int;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if p_expected_revision is null or p_expected_revision < 0 then raise exception 'invalid_revision'; end if;

  if p_expected_revision = 0 then
    insert into public.projects(id, owner_id, name, art_style, format_version, document, revision, created_at, updated_at)
    values (p_id, v_uid, trim(p_name), p_art_style, p_format_version, coalesce(p_document, '{}'::jsonb), 1,
            coalesce(p_created_at, now()), coalesce(p_updated_at, now()));
    return jsonb_build_object('ok', true, 'revision', 1);
  end if;

  update public.projects
     set name = trim(p_name), art_style = p_art_style, format_version = p_format_version,
         document = coalesce(p_document, '{}'::jsonb), revision = revision + 1,
         updated_at = coalesce(p_updated_at, now())
   where id = p_id and owner_id = v_uid and revision = p_expected_revision
   returning revision into v_rev;
  if not found then return jsonb_build_object('ok', false, 'reason', 'revision_conflict'); end if;
  return jsonb_build_object('ok', true, 'revision', v_rev);
end $$;
revoke all on function public.sync_push_project(uuid, text, text, int, jsonb, timestamptz, timestamptz, int) from public, anon;
grant execute on function public.sync_push_project(uuid, text, text, int, jsonb, timestamptz, timestamptz, int) to authenticated;
