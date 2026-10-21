package com.humanstudio3d.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {
    @Test
    fun trailingSlashIsRemoved() = assertEquals("https://example.com", ServerUrl.normalize("https://example.com/"))

    @Test
    fun pathIsKept() = assertEquals("https://example.com/api", ServerUrl.normalize(" https://example.com/api/ "))

    @Test
    fun httpIsRejected() = assertNull(ServerUrl.normalize("http://example.com"))

    @Test
    fun emptyAndSchemeOnlyAreRejected() {
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("https://"))
        assertNull(ServerUrl.normalize("https:///path"))
    }

    @Test
    fun credentialsQueryAndSpacesAreRejected() {
        assertNull(ServerUrl.normalize("https://user:pw@example.com"))
        assertNull(ServerUrl.normalize("https://example.com?a=1"))
        assertNull(ServerUrl.normalize("https://exa mple.com"))
    }

    @Test
    fun hostIsExtracted() {
        assertEquals("example.com", ServerUrl.host("https://example.com/api"))
        assertEquals("example.com:8443", ServerUrl.host("https://example.com:8443"))
    }
}
