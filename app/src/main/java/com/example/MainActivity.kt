package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ScannerScreenRoute
import com.example.ui.ScannerViewModel
import com.example.ui.screens.CameraScannerScreen
import com.example.ui.screens.CornerAdjustScreen
import com.example.ui.screens.SessionReviewScreen
import com.example.ui.theme.EmeraldLock
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianSurface
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                DocScanProApp()
            }
        }
    }
}

@Composable
fun DocScanProApp(
    scannerViewModel: ScannerViewModel = viewModel()
) {
    val uiState by scannerViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Automatically dismiss transient status banner after 4 seconds
    LaunchedEffect(uiState.statusBannerMessage) {
        if (uiState.statusBannerMessage != null) {
            delay(4000L)
            scannerViewModel.clearStatusBanner()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState.currentRoute) {
            ScannerScreenRoute.CAMERA_PREVIEW -> {
                CameraScannerScreen(
                    uiState = uiState,
                    onToggleAutoCapture = scannerViewModel::toggleAutoCapture,
                    onToggleFlash = scannerViewModel::toggleFlash,
                    onLiveQuadDetected = scannerViewModel::onLiveQuadDetected,
                    onSetCapturing = scannerViewModel::setCapturingFrame,
                    onRawDocumentCaptured = scannerViewModel::onRawDocumentCaptured,
                    onLoadSampleDocument = scannerViewModel::loadSampleDocumentForCrop,
                    onImportPhotoUri = { uri ->
                        scannerViewModel.onPhotoImportedFromUri(context, uri)
                    },
                    onOpenSessionReview = {
                        scannerViewModel.navigateTo(ScannerScreenRoute.SESSION_REVIEW)
                    }
                )
            }

            ScannerScreenRoute.CORNER_ADJUST -> {
                CornerAdjustScreen(
                    uiState = uiState,
                    onCornerMoved = scannerViewModel::updateDraftCorner,
                    onAutoDetectCorners = scannerViewModel::reDetectDraftCorners,
                    onResetFullFrame = scannerViewModel::resetDraftCornersToFullFrame,
                    onSelectAspectRatio = scannerViewModel::setDraftAspectRatio,
                    onSelectFilterMode = scannerViewModel::setDraftFilterMode,
                    onConfirmWarpAndEnhance = scannerViewModel::confirmCropAndProcessPage,
                    onBack = {
                        if (uiState.editingExistingPageId != null && uiState.pages.isNotEmpty()) {
                            scannerViewModel.navigateTo(ScannerScreenRoute.SESSION_REVIEW)
                        } else {
                            scannerViewModel.navigateTo(ScannerScreenRoute.CAMERA_PREVIEW)
                        }
                    }
                )
            }

            ScannerScreenRoute.SESSION_REVIEW -> {
                SessionReviewScreen(
                    uiState = uiState,
                    onSelectPage = scannerViewModel::selectPage,
                    onUpdatePageFilter = scannerViewModel::updatePageFilter,
                    onRotatePage = { pageId -> scannerViewModel.rotatePage(pageId, 90) },
                    onReCropPage = scannerViewModel::startReCropPage,
                    onRetakePage = scannerViewModel::retakePage,
                    onDeletePage = scannerViewModel::deletePage,
                    onMovePage = scannerViewModel::movePage,
                    onOpenOcrModal = scannerViewModel::openOcrInspector,
                    onAddPageFromCamera = {
                        scannerViewModel.navigateTo(ScannerScreenRoute.CAMERA_PREVIEW)
                    },
                    onAddSamplePage = scannerViewModel::loadSampleDocumentForCrop,
                    onExportSearchablePdf = {
                        scannerViewModel.exportSessionToSearchablePdf(context)
                    },
                    onExportJpegs = { singleId ->
                        scannerViewModel.exportSessionToJpegs(context, singleId)
                    },
                    onDismissExportArtifact = scannerViewModel::dismissExportArtifact
                )
            }
        }

        // Floating Status Notification Pill
        AnimatedVisibility(
            visible = uiState.statusBannerMessage != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 64.dp, start = 20.dp, end = 20.dp)
        ) {
            uiState.statusBannerMessage?.let { message ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = ObsidianSurface.copy(alpha = 0.96f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldLock),
                    tonalElevation = 8.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldLock
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
