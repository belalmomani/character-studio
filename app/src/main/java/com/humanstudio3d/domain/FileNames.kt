package com.humanstudio3d.domain

/** Builds safe file names for export. Pure Kotlin so it can be unit-tested. */
object FileNames {
    private val invalidChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val whitespace = Regex("\\s+")
    private const val MAX_LENGTH = 60

    fun sanitize(raw: String, fallback: String = "character"): String {
        var name = raw.replace(invalidChars, " ").trim().replace(whitespace, "_")
        if (name.endsWith(".glb", ignoreCase = true)) name = name.dropLast(4)
        name = name.trim('.', '_', ' ').take(MAX_LENGTH).trim('.', '_', ' ')
        return name.ifEmpty { fallback }
    }

    fun glbFileName(raw: String): String = sanitize(raw) + ".glb"
}
