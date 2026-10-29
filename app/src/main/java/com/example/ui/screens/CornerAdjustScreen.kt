package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AspectRatioPreset
import com.example.model.NormalizedPoint
import com.example.model.ScanFilterMode
import com.example.ui.ScannerUiState
import com.example.ui.components.InteractiveCornerCropOverlay
import com.example.ui.theme.AmberHandle
import com.example.ui.theme.EmeraldLock
import com.example.ui.theme.LaserCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant

/**
 * Post-Capture / Re-Crop Screen where the user fine-tunes the 4 document corners with a
 * magnifying loupe, selects target aspect ratio (Natural Euclidean, ISO A4, US Letter, Receipt),
 * picks the initial flatbed filter mode (Crisp B&W, Magic Color, Original), and triggers
 * 4-point homography perspective warp + ML Kit auto-orientation.
 */
@Composable
fun CornerAdjustScreen(
    uiState: ScannerUiState,
    onCornerMoved: (Int, NormalizedPoint) -> Unit,
    onAutoDetectCorners: () -> Unit,
    onResetFullFrame: () -> Unit,
    onSelectAspectRatio: (AspectRatioPreset) -> Unit,
    onSelectFilterMode: (ScanFilterMode) -> Unit,
    onConfirmWarpAndEnhance: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val sourceBitmap = uiState.draftSourceBitmap
    if (sourceBitmap == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No image loaded", color = Color.White)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("corner_adjust_screen")
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("corner_adjust_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.btn_retake),
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = "4-Point Perspective Crop",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.hint_adjust_corners),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Corner Reset / Re-detect buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = onAutoDetectCorners,
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LaserCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("btn_auto_detect_edges")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Auto-Detect Edges",
                            tint = LaserCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Snap Edges",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    onClick = onResetFullFrame,
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurfaceVariant,
                    modifier = Modifier.testTag("btn_full_frame_quad")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = "Full Frame",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Full",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Interactive 4-Corner Polygon Editor + Magnifying Loupe
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            InteractiveCornerCropOverlay(
                bitmap = sourceBitmap,
                corners = uiState.draftCorners,
                onCornerMoved = onCornerMoved,
                modifier = Modifier.fillMaxSize()
            )

            if (uiState.isProcessingScan) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurface.copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LaserCyan),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = LaserCyan,
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = uiState.processingStatusMessage.ifBlank { "Warping & enhancing…" },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Bottom Control Sheet: Target Aspect Ratio + Flatbed Filter Selector + "Warp & Enhance" CTA
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Target Page Geometry / Aspect Ratio Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PAGE GEOMETRY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.draftAspectRatio.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = LaserCyan
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(AspectRatioPreset.entries) { preset ->
                        val selected = preset == uiState.draftAspectRatio
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) LaserCyan.copy(alpha = 0.2f)
                                    else ObsidianSurfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (selected) LaserCyan else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectAspectRatio(preset) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("aspect_preset_${preset.name.lowercase()}")
                        ) {
                            Text(
                                text = preset.shortName,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) LaserCyan else Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Flatbed Enhancement Filter Selector (Crisp B&W / Magic Color / Original)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "FLATBED ENHANCEMENT PIPELINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.draftFilterMode.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldLock
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScanFilterMode.entries.forEach { mode ->
                        val selected = mode == uiState.draftFilterMode
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) AmberHandle.copy(alpha = 0.2f)
                                    else ObsidianSurfaceVariant
                                )
                                .border(
                                    1.5.dp,
                                    if (selected) AmberHandle else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectFilterMode(mode) }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                                .testTag("draft_filter_${mode.name.lowercase()}")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = mode.title,
                                    tint = if (selected) AmberHandle else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) AmberHandle else Color.White,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Action: Warp Perspective + Auto-Orient + Flatbed Filter + OCR
                Button(
                    onClick = onConfirmWarpAndEnhance,
                    enabled = !uiState.isProcessingScan,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LaserCyan,
                        contentColor = ObsidianBackground
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("confirm_warp_enhance_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_apply_crop),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
