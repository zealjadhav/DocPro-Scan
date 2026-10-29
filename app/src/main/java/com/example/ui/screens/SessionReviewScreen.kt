package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import com.example.R
import com.example.model.ExportArtifact
import com.example.model.ScanFilterMode
import com.example.model.ScannedPage
import com.example.ui.ScannerUiState
import com.example.ui.theme.AmberHandle
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldLock
import com.example.ui.theme.LaserCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.vision.SampleDocumentGenerator
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Multi-Page Session Review, Reordering, Filter Switching, OCR Inspection, and
 * Searchable PDF / High-Res JPEG Export Screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionReviewScreen(
    uiState: ScannerUiState,
    onSelectPage: (String) -> Unit,
    onUpdatePageFilter: (String, ScanFilterMode) -> Unit,
    onRotatePage: (String) -> Unit,
    onReCropPage: (String) -> Unit,
    onRetakePage: (String) -> Unit,
    onDeletePage: (String) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onOpenOcrModal: (String?) -> Unit,
    onAddPageFromCamera: () -> Unit,
    onAddSamplePage: (SampleDocumentGenerator.SampleTemplate) -> Unit,
    onExportSearchablePdf: () -> Unit,
    onExportJpegs: (String?) -> Unit,
    onDismissExportArtifact: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onAddPageFromCamera)

    val context = LocalContext.current
    val selectedPage = uiState.selectedPage
    var showOcrBoxesOnPreview by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("session_review_screen")
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onAddPageFromCamera,
                    modifier = Modifier.testTag("review_back_to_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Camera",
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = "Scan Session (${uiState.pages.size} ${if (uiState.pages.size == 1) "Page" else "Pages"})",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Long-press & drag cards to reorder • Tap to edit or view OCR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Add another page button (+ Camera)
            Surface(
                onClick = onAddPageFromCamera,
                shape = RoundedCornerShape(12.dp),
                color = LaserCyan.copy(alpha = 0.18f),
                border = androidx.compose.foundation.BorderStroke(1.dp, LaserCyan),
                modifier = Modifier.testTag("add_page_camera_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Scan Next Page",
                        tint = LaserCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Page",
                        style = MaterialTheme.typography.labelLarge,
                        color = LaserCyan
                    )
                }
            }
        }

        // Export Artifact Success Card (when PDF or JPEGs were just generated)
        AnimatedVisibility(visible = uiState.lastExportArtifact != null) {
            uiState.lastExportArtifact?.let { artifact ->
                ExportSuccessBanner(
                    artifact = artifact,
                    onShare = { shareExportedFile(context, artifact) },
                    onDismiss = onDismissExportArtifact
                )
            }
        }

        // Selected Page Hero Inspector + Live Filter Bar + Page Actions
        if (selectedPage != null) {
            SelectedPageEditorCard(
                page = selectedPage,
                showOcrBoxes = showOcrBoxesOnPreview,
                onToggleOcrBoxes = { showOcrBoxesOnPreview = !showOcrBoxesOnPreview },
                onSelectFilter = { mode -> onUpdatePageFilter(selectedPage.id, mode) },
                onReCrop = { onReCropPage(selectedPage.id) },
                onRotate = { onRotatePage(selectedPage.id) },
                onOpenOcrSheet = { onOpenOcrModal(selectedPage.id) },
                onRetake = { onRetakePage(selectedPage.id) },
                onDelete = { onDeletePage(selectedPage.id) },
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }

        // Multi-Page Reorderable Session Grid Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "MULTI-PAGE QUEUE (DRAG OR TAP ARROWS TO REORDER)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Quick helper to append a second/third sample page for multi-page PDF testing
            val nextTemplate = when (uiState.pages.size % 3) {
                0 -> SampleDocumentGenerator.SampleTemplate.INVOICE
                1 -> SampleDocumentGenerator.SampleTemplate.TECH_SPEC
                else -> SampleDocumentGenerator.SampleTemplate.EXPENSE_RECEIPT
            }
            Text(
                text = "+ Append Sample Page",
                style = MaterialTheme.typography.labelSmall,
                color = LaserCyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onAddSamplePage(nextTemplate) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("append_sample_page_button")
            )
        }

        // Reorderable Multi-Page Grid
        var draggedIndex by remember { mutableIntStateOf(-1) }
        var dragOffsetX by remember { mutableFloatStateOf(0f) }
        var dragOffsetY by remember { mutableFloatStateOf(0f) }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(0.46f)
                .fillMaxWidth()
                .testTag("multi_page_grid")
        ) {
            itemsIndexed(
                items = uiState.pages,
                key = { _, item -> item.id }
            ) { index, page ->
                val isSelected = page.id == selectedPage?.id
                val isDragging = index == draggedIndex

                PageGridCard(
                    page = page,
                    pageIndex = index,
                    totalPages = uiState.pages.size,
                    isSelected = isSelected,
                    isDragging = isDragging,
                    dragOffsetX = if (isDragging) dragOffsetX else 0f,
                    dragOffsetY = if (isDragging) dragOffsetY else 0f,
                    onClick = { onSelectPage(page.id) },
                    onMoveEarlier = {
                        if (index > 0) onMovePage(index, index - 1)
                    },
                    onMoveLater = {
                        if (index < uiState.pages.lastIndex) onMovePage(index, index + 1)
                    },
                    onDragStart = {
                        draggedIndex = index
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDragDelta = { dx, dy ->
                        dragOffsetX += dx
                        dragOffsetY += dy
                        // Trigger page swap when dragged across card width threshold
                        val thresholdPx = 160f
                        if (dragOffsetX > thresholdPx && draggedIndex < uiState.pages.lastIndex) {
                            val target = draggedIndex + 1
                            onMovePage(draggedIndex, target)
                            draggedIndex = target
                            dragOffsetX = 0f
                        } else if (dragOffsetX < -thresholdPx && draggedIndex > 0) {
                            val target = draggedIndex - 1
                            onMovePage(draggedIndex, target)
                            draggedIndex = target
                            dragOffsetX = 0f
                        }
                    },
                    onDragEnd = {
                        draggedIndex = -1
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    }
                )
            }
        }

        // Bottom Export Action Bar: [Searchable PDF (All Pages)] + [Save JPEGs]
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Export High-Res JPEGs to Gallery
                OutlinedButton(
                    onClick = { onExportJpegs(null) },
                    enabled = !uiState.isProcessingScan && uiState.pages.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LaserCyan),
                    modifier = Modifier
                        .weight(0.45f)
                        .height(52.dp)
                        .testTag("export_jpeg_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = LaserCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_export_jpeg),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }

                // Export Multi-Page Searchable PDF with Embedded ML Kit OCR Layer
                Button(
                    onClick = onExportSearchablePdf,
                    enabled = !uiState.isProcessingScan && uiState.pages.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LaserCyan,
                        contentColor = ObsidianBackground
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(0.55f)
                        .height(52.dp)
                        .testTag("export_searchable_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${stringResource(R.string.btn_export_pdf)} (${uiState.pages.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Processing spinner overlay
    if (uiState.isProcessingScan) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ObsidianSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, LaserCyan)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    CircularProgressIndicator(
                        color = LaserCyan,
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = uiState.processingStatusMessage.ifBlank { "Processing…" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }

    // OCR Extracted Text & Bounding Box Inspector Bottom Sheet
    val ocrModalPage = uiState.pages.firstOrNull { it.id == uiState.activeOcrModalPageId }
    if (ocrModalPage != null) {
        ModalBottomSheet(
            onDismissRequest = { onOpenOcrModal(null) },
            containerColor = ObsidianSurface,
            contentColor = Color.White
        ) {
            OcrExtractedTextSheetContent(
                page = ocrModalPage,
                onDismiss = { onOpenOcrModal(null) }
            )
        }
    }
}

@Composable
private fun SelectedPageEditorCard(
    page: ScannedPage,
    showOcrBoxes: Boolean,
    onToggleOcrBoxes: () -> Unit,
    onSelectFilter: (ScanFilterMode) -> Unit,
    onReCrop: () -> Unit,
    onRotate: () -> Unit,
    onOpenOcrSheet: () -> Unit,
    onRetake: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Top metadata row: Page Title, Resolution, OCR word badge, and Quick Action Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ObsidianSurfaceVariant
                    ) {
                        Text(
                            text = page.resolutionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        onClick = onToggleOcrBoxes,
                        shape = RoundedCornerShape(6.dp),
                        color = if (showOcrBoxes) EmeraldLock.copy(alpha = 0.18f) else ObsidianSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (showOcrBoxes) EmeraldLock else Color.Transparent
                        )
                    ) {
                        Text(
                            text = "${page.ocrResult.wordCount} OCR words",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showOcrBoxes) EmeraldLock else Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Action Toolbar: [Re-Crop] [Rotate 90°] [View OCR Text] [Retake] [Delete]
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onReCrop,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("page_action_recrop")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = "Re-Crop Corners",
                            tint = LaserCyan,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onRotate,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("page_action_rotate")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = stringResource(R.string.btn_rotate_right),
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenOcrSheet,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("page_action_ocr_text")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TextSnippet,
                            contentDescription = stringResource(R.string.btn_view_ocr),
                            tint = EmeraldLock,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onRetake,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("page_action_retake")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.btn_retake),
                            tint = AmberHandle,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("page_action_delete")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Page",
                            tint = CoralAlert,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // High-Resolution Processed Scan Preview with optional ML Kit OCR Bounding Box Overlay
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianBackground)
            ) {
                val bmp = page.processedBitmap
                val imageBitmap = remember(page.id, bmp) { bmp.asImageBitmap() }
                val viewW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val viewH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val scale = min(viewW / bmp.width.toFloat(), viewH / bmp.height.toFloat())
                val drawW = bmp.width * scale
                val drawH = bmp.height * scale
                val offsetX = (viewW - drawW) / 2f
                val offsetY = (viewH - drawH) / 2f

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(
                        image = imageBitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                        dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt())
                    )

                    // Render aligned ML Kit OCR bounding boxes over the document when enabled
                    if (showOcrBoxes && page.ocrResult.blocks.isNotEmpty()) {
                        val ocrScaleX = drawW / page.ocrResult.sourceWidth.coerceAtLeast(1).toFloat()
                        val ocrScaleY = drawH / page.ocrResult.sourceHeight.coerceAtLeast(1).toFloat()

                        for (block in page.ocrResult.blocks) {
                            for (line in block.lines) {
                                val r = line.boundingBox
                                val left = offsetX + r.left * ocrScaleX
                                val top = offsetY + r.top * ocrScaleY
                                val w = r.width() * ocrScaleX
                                val h = r.height() * ocrScaleY
                                drawRect(
                                    color = LaserCyan.copy(alpha = 0.16f),
                                    topLeft = Offset(left, top),
                                    size = Size(w, h)
                                )
                                drawRect(
                                    color = LaserCyan.copy(alpha = 0.78f),
                                    topLeft = Offset(left, top),
                                    size = Size(w, h),
                                    style = Stroke(width = 1.2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Real-time Filter Switcher (Crisp B&W / Magic Color / Original) + OCR Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScanFilterMode.entries.forEach { mode ->
                    val selected = page.filterMode == mode
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) LaserCyan.copy(alpha = 0.22f)
                                else ObsidianSurfaceVariant
                            )
                            .border(
                                1.dp,
                                if (selected) LaserCyan else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectFilter(mode) }
                            .padding(vertical = 8.dp)
                            .testTag("review_filter_${mode.name.lowercase()}")
                    ) {
                        Text(
                            text = mode.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) LaserCyan else Color.White,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // Quick button to view & copy OCR text
                Surface(
                    onClick = onOpenOcrSheet,
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldLock.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldLock),
                    modifier = Modifier.testTag("open_ocr_modal_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "View OCR",
                            tint = EmeraldLock,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "OCR",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldLock,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageGridCard(
    page: ScannedPage,
    pageIndex: Int,
    totalPages: Int,
    isSelected: Boolean,
    isDragging: Boolean,
    dragOffsetX: Float,
    dragOffsetY: Float,
    onClick: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val borderColor = when {
        isDragging -> AmberHandle
        isSelected -> LaserCyan
        else -> Color.White.copy(alpha = 0.15f)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected || isDragging) 2.dp else 1.dp,
            color = borderColor
        ),
        modifier = Modifier
            .zIndex(if (isDragging) 10f else 1f)
            .graphicsLayer {
                translationX = dragOffsetX
                translationY = dragOffsetY
                scaleX = if (isDragging) 1.05f else 1f
                scaleY = if (isDragging) 1.05f else 1f
            }
            .clickable(onClick = onClick)
            .pointerInput(page.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .testTag("page_grid_card_${pageIndex + 1}")
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBackground)
            ) {
                Image(
                    bitmap = remember(page.id, page.processedBitmap) {
                        page.processedBitmap.asImageBitmap()
                    },
                    contentDescription = page.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                // Page Number Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) LaserCyan else ObsidianSurface.copy(alpha = 0.9f))
                ) {
                    Text(
                        text = "${pageIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) ObsidianBackground else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Filter Tag Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ObsidianSurface.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = page.filterMode.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = LaserCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Card footer with drag indicator & accessible left/right reorder buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (totalPages > 1) {
                    Row {
                        IconButton(
                            onClick = onMoveEarlier,
                            enabled = pageIndex > 0,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("move_left_page_${pageIndex + 1}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Move Page Earlier",
                                tint = if (pageIndex > 0) Color.White else Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onMoveLater,
                            enabled = pageIndex < totalPages - 1,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("move_right_page_${pageIndex + 1}")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Move Page Later",
                                tint = if (pageIndex < totalPages - 1) Color.White else Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportSuccessBanner(
    artifact: ExportArtifact,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = EmeraldLock.copy(alpha = 0.16f),
        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldLock),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .testTag("export_success_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Export Complete • ${artifact.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = EmeraldLock,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Saved to ${artifact.displayPath} (${artifact.fileSizeBytes / 1024} KB)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (artifact.shareableFilePath != null) {
                    FilledTonalButton(
                        onClick = onShare,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("share_exported_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share File",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Share")
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = "OK", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun OcrExtractedTextSheetContent(
    page: ScannedPage,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var copiedFeedback by remember { mutableStateOf(false) }
    val ocr = page.ocrResult

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("ocr_extracted_text_sheet")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${page.title} — Extracted OCR Text",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Text(
                    text = "${ocr.blocks.size} TextBlocks • ${ocr.lineCount} Lines • ${ocr.wordCount} Words",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldLock
                )
            }

            Button(
                onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText("Scanned OCR Text", ocr.fullText)
                    )
                    copiedFeedback = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = LaserCyan,
                    contentColor = ObsidianBackground
                ),
                modifier = Modifier.testTag("copy_ocr_text_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.btn_copy_ocr),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (copiedFeedback) "Copied!" else stringResource(R.string.btn_copy_ocr),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(14.dp)
            ) {
                if (ocr.fullText.isBlank()) {
                    Text(
                        text = "No machine-readable text detected on this page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = ocr.fullText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Close OCR Inspector", color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun shareExportedFile(context: Context, artifact: ExportArtifact) {
    val path = artifact.shareableFilePath ?: return
    val file = File(path)
    if (!file.exists()) return
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = artifact.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, artifact.title))
    } catch (_: Throwable) {
    }
}
