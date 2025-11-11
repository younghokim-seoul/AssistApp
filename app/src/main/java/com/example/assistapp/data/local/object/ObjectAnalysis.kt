package com.example.assistapp.data.local.`object`

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.graphics.scale


@Singleton
class ObjectAnalysis @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ObjectDataSource, AutoCloseable {

    private val confidenceThreshold = 0.7f
    private val modelFile = "weights_int8.tflite"
    private val labelFile = "labels.txt"

    private val labels: List<String> by lazy {
        loadLabels()
    }

    private val interpreter: Interpreter by lazy {
        val modelBuffer = loadModelFile()
        val options = Interpreter.Options().apply {
            numThreads = 2
        }
        Interpreter(modelBuffer, options)
    }

    private val shape: IntArray by lazy { interpreter.getInputTensor(0).shape() }
    private val modelH: Int by lazy { shape[1] }
    private val modelW: Int by lazy { shape[2] }
    private val imageMean = 127.5f
    private val imageStd = 127.5f

    private val outputShape: IntArray by lazy {
        interpreter.getOutputTensor(0).shape() // [1, 84, 8400]
    }

    private val numClasses: Int by lazy {
        labels.size
    }

    private val numProposals: Int by lazy {
        outputShape[2] // 8400
    }

    private val outputBuffer: Array<Array<FloatArray>> by lazy {
        val expectedChannels = 4 + numClasses
        if (outputShape[0] != 1 || outputShape[1] != expectedChannels || outputShape[2] != numProposals) {
            throw IllegalArgumentException(
                "TFLite 모델 출력 Shape 불일치! " +
                        "예상: [1, $expectedChannels, $numProposals], " +
                        "실제: ${outputShape.contentToString()}"
            )
        }
        // [1][84][8400] 크기의 Float 배열 생성
        Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
    }

    init {
        Timber.i("modelH => $modelH modelW $modelW")
    }


    /**
     * TFLite 모델 파일을 MappedByteBuffer로 로드합니다.
     */
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(): List<String> {
        return context.assets.open(labelFile).bufferedReader().useLines { lines ->
            lines.filter { it.isNotEmpty() }.toList()
        }
    }

    override suspend fun infer(image: ImageProxy): ObjectPoint {

       return withContext(Dispatchers.Default) {
            val bitmap = imageToBitmap(image)
            val inputBuffer = preProcess(bitmap)

            // 2. 추론 실행
            val outputs = mutableMapOf<Int, Any>()
            outputs[0] = outputBuffer // 'by lazy'로 생성된 버퍼 재사용
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            val detected = postProcess(outputBuffer[0])
            return@withContext ObjectPoint(detected = detected)
        }
    }


    private fun imageToBitmap(imageProxy: ImageProxy): Bitmap = imageProxy.toBitmap()


    private fun preProcess(bitmap: Bitmap): ByteBuffer {
        val rescaledBitmap = bitmap.scale(modelW, modelH)
        // 1 * W * H * 3(RGB) * 4(Float)
        val cap = 1 * modelW * modelH * 3 * 4
        val buffer = ByteBuffer.allocateDirect(cap).order(ByteOrder.nativeOrder())

        val intValues = IntArray(modelW * modelH)
        rescaledBitmap.getPixels(intValues, 0, modelW, 0, 0, modelW, modelH)

        // 픽셀 정규화: (값 - 127.5) / 127.5
        for (pixelValue in intValues) {
            buffer.putFloat((((pixelValue shr 16) and 0xFF) - imageMean) / imageStd) // Red
            buffer.putFloat((((pixelValue shr 8) and 0xFF) - imageMean) / imageStd)  // Green
            buffer.putFloat(((pixelValue and 0xFF) - imageMean) / imageStd)         // Blue
        }

        buffer.rewind()
        return buffer
    }

    private fun postProcess(detections: Array<FloatArray>): Boolean {
        // detections shape is [84, 8400]
        for (i in 0 until numProposals) { // 8400번 반복
            for (c in 0 until numClasses) { // 80번 반복

                // 디양자화 없이 Float 퍼센테이지를 바로 읽음
                val classConfidence = detections[c + 4][i]

                if (classConfidence > confidenceThreshold) {
                     Timber.d("감지됨: ${labels[c]} ($classConfidence)") // 필요시 로그 활성화
                    return true // 감지 성공!
                }
            }
        }
        return false // 감지 실패
    }

    override fun close() {
        interpreter.close()
    }
}