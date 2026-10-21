package com.humanstudio3d.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationControllerTest {

    @Test
    fun loopWrapsAround() {
        val c = AnimationController()
        c.advance(1.5, 2f)
        c.advance(1.0, 2f)
        assertEquals(0.5, c.time, 1e-9)
    }

    @Test
    fun playOnceClampsAtEnd() {
        val c = AnimationController().apply { loop = false }
        c.advance(5.0, 2f)
        assertEquals(2.0, c.time, 1e-9)
    }

    @Test
    fun pausedDoesNotMove() {
        val c = AnimationController().apply { playing = false }
        c.advance(1.0, 2f)
        assertEquals(0.0, c.time, 1e-9)
    }

    @Test
    fun speedScalesTime() {
        val c = AnimationController().apply { speed = 2f }
        c.advance(0.5, 10f)
        assertEquals(1.0, c.time, 1e-9)
    }

    @Test
    fun restartResetsTime() {
        val c = AnimationController()
        c.advance(1.0, 10f)
        c.restart()
        assertEquals(0.0, c.time, 1e-9)
    }

    @Test
    fun zeroDurationIsIgnored() {
        val c = AnimationController()
        c.advance(1.0, 0f)
        assertEquals(0.0, c.time, 1e-9)
    }
}
