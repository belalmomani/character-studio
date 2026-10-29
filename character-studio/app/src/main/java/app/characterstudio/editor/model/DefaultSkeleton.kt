package app.characterstudio.editor.model

/** الهيكل المرجعي الافتراضي على لوحة 1024×1024 (يمين/يسار بالنسبة للشخصية). */
object DefaultSkeleton {
    private val data = listOf(
        Triple("head_center", "مركز الرأس", 512f to 150f),
        Triple("head_base", "قاعدة الرأس", 512f to 230f),
        Triple("neck", "الرقبة", 512f to 262f),
        Triple("torso_top", "أعلى الجذع", 512f to 300f),
        Triple("torso_bottom", "أسفل الجذع", 512f to 520f),
        Triple("shoulder_r", "الكتف الأيمن", 440f to 305f),
        Triple("shoulder_l", "الكتف الأيسر", 584f to 305f),
        Triple("elbow_r", "المرفق الأيمن", 400f to 430f),
        Triple("elbow_l", "المرفق الأيسر", 624f to 430f),
        Triple("wrist_r", "الرسغ الأيمن", 370f to 560f),
        Triple("wrist_l", "الرسغ الأيسر", 654f to 560f),
        Triple("hand_r", "اليد اليمنى", 360f to 610f),
        Triple("hand_l", "اليد اليسرى", 664f to 610f),
        Triple("hip_r", "الورك الأيمن", 470f to 540f),
        Triple("hip_l", "الورك الأيسر", 554f to 540f),
        Triple("knee_r", "الركبة اليمنى", 460f to 720f),
        Triple("knee_l", "الركبة اليسرى", 564f to 720f),
        Triple("ankle_r", "الكاحل الأيمن", 455f to 900f),
        Triple("ankle_l", "الكاحل الأيسر", 569f to 900f),
        Triple("weapon_r", "تثبيت سلاح (يمين)", 340f to 640f),
        Triple("weapon_l", "تثبيت سلاح (يسار)", 684f to 640f),
        Triple("accessory_head", "تثبيت إكسسوار الرأس", 512f to 80f),
        Triple("accessory_back", "تثبيت إكسسوار الظهر", 512f to 400f),
        Triple("accessory_waist", "تثبيت إكسسوار الخصر", 512f to 560f),
    )

    fun anchors(): List<Anchor> = data.map { (id, label, p) -> Anchor(id, label, p.first, p.second) }

    /** وصلات الرسم بين النقاط. */
    val bones: List<Pair<String, String>> = listOf(
        "head_center" to "head_base", "head_base" to "neck", "neck" to "torso_top",
        "torso_top" to "torso_bottom",
        "torso_top" to "shoulder_r", "torso_top" to "shoulder_l",
        "shoulder_r" to "elbow_r", "elbow_r" to "wrist_r", "wrist_r" to "hand_r",
        "shoulder_l" to "elbow_l", "elbow_l" to "wrist_l", "wrist_l" to "hand_l",
        "torso_bottom" to "hip_r", "torso_bottom" to "hip_l",
        "hip_r" to "knee_r", "knee_r" to "ankle_r",
        "hip_l" to "knee_l", "knee_l" to "ankle_l",
    )
}
