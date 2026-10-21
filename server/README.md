# الخادم المرجعي (Reference server)

يطبّق `docs/SERVER_CONTRACT.md` بمكتبة بايثون القياسية فقط (بلا تثبيت حزم).

## الحالة الصريحة
- **منطق العقد مُختبَر فعليًا** (13 اختبارًا ناجحًا، 3 تشغيلات): المصادقة، الرفع، الحالة، التنزيل، الحذف بعد التنزيل وبعد DELETE وبعد TTL، رفض غير JPEG، حد الحجم 413، السقف اليومي 429، فشل المحرك، رفض البدء بلا رمز.
- **محرك `stub` للاختبار فقط**: يُنتج GLB فارغًا ولا يحوّل الصورة.
- **محرك `trellis2` غير منفَّذ**: يرفض العمل بخطأ صريح. المتحقَّق منه من المصدر الرسمي: `from_pretrained` و`run(image, seed, preprocess_image, pipeline_type)` يعيد `List[MeshWithVoxel]`. غير المتحقَّق: خطوة تصدير GLB، وهي تحتاج قراءة أمثلة المشروع وتجربة على NVIDIA 24GB+.
- لم يُشغَّل مع التطبيق الحقيقي، ولا مع TLS.

## التشغيل
```
cd server
HCS_TOKEN=<رمز قوي> HCS_ENGINE=stub python3 hcs_server.py   # للتجربة فقط
```
المتغيرات: `HCS_TOKEN` (إلزامي، أو `HCS_ALLOW_NO_TOKEN=1` لجهاز محلي فقط)، `HCS_ENGINE`، `HCS_HOST` (الافتراضي 127.0.0.1)، `HCS_PORT`، `HCS_MAX_IMAGE_MB` (10)، `HCS_DAILY_LIMIT` (50)، `HCS_TTL_SECONDS` (900).

التطبيق يقبل HTTPS فقط، فضع الخادم خلف reverse proxy يُنهي TLS.

## الاختبار
```
cd server/tests && python3 -m unittest test_contract -v
```

## تنبيهات
- لا يسجّل مسارات ولا رؤوس ولا رموزًا.
- مهمة واحدة في آن واحد. الصورة الأصلية تُحذف بعد انتهاء التحويل، والنتيجة تُحذف عند التنزيل.
- التراخيص: انظر `docs/SERVER_CONTRACT.md` (RMBG-2.0 غير تجاري، DINOv3 بترخيص Meta).
