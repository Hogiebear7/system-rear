package com.hogie.systemrear.rear

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hogie.systemrear.data.Store
import com.hogie.systemrear.service.Steps

/**
 * The System panel, launched ON the rear display (see AndroidManifest: miui.rear.policy = 1).
 */
class RearActivity : ComponentActivity() {
    companion object {
        @Volatile var visible = false
    }

    private var recognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!tokenOk(intent)) {
            finish()
            return
        }
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        if (Store.settings.value.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Steps.start(this)
        setContent { RearRoot(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!tokenOk(intent)) return
    }

    private fun tokenOk(i: Intent?) = i?.getStringExtra(RearLauncher.EXTRA_TOKEN) == Store.settings.value.token

    override fun onResume() {
        super.onResume()
        visible = true
        Store.tick()
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        Steps.stop(this)
        super.onDestroy()
    }

    val locked: Boolean get() = getSystemService(KeyguardManager::class.java).isKeyguardLocked

    /** Runs [action] now, or after the phone is unlocked when lock privacy is on. */
    fun guarded(action: () -> Unit) {
        val km = getSystemService(KeyguardManager::class.java)
        if (Store.settings.value.lockPrivacy && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = action()
            })
        } else {
            action()
        }
    }

    fun buzz(strong: Boolean = false) {
        runCatching {
            val v = getSystemService(VibratorManager::class.java).defaultVibrator
            v.vibrate(VibrationEffect.createPredefined(if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_TICK))
        }
    }

    fun hasPermission(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Voice-to-text for replies. Calls [onText] with the result, or [fail] with a reason. */
    fun listen(onPartial: (String) -> Unit, onText: (String) -> Unit, fail: (String) -> Unit) {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) return fail("Allow the microphone in the front app")
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return fail("No voice typing service on this phone")
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onPartial("Listening…")
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onPartial("…")
            override fun onError(error: Int) = fail(if (error == SpeechRecognizer.ERROR_NO_MATCH) "Didn't catch that" else "Voice error $error")
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) fail("Didn't catch that") else onText(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        )
    }

    fun stopListening() {
        recognizer?.stopListening()
    }
}
