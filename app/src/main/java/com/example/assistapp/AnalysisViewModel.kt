package com.example.assistapp

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aallam.openai.api.file.FileSource
import com.aallam.openai.api.file.FileUpload
import com.aallam.openai.api.file.Purpose
import com.aallam.openai.client.OpenAI
import com.example.assistapp.data.local.`object`.ObjectDataSource
import com.example.assistapp.data.network.model.Summary
import com.example.assistapp.util.toTempFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import android.util.Base64
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.model.ModelId
import com.example.assistapp.util.toBase64

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val objectAnalysis: ObjectDataSource,
    private val openAI: OpenAI,
    private val json: Json
) : ViewModel() {

    private val processingMutex = Mutex()
    fun startAnalysis(imageProxy: ImageProxy) {

        if (!processingMutex.tryLock()) {
            imageProxy.close()
            return
        }
        viewModelScope.launch {
            var detectedBitmap: Bitmap? = null

            runCatching {
                val result = objectAnalysis.infer(imageProxy)

                if (result.detected) {
                    Timber.d("!!! 옷 감지 성공. 프레임 캡처 !!!")
                    detectedBitmap = imageProxy.toBitmap()
                }
            }.also {
                imageProxy.close()
            }


            detectedBitmap?.let { bitmap ->
                var tempFile: File? = null
                try {
                    tempFile = bitmap.toTempFile(context)
                    Timber.d("GPT 요약 요청 시작...")
                    val summary = requestSummaryWithSdk(tempFile)
                    Timber.d("GPT 요약: ${summary.summaries}")
                } catch (e: Exception) {
                    Timber.e(e, "GPT API 호출 또는 파일 변환 실패")
                } finally {
                    // 4-3. 임시 파일 정리
                    tempFile?.delete()
                }
            }
            processingMutex.unlock()
        }
    }

    private suspend fun requestSummaryWithSdk(file: File): Summary {
        val startTime = System.currentTimeMillis()

        val base64Image = file.toBase64()

        val promptText = """
            옷 사진을 분석해서 한글로 요약주세요.
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
}