package com.ddakmatchum.car.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener as TtsEvents
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import android.speech.tts.TextToSpeech as AndroidTts

/**
 * 음성: Pleos Gleo AI TTS/STT (온디바이스) → 안 되면 안드로이드 기본 TTS.
 * STT가 없으면 화면의 큰 [네]/[아니요] 버튼으로 대신한다.
 */
class Voice(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var pleosTts: PleosTts? = null
    private var pleosReady = false
    private var androidTts: AndroidTts? = null
    private var androidReady = false
    private var stt: SpeechToText? = null
    private var sttReady = false

    private var afterSpeak: (() -> Unit)? = null
    private var pendingListen: ((String?) -> Unit)? = null
    private var best: String? = null

    private val _status = MutableStateFlow("음성 준비 중")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()
    private val _heard = MutableStateFlow("")
    val heard: StateFlow<String> = _heard.asStateFlow()
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val listenTimeout = Runnable { finishListen(best) }

    private val ttsEvents = object : TtsEvents {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() {
            main.post { finishSpeak() }
        }
        override fun onError(error: TtsError) {
            main.post { finishSpeak() }
        }
        override fun onStop() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val sttResults = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            main.post {
                if (text.isNotBlank()) {
                    best = text
                    _heard.value = text
                }
                if (isFinal) finishListen(best)
            }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() {
            main.post { finishListen(best) }
        }
        override fun onError(error: SttError) {
            main.post { finishListen(best) }
        }
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        initPleosTts()
        initStt()
    }

    private fun initPleosTts() {
        runCatching {
            PleosTts(app, TtsMode.ON_DEVICE).also { t ->
                pleosTts = t
                t.initialize(object : TtsInit {
                    override fun onSuccess() {
                        main.post {
                            t.addEventListener(ttsEvents)
                            t.setLocale(Locale.KOREA)
                            pleosReady = true
                            updateStatus()
                        }
                    }
                    override fun onFailure(error: TtsError) {
                        main.post { useAndroidTts() }
                    }
                })
            }
        }.onFailure { useAndroidTts() }
    }

    /** 마이크 권한을 허용한 뒤 다시 호출 */
    fun initStt() {
        if (sttReady) return
        runCatching { stt?.release() }
        stt = null
        runCatching {
            SpeechToText(app, SttMode.ON_DEVICE).also { engine ->
                stt = engine
                engine.initialize(object : SttInit {
                    override fun onSuccess() {
                        main.post {
                            engine.addListener(sttResults)
                            engine.setLocale(Locale.KOREA)
                            sttReady = true
                            updateStatus()
                        }
                    }
                    override fun onFailure(error: SttError) {
                        main.post { updateStatus() }
                    }
                })
            }
        }.onFailure { updateStatus() }
    }

    private fun useAndroidTts() {
        if (androidTts != null) return
        androidTts = AndroidTts(app) { code ->
            main.post {
                androidReady = code == AndroidTts.SUCCESS
                androidTts?.language = Locale.KOREAN
                androidTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        main.post { finishSpeak() }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post { finishSpeak() }
                    }
                })
                updateStatus()
            }
        }
    }

    private fun updateStatus() {
        val out = when {
            pleosReady -> "Pleos TTS"
            androidReady -> "기본 TTS"
            else -> "음성 출력 없음"
        }
        val input = if (sttReady) "Pleos STT" else "버튼 입력"
        _status.value = "$out, $input"
    }

    val canListen: Boolean get() = sttReady

    fun speak(text: String, then: (() -> Unit)? = null) {
        main.post {
            afterSpeak = then
            _speaking.value = true
            when {
                pleosReady -> runCatching { pleosTts?.speak(text, true) }.onFailure { finishSpeak() }
                androidReady -> androidTts?.speak(text, AndroidTts.QUEUE_FLUSH, null, "u" + text.hashCode())
                else -> main.postDelayed({ finishSpeak() }, 1200)
            }
        }
    }

    private fun finishSpeak() {
        _speaking.value = false
        val cb = afterSpeak ?: return
        afterSpeak = null
        cb()
    }

    fun listen(timeoutMs: Long = 6000, onResult: (String?) -> Unit) {
        main.post {
            finishListen(null)
            if (!sttReady) {
                onResult(null)
                return@post
            }
            best = null
            _heard.value = ""
            pendingListen = onResult
            _listening.value = true
            runCatching { stt?.request() }.onFailure {
                finishListen(null)
                return@post
            }
            main.postDelayed(listenTimeout, timeoutMs)
        }
    }

    fun cancelListen() = main.post { finishListen(null) }

    private fun finishListen(result: String?) {
        val cb = pendingListen ?: return
        pendingListen = null
        _listening.value = false
        main.removeCallbacks(listenTimeout)
        runCatching { stt?.stop() }
        cb(result)
    }

    fun release() {
        finishListen(null)
        runCatching { pleosTts?.release() }
        runCatching { stt?.release() }
        runCatching { androidTts?.shutdown() }
    }
}

/** "응", "네 좋아요" → 예 / "아니", "됐어" → 아니오 / 그 외 → 모름 */
object Answer {
    private val no = listOf("아니", "됐어", "싫어", "취소", "괜찮아", "안 ", "안해", "말고", "노")
    private val yes = listOf("응", "네", "예", "그래", "좋아", "주문", "어 ", "콜", "오케이", "ok", "그렇게", "부탁")

    fun parse(text: String?): Boolean? {
        val t = (text ?: return null).trim().lowercase() + " "
        if (no.any { t.contains(it) }) return false
        if (yes.any { t.contains(it) }) return true
        return null
    }
}
