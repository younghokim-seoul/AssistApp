package com.example.assistapp.data.local.papar

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
import com.example.assistapp.data.local.`object`.ObjectDataSource
import com.example.assistapp.data.local.`object`.ObjectPoint


@Singleton
class PaperAnalysis @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PaperDataSource, AutoCloseable {


    private val modelFile = "paper_classifier.tflite"
    private val labelFile = "paper_label.txt"

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

    private val inputShape: IntArray by lazy { interpreter.getInputTensor(0).shape() } // [1, 3, 224, 224
    private val modelC: Int by lazy { inputShape[1] }
    private val modelH: Int by lazy { inputShape[2] }
    private val modelW: Int by lazy { inputShape[3] }
    private val imageStd = 255.0f

    private val outputShape: IntArray by lazy { interpreter.getOutputTensor(0).shape() } // [1, 2]
    private val numClasses: Int by lazy { outputShape[1] } // 2


    private val outputBuffer: Array<FloatArray> by lazy {
        // Shape 검증
        if (numClasses != labels.size) {
            throw IllegalArgumentException(
                "모델 출력 클래스 수(${numClasses})와 " +
                        "레이블 파일(${labelFile})의 줄 수(${labels.size})가 다릅니다."
            )
        }
        Array(outputShape[0]) { FloatArray(outputShape[1]) }
    }

    init {
        Timber.i("modelH => $modelH modelW $modelW modelC $modelC")
    }

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

    override suspend fun infer(image: ImageProxy): PaperResult {

       return withContext(Dispatchers.Default) {
            val bitmap = imageToBitmap(image)
            val inputBuffer = preProcess(bitmap)

           val outputs = mutableMapOf<Int, Any>()
           outputs[0] = outputBuffer // [1, 2] 버퍼 재사용
           interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)


            return@withContext postProcess(outputBuffer[0])
        }
    }


    private fun imageToBitmap(imageProxy: ImageProxy): Bitmap = imageProxy.toBitmap()


    private fun preProcess(bitmap: Bitmap): ByteBuffer {
        val rescaledBitmap = Bitmap.createScaledBitmap(bitmap, modelW, modelH, true)
        val cap = 1 * modelC * modelH * modelW * 4 // 1 * 3 * 224 * 224 * 4(Float)
        val buffer = ByteBuffer.allocateDirect(cap).order(ByteOrder.nativeOrder())

        val intValues = IntArray(modelW * modelH)
        rescaledBitmap.getPixels(intValues, 0, modelW, 0, 0, modelW, modelH)

        // NCHW (평면형) 형식으로 버퍼 채우기
        // [RRRR...][GGGG...][BBBB...]
        val area = modelW * modelH
        for (i in 0 until area) {
            val pixelValue = intValues[i]
            // Red 채널 (인덱스 0 ~ area-1)
            buffer.putFloat(((pixelValue shr 16) and 0xFF) / imageStd)
        }
        for (i in 0 until area) {
            val pixelValue = intValues[i]
            // Green 채널 (인덱스 area ~ 2*area-1)
            buffer.putFloat(((pixelValue shr 8) and 0xFF) / imageStd)
        }
        for (i in 0 until area) {
            val pixelValue = intValues[i]
            // Blue 채널 (인덱스 2*area ~ 3*area-1)
            buffer.putFloat((pixelValue and 0xFF) / imageStd)
        }

        buffer.rewind()
        return buffer
    }

    private fun postProcess(probabilities: FloatArray): PaperResult {

        if (probabilities.isEmpty()) return PaperResult.empty

        var maxIndex = 0
        var maxConfidence = probabilities[0]

        for (i in 1 until probabilities.size) {
            if (probabilities[i] > maxConfidence) {
                maxConfidence = probabilities[i]
                maxIndex = i
            }
        }

        return PaperResult(
            detectedLabel = labels[maxIndex],
            confidence = maxConfidence
        )
    }

    override fun close() {
        interpreter.close()
    }
}