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

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val objectAnalysis: ObjectDataSource,
    private val openAI: OpenAI,
    private val okHttpClient: OkHttpClient,
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
                    val summary = requestSummary(tempFile)
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

    private suspend fun requestSummary(file: File): Summary {
        val fileId = getFileUploadId(file)
        Timber.d("File ID => $fileId")

        val startTime = System.currentTimeMillis()


        val requestBody = buildJsonObject {
            put("model", "gpt-5")
            put("max_output_tokens", 1000)
            putJsonObject("text") {
                putJsonObject("format") {
                    put("type", "json_object")
                }
            }
            putJsonArray("input") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "input_text")
                            put(
                                "text", """
                                옷 사진을 분석해서 한글로 요약주세요.
                                반드시 아래 JSON으로만 응답:
                                {
                                  "summaries": ""
                                }
                            """.trimIndent()
                            )
                        })
                        add(buildJsonObject {
                            put("type", "input_image")
                            put("file_id", fileId)
                        })
                    }
                })
            }
        }.toString()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .header("Authorization", "Bearer ${BuildConfig.ACCESS_KEY}")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("OkHttp Error: ${response.code} ${response.message}")
            }

            val responseBody = requireNotNull(response.body.string())
            val responseJson =
                json.decodeFromString<kotlinx.serialization.json.JsonObject>(responseBody)

            val text = requireNotNull(extractOutputText(responseJson))

            val summary = json.decodeFromString<Summary>(text)

            val duration = (System.currentTimeMillis() - startTime) / 1000

            Timber.d("Qna 시간 : ${duration}초")

            return@withContext summary
        }

    }

    private fun extractOutputText(data: kotlinx.serialization.json.JsonObject): String? {
        data["output_text"]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) return it }
        data["output"]?.jsonArray?.forEach { msg ->
            msg.jsonObject["content"]?.jsonArray?.forEach { item ->
                val itemObj = item.jsonObject
                if (itemObj["type"]?.jsonPrimitive?.content == "output_text") {
                    itemObj["text"]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) return it }
                    itemObj["text"]?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) return it }
                }
            }
        }
        return null
    }

}