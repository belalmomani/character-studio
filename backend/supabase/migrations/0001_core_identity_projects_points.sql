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
