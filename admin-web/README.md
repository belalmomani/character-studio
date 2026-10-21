# لوحة إدارة الويب (الشريحة الأولى)
منفَّذ: تسجيل دخول (Supabase Auth)، تحقق من دور administrator من جدول user_roles، لوحة معلومات (أرقام حقيقية من admin_stats)،
إعدادات الإعلانات المكافأة، سجل التدقيق. غير منفَّذ بعد: الأصول، الميزات والأسعار، الاشتراكات، المدفوعات، التحويل البنكي، المستخدمون، المصادقة الثنائية.
## التشغيل
1. شغّل migrations من 0001 إلى 0004 في Supabase.
2. أنشئ حسابك من Supabase → Authentication → Users (Add user) بالبريد وكلمة مرور قوية، ثم شغّل `backend/supabase/seed_admin.sql`.
3. ضع SUPABASE_URL والمفتاح العام (anon/publishable) في `config.js`. لا تضع service_role.
4. استضف مجلد admin-web على خدمة ملفات ثابتة (Cloudflare Pages أو Netlify مثلًا) وافتح الرابط من الهاتف.
الأمان الفعلي في الخادم (RLS ودالة is_admin)؛ الواجهة لا تمنح صلاحية.
