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
