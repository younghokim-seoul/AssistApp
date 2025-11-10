package com.example.assistapp.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun Bitmap.toTempFile(
    context: Context,
    quality: Int = 90,
    prefix: String = "temp_capture_"
): File = withContext(Dispatchers.IO) {
    val file = File(
        context.cacheDir,
        "${prefix}${System.currentTimeMillis()}.jpg"
    )

    file.outputStream().use { out ->
        this@toTempFile.compress(Bitmap.CompressFormat.JPEG, quality, out)
    }
    file
}

suspend fun File.toBase64(): String = withContext(Dispatchers.IO) {
    val fileBytes = readBytes()
    Base64.encodeToString(fileBytes, Base64.NO_WRAP)
}