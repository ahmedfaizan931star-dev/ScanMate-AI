package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object FileUtils {
    fun createUniqueImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = context.getExternalFilesDir("Scans")
        if (storageDir?.exists() == false) storageDir.mkdirs()
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    fun appFolder(context: Context, name: String): File? {
        val dir = context.getExternalFilesDir(name)
        if (dir?.exists() == false) dir.mkdirs()
        return dir
    }

    fun listManagedFiles(context: Context): List<File> {
        val folders = listOf("Scans", "PDFs", "QRCodes", "OCR", "Backups")
        return folders.flatMap { folder ->
            appFolder(context, folder)?.listFiles()?.toList().orEmpty()
        }.sortedByDescending { it.lastModified() }
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share File"))
    }

    fun shareText(context: Context, text: String, title: String = "Share Text") {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(shareIntent, title))
    }

    suspend fun saveTextFile(context: Context, text: String, filename: String): File? = withContext(Dispatchers.IO) {
        try {
            val storageDir = appFolder(context, "OCR") ?: return@withContext null
            val safeName = filename.ifBlank { "OCR_${System.currentTimeMillis()}" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            val file = File(storageDir, if (safeName.endsWith(".txt")) safeName else "$safeName.txt")
            FileOutputStream(file).use { out -> out.write(text.toByteArray()) }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveBitmapAsPng(context: Context, bitmap: Bitmap, filename: String): File? = withContext(Dispatchers.IO) {
        try {
            val storageDir = appFolder(context, "QRCodes") ?: return@withContext null
            val file = File(storageDir, "$filename.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decodeSampledBitmap(path: String, reqWidth: Int = 1600, reqHeight: Int = 1600): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    suspend fun generatePdf(context: Context, images: List<Bitmap>, filename: String): File? = withContext(Dispatchers.IO) {
        generatePdfInternal(context, images, filename)
    }

    suspend fun generatePdfFromPaths(
        context: Context,
        imagePaths: List<String>,
        filename: String,
        quality: PdfExportQuality = PdfExportQuality.BALANCED
    ): File? = withContext(Dispatchers.IO) {
        val targetSize = when (quality) {
            PdfExportQuality.SMALL -> 1000
            PdfExportQuality.BALANCED -> 1600
            PdfExportQuality.HIGH -> 2400
        }
        val bitmaps = imagePaths.mapNotNull { decodeSampledBitmap(it, targetSize, targetSize) }
        generatePdfInternal(context, bitmaps, filename)
    }

    private fun generatePdfInternal(context: Context, images: List<Bitmap>, filename: String): File? {
        if (images.isEmpty()) return null
        return try {
            val pdfDocument = PdfDocument()
            val a4Width = 595
            val a4Height = 842

            for ((index, bitmap) in images.withIndex()) {
                val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
                var targetWidth = a4Width
                var targetHeight = (targetWidth * aspectRatio).toInt()

                if (targetHeight > a4Height) {
                    targetHeight = a4Height
                    targetWidth = (targetHeight / aspectRatio).toInt()
                }

                val pageInfo = PdfDocument.PageInfo.Builder(a4Width, a4Height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val left = (a4Width - targetWidth) / 2f
                val top = (a4Height - targetHeight) / 2f
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                page.canvas.drawBitmap(scaledBitmap, left, top, null)
                if (scaledBitmap !== bitmap) scaledBitmap.recycle()
                pdfDocument.finishPage(page)
            }

            val storageDir = appFolder(context, "PDFs") ?: return null
            val safeName = filename.ifBlank { "ScanMate_${System.currentTimeMillis()}" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            val file = File(storageDir, "$safeName.pdf")
            FileOutputStream(file).use { outputStream -> pdfDocument.writeTo(outputStream) }
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun mimeTypeFor(file: File): String = when (file.extension.lowercase(Locale.US)) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "txt" -> "text/plain"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    fun applyFilter(original: Bitmap, type: FilterType): Bitmap {
        val config = original.config ?: Bitmap.Config.ARGB_8888
        val result = Bitmap.createBitmap(original.width, original.height, config)
        val canvas = Canvas(result)
        val paint = Paint()

        val colorMatrix = ColorMatrix()
        when (type) {
            FilterType.ORIGINAL -> Unit
            FilterType.GRAYSCALE -> colorMatrix.setSaturation(0f)
            FilterType.BLACK_WHITE -> {
                colorMatrix.setSaturation(0f)
                val contrast = 2.4f
                val brightness = -180f
                colorMatrix.postConcat(ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterType.BRIGHT -> {
                val contrast = 1.2f
                val brightness = 30f
                colorMatrix.set(floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(original, 0f, 0f, paint)
        return result
    }
}

enum class FilterType {
    ORIGINAL, GRAYSCALE, BLACK_WHITE, BRIGHT
}

enum class PdfExportQuality(val label: String, val description: String) {
    SMALL("Small", "Lower memory and smaller files"),
    BALANCED("Balanced", "Recommended for normal documents"),
    HIGH("High", "Sharper output for important scans")
}
