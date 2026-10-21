package com.humanstudio3d.domain

import java.io.File

/**
 * Integration point for an image-to-3D provider (Meshy, Tripo, a self-hosted open-source
 * model behind a proxy, ...). Providers must be swappable without touching the UI.
 *
 * Contract for real implementations:
 * - Never upload before the user explicitly consented to this specific provider.
 * - Never embed secret API keys in the APK; talk to a proxy that holds them.
 * - Report only real stages through [onState]; never invent progress.
 * - Delete temporary files on success, failure and cancellation.
 * - Never log images, private download URLs or tokens.
 */
interface ImageTo3DService {
    /** False until a provider is wired and configured. */
    val isConfigured: Boolean

    suspend fun generate(image: File, onState: (ProcessingState) -> Unit): ImageTo3DResult
}

sealed interface ImageTo3DResult {
    data class Success(val glb: File) : ImageTo3DResult
    data class Failure(val reason: FailureReason) : ImageTo3DResult
}

/**
 * Honest default while no provider has been approved: it reports that fact and uploads nothing.
 * This is not a fake generator and never returns a model.
 */
class NotConfiguredImageTo3DService : ImageTo3DService {
    override val isConfigured: Boolean = false

    override suspend fun generate(
        image: File,
        onState: (ProcessingState) -> Unit,
    ): ImageTo3DResult = ImageTo3DResult.Failure(FailureReason.PROVIDER_NOT_CONFIGURED)
}
