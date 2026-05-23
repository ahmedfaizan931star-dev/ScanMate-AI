package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object QRUtils {
    fun generateQRCode(text: String, size: Int = 512): Bitmap? {
        if (text.isBlank()) return null
        return try {
            val bitMatrix: BitMatrix = QRCodeWriter().encode(text.trim(), BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(bitMatrix.width, bitMatrix.height, Bitmap.Config.RGB_565)
            for (x in 0 until bitMatrix.width) {
                for (y in 0 until bitMatrix.height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

object ZipUtils {
    suspend fun createZip(context: Context, sourceFiles: List<File>, zipFileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val storageDir = FileUtils.appFolder(context, "Backups") ?: return@withContext null
            val zipFile = File(storageDir, "$zipFileName.zip")
            val files = sourceFiles.filter { it.exists() && it.isFile }
            if (files.isEmpty()) return@withContext null

            ZipOutputStream(FileOutputStream(zipFile).buffered()).use { zos ->
                files.forEach { file ->
                    FileInputStream(file).buffered().use { fis ->
                        val entry = ZipEntry(file.name)
                        zos.putNextEntry(entry)
                        fis.copyTo(zos, bufferSize = 16 * 1024)
                        zos.closeEntry()
                    }
                }
            }
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
