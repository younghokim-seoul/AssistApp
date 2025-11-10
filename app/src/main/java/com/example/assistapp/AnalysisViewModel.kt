package com.example.assistapp

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assistapp.data.local.`object`.ObjectDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val objectAnalysis: ObjectDataSource
) : ViewModel() {

    private val processingMutex = Mutex()
    fun startAnalysis(imageProxy: ImageProxy) {

        if (!processingMutex.tryLock()) {
            imageProxy.close()
            return
        }
        viewModelScope.launch {
            runCatching {
                objectAnalysis.infer(imageProxy)
            }.also {
                imageProxy.close()
                processingMutex.unlock()
            }
        }
    }


}