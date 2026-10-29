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
