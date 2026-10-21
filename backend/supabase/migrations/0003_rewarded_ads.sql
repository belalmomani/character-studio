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
