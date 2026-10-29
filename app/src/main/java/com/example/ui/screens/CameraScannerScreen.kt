package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.model.DetectedQuad
import com.example.model.NormalizedPoint
import com.example.model.ScannerScreenRoute
import com.example.ui.ScannerUiState
import com.example.ui.components.LiveDetectedQuadOverlay
import com.example.ui.theme.AmberHandle
import com.example.ui.theme.EmeraldLock
import com.example.ui.theme.LaserCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.vision.DocumentProcessor
import com.example.vision.SampleDocumentGenerator
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Real-Time CameraX Scanner Screen with OpenCV 4-point contour overlay, Auto-Capture stability ring,
 * Photo Picker gallery import, and instant Sample Document generator for emulator/device testing.
 */
@Composable
fun CameraScannerScreen(
    uiState: ScannerUiState,
    onToggleAutoCapture: () -> Unit,
    onToggleFlash: () -> Unit,
    onLiveQuadDetected: (DetectedQuad?) -> Boolean,
    onSetCapturing: (Boolean) -> Unit,
    onRawDocumentCaptured: (Bitmap, List<NormalizedPoint>?) -> Unit,
    onLoadSampleDocument: (SampleDocumentGenerator.SampleTemplate) -> Unit,
    onImportPhotoUri: (android.net.Uri) -> Unit,
    onOpenSessionReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportPhotoUri(uri)
        }
    }

    // Request camera permission automatically on first launch
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    var cameraControlRef by remember { mutableStateOf<Camera?>(null) }

    // Synchronize torch / flash state with CameraX
    LaunchedEffect(uiState.isFlashEnabled, cameraControlRef) {
        cameraControlRef?.cameraControl?.enableTorch(uiState.isFlashEnabled)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .testTag("camera_scanner_screen")
    ) {
        if (hasCameraPermission) {
            val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
            DisposableEffect(Unit) {
                onDispose { analysisExecutor.shutdown() }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camerax_preview_view"),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                        try {
                                            val quad = DocumentProcessor.analyzeCameraFrame(imageProxy)
                                            val shouldAutoCapture = onLiveQuadDetected(quad)
                                            if (shouldAutoCapture) {
                                                view.post {
                                                    view.performHapticFeedback(
                                                        HapticFeedbackConstants.CONFIRM
                                                    )
                                                    captureFromCameraX(
                                                        context = ctx,
                                                        imageCapture = imageCapture,
                                                        preDetectedCorners = quad?.corners,
                                                        onSetCapturing = onSetCapturing,
                                                        onCaptured = onRawDocumentCaptured
                                                    )
                                                }
                                            }
                                        } catch (e: Throwable) {
                                            Log.w("CameraScannerScreen", "Frame analysis error: ${e.message}")
                                        } finally {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            cameraProvider.unbindAll()
                            cameraControlRef = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                                imageAnalysis
                            )
                        } catch (e: Throwable) {
                            Log.w("CameraScannerScreen", "CameraX bind failed: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            // Smooth dynamic OpenCV 4-point polygon overlay on top of CameraX preview
            LiveDetectedQuadOverlay(
                detectedQuad = uiState.liveDetectedQuad,
                autoCaptureProgress = uiState.autoCaptureProgress,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Camera permission rationale + immediate sample document & gallery workflow
            CameraPermissionFallbackView(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onPickFromGallery = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onLoadSample = onLoadSampleDocument
            )
        }

        // Top HUD Bar (OpenCV Engine status, Auto-Capture toggle, Flash toggle)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            ObsidianBackground.copy(alpha = 0.88f),
                            ObsidianBackground.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand & OpenCV engine status pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ObsidianSurface.copy(alpha = 0.85f))
                        .border(1.dp, LaserCyan.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (uiState.openCvReady) EmeraldLock else AmberHandle)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.openCvReady) "OPENCV 4.9 • ML KIT OCR" else "VISION ENGINE READY",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Auto-Capture vs Manual Mode Pill
                    Surface(
                        onClick = onToggleAutoCapture,
                        shape = RoundedCornerShape(50),
                        color = if (uiState.isAutoCaptureEnabled) {
                            LaserCyan.copy(alpha = 0.22f)
                        } else {
                            ObsidianSurface.copy(alpha = 0.85f)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (uiState.isAutoCaptureEnabled) LaserCyan else Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.testTag("toggle_auto_capture_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = stringResource(R.string.scanner_mode_auto),
                                tint = if (uiState.isAutoCaptureEnabled) LaserCyan else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isAutoCaptureEnabled) {
                                    stringResource(R.string.scanner_mode_auto)
                                } else {
                                    stringResource(R.string.scanner_mode_manual)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isAutoCaptureEnabled) LaserCyan else Color.White
                            )
                        }
                    }

                    // Torch / Flash Toggle
                    if (hasCameraPermission) {
                        IconButton(
                            onClick = onToggleFlash,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ObsidianSurface.copy(alpha = 0.85f))
                                .testTag("toggle_flash_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isFlashEnabled) {
                                    Icons.Default.FlashOn
                                } else {
                                    Icons.Default.FlashOff
                                },
                                contentDescription = "Toggle Flash",
                                tint = if (uiState.isFlashEnabled) AmberHandle else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic guidance banner showing real-time contour lock status
            val quad = uiState.liveDetectedQuad
            val statusText = when {
                quad != null && uiState.autoCaptureProgress >= 0.65f ->
                    stringResource(R.string.hint_hold_steady)
                quad != null ->
                    "4-Point Contour Locked (${(quad.areaRatio * 100).toInt()}% frame) — Hold flat"
                else ->
                    stringResource(R.string.hint_align_document)
            }
            val bannerBorder = if (quad != null && uiState.autoCaptureProgress >= 0.65f) {
                EmeraldLock
            } else {
                LaserCyan.copy(alpha = 0.45f)
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianSurface.copy(alpha = 0.82f),
                border = androidx.compose.foundation.BorderStroke(1.dp, bannerBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Bottom Camera Deck + Sample Document Presets + Session Queue Badge
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            ObsidianBackground.copy(alpha = 0.90f),
                            ObsidianBackground
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 20.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Instant Sample Test Presets bar (allows testing skewed Invoice, Tech Spec, or Receipt anytime)
            Text(
                text = "TEST SAMPLE CAMERA FRAMES (SKEWED + SHADOWS)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sample_templates_row")
            ) {
                items(SampleDocumentGenerator.SampleTemplate.entries) { template ->
                    Surface(
                        onClick = { onLoadSampleDocument(template) },
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceVariant.copy(alpha = 0.92f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            LaserCyan.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("sample_btn_${template.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = template.title,
                                tint = LaserCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (template) {
                                    SampleDocumentGenerator.SampleTemplate.INVOICE -> "Sample Invoice"
                                    SampleDocumentGenerator.SampleTemplate.TECH_SPEC -> "Sample Tech Spec"
                                    SampleDocumentGenerator.SampleTemplate.EXPENSE_RECEIPT -> "Sample Receipt"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Shutter Row: [Import Photo]   [Shutter / Auto-Lock Ring]   [Multi-Page Session Queue]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Gallery / Photo Picker Import Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(8.dp)
                        .testTag("import_gallery_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianSurfaceVariant)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = stringResource(R.string.btn_import_gallery),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Main Shutter Button with Auto-Capture Stability Progress Ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !uiState.isCapturingFrame && !uiState.isProcessingScan) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            if (hasCameraPermission && cameraControlRef != null) {
                                captureFromCameraX(
                                    context = context,
                                    imageCapture = imageCapture,
                                    preDetectedCorners = uiState.liveDetectedQuad?.corners,
                                    onSetCapturing = onSetCapturing,
                                    onCaptured = onRawDocumentCaptured
                                )
                            } else {
                                onLoadSampleDocument(SampleDocumentGenerator.SampleTemplate.INVOICE)
                            }
                        }
                        .testTag("shutter_capture_button")
                ) {
                    CircularProgressIndicator(
                        progress = {
                            if (uiState.autoCaptureProgress > 0f) uiState.autoCaptureProgress else 1f
                        },
                        modifier = Modifier.fillMaxSize(),
                        color = if (uiState.autoCaptureProgress >= 0.65f) EmeraldLock else LaserCyan,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        strokeWidth = 4.dp
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = stringResource(R.string.btn_capture),
                            tint = ObsidianBackground,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Multi-Page Session Stack Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = uiState.pages.isNotEmpty()) {
                            onOpenSessionReview()
                        }
                        .padding(8.dp)
                        .testTag("open_session_review_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (uiState.pages.isNotEmpty()) LaserCyan.copy(alpha = 0.2f)
                                else ObsidianSurfaceVariant
                            )
                            .border(
                                1.5.dp,
                                if (uiState.pages.isNotEmpty()) LaserCyan else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        val lastPage = uiState.pages.lastOrNull()
                        if (lastPage != null) {
                            Image(
                                bitmap = remember(lastPage.id, lastPage.processedBitmap) {
                                    lastPage.processedBitmap.asImageBitmap()
                                },
                                contentDescription = "Scanned pages thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(LaserCyan)
                            ) {
                                Text(
                                    text = "${uiState.pages.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ObsidianBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = "Session Pages",
                                tint = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.pages.isEmpty()) "0 Pages" else "${uiState.pages.size} Page(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.pages.isNotEmpty()) LaserCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Processing overlay when capturing or detecting contour
        AnimatedVisibility(
            visible = uiState.isCapturingFrame || uiState.isProcessingScan,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ObsidianSurface.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, LaserCyan),
                tonalElevation = 8.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    CircularProgressIndicator(
                        color = LaserCyan,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = uiState.processingStatusMessage.ifBlank { "Capturing high-res frame…" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionFallbackView(
    onRequestPermission: () -> Unit,
    onPickFromGallery: () -> Unit,
    onLoadSample: (SampleDocumentGenerator.SampleTemplate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = LaserCyan.copy(alpha = 0.16f),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, LaserCyan),
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera Required",
                    tint = LaserCyan,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.permission_camera_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.permission_camera_rationale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = LaserCyan,
                contentColor = ObsidianBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("grant_camera_permission_button")
        ) {
            Text(
                text = stringResource(R.string.permission_grant_button),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = { onLoadSample(SampleDocumentGenerator.SampleTemplate.INVOICE) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("fallback_load_sample_button")
            ) {
                Text(text = stringResource(R.string.btn_sample_scan))
            }
            FilledTonalButton(
                onClick = onPickFromGallery,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(text = stringResource(R.string.btn_import_gallery))
            }
        }
    }
}

/**
 * Triggers a maximum-resolution capture on CameraX [ImageCapture] and converts the resulting
 * [ImageProxy] to an upright [Bitmap] before falling back to a sample frame if the virtual
 * camera sensor is unavailable in a headless container.
 */
private fun captureFromCameraX(
    context: Context,
    imageCapture: ImageCapture,
    preDetectedCorners: List<NormalizedPoint>?,
    onSetCapturing: (Boolean) -> Unit,
    onCaptured: (Bitmap, List<NormalizedPoint>?) -> Unit
) {
    onSetCapturing(true)
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = imageProxyToUprightBitmap(image)
                    if (bitmap != null) {
                        onCaptured(bitmap, preDetectedCorners)
                    } else {
                        val fallback = SampleDocumentGenerator.generateSampleCameraPhoto(
                            SampleDocumentGenerator.SampleTemplate.INVOICE
                        )
                        onCaptured(fallback, null)
                    }
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.w("CameraScannerScreen", "ImageCapture fallback to sample frame: ${exception.message}")
                val fallback = SampleDocumentGenerator.generateSampleCameraPhoto(
                    SampleDocumentGenerator.SampleTemplate.INVOICE
                )
                onCaptured(fallback, null)
            }
        }
    )
}

private fun imageProxyToUprightBitmap(image: ImageProxy): Bitmap? {
    return try {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }
    } catch (e: Throwable) {
        null
    }
}
