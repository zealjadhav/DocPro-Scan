package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.model.DetectedQuad
import com.example.model.NormalizedPoint
import com.example.ui.theme.AmberHandle
import com.example.ui.theme.EmeraldLock
import com.example.ui.theme.LaserCyan
import com.example.ui.theme.ObsidianBackground
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Interactive 4-Corner Polygon Adjuster with a real-time Magnifying Loupe.
 *
 * Features:
 * - Renders the captured source [Bitmap] letterboxed cleanly inside the available viewport.
 * - Draws darkened exterior mask outside the 4-point document quad.
 * - Draws perspective 3x3 subdivision grid lines inside the quad to assist visual alignment.
 * - Provides 4 large (48dp+) touch handles at Top-Left, Top-Right, Bottom-Right, and Bottom-Left.
 * - Displays a high-magnification (2.8x) circular Loupe with precision crosshair above the
 *   active dragged corner so the user's finger never blocks the paper corner.
 */
@Composable
fun InteractiveCornerCropOverlay(
    bitmap: Bitmap,
    corners: List<NormalizedPoint>,
    onCornerMoved: (index: Int, newPoint: NormalizedPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var activeCornerIndex by remember { mutableIntStateOf(-1) }
    var activeDragPosition by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("corner_crop_overlay")
    ) {
        val canvasW = constraints.maxWidth.toFloat()
        val canvasH = constraints.maxHeight.toFloat()

        // Compute aspect-fit rect for the source bitmap inside the canvas
        val bmpW = bitmap.width.toFloat().coerceAtLeast(1f)
        val bmpH = bitmap.height.toFloat().coerceAtLeast(1f)
        val scale = min(canvasW / bmpW, canvasH / bmpH)
        val drawW = bmpW * scale
        val drawH = bmpH * scale
        val offsetX = (canvasW - drawW) / 2f
        val offsetY = (canvasH - drawH) / 2f

        fun normalizedToScreen(pt: NormalizedPoint): Offset = Offset(
            x = offsetX + pt.x * drawW,
            y = offsetY + pt.y * drawH
        )

        fun screenToNormalized(offset: Offset): NormalizedPoint = NormalizedPoint(
            x = ((offset.x - offsetX) / drawW).coerceIn(0.01f, 0.99f),
            y = ((offset.y - offsetY) / drawH).coerceIn(0.01f, 0.99f)
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bmpW, bmpH, drawW, drawH, offsetX, offsetY) {
                    val touchRadiusPx = 44.dp.toPx()
                    detectDragGestures(
                        onDragStart = { downOffset ->
                            // Find closest corner handle within touch radius
                            var bestIdx = -1
                            var bestDist = Float.MAX_VALUE
                            for (i in 0 until min(4, corners.size)) {
                                val screenPt = normalizedToScreen(corners[i])
                                val dist = hypot(
                                    downOffset.x - screenPt.x,
                                    downOffset.y - screenPt.y
                                )
                                if (dist < bestDist && dist <= touchRadiusPx * 1.6f) {
                                    bestDist = dist
                                    bestIdx = i
                                }
                            }
                            activeCornerIndex = bestIdx
                            if (bestIdx != -1) {
                                activeDragPosition = normalizedToScreen(corners[bestIdx])
                            }
                        },
                        onDrag = { change, dragAmount ->
                            val idx = activeCornerIndex
                            if (idx in 0..3) {
                                change.consume()
                                val updatedScreen = Offset(
                                    x = (activeDragPosition.x + dragAmount.x).coerceIn(
                                        offsetX,
                                        offsetX + drawW
                                    ),
                                    y = (activeDragPosition.y + dragAmount.y).coerceIn(
                                        offsetY,
                                        offsetY + drawH
                                    )
                                )
                                activeDragPosition = updatedScreen
                                onCornerMoved(idx, screenToNormalized(updatedScreen))
                            }
                        },
                        onDragEnd = { activeCornerIndex = -1 },
                        onDragCancel = { activeCornerIndex = -1 }
                    )
                }
        ) {
            // 1. Draw the source document bitmap
            drawImage(
                image = imageBitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt())
            )

            if (corners.size == 4) {
                val screenPts = corners.map { normalizedToScreen(it) }
                val quadPath = Path().apply {
                    moveTo(screenPts[0].x, screenPts[0].y)
                    lineTo(screenPts[1].x, screenPts[1].y)
                    lineTo(screenPts[2].x, screenPts[2].y)
                    lineTo(screenPts[3].x, screenPts[3].y)
                    close()
                }

                // 2. Dim the area outside the selected 4-corner polygon
                clipPath(quadPath, clipOp = ClipOp.Difference) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.58f),
                        topLeft = Offset(offsetX, offsetY),
                        size = Size(drawW, drawH)
                    )
                }

                // 3. Draw bilinear rule-of-thirds perspective alignment grid inside the polygon
                val gridStroke = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                for (step in 1..2) {
                    val t = step / 3f
                    // Horizontal perspective line
                    val leftPt = lerpOffset(screenPts[0], screenPts[3], t)
                    val rightPt = lerpOffset(screenPts[1], screenPts[2], t)
                    drawLine(
                        color = LaserCyan.copy(alpha = 0.48f),
                        start = leftPt,
                        end = rightPt,
                        strokeWidth = gridStroke.width,
                        pathEffect = gridStroke.pathEffect
                    )
                    // Vertical perspective line
                    val topPt = lerpOffset(screenPts[0], screenPts[1], t)
                    val bottomPt = lerpOffset(screenPts[3], screenPts[2], t)
                    drawLine(
                        color = LaserCyan.copy(alpha = 0.48f),
                        start = topPt,
                        end = bottomPt,
                        strokeWidth = gridStroke.width,
                        pathEffect = gridStroke.pathEffect
                    )
                }

                // 4. Draw glowing cyan polygon perimeter
                drawPath(
                    path = quadPath,
                    color = LaserCyan.copy(alpha = 0.28f),
                    style = Stroke(
                        width = 7.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                drawPath(
                    path = quadPath,
                    color = LaserCyan,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // 5. Draw 4 corner drag handles
                val outerRadius = 15.dp.toPx()
                val innerRadius = 6.dp.toPx()
                screenPts.forEachIndexed { idx, pt ->
                    val isSelected = idx == activeCornerIndex
                    val handleColor = if (isSelected) AmberHandle else LaserCyan

                    // Outer halo
                    drawCircle(
                        color = ObsidianBackground.copy(alpha = 0.85f),
                        radius = if (isSelected) outerRadius * 1.25f else outerRadius,
                        center = pt
                    )
                    drawCircle(
                        color = handleColor,
                        radius = if (isSelected) outerRadius * 1.25f else outerRadius,
                        center = pt,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    // Precision center dot
                    drawCircle(
                        color = Color.White,
                        radius = innerRadius,
                        center = pt
                    )
                }

                // 6. Draw Magnifying Loupe over the active dragged corner
                if (activeCornerIndex in 0..3) {
                    val activePt = screenPts[activeCornerIndex]
                    val normPt = corners[activeCornerIndex]
                    val loupeRadius = 56.dp.toPx()
                    val verticalLift = 96.dp.toPx()

                    // Place loupe above finger unless near the very top edge of screen
                    val loupeCenterY = if (activePt.y - verticalLift - loupeRadius > 16f) {
                        activePt.y - verticalLift
                    } else {
                        activePt.y + verticalLift
                    }
                    val loupeCenterX = activePt.x.coerceIn(
                        loupeRadius + 16f,
                        size.width - loupeRadius - 16f
                    )
                    val loupeCenter = Offset(loupeCenterX, loupeCenterY)

                    val loupeClip = Path().apply {
                        addOval(
                            Rect(
                                left = loupeCenter.x - loupeRadius,
                                top = loupeCenter.y - loupeRadius,
                                right = loupeCenter.x + loupeRadius,
                                bottom = loupeCenter.y + loupeRadius
                            )
                        )
                    }

                    // Draw dark backing & zoomed region of bitmap (2.8x zoom)
                    drawCircle(
                        color = ObsidianBackground,
                        radius = loupeRadius + 4.dp.toPx(),
                        center = loupeCenter
                    )

                    clipPath(loupeClip) {
                        drawCircle(
                            color = ObsidianBackground,
                            radius = loupeRadius,
                            center = loupeCenter
                        )

                        val zoomFactor = 2.8f
                        val sampleRadiusSrcX = (loupeRadius / (scale * zoomFactor)).roundToInt().coerceAtLeast(12)
                        val sampleRadiusSrcY = (loupeRadius / (scale * zoomFactor)).roundToInt().coerceAtLeast(12)
                        val centerSrcX = (normPt.x * bitmap.width).roundToInt()
                        val centerSrcY = (normPt.y * bitmap.height).roundToInt()

                        val srcLeft = (centerSrcX - sampleRadiusSrcX).coerceIn(0, bitmap.width - 2)
                        val srcTop = (centerSrcY - sampleRadiusSrcY).coerceIn(0, bitmap.height - 2)
                        val srcRight = (centerSrcX + sampleRadiusSrcX).coerceIn(srcLeft + 1, bitmap.width)
                        val srcBottom = (centerSrcY + sampleRadiusSrcY).coerceIn(srcTop + 1, bitmap.height)

                        drawImage(
                            image = imageBitmap,
                            srcOffset = IntOffset(srcLeft, srcTop),
                            srcSize = IntSize(srcRight - srcLeft, srcBottom - srcTop),
                            dstOffset = IntOffset(
                                (loupeCenter.x - loupeRadius).roundToInt(),
                                (loupeCenter.y - loupeRadius).roundToInt()
                            ),
                            dstSize = IntSize(
                                (loupeRadius * 2).roundToInt(),
                                (loupeRadius * 2).roundToInt()
                            )
                        )

                        // Precision target crosshair inside the magnifying loupe
                        val crossLen = loupeRadius * 0.45f
                        drawLine(
                            color = AmberHandle,
                            start = Offset(loupeCenter.x - crossLen, loupeCenter.y),
                            end = Offset(loupeCenter.x + crossLen, loupeCenter.y),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawLine(
                            color = AmberHandle,
                            start = Offset(loupeCenter.x, loupeCenter.y - crossLen),
                            end = Offset(loupeCenter.x, loupeCenter.y + crossLen),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawCircle(
                            color = EmeraldLock,
                            radius = 3.dp.toPx(),
                            center = loupeCenter
                        )
                    }

                    // Loupe outer metallic ring
                    drawCircle(
                        color = AmberHandle,
                        radius = loupeRadius,
                        center = loupeCenter,
                        style = Stroke(width = 3.5.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Live Camera Overlay that renders the smooth dynamic 4-point polygon detected by OpenCV
 * on top of the CameraX preview along with a corner lock & auto-capture progress indicator.
 */
@Composable
fun LiveDetectedQuadOverlay(
    detectedQuad: DetectedQuad?,
    autoCaptureProgress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val quad = detectedQuad ?: return@Canvas
        if (quad.corners.size != 4) return@Canvas

        val pts = quad.corners.map { Offset(it.x * size.width, it.y * size.height) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            lineTo(pts[1].x, pts[1].y)
            lineTo(pts[2].x, pts[2].y)
            lineTo(pts[3].x, pts[3].y)
            close()
        }

        val isLocked = autoCaptureProgress >= 0.65f
        val strokeColor = if (isLocked) EmeraldLock else LaserCyan

        // Translucent fill inside the detected document boundary
        drawPath(
            path = path,
            color = strokeColor.copy(alpha = 0.16f + 0.14f * autoCaptureProgress)
        )

        // Outer glow stroke
        drawPath(
            path = path,
            color = strokeColor.copy(alpha = 0.35f),
            style = Stroke(
                width = 8.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Crisp perimeter stroke
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Corner lock markers
        pts.forEach { corner ->
            drawCircle(
                color = ObsidianBackground,
                radius = 8.dp.toPx(),
                center = corner
            )
            drawCircle(
                color = strokeColor,
                radius = 6.dp.toPx(),
                center = corner
            )
        }
    }
}

private fun lerpOffset(a: Offset, b: Offset, t: Float): Offset = Offset(
    x = a.x + (b.x - a.x) * t,
    y = a.y + (b.y - a.y) * t
)
