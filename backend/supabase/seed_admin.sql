-- يُنفَّذ يدويًا من SQL Editor في لوحة Supabase، بعد أن يسجّل صاحب البريد دخوله ويؤكد بريده.
-- منح الدور قرار خادمي؛ لا يمنحه التطبيق ولا لوحة الويب لنفسها.
insert into public.user_roles (user_id, role)
select id, 'administrator' from auth.users
where email = 'bilalalmomani90@gmail.com' and email_confirmed_at is not null
on conflict do nothing;
