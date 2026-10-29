package app.characterstudio.export

enum class ExportFormat(val mime: String, val extension: String, val supportsAlpha: Boolean, val hasQuality: Boolean) {
    Png("image/png", "png", true, false),
    Jpeg("image/jpeg", "jpg", false, true),
    Webp("image/webp", "webp", true, true),
}

data class ExportOptions(
    val format: ExportFormat = ExportFormat.Png,
    val size: Int = 1024,
    /** خلفية شفافة (تُتجاهل للصيغ التي لا تدعم الشفافية). */
    val transparent: Boolean = true,
    /** لون الخلفية عند عدم الشفافية وعدم وجود خلفية محفوظة في المشروع. */
    val backgroundArgb: Long = 0xFFFFFFFF,
    val quality: Int = 90,
    val includeSkeleton: Boolean = false,
)

object ExportRules {
    val ALL_SIZES = listOf(1024, 2048, 4096)

    /**
     * الدقات التي تتسع لها ذاكرة الجهاز فعلًا. نقدّر 3× حجم الصورة (الصورة + الترميز + هامش).
     * 1024 متاحة دائمًا.
     */
    fun supportedSizes(maxHeapBytes: Long): List<Int> =
        ALL_SIZES.filter { it == 1024 || it.toLong() * it * 4 * 3 <= maxHeapBytes }

    /** لون الخلفية الفعلي المرسوم، أو null = شفافية حقيقية. المشروع بخلفية محفوظة يتقدّم دائمًا. */
    fun effectiveBackground(o: ExportOptions, sceneBackground: Long?): Long? = when {
        sceneBackground != null -> sceneBackground
        o.format.supportsAlpha && o.transparent -> null
        else -> o.backgroundArgb
    }

    fun sanitized(o: ExportOptions, maxHeapBytes: Long): ExportOptions {
        val size = if (o.size in supportedSizes(maxHeapBytes)) o.size else 1024
        return o.copy(size = size, quality = o.quality.coerceIn(1, 100))
    }

    fun fileName(projectName: String, o: ExportOptions): String {
        val base = projectName.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(40)
            .ifEmpty { "character" }
        return "${base}_${o.size}.${o.format.extension}"
    }
}
