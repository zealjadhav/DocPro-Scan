package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.export.PdfExportManager
import com.example.model.AspectRatioPreset
import com.example.model.DetectedQuad
import com.example.model.ExportArtifact
import com.example.model.NormalizedPoint
import com.example.model.ScanFilterMode
import com.example.model.ScannedPage
import com.example.model.ScannerScreenRoute
import com.example.vision.DocumentProcessor
import com.example.vision.SampleDocumentGenerator
import java.util.UUID
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI State for the entire Document Scanner application.
 */
data class ScannerUiState(
    val currentRoute: ScannerScreenRoute = ScannerScreenRoute.CAMERA_PREVIEW,
    val openCvReady: Boolean = false,
    // Camera & Real-time Edge Detection State
    val isAutoCaptureEnabled: Boolean = true,
    val isFlashEnabled: Boolean = false,
    val liveDetectedQuad: DetectedQuad? = null,
    val autoCaptureProgress: Float = 0f, // 0f..1f stability lock progress
    val isCapturingFrame: Boolean = false,
    // Active Draft / Re-crop Page State (CornerAdjustScreen)
    val draftSourceBitmap: Bitmap? = null,
    val draftCorners: List<NormalizedPoint> = DetectedQuad.defaultInsetQuad(),
    val draftFilterMode: ScanFilterMode = ScanFilterMode.CRISP_BW,
    val draftAspectRatio: AspectRatioPreset = AspectRatioPreset.AUTO_EUCLIDEAN,
    val editingExistingPageId: String? = null,
    // Multi-Page Session State (SessionReviewScreen)
    val pages: List<ScannedPage> = emptyList(),
    val selectedPageId: String? = null,
    val isProcessingScan: Boolean = false,
    val processingStatusMessage: String = "",
    // Export & OCR Modal State
    val activeOcrModalPageId: String? = null,
    val lastExportArtifact: ExportArtifact? = null,
    val statusBannerMessage: String? = null
) {
    val selectedPage: ScannedPage?
        get() = pages.firstOrNull { it.id == selectedPageId } ?: pages.firstOrNull()
}

class ScannerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    // Real-time contour smoothing and stability tracking for Auto-Capture
    private var smoothedCorners: List<NormalizedPoint>? = null
    private var stableFrameCount: Int = 0
    private var lastAutoCaptureTimestampMs: Long = 0L

    companion object {
        private const val REQUIRED_STABLE_FRAMES = 14
        private const val CORNER_STABILITY_DELTA_MAX = 0.028f
        private const val AUTO_CAPTURE_COOLDOWN_MS = 3200L
    }

    init {
        val ready = DocumentProcessor.ensureOpenCvInitialized()
        _uiState.update { it.copy(openCvReady = ready) }
    }

    fun navigateTo(route: ScannerScreenRoute) {
        _uiState.update { it.copy(currentRoute = route) }
    }

    fun toggleAutoCapture() {
        stableFrameCount = 0
        _uiState.update {
            it.copy(
                isAutoCaptureEnabled = !it.isAutoCaptureEnabled,
                autoCaptureProgress = 0f
            )
        }
    }

    fun toggleFlash() {
        _uiState.update { it.copy(isFlashEnabled = !it.isFlashEnabled) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    fun dismissExportArtifact() {
        _uiState.update { it.copy(lastExportArtifact = null) }
    }

    fun openOcrInspector(pageId: String?) {
        _uiState.update { it.copy(activeOcrModalPageId = pageId) }
    }

    /**
     * Called by CameraX `ImageAnalysis.Analyzer` on each frame with the OpenCV-detected
     * 4-point convex contour (or null if no valid document quad is in view).
     *
     * Performs exponential smoothing on the 4 corners for a fluid polygon overlay and returns
     * `true` when the contour has remained locked and steady long enough to trigger Auto-Capture.
     */
    fun onLiveQuadDetected(detectedQuad: DetectedQuad?): Boolean {
        val state = _uiState.value
        if (state.isCapturingFrame || state.isProcessingScan ||
            state.currentRoute != ScannerScreenRoute.CAMERA_PREVIEW
        ) {
            return false
        }

        if (detectedQuad == null || detectedQuad.corners.size != 4) {
            stableFrameCount = (stableFrameCount - 2).coerceAtLeast(0)
            if (stableFrameCount == 0) {
                smoothedCorners = null
            }
            _uiState.update {
                it.copy(
                    liveDetectedQuad = if (stableFrameCount == 0) null else it.liveDetectedQuad,
                    autoCaptureProgress = (stableFrameCount.toFloat() / REQUIRED_STABLE_FRAMES).coerceIn(0f, 1f)
                )
            }
            return false
        }

        val currentSmoothed = smoothedCorners
        val nextCorners = if (currentSmoothed == null || currentSmoothed.size != 4) {
            stableFrameCount = 1
            detectedQuad.corners
        } else {
            // Measure max corner displacement since previous frame
            var maxDelta = 0f
            val lerped = List(4) { i ->
                val prev = currentSmoothed[i]
                val curr = detectedQuad.corners[i]
                val d = hypot(curr.x - prev.x, curr.y - prev.y)
                if (d > maxDelta) maxDelta = d
                // Smooth factor 0.38f prevents jitter while tracking paper movement responsively
                NormalizedPoint(
                    x = prev.x + (curr.x - prev.x) * 0.38f,
                    y = prev.y + (curr.y - prev.y) * 0.38f
                )
            }

            if (maxDelta <= CORNER_STABILITY_DELTA_MAX && detectedQuad.areaRatio >= 0.16f) {
                stableFrameCount = (stableFrameCount + 1).coerceAtMost(REQUIRED_STABLE_FRAMES)
            } else {
                stableFrameCount = (stableFrameCount - 1).coerceAtLeast(1)
            }
            lerped
        }

        smoothedCorners = nextCorners
        val progress = if (state.isAutoCaptureEnabled) {
            (stableFrameCount.toFloat() / REQUIRED_STABLE_FRAMES).coerceIn(0f, 1f)
        } else {
            0f
        }

        _uiState.update {
            it.copy(
                liveDetectedQuad = detectedQuad.copy(corners = nextCorners),
                autoCaptureProgress = progress
            )
        }

        val now = SystemClock.elapsedRealtime()
        if (state.isAutoCaptureEnabled &&
            stableFrameCount >= REQUIRED_STABLE_FRAMES &&
            now - lastAutoCaptureTimestampMs > AUTO_CAPTURE_COOLDOWN_MS
        ) {
            stableFrameCount = 0
            lastAutoCaptureTimestampMs = now
            return true
        }
        return false
    }

    fun setCapturingFrame(capturing: Boolean) {
        _uiState.update { it.copy(isCapturingFrame = capturing, autoCaptureProgress = 0f) }
    }

    /**
     * Accepts a newly captured camera [Bitmap] (or imported photo / sample photo), runs
     * OpenCV 4-point contour detection to initialize the corner handles, and navigates to
     * [ScannerScreenRoute.CORNER_ADJUST] so the user can verify or fine-tune the 4 corners
     * with the magnifying loupe.
     */
    fun onRawDocumentCaptured(
        bitmap: Bitmap,
        preDetectedCorners: List<NormalizedPoint>? = null
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCapturingFrame = false,
                    isProcessingScan = true,
                    processingStatusMessage = "Detecting 4-point document contour…"
                )
            }

            val detectedCorners = withContext(Dispatchers.Default) {
                preDetectedCorners?.takeIf { it.size == 4 }
                    ?: DocumentProcessor.detectDocumentQuad(bitmap)?.corners
                    ?: DetectedQuad.defaultInsetQuad(0.08f)
            }

            _uiState.update {
                it.copy(
                    isProcessingScan = false,
                    processingStatusMessage = "",
                    draftSourceBitmap = bitmap,
                    draftCorners = DocumentProcessor.orderCornersClockwise(detectedCorners),
                    editingExistingPageId = null,
                    currentRoute = ScannerScreenRoute.CORNER_ADJUST
                )
            }
        }
    }

    /**
     * Loads a realistic skewed sample document photo (Invoice, Tech Spec, or Expense Receipt)
     * and opens the Corner Adjust screen with OpenCV-detected corners.
     */
    fun loadSampleDocumentForCrop(
        template: SampleDocumentGenerator.SampleTemplate = SampleDocumentGenerator.SampleTemplate.INVOICE
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingScan = true,
                    processingStatusMessage = "Rendering sample camera frame & running OpenCV contour lock…"
                )
            }
            val sampleBitmap = withContext(Dispatchers.Default) {
                SampleDocumentGenerator.generateSampleCameraPhoto(template)
            }
            val detected = withContext(Dispatchers.Default) {
                DocumentProcessor.detectDocumentQuad(sampleBitmap)?.corners
                    ?: DetectedQuad.defaultInsetQuad(0.09f)
            }
            _uiState.update {
                it.copy(
                    isProcessingScan = false,
                    processingStatusMessage = "",
                    draftSourceBitmap = sampleBitmap,
                    draftCorners = detected,
                    draftFilterMode = if (template == SampleDocumentGenerator.SampleTemplate.INVOICE) {
                        ScanFilterMode.MAGIC_COLOR
                    } else {
                        ScanFilterMode.CRISP_BW
                    },
                    editingExistingPageId = null,
                    currentRoute = ScannerScreenRoute.CORNER_ADJUST
                )
            }
        }
    }

    /**
     * Imports a photo from the Android Photo Picker (`Uri`), corrects EXIF orientation,
     * detects its 4 document corners via OpenCV, and opens the Corner Adjust screen.
     */
    fun onPhotoImportedFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingScan = true,
                    processingStatusMessage = "Loading image & detecting document boundary…"
                )
            }
            val bitmap = withContext(Dispatchers.IO) {
                decodeBitmapFromUriWithExif(context, uri)
            }
            if (bitmap != null) {
                onRawDocumentCaptured(bitmap)
            } else {
                _uiState.update {
                    it.copy(
                        isProcessingScan = false,
                        processingStatusMessage = "",
                        statusBannerMessage = "Could not decode selected image."
                    )
                }
            }
        }
    }

    /**
     * Updates a single corner handle index (0=TL, 1=TR, 2=BR, 3=BL) during interactive drag.
     */
    fun updateDraftCorner(index: Int, newPoint: NormalizedPoint) {
        if (index !in 0..3) return
        _uiState.update { state ->
            val mutable = state.draftCorners.toMutableList()
            if (mutable.size == 4) {
                mutable[index] = newPoint.clamp()
            }
            state.copy(draftCorners = mutable)
        }
    }

    fun resetDraftCornersToFullFrame() {
        _uiState.update {
            it.copy(draftCorners = DetectedQuad.defaultInsetQuad(0.03f))
        }
    }

    fun reDetectDraftCorners() {
        val source = _uiState.value.draftSourceBitmap ?: return
        viewModelScope.launch {
            val detected = withContext(Dispatchers.Default) {
                DocumentProcessor.detectDocumentQuad(source)?.corners
                    ?: DetectedQuad.defaultInsetQuad(0.08f)
            }
            _uiState.update {
                it.copy(
                    draftCorners = detected,
                    statusBannerMessage = "Locked onto 4-point document contour"
                )
            }
        }
    }

    fun setDraftFilterMode(filterMode: ScanFilterMode) {
        _uiState.update { it.copy(draftFilterMode = filterMode) }
    }

    fun setDraftAspectRatio(preset: AspectRatioPreset) {
        _uiState.update { it.copy(draftAspectRatio = preset) }
    }

    /**
     * Executes the full "True Flatbed Scanner" pipeline on the current draft page:
     * 1. Orders the 4 user-confirmed corners clockwise.
     * 2. Applies OpenCV 4-point Homography Perspective Transform (`getPerspectiveTransform` + `warpPerspective`).
     * 3. Runs ML Kit Text Recognition preliminary orientation pass to auto-rotate 90/180/270 deg right-side up.
     * 4. Applies the selected enhancement filter (`Crisp B&W`, `Magic Color`, or `Original`).
     * 5. Extracts full on-device OCR (`TextBlock`, `Line`, `Element` bounding boxes) and adds/updates
     *    the page in the active multi-page session queue.
     */
    fun confirmCropAndProcessPage() {
        val state = _uiState.value
        val sourceBitmap = state.draftSourceBitmap ?: return
        val orderedCorners = DocumentProcessor.orderCornersClockwise(state.draftCorners)
        val filterMode = state.draftFilterMode
        val aspectPreset = state.draftAspectRatio
        val editingId = state.editingExistingPageId

        viewModelScope.launch {
            val t0 = SystemClock.elapsedRealtime()
            _uiState.update {
                it.copy(
                    isProcessingScan = true,
                    processingStatusMessage = "Warping 4-point perspective & removing shadows…"
                )
            }

            // Step 1: 4-Point Homography Perspective Warp
            val warpedBitmap = withContext(Dispatchers.Default) {
                DocumentProcessor.warpPerspectiveDocument(
                    sourceBitmap = sourceBitmap,
                    corners = orderedCorners,
                    aspectRatioPreset = aspectPreset
                )
            }

            _uiState.update {
                it.copy(processingStatusMessage = "Running ML Kit OCR & auto-orientation detection…")
            }

            // Step 2: ML Kit Auto-Orientation (0, 90, 180, 270 deg) + OCR Extraction
            val orientedOcr = PdfExportManager.autoOrientAndExtractOcr(warpedBitmap)

            _uiState.update {
                it.copy(processingStatusMessage = "Applying ${filterMode.title} flatbed filter…")
            }

            // Step 3: Apply Flatbed Enhancement Filter (Crisp B&W / Magic Color / Original)
            val enhancedBitmap = withContext(Dispatchers.Default) {
                DocumentProcessor.applyEnhancementFilter(
                    warpedBitmap = orientedOcr.uprightWarpedBitmap,
                    filterMode = filterMode
                )
            }

            // Step 4: If Crisp B&W or Magic Color improved contrast, re-run OCR if initial pass had few words
            val finalOcr = if (orientedOcr.ocrPageResult.wordCount < 5) {
                PdfExportManager.extractOcrForBitmap(
                    bitmap = enhancedBitmap,
                    detectedRotationDegrees = orientedOcr.autoRotationAppliedDegrees
                )
            } else {
                orientedOcr.ocrPageResult
            }

            val elapsedMs = SystemClock.elapsedRealtime() - t0

            _uiState.update { current ->
                val updatedPages = if (editingId != null) {
                    current.pages.map { existing ->
                        if (existing.id == editingId) {
                            existing.copy(
                                corners = orderedCorners,
                                filterMode = filterMode,
                                aspectRatioPreset = aspectPreset,
                                autoOrientationDegrees = orientedOcr.autoRotationAppliedDegrees,
                                manualRotationDegrees = 0,
                                processedBitmap = enhancedBitmap,
                                ocrResult = finalOcr,
                                processingTimeMs = elapsedMs
                            )
                        } else {
                            existing
                        }
                    }
                } else {
                    val newPageNumber = current.pages.size + 1
                    val newPage = ScannedPage(
                        id = UUID.randomUUID().toString(),
                        title = "Page $newPageNumber",
                        sourceBitmap = sourceBitmap,
                        corners = orderedCorners,
                        filterMode = filterMode,
                        aspectRatioPreset = aspectPreset,
                        autoOrientationDegrees = orientedOcr.autoRotationAppliedDegrees,
                        manualRotationDegrees = 0,
                        processedBitmap = enhancedBitmap,
                        ocrResult = finalOcr,
                        processingTimeMs = elapsedMs
                    )
                    current.pages + newPage
                }

                val targetSelectedId = editingId ?: updatedPages.lastOrNull()?.id
                current.copy(
                    isProcessingScan = false,
                    processingStatusMessage = "",
                    pages = renumberPages(updatedPages),
                    selectedPageId = targetSelectedId,
                    draftSourceBitmap = null,
                    editingExistingPageId = null,
                    currentRoute = ScannerScreenRoute.SESSION_REVIEW,
                    statusBannerMessage = buildString {
                        append("Processed in ${elapsedMs}ms")
                        if (orientedOcr.autoRotationAppliedDegrees != 0) {
                            append(" • Auto-rotated ${orientedOcr.autoRotationAppliedDegrees}°")
                        }
                        if (finalOcr.wordCount > 0) {
                            append(" • ${finalOcr.wordCount} OCR words indexed")
                        }
                    }
                )
            }
        }
    }

    /**
     * Opens an existing [ScannedPage] in [ScannerScreenRoute.CORNER_ADJUST] so the user can
     * re-crop its 4 corners, change its aspect ratio, or re-warp.
     */
    fun startReCropPage(pageId: String) {
        val page = _uiState.value.pages.firstOrNull { it.id == pageId } ?: return
        _uiState.update {
            it.copy(
                draftSourceBitmap = page.sourceBitmap,
                draftCorners = page.corners,
                draftFilterMode = page.filterMode,
                draftAspectRatio = page.aspectRatioPreset,
                editingExistingPageId = page.id,
                currentRoute = ScannerScreenRoute.CORNER_ADJUST
            )
        }
    }

    fun selectPage(pageId: String) {
        _uiState.update { it.copy(selectedPageId = pageId) }
    }

    /**
     * Changes the enhancement filter (`Crisp B&W`, `Magic Color`, `Original`) on an already-scanned page
     * in the session review screen and re-renders the flatbed output immediately.
     */
    fun updatePageFilter(pageId: String, newFilter: ScanFilterMode) {
        val page = _uiState.value.pages.firstOrNull { it.id == pageId } ?: return
        if (page.filterMode == newFilter) return

        viewModelScope.launch {
            val t0 = SystemClock.elapsedRealtime()
            _uiState.update {
                it.copy(
                    isProcessingScan = true,
                    processingStatusMessage = "Applying ${newFilter.title} filter…"
                )
            }

            val updatedBitmap = withContext(Dispatchers.Default) {
                val warped = DocumentProcessor.warpPerspectiveDocument(
                    sourceBitmap = page.sourceBitmap,
                    corners = page.corners,
                    aspectRatioPreset = page.aspectRatioPreset
                )
                val totalRotation = (page.autoOrientationDegrees + page.manualRotationDegrees) % 360
                val oriented = DocumentProcessor.rotateBitmap(warped, totalRotation)
                DocumentProcessor.applyEnhancementFilter(oriented, newFilter)
            }

            val elapsed = SystemClock.elapsedRealtime() - t0
            _uiState.update { state ->
                state.copy(
                    isProcessingScan = false,
                    processingStatusMessage = "",
                    pages = state.pages.map {
                        if (it.id == pageId) {
                            it.copy(
                                filterMode = newFilter,
                                processedBitmap = updatedBitmap,
                                processingTimeMs = elapsed
                            )
                        } else {
                            it
                        }
                    }
                )
            }
        }
    }

    /**
     * Rotates a scanned page by [deltaDegrees] (e.g., +90 or -90 degrees) and re-aligns its OCR
     * bounding boxes so Searchable PDF export stays 100% aligned.
     */
    fun rotatePage(pageId: String, deltaDegrees: Int = 90) {
        val page = _uiState.value.pages.firstOrNull { it.id == pageId } ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingScan = true,
                    processingStatusMessage = "Rotating page & updating OCR coordinates…"
                )
            }

            val rotatedBitmap = withContext(Dispatchers.Default) {
                DocumentProcessor.rotateBitmap(page.processedBitmap, deltaDegrees)
            }
            val newManualRotation = ((page.manualRotationDegrees + deltaDegrees) % 360 + 360) % 360
            val updatedOcr = PdfExportManager.extractOcrForBitmap(
                bitmap = rotatedBitmap,
                detectedRotationDegrees = (page.autoOrientationDegrees + newManualRotation) % 360
            )

            _uiState.update { state ->
                state.copy(
                    isProcessingScan = false,
                    processingStatusMessage = "",
                    pages = state.pages.map {
                        if (it.id == pageId) {
                            it.copy(
                                manualRotationDegrees = newManualRotation,
                                processedBitmap = rotatedBitmap,
                                ocrResult = updatedOcr
                            )
                        } else {
                            it
                        }
                    }
                )
            }
        }
    }

    /**
     * Reorders pages in the multi-page session (used by drag-and-drop grid and move controls).
     */
    fun movePage(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (fromIndex !in state.pages.indices || toIndex !in state.pages.indices || fromIndex == toIndex) {
                return@update state
            }
            val mutable = state.pages.toMutableList()
            val moved = mutable.removeAt(fromIndex)
            mutable.add(toIndex, moved)
            state.copy(pages = renumberPages(mutable))
        }
    }

    /**
     * Deletes an individual page from the session.
     */
    fun deletePage(pageId: String) {
        _uiState.update { state ->
            val remaining = renumberPages(state.pages.filterNot { it.id == pageId })
            val nextSelected = if (state.selectedPageId == pageId) {
                remaining.firstOrNull()?.id
            } else {
                state.selectedPageId
            }
            state.copy(
                pages = remaining,
                selectedPageId = nextSelected,
                currentRoute = if (remaining.isEmpty()) {
                    ScannerScreenRoute.CAMERA_PREVIEW
                } else {
                    state.currentRoute
                },
                statusBannerMessage = "Page removed from session"
            )
        }
    }

    /**
     * Replaces an existing page by returning to the Camera screen (or loading a new capture).
     */
    fun retakePage(pageId: String) {
        deletePage(pageId)
        _uiState.update { it.copy(currentRoute = ScannerScreenRoute.CAMERA_PREVIEW) }
    }

    /**
     * Exports all scanned pages in the current session as a multi-page Searchable PDF with an
     * embedded invisible ML Kit OCR text layer.
     */
    fun exportSessionToSearchablePdf(context: Context) {
        val pages = _uiState.value.pages
        if (pages.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingScan = true,
                    processingStatusMessage = "Generating ${pages.size}-page Searchable PDF with OCR text layer…"
                )
            }

            val result = PdfExportManager.exportSearchablePdf(
                context = context,
                pages = pages
            )

            result.fold(
                onSuccess = { artifact ->
                    _uiState.update {
                        it.copy(
                            isProcessingScan = false,
                            processingStatusMessage = "",
                            lastExportArtifact = artifact,
                            statusBannerMessage = "Saved Searchable PDF to ${artifact.displayPath}"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isProcessingScan = false,
                            processingStatusMessage = "",
                            statusBannerMessage = "PDF Export failed: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Batch-exports all pages (or a single page) as high-resolution `.jpg` files to `Pictures/Scans`.
     */
    fun exportSessionToJpegs(context: Context, singlePageId: String? = null) {
        val targetPages = if (singlePageId != null) {
            _uiState.value.pages.filter { it.id == singlePageId }
        } else {
            _uiState.value.pages
        }
        if (targetPages.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingScan = true,
                    processingStatusMessage = "Saving ${targetPages.size} high-resolution JPEG scan(s) to Gallery…"
                )
            }

            val result = PdfExportManager.exportPagesAsJpeg(
                context = context,
                pages = targetPages
            )

            result.fold(
                onSuccess = { artifact ->
                    _uiState.update {
                        it.copy(
                            isProcessingScan = false,
                            processingStatusMessage = "",
                            lastExportArtifact = artifact,
                            statusBannerMessage = "Saved ${artifact.pageCount} JPEG(s) to Pictures/Scans"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isProcessingScan = false,
                            processingStatusMessage = "",
                            statusBannerMessage = "JPEG Export failed: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    private fun renumberPages(pages: List<ScannedPage>): List<ScannedPage> =
        pages.mapIndexed { index, page ->
            page.copy(title = "Page ${index + 1}")
        }

    private fun decodeBitmapFromUriWithExif(context: Context, uri: Uri): Bitmap? {
        return try {
            val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0

            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null

            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            } else {
                raw
            }
        } catch (e: Throwable) {
            null
        }
    }
}
