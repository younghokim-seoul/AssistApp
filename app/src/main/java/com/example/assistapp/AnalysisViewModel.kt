package com.example.assistapp

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.file.FileSource
import com.aallam.openai.api.file.FileUpload
import com.aallam.openai.api.file.Purpose
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.example.assistapp.data.local.`object`.ObjectDataSource
import com.example.assistapp.data.local.papar.PaperAnalysis
import com.example.assistapp.model.Summary
import com.example.assistapp.util.toBase64
import com.example.assistapp.util.toTempFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

const val ANALYSIS_MODE_KEY = "analysisMode"

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val objectAnalysis: ObjectDataSource,
    private val paperAnalysis: PaperAnalysis,
    private val openAI: OpenAI,
    private val json: Json
) : ViewModel() {


    val analysisMode: StateFlow<AnalysisMode> =
        savedStateHandle.getStateFlow(key = ANALYSIS_MODE_KEY, initialValue = AnalysisMode.CLOTHES)

    private val _stateFlow: MutableStateFlow<AnalysisState> =
        MutableStateFlow(AnalysisState.Uninitialized)
    val stateFlow: StateFlow<AnalysisState> get() = _stateFlow

    private val processingMutex = Mutex()


    fun startAnalysis(imageProxy: ImageProxy) {

        if (_stateFlow.value is AnalysisState.Success) {
            imageProxy.close()
            return
        }

        if (!processingMutex.tryLock()) {
            imageProxy.close()
            return
        }

        setState(AnalysisState.Scanning)

        viewModelScope.launch {
            runCatching {
                when (analysisMode.value) {
                    AnalysisMode.CLOTHES -> runClothesAnalysis(imageProxy)
                    AnalysisMode.PAPER -> runPaperAnalysis(imageProxy)
                }
            }.onFailure {
                setState(AnalysisState.Error(it))
            }.also {
                processingMutex.unlock()
            }
        }
    }

    private suspend fun runPaperAnalysis(imageProxy: ImageProxy) {
        var detectedBitmap: Bitmap? = null


        runCatching {
            val result = paperAnalysis.infer(imageProxy)
            if (result.detectedLabel != null) {
                Timber.d("!!! 종이 감지 성공: ${result.detectedLabel} (${result.confidence}) !!!")
                setState(AnalysisState.PaperDetected(result.detectedLabel, result.confidence))
                detectedBitmap = imageProxy.toBitmap()
            }
        }.onFailure {
            Timber.e(it)
            setState(AnalysisState.Error(it))
        }.also {
            if (detectedBitmap == null) imageProxy.close()
        }
        detectedBitmap?.let { requestOpenAI(it) }
    }

    private suspend fun runClothesAnalysis(imageProxy: ImageProxy) {
        var detectedBitmap: Bitmap? = null

        runCatching {
            val result = objectAnalysis.infer(imageProxy)

            if (result.detected) {
                setState(AnalysisState.ObjectDetected)
                detectedBitmap = imageProxy.toBitmap()
            }
        }.onFailure {
            Timber.e(it)
            setState(AnalysisState.Error(it))
        }.also {
            if (detectedBitmap == null) imageProxy.close()
        }

        detectedBitmap?.let { requestOpenAI(it) }
    }

    private suspend fun requestOpenAI(bitmap: Bitmap) {
        var tempFile: File? = null
        runCatching {
            setState(AnalysisState.RequestingSummary)
            tempFile = bitmap.toTempFile(context)

            val summary = requestSummaryWithSdk(tempFile) // API 호출
            Timber.d("OPEN AI summary ${summary.summaries}")
            setState(AnalysisState.Success(summary.summaries))
        }.onFailure {
            setState(AnalysisState.Error(it))
        }.also {
            tempFile?.delete()
        }
    }

    private suspend fun requestSummaryWithSdk(file: File): Summary {
        val startTime = System.currentTimeMillis()

        val base64Image = file.toBase64()


        val promptText = """
            사진을 분석해서 한글로 요약주세요.
            반드시 아래 JSON으로만 응답:
            {
              "summaries": ""
            }
        """.trimIndent()

        val request = ChatCompletionRequest(
            model = ModelId("gpt-5"),
            responseFormat = ChatResponseFormat.JsonObject,
            messages = listOf(
                ChatMessage(
                    role = ChatRole.User,
                    content = listOf(
                        TextPart(promptText),
                        ImagePart("data:image/jpeg;base64,$base64Image")
                    )
                )
            ),
            maxCompletionTokens = 1000
        )

        return withContext(Dispatchers.IO) {
            val response = openAI.chatCompletion(request)
            val jsonText = response.choices.first().message.content
                ?: throw IOException("No content in response")
            val summary = json.decodeFromString<Summary>(jsonText)

            val duration = (System.currentTimeMillis() - startTime) / 1000
            Timber.d("Qna 시간 : ${duration}초")

            return@withContext summary
        }
    }

    private suspend fun getFileUploadId(
        file: File,
        purpose: String = "vision"
    ): String {
        val pathString = file.path
        val path = Path(pathString)
        val fileSource = FileSource(path)
        val request = FileUpload(
            file = fileSource,
            purpose = Purpose(purpose)
        )
        val fileResponse: com.aallam.openai.api.file.File = openAI.file(request = request)

        return fileResponse.id.id
    }

    protected fun setState(state: AnalysisState) {
        _stateFlow.value = state
    }
}

sealed class AnalysisState {

    object Uninitialized : AnalysisState()

    object Scanning : AnalysisState()

    object ObjectDetected : AnalysisState()
    data class PaperDetected(val label: String, val confidence: Float) : AnalysisState()
    object RequestingSummary : AnalysisState()
    data class Success(val summaryScript: String) : AnalysisState() // 최종 요약 결과


    data class Error(
        val e: Throwable
    ) : AnalysisState()

}
