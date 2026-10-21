package com.humanstudio3d.domain

import java.io.File

/** Real processing stages. The app never shows a made-up percentage. */
sealed interface ProcessingState {
    data object Idle : ProcessingState
    data object ReadyToUpload : ProcessingState
    data object Uploading : ProcessingState
    data object Generating : ProcessingState
    data object Downloading : ProcessingState
    data object Validating : ProcessingState
    data class Completed(val model: File, val report: GlbReport) : ProcessingState
    data class Failed(val reason: FailureReason) : ProcessingState
    data object Cancelled : ProcessingState
}

enum class FailureReason {
    PROVIDER_NOT_CONFIGURED,
    NETWORK,
    TIMEOUT,
    QUOTA_EXHAUSTED,
    AUTH,
    INVALID_MODEL,
    UNKNOWN,
}

enum class ImageErrorKind { UNSUPPORTED, TOO_LARGE, IO }

enum class ModelErrorKind { TOO_LARGE, INVALID, IO }

enum class ExportErrorKind { IO, VERIFY_FAILED }
