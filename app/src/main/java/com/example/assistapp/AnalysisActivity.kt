package com.example.assistapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.assistapp.databinding.ActivityFeature1Binding
import com.example.assistapp.util.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@AndroidEntryPoint
class AnalysisActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var binding: ActivityFeature1Binding
    private lateinit var gestureDetector: GestureDetector
    private lateinit var tts: TextToSpeech
    private var tapCount = 0

    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService


    private val analysisViewModel by viewModels<AnalysisViewModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeature1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        gestureDetector = GestureDetector(this, GestureListener())
        tts = TextToSpeech(this, this)
        speak("기능 1 화면입니다. 두 번 탭하면 실행합니다.")

        setUpCamera()
        observeData()
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    analysisViewModel.stateFlow.collect { state ->
                        when (state) {
                            is AnalysisState.Uninitialized -> handleUninitialized()
                            is AnalysisState.Scanning -> handleScanning()
                            is AnalysisState.ObjectDetected -> handleObjectDetected()
                            is AnalysisState.PaperDetected -> handlePagerDetected(state.label+"("+String.format("%.2f",state.confidence)+")")
                            is AnalysisState.RequestingSummary -> handleRequestingSummary()
                            is AnalysisState.Success -> handleSuccess(state.summaryScript)
                            is AnalysisState.Error -> handleError()
                        }
                    }
                }
            }
        }
    }

    private fun handleUninitialized() = with(binding) {
        previewView.visible()
        layoutStatus.visible()
    }

    private fun handleScanning() = with(binding) {
        tvStatus.text = "분석 중입니다..."
    }

    private fun handleObjectDetected() = with(binding) {
        Timber.i("인식 김인식")
        tvStatus.text = "인식되었습니다..."
    }

    private fun handlePagerDetected(label: String) = with(binding) {
        tvStatus.text = "인식되었습니다...$label"
    }

    private fun handleRequestingSummary() = with(binding) {
        speak("스캔된 제품 정보를 기다리는 중...")
        tvStatus.text = "스캔된 제품 정보를 기다리는 중..."
    }

    private fun handleSuccess(summary: String) = with(binding) {
        speak(summary)
        tvStatus.text = summary
    }

    private fun handleError() = with(binding) {
        tvStatus.text = "옷을 인식할 수 없습니다."
    }

    private fun setUpCamera() {
        Timber.d("setUpCamera")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = try {
                cameraProviderFuture.get()
            } catch (e: ExecutionException) {
                throw IllegalStateException("Camera initialization failed.", e.cause!!)
            }
            bindCameraUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(this))
    }


    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {

        val resolutionSelector = ResolutionSelector
            .Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()



        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val analysis = ImageAnalysis
            .Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(cameraExecutor) { imageProxy ->
                    analysisViewModel.startAnalysis(imageProxy)
                }
            }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, analysis
            )
            preview.surfaceProvider = binding.previewView.surfaceProvider
        } catch (exc: IllegalStateException) {
            Timber.e(exc, "Use case binding failed. This must be running on main thread.%s")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale.KOREAN
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null || e2 == null) return false
            val diffX = e2.x - e1.x
            if (diffX > SWIPE_THRESHOLD) {
                finish() // 왼쪽 스와이프 → 이전 화면
                return true
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            tapCount++
            if (tapCount == 1) {
                binding.featureText.text = "기능1이 실행되었습니다."
                speak("기능 1이 실행되었습니다.")
            } else if (tapCount == 2) {
                binding.featureText.text = "기능이 종료되었습니다."
                speak("기능이 종료되었습니다. 메인화면으로 돌아갑니다.")
                startActivity(Intent(this@AnalysisActivity, MainActivity::class.java))
                finish()
            }
            return true
        }
    }

    private fun speak(msg: String) {
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
        cameraExecutor.shutdownNow()
    }

    companion object {
        fun newIntent(context: Context, mode: AnalysisMode): Intent {
            return Intent(context, AnalysisActivity::class.java).apply {
                putExtra(ANALYSIS_MODE_KEY, mode)
            }
        }
    }


}
