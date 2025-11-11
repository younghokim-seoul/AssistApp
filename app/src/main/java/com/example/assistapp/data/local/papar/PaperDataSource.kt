package com.example.assistapp.data.local.papar

import androidx.camera.core.ImageProxy

interface PaperDataSource {
    suspend fun infer(image: ImageProxy): PaperResult
}