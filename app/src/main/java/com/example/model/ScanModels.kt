package com.example.model

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.hypot

/**
 * Normalized 2D coordinate in [0f..1f] relative to the source image width and height.
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float
) {
    fun clamp(): NormalizedPoint = NormalizedPoint(
        x = x.coerceIn(0.01f, 0.99f),
        y = y.coerceIn(0.01f, 0.99f)
    )

    fun distanceTo(other: NormalizedPoint): Float =
        hypot(x - other.x, y - other.y)
}

/**
 * Target aspect ratio presets for perspective warp output.
 */
enum class AspectRatioPreset(
    val label: String,
    val shortName: String,
    val widthToHeightRatio: Float?
) {
    AUTO_EUCLIDEAN("Natural Euclidean", "Auto", null),
    ISO_A4("ISO A4 (210 × 297 mm)", "A4", 1f / 1.4142f),
    US_LETTER("US Letter (8.5 × 11 in)", "Letter", 8.5f / 11f),
    RECEIPT("Tall Receipt (1 : 2)", "Receipt", 0.5f)
}

/**
 * "True Flatbed Scanner" image enhancement filter modes.
 */
enum class ScanFilterMode(
    val title: String,
    val subtitle: String
) {
    CRISP_BW(
        title = "Crisp B&W",
        subtitle = "Shadow removal + Adaptive Gaussian Threshold"
    ),
    MAGIC_COLOR(
        title = "Magic Color",
        subtitle = "LAB CLAHE + Illumination leveling + Vivid ink"
    ),
    ORIGINAL(
        title = "Original",
        subtitle = "Perspective-warped & auto-oriented true color"
    )
}

/**
 * Result of real-time or single-frame 4-point contour detection.
 */
data class DetectedQuad(
    val corners: List<NormalizedPoint>, // Always ordered: TL, TR, BR, BL
    val areaRatio: Float,               // Fraction of frame covered by contour [0..1]
    val confidence: Float,              // Edge lock score [0..1]
    val isConvex: Boolean = true
) {
    companion object {
        fun defaultInsetQuad(inset: Float = 0.08f): List<NormalizedPoint> = listOf(
            NormalizedPoint(inset, inset),
            NormalizedPoint(1f - inset, inset),
            NormalizedPoint(1f - inset, 1f - inset),
            NormalizedPoint(inset, 1f - inset)
        )
    }
}

/**
 * Word-level OCR bounding box & text from ML Kit Text Recognition v2.
 */
data class OcrWordElement(
    val text: String,
    val boundingBox: Rect,
    val angleDegrees: Float
)

/**
 * Line-level OCR bounding box, text, and child word elements.
 */
data class OcrTextLine(
    val text: String,
    val boundingBox: Rect,
    val angleDegrees: Float,
    val elements: List<OcrWordElement>
)

/**
 * Block-level OCR grouping from ML Kit.
 */
data class OcrTextBlock(
    val text: String,
    val boundingBox: Rect,
    val lines: List<OcrTextLine>
)

/**
 * Complete OCR extraction and orientation analysis result for a single scanned page.
 */
data class OcrPageResult(
    val fullText: String,
    val blocks: List<OcrTextBlock>,
    val detectedRotationDegrees: Int, // 0, 90, 180, or 270
    val sourceWidth: Int,
    val sourceHeight: Int
) {
    val lineCount: Int
        get() = blocks.sumOf { it.lines.size }

    val wordCount: Int
        get() = blocks.sumOf { block -> block.lines.sumOf { it.elements.size } }
}

/**
 * Represents a single page in the active multi-page document scan session.
 */
data class ScannedPage(
    val id: String,
    val title: String,
    val sourceBitmap: Bitmap,
    val corners: List<NormalizedPoint>, // Ordered [TL, TR, BR, BL]
    val filterMode: ScanFilterMode,
    val aspectRatioPreset: AspectRatioPreset,
    val manualRotationDegrees: Int = 0,
    val autoOrientationDegrees: Int = 0,
    val processedBitmap: Bitmap,
    val ocrResult: OcrPageResult,
    val processingTimeMs: Long = 0L
) {
    val resolutionLabel: String
        get() = "${processedBitmap.width} × ${processedBitmap.height} px"
}

/**
 * High-level screen navigation state.
 */
enum class ScannerScreenRoute {
    CAMERA_PREVIEW,
    CORNER_ADJUST,
    SESSION_REVIEW
}

/**
 * Export operation result for user feedback and file sharing.
 */
data class ExportArtifact(
    val title: String,
    val displayPath: String,
    val mimeType: String,
    val shareableFilePath: String?,
    val pageCount: Int,
    val fileSizeBytes: Long
)
