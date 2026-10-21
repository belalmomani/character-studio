package com.humanstudio3d.domain

/**
 * Validation for the conversion server address. Only HTTPS is accepted, so images and tokens
 * never travel unencrypted. Pure Kotlin so it can be unit-tested.
 */
object ServerUrl {
    private const val PREFIX = "https://"

    /** Returns the base URL without a trailing slash, or null when the input is not acceptable. */
    fun normalize(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (!trimmed.startsWith(PREFIX, ignoreCase = true)) return null
        val rest = trimmed.substring(PREFIX.length)
        if (rest.isEmpty() || rest.any { it.isWhitespace() || it == '?' || it == '#' }) return null
        val authority = rest.substringBefore('/')
        // No credentials inside the URL: tokens go in a separate field.
        if (authority.isEmpty() || authority.contains('@')) return null
        return PREFIX + rest
    }

    /** Host part shown to the user in the upload consent dialog. */
    fun host(normalized: String): String =
        normalized.removePrefix(PREFIX).substringBefore('/')
}
