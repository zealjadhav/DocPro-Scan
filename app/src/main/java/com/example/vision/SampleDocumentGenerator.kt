package com.example.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Generates high-resolution, realistic camera-captured document bitmaps with natural
 * perspective skew, dark desk background, uneven lighting shadows, colored stamps, and
 * ink signatures.
 */
object SampleDocumentGenerator {

    enum class SampleTemplate(val title: String, val subtitle: String) {
        INVOICE(
            title = "Commercial Invoice #INV-2026-084",
            subtitle = "Skewed page with uneven shadow, table, red stamp & blue signature"
        ),
        TECH_SPEC(
            title = "Optical Vision Architecture Spec",
            subtitle = "Dense technical document ideal for Crisp B&W & OCR text extraction"
        ),
        EXPENSE_RECEIPT(
            title = "Field Engineering Expense Voucher",
            subtitle = "Tilted receipt with colored highlighter & itemized totals"
        )
    }

    fun generateSampleCameraPhoto(template: SampleTemplate): Bitmap {
        val photoW = 1200
        val photoH = 1600
        val photo = Bitmap.createBitmap(photoW, photoH, Bitmap.Config.ARGB_8888)
        val photoCanvas = Canvas(photo)

        // 1. Fill dark walnut / obsidian studio desk background so 4 paper edges have strong contrast
        photoCanvas.drawColor(Color.rgb(24, 20, 18))
        val deskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 23, 20)
            shader = LinearGradient(
                0f, 0f, photoW.toFloat(), photoH.toFloat(),
                intArrayOf(
                    Color.rgb(26, 22, 20),
                    Color.rgb(38, 31, 27),
                    Color.rgb(20, 17, 15)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        photoCanvas.drawRect(0f, 0f, photoW.toFloat(), photoH.toFloat(), deskPaint)

        // Subtle wood grain lines on desk
        val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(20, 255, 220, 190)
            strokeWidth = 2f
        }
        for (y in 40 until photoH step 55) {
            photoCanvas.drawLine(0f, y.toFloat(), photoW.toFloat(), (y + 18).toFloat(), grainPaint)
        }

        // 2. Define 4 perspective-skewed paper corners on the desk
        val pageW = 1000
        val pageH = 1414 // ISO A4 proportion (1 : sqrt(2))
        val dstQuad = when (template) {
            SampleTemplate.INVOICE -> floatArrayOf(
                135f, 140f,   // Top-Left
                1045f, 195f,  // Top-Right
                1095f, 1445f, // Bottom-Right
                85f, 1410f    // Bottom-Left
            )
            SampleTemplate.TECH_SPEC -> floatArrayOf(
                110f, 175f,
                1065f, 125f,
                1085f, 1455f,
                140f, 1425f
            )
            SampleTemplate.EXPENSE_RECEIPT -> floatArrayOf(
                160f, 150f,
                1020f, 180f,
                1060f, 1430f,
                115f, 1450f
            )
        }

        // Draw subtle drop shadow under the skewed paper sheet
        val shadowPath = Path().apply {
            moveTo(dstQuad[0] + 14f, dstQuad[1] + 18f)
            lineTo(dstQuad[2] + 14f, dstQuad[3] + 18f)
            lineTo(dstQuad[4] + 14f, dstQuad[5] + 18f)
            lineTo(dstQuad[6] + 14f, dstQuad[7] + 18f)
            close()
        }
        val dropShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 0, 0, 0)
            style = Paint.Style.FILL
        }
        photoCanvas.drawPath(shadowPath, dropShadowPaint)

        // Draw the skewed paper quad directly on photoCanvas
        val paperQuadPath = Path().apply {
            moveTo(dstQuad[0], dstQuad[1])
            lineTo(dstQuad[2], dstQuad[3])
            lineTo(dstQuad[4], dstQuad[5])
            lineTo(dstQuad[6], dstQuad[7])
            close()
        }
        val solidPaperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 244, 238)
            style = Paint.Style.FILL
        }
        photoCanvas.drawPath(paperQuadPath, solidPaperPaint)

        // 3. Render the high-resolution document content and warp onto the desk photo
        val flatPage = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val pageCanvas = Canvas(flatPage)
        pageCanvas.drawColor(Color.rgb(246, 245, 240))

        val paperGradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(242, 241, 236)
            shader = LinearGradient(
                0f, 0f, pageW.toFloat(), pageH.toFloat(),
                intArrayOf(
                    Color.rgb(250, 249, 245),
                    Color.rgb(235, 234, 228),
                    Color.rgb(196, 198, 204) // Simulated overhead shadow cast across page corner
                ),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        pageCanvas.drawRect(0f, 0f, pageW.toFloat(), pageH.toFloat(), paperGradientPaint)

        when (template) {
            SampleTemplate.INVOICE -> drawInvoiceContent(pageCanvas, pageW)
            SampleTemplate.TECH_SPEC -> drawTechSpecContent(pageCanvas)
            SampleTemplate.EXPENSE_RECEIPT -> drawExpenseVoucherContent(pageCanvas)
        }

        val warpMatrix = Matrix()
        val srcQuad = floatArrayOf(
            0f, 0f,
            pageW.toFloat(), 0f,
            pageW.toFloat(), pageH.toFloat(),
            0f, pageH.toFloat()
        )
        warpMatrix.setPolyToPoly(srcQuad, 0, dstQuad, 0, 4)
        photoCanvas.drawBitmap(
            flatPage,
            warpMatrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        flatPage.recycle()

        return photo
    }

    private fun drawInvoiceContent(canvas: Canvas, w: Int) {
        val darkInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 28, 36)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 42f
        }
        val bodyInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 42, 52)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 25f
        }
        val boldBodyInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 28, 36)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 26f
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 76, 129)
            style = Paint.Style.FILL
        }

        // Top header bar
        canvas.drawRect(70f, 65f, (w - 70).toFloat(), 77f, accentPaint)
        canvas.drawText("APEX OPTICAL SYSTEMS INC.", 70f, 135f, darkInk)
        canvas.drawText("INVOICE #INV-2026-084", 70f, 182f, boldBodyInk)
        canvas.drawText("Date: October 29, 2026   |   Terms: Net 30 Days", 70f, 222f, bodyInk)
        canvas.drawText("Bill To: Horizon Medical Imaging Labs, Seattle WA", 70f, 262f, bodyInk)

        // Yellow highlighter strip on key line (tests Magic Color filter!)
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(115, 255, 235, 59)
            style = Paint.Style.FILL
        }
        canvas.drawRect(66f, 285f, 680f, 326f, highlightPaint)
        canvas.drawText("Priority Dispatch: Calibration Certificate Included", 70f, 314f, boldBodyInk)

        // Table Header
        val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(225, 232, 240)
            style = Paint.Style.FILL
        }
        canvas.drawRect(70f, 365f, (w - 70).toFloat(), 420f, tableHeaderPaint)
        canvas.drawText("DESCRIPTION", 90f, 402f, boldBodyInk)
        canvas.drawText("QTY", 620f, 402f, boldBodyInk)
        canvas.drawText("AMOUNT", 770f, 402f, boldBodyInk)

        val items = listOf(
            Triple("1. High-Speed Line Scan Camera Module 12MP", "2", "$2,400.00"),
            Triple("2. Telecentric Quartz Lens Assembly 35mm", "2", "$1,180.00"),
            Triple("3. Coaxial LED Ring Illuminator Unit", "4", "$760.00"),
            Triple("4. Factory Homography & Flat-Field Calibration", "1", "$550.00")
        )

        var rowY = 475f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 188, 198)
            strokeWidth = 2f
        }
        for ((desc, qty, amt) in items) {
            canvas.drawText(desc, 90f, rowY, bodyInk)
            canvas.drawText(qty, 635f, rowY, bodyInk)
            canvas.drawText(amt, 770f, rowY, boldBodyInk)
            canvas.drawLine(70f, rowY + 24f, (w - 70).toFloat(), rowY + 24f, linePaint)
            rowY += 78f
        }

        // Totals box
        canvas.drawText("SUBTOTAL: $4,890.00", 630f, 830f, bodyInk)
        canvas.drawText("TAX (0% EXEMPT): $0.00", 630f, 872f, bodyInk)
        val totalPaint = Paint(darkInk).apply { textSize = 34f }
        canvas.drawText("TOTAL DUE: $4,890.00", 545f, 932f, totalPaint)

        // Notes paragraph for OCR extraction verification
        canvas.drawText("Compliance & Warranty Notes:", 70f, 1020f, boldBodyInk)
        canvas.drawText("All optical sensors passed ISO-12233 resolution chart verification.", 70f, 1062f, bodyInk)
        canvas.drawText("Serial Numbers: AOX-99281-A, AOX-99281-B. Retain this scan for", 70f, 1100f, bodyInk)
        canvas.drawText("audit records and automated ERP archival.", 70f, 1138f, bodyInk)

        // Red "VERIFIED SCAN" rubber stamp
        canvas.save()
        canvas.rotate(-12f, 260f, 1260f)
        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(210, 35, 42)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(RectF(110f, 1205f, 430f, 1305f), 14f, 14f, stampPaint)
        val stampTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(210, 35, 42)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 36f
        }
        canvas.drawText("VERIFIED SCAN", 138f, 1268f, stampTextPaint)
        canvas.restore()

        // Royal Blue ink handwritten-style signature
        val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 64, 178)
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val sigPath = Path().apply {
            moveTo(580f, 1275f)
            cubicTo(610f, 1210f, 650f, 1295f, 690f, 1235f)
            cubicTo(720f, 1195f, 745f, 1280f, 795f, 1245f)
            quadTo(835f, 1225f, 885f, 1265f)
        }
        canvas.drawPath(sigPath, sigPaint)
        canvas.drawLine(560f, 1295f, 910f, 1295f, linePaint)
        canvas.drawText("Authorized Signature — Dr. E. Vance", 560f, 1332f, bodyInk)
    }

    private fun drawTechSpecContent(canvas: Canvas) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 24, 32)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 38f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(12, 85, 140)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 28f
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 38, 46)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 24f
        }

        canvas.drawText("SPECIFICATION: FLATBED VISION PIPELINE v4.2", 75f, 125f, titlePaint)
        canvas.drawText("Document ID: ENG-CV-409   |   Classification: Production Architecture", 75f, 170f, bodyPaint)

        val paragraphs = listOf(
            "1. CONTOUR DETECTION & HOMOGRAPHY" to listOf(
                "CameraX ImageAnalysis frames are converted from YUV_420_888 to 8-bit",
                "grayscale luma matrices. A 5x5 Gaussian kernel suppresses sensor noise",
                "prior to Canny edge detection and 4-point Douglas-Peucker polygon",
                "approximation (approxPolyDP). Convexity and interior angle cosines",
                "validate the document boundary before computing the 3x3 homography."
            ),
            "2. ILLUMINATION NORMALIZATION & ADAPTIVE THRESHOLDING" to listOf(
                "To eliminate uneven room shadows and paper creases, a 21x21",
                "morphological dilation followed by median filtering estimates the",
                "background illumination envelope. Dividing the grayscale page by",
                "the background map yields uniform #FFFFFF paper prior to Adaptive",
                "Gaussian Thresholding."
            ),
            "3. SEARCHABLE PDF INVISIBLE TEXT LAYER" to listOf(
                "Google ML Kit Text Recognition v2 extracts TextBlock, Line, and",
                "Element bounding boxes. Each recognized string is drawn onto the",
                "PdfDocument canvas using an alpha=0 Paint scaled to match the exact",
                "pixel coordinates of the warped bitmap."
            )
        )

        var y = 245f
        for ((heading, lines) in paragraphs) {
            canvas.drawText(heading, 75f, y, sectionPaint)
            y += 46f
            for (line in lines) {
                canvas.drawText(line, 75f, y, bodyPaint)
                y += 38f
            }
            y += 34f
        }
    }

    private fun drawExpenseVoucherContent(canvas: Canvas) {
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 26, 34)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 36f
        }
        val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 40, 48)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 26f
        }

        canvas.drawText("FIELD ENGINEERING EXPENSE VOUCHER", 85f, 140f, headerPaint)
        canvas.drawText("--------------------------------------------", 85f, 185f, monoPaint)
        canvas.drawText("Project Code : TITAN-OPTICS-2026", 85f, 235f, monoPaint)
        canvas.drawText("Engineer     : K. Takahashi", 85f, 280f, monoPaint)
        canvas.drawText("Location     : Tokyo Metro Lab #04", 85f, 325f, monoPaint)
        canvas.drawText("--------------------------------------------", 85f, 370f, monoPaint)

        val rows = listOf(
            "Cleanroom Quartz Substrates      $420.00",
            "High-Index Immersion Oil         $115.00",
            "Laser Diode Alignment Jig        $640.00",
            "Express Lab Courier Service       $85.00",
            "Calibration Target Chart ISO     $195.00"
        )
        var y = 435f
        for (r in rows) {
            canvas.drawText(r, 85f, y, monoPaint)
            y += 62f
        }

        canvas.drawText("--------------------------------------------", 85f, y + 10f, monoPaint)
        canvas.drawText("TOTAL REIMBURSEMENT APPROVED:  $1,455.00", 85f, y + 70f, headerPaint)
    }
}
