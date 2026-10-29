package com.example

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.example.export.PdfExportManager
import com.example.model.AspectRatioPreset
import com.example.model.NormalizedPoint
import com.example.model.OcrPageResult
import com.example.model.OcrTextBlock
import com.example.model.OcrTextLine
import com.example.model.OcrWordElement
import com.example.model.ScanFilterMode
import com.example.model.ScannedPage
import com.example.vision.DocumentProcessor
import com.example.vision.SampleDocumentGenerator
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DocScan Pro", appName)
    }

    @Test
    fun `orderCornersClockwise orders scrambled 4-point contour into TL TR BR BL`() {
        val scrambled = listOf(
            NormalizedPoint(0.85f, 0.88f), // BR
            NormalizedPoint(0.12f, 0.86f), // BL
            NormalizedPoint(0.86f, 0.14f), // TR
            NormalizedPoint(0.10f, 0.12f)  // TL
        )
        val ordered = DocumentProcessor.orderCornersClockwise(scrambled)
        assertEquals(4, ordered.size)
        // Top-Left has smallest x & y
        assertEquals(0.10f, ordered[0].x, 0.001f)
        assertEquals(0.12f, ordered[0].y, 0.001f)
        // Top-Right
        assertEquals(0.86f, ordered[1].x, 0.001f)
        assertEquals(0.14f, ordered[1].y, 0.001f)
        // Bottom-Right
        assertEquals(0.85f, ordered[2].x, 0.001f)
        assertEquals(0.88f, ordered[2].y, 0.001f)
        // Bottom-Left
        assertEquals(0.12f, ordered[3].x, 0.001f)
        assertEquals(0.86f, ordered[3].y, 0.001f)
    }

    @Test
    fun `sample generation, perspective warp, filters, and searchable PDF export succeed`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sampleBitmap = SampleDocumentGenerator.generateSampleCameraPhoto(
            SampleDocumentGenerator.SampleTemplate.INVOICE
        )
        assertNotNull(sampleBitmap)
        assertTrue(sampleBitmap.width > 500 && sampleBitmap.height > 500)

        val quad = DocumentProcessor.detectDocumentQuad(sampleBitmap)
        assertNotNull(quad)
        assertEquals(4, quad!!.corners.size)

        val warped = DocumentProcessor.warpPerspectiveDocument(
            sourceBitmap = sampleBitmap,
            corners = quad.corners,
            aspectRatioPreset = AspectRatioPreset.ISO_A4
        )
        assertTrue(warped.width >= 320 && warped.height >= 320)

        val crispBw = DocumentProcessor.applyEnhancementFilter(warped, ScanFilterMode.CRISP_BW)
        val magicColor = DocumentProcessor.applyEnhancementFilter(warped, ScanFilterMode.MAGIC_COLOR)
        assertEquals(warped.width, crispBw.width)
        assertEquals(warped.height, magicColor.height)

        val mockOcr = OcrPageResult(
            fullText = "INVOICE #INV-2026-084 TOTAL DUE: $4,890.00",
            blocks = listOf(
                OcrTextBlock(
                    text = "INVOICE #INV-2026-084",
                    boundingBox = Rect(50, 80, 420, 130),
                    lines = listOf(
                        OcrTextLine(
                            text = "INVOICE #INV-2026-084",
                            boundingBox = Rect(50, 80, 420, 130),
                            angleDegrees = 0f,
                            elements = listOf(
                                OcrWordElement("INVOICE", Rect(50, 80, 210, 130), 0f),
                                OcrWordElement("#INV-2026-084", Rect(220, 80, 420, 130), 0f)
                            )
                        )
                    )
                )
            ),
            detectedRotationDegrees = 0,
            sourceWidth = crispBw.width,
            sourceHeight = crispBw.height
        )

        val page = ScannedPage(
            id = "test-page-1",
            title = "Page 1",
            sourceBitmap = sampleBitmap,
            corners = quad.corners,
            filterMode = ScanFilterMode.CRISP_BW,
            aspectRatioPreset = AspectRatioPreset.ISO_A4,
            processedBitmap = crispBw,
            ocrResult = mockOcr
        )

        val pdfResult = PdfExportManager.exportSearchablePdf(
            context = context,
            pages = listOf(page),
            documentTitle = "Test_Searchable_Scan"
        )
        assertTrue(pdfResult.isSuccess)
        val artifact = pdfResult.getOrThrow()
        assertEquals(1, artifact.pageCount)
        assertTrue(artifact.fileSizeBytes > 100L)
        assertNotNull(artifact.shareableFilePath)
        assertTrue(File(artifact.shareableFilePath!!).exists())
    }
}
