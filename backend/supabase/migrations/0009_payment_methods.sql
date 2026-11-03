-- يعتمد على 0005. لم يُشغَّل على قاعدة بيانات بعد.
-- 1) get_payment_methods: يعرف التطبيق أي طرق دفع مفعّلة دون كشف إعدادات المسؤول (IBAN وغيره).
-- 2) cancel_my_order: يلغي المستخدم طلبًا لم يبدأ دفعه (لم يُرسل إشعار تحويل ولم يُفتح عند المزود).

create or replace function public.get_payment_methods() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare ps public.payment_settings%rowtype; bs public.bank_transfer_settings%rowtype;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  select * into ps from public.payment_settings where id = 1;
  select * into bs from public.bank_transfer_settings where id = 1;
  return jsonb_build_object(
    'card', coalesce(ps.card_enabled, false) and ps.card_provider is not null,
    'bank_transfer', coalesce(bs.enabled, false)
  );
end $$;
revoke all on function public.get_payment_methods() from public, anon;
grant execute on function public.get_payment_methods() to authenticated;

create or replace function public.cancel_my_order(p_order uuid) returns text
language plpgsql security definer set search_path = public as $$
begin
  update public.payment_orders set status = 'canceled'
   where id = p_order and user_id = auth.uid() and status = 'created'
     and not exists (select 1 from public.bank_transfer_submissions s where s.order_id = p_order);
  if not found then raise exception 'order_not_cancelable'; end if;
  return 'canceled';
end $$;
revoke all on function public.cancel_my_order(uuid) from public, anon;
grant execute on function public.cancel_my_order(uuid) to authenticated;
