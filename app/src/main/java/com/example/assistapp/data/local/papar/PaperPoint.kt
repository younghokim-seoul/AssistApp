package com.example.assistapp.data.local.papar

data class PaperResult(
    val detectedLabel: String?,
    val confidence: Float = 0f
) {
    companion object {
        val empty = PaperResult(detectedLabel = null)
    }
}
