package com.example.assistapp

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.example.assistapp.databinding.ActivityFeature2Binding
import java.util.*

class Feature2Activity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var binding: ActivityFeature2Binding
    private lateinit var gestureDetector: GestureDetector
    private lateinit var tts: TextToSpeech
    private var tapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeature2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        gestureDetector = GestureDetector(this, GestureListener())
        tts = TextToSpeech(this, this)
        speak("기능 2 화면입니다. 두 번 탭하면 실행합니다.")
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

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null || e2 == null) return false
            val diffX = e2.x - e1.x
            if (diffX > SWIPE_THRESHOLD) {
                finish()
                return true
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            tapCount++
            if (tapCount == 1) {
                binding.featureText.text = "기능2가 실행되었습니다."
                speak("기능 2가 실행되었습니다.")
            } else if (tapCount == 2) {
                binding.featureText.text = "기능이 종료되었습니다."
                speak("기능이 종료되었습니다. 메인화면으로 돌아갑니다.")
                startActivity(Intent(this@Feature2Activity, MainActivity::class.java))
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
    }
}
