package app.characterstudio.assets

/** بناء روابط Cloudinary وأسماء الملفات المحلية (دوال نقية قابلة للاختبار). */
object CloudinaryUrls {
    private val SAFE_ID = Regex("^[A-Za-z0-9_/\\-]+$")
    private val SAFE_CLOUD = Regex("^[A-Za-z0-9_\\-]+$")

    private fun check(cloud: String, publicId: String) {
        require(SAFE_CLOUD.matches(cloud)) { "invalid_cloud_name" }
        require(SAFE_ID.matches(publicId) && !publicId.contains("..")) { "invalid_public_id" }
    }

    /** الملف الأصلي بلا أي تحويل (لتطابق البصمة SHA-256). */
    fun original(cloud: String, publicId: String, fileType: String): String {
        check(cloud, publicId)
        require(fileType == "png" || fileType == "webp") { "invalid_file_type" }
        return "https://res.cloudinary.com/$cloud/image/upload/$publicId.$fileType"
    }

    /** صورة مصغرة بصيغة WebP (تدعم الشفافية في كل إصدارات Android المدعومة). */
    fun thumb(cloud: String, publicId: String, size: Int = 256): String {
        check(cloud, publicId)
        val s = size.coerceIn(32, 1024)
        return "https://res.cloudinary.com/$cloud/image/upload/c_limit,w_$s,h_$s,f_webp,q_auto/$publicId"
    }
}

object AssetFileNames {
    /** اسم ملف آمن (لا يسمح بمسارات) لنسخة أصل محفوظة محليًا. */
    fun of(assetId: String, version: Int, fileType: String): String {
        require(fileType == "png" || fileType == "webp") { "invalid_file_type" }
        require(version > 0) { "invalid_version" }
        val safe = assetId.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(64)
        require(safe.isNotEmpty()) { "invalid_asset_id" }
        return "${safe}_$version.$fileType"
    }

    /** يتحقق أن الاسم المقروء من مشروع محفوظ آمن (لا مسارات) قبل فتحه. */
    fun safeExisting(name: String): String {
        require(Regex("^[A-Za-z0-9_\\-]+\\.(png|webp)$").matches(name)) { "invalid_file_name" }
        return name
    }
}
