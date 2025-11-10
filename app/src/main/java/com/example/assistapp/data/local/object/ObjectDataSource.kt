package com.example.assistapp.data.local.`object`

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

interface ObjectDataSource {
    suspend fun infer(image: ImageProxy): ObjectPoint
}