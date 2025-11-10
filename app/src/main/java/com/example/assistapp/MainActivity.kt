package com.example.assistapp

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.assistapp.databinding.ActivityMainBinding
import com.gun0912.tedpermission.coroutine.TedPermission
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var tts: TextToSpeech
    private lateinit var gestureDetector: GestureDetector
    private lateinit var prefs: SharedPreferences
    private var currentScreen = 0
    private var screens = mutableListOf("메인화면","기능 1","기능 2")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        gestureDetector = GestureDetector(this, GestureListener())

        // 기능 목록 불러오기
        loadActiveFeatures()

        binding.btnManageFeatures.setOnClickListener {
            startActivity(Intent(this, FeatureManagerActivity::class.java))
        }

        requestCameraPermission()

        speakCurrent()
    }

    private fun requestCameraPermission() {
        lifecycleScope.launch {
            TedPermission.create()
                .setPermissions(Manifest.permission.CAMERA)
                .setRationaleMessage("카메라 권한이 필요합니다.")
                .setDeniedMessage("[설정] > [권한]에서 권한을 허용할 수 있습니다.")
                .check()
        }
    }

    override fun onResume() {
        super.onResume()
        loadActiveFeatures()
        speakCurrent()
    }

    private fun loadActiveFeatures() {
        val activeSet = prefs.getStringSet("activeFeatures", setOf("1", "2", "3"))!!
        screens = mutableListOf("메인화면")
        for (i in activeSet.sorted()) {
            screens.add("기능 $i")
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
        private val SWIPE_VELOCITY_THRESHOLD = 100
        private var lastTapTime = 0L

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null || e2 == null) return false
            val diffX = e2.x - e1.x

            if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0) movePrev() else moveNext()
                return true
            }
            return false
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val now = System.currentTimeMillis()
            Timber.i("onSingleTapConfirmed")
            if (now - lastTapTime < 400) {
                onDoubleTap()
                lastTapTime = 0
            } else {
                lastTapTime = now
            }
            onDoubleTap()
            return true
        }
    }

    private fun moveNext() {
        currentScreen = (currentScreen + 1) % screens.size
        speakCurrent()
    }

    private fun movePrev() {
        currentScreen = if (currentScreen - 1 < 0) screens.size - 1 else currentScreen - 1
        speakCurrent()
    }

    private fun speakCurrent() {
        val msg = "${screens[currentScreen]}입니다. 더블탭하면 실행합니다."
        binding.textView.text = msg
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun onDoubleTap() {
        Timber.i("onDoubleTap $currentScreen")
        when (screens[currentScreen]) {
            "기능 1" -> startActivity(Intent(this, Feature1Activity::class.java))
            "기능 2" -> startActivity(Intent(this, Feature2Activity::class.java))
            "기능 3" -> startActivity(Intent(this, Feature3Activity::class.java))
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
