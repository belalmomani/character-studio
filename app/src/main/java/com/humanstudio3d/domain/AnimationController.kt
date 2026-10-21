package com.humanstudio3d.domain

/**
 * Playback state for one animation clip embedded in the loaded GLB. Pure Kotlin so it can be
 * unit-tested. It only decides *which time* to show; applying it to the model is the viewer's job.
 */
class AnimationController {
    var clipIndex: Int = 0
    var playing: Boolean = true
    var speed: Float = 1f
    var loop: Boolean = true

    /** Current time inside the clip, in seconds. */
    var time: Double = 0.0
        private set

    fun advance(deltaSeconds: Double, durationSeconds: Float) {
        if (!playing || durationSeconds <= 0f) return
        time += deltaSeconds * speed
        if (loop) {
            time %= durationSeconds
        } else if (time > durationSeconds) {
            time = durationSeconds.toDouble()
        }
    }

    fun restart() {
        time = 0.0
    }
}
