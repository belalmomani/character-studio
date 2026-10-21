package com.humanstudio3d.ui.viewer

import android.view.Choreographer
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.filament.IndirectLight
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import com.humanstudio3d.domain.AnimationController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Real 3D rendering of a GLB with Google's Filament engine (ModelViewer from filament-utils).
 * Orbit and pinch-zoom gestures come from ModelViewer. Animations embedded in the GLB are driven
 * by [AnimationController] (clip, play/pause, speed, loop) from the frame callback, because
 * ModelViewer 1.68 does not play animations itself.
 *
 * To reset the camera, the caller recreates this composable (`key(...)`), which rebuilds the
 * engine and reloads the file.
 */
@Composable
fun FilamentGlbViewer(
    glbFile: File,
    clipIndex: Int,
    playing: Boolean,
    speed: Float,
    loop: Boolean,
    modifier: Modifier = Modifier,
    onLoaded: (animationNames: List<String>) -> Unit,
    onLoadFailed: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaceView = remember { SurfaceView(context) }
    val controller = remember { AnimationController() }
    var viewer by remember { mutableStateOf<ModelViewer?>(null) }

    SideEffect {
        if (controller.clipIndex != clipIndex) {
            controller.clipIndex = clipIndex
            controller.restart()
        }
        controller.playing = playing
        controller.speed = speed
        controller.loop = loop
    }

    DisposableEffect(surfaceView) {
        FilamentNative.ensureLoaded()
        val modelViewer = ModelViewer(surfaceView)
        surfaceView.setOnTouchListener(modelViewer)

        // ModelViewer adds a sun light only; without ambient light the shaded side is black.
        // This light is released together with the engine when ModelViewer shuts down.
        val ambient = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.8f, 0.8f, 0.8f))
            .intensity(30_000f)
            .build(modelViewer.engine)
        modelViewer.scene.indirectLight = ambient

        val choreographer = Choreographer.getInstance()
        var lastFrameNanos = 0L
        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                choreographer.postFrameCallback(this)

                val delta = if (lastFrameNanos == 0L) 0.0 else (frameTimeNanos - lastFrameNanos) / 1e9
                lastFrameNanos = frameTimeNanos

                val animator = modelViewer.animator
                if (animator != null && animator.animationCount > 0) {
                    val clip = controller.clipIndex.coerceIn(0, animator.animationCount - 1)
                    controller.advance(delta, animator.getAnimationDuration(clip))
                    animator.applyAnimation(clip, controller.time.toFloat())
                    animator.updateBoneMatrices()
                }
                modelViewer.render(frameTimeNanos)
            }
        }
        // Render only while the screen is resumed to save battery.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    lastFrameNanos = 0L
                    choreographer.removeFrameCallback(frameCallback)
                    choreographer.postFrameCallback(frameCallback)
                }
                Lifecycle.Event.ON_PAUSE -> choreographer.removeFrameCallback(frameCallback)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewer = modelViewer

        onDispose {
            // Stop rendering first. ModelViewer destroys its model, renderer, scene and engine
            // itself when the SurfaceView detaches from the window, so nothing is destroyed
            // here (touching a destroyed engine would crash).
            lifecycleOwner.lifecycle.removeObserver(observer)
            choreographer.removeFrameCallback(frameCallback)
            surfaceView.setOnTouchListener(null)
            viewer = null
        }
    }

    val current = viewer
    if (current != null) {
        LaunchedEffect(current, glbFile) {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { glbFile.readBytes() }.getOrNull()
            }
            if (bytes == null) {
                onLoadFailed()
                return@LaunchedEffect
            }
            // Filament calls must stay on the main thread; LaunchedEffect runs there.
            current.loadModelGlb(ByteBuffer.wrap(bytes))
            current.transformToUnitCube()

            if (current.asset == null) {
                onLoadFailed()
            } else {
                val animator = current.animator
                val names = if (animator == null) {
                    emptyList()
                } else {
                    List(animator.animationCount) { index -> animator.getAnimationName(index).orEmpty() }
                }
                onLoaded(names)
            }
        }
    }

    AndroidView(factory = { surfaceView }, modifier = modifier)
}

/** Loads Filament's native libraries once. */
private object FilamentNative {
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            Utils.init()
            loaded = true
        }
    }
}
