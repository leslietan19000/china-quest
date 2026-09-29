package org.chinaquest.app

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Speech is started only by [speak]. The bundled recordings work without a TTS engine. */
enum class SpeechStatus { READY, INITIALIZING, MISSING_VOICE, ENGINE_ERROR, CLOSED }

/**
 * UI-facing speech adapter. Call its methods on the main thread. A new tap replaces the current
 * utterance, and only the latest tap is retained while the optional Android engine initializes.
 */
class OfflineChineseSpeech internal constructor(
    private val clipPlayer: SpeechClipPlayer,
    private val engineFactory: SpeechEngineFactory,
    private val onState: (SpeechStatus) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    constructor(
        context: Context,
        onState: (SpeechStatus) -> Unit,
        onMessage: (String) -> Unit,
    ) : this(
        AssetClipPlayer(context.applicationContext),
        AndroidSpeechEngineFactory(context.applicationContext),
        onState,
        onMessage,
    )

    var status: SpeechStatus = SpeechStatus.READY
        private set

    private var engine: SpeechEngine? = null
    private var engineInitializing = false
    private var selectedVoice = false
    private var pendingText: String? = null
    private var utteranceNumber = 0L
    private var currentUtterance: String? = null
    private var noVoiceAvailable = false
    private var ttsFailures = 0

    fun speak(text: String) {
        if (status == SpeechStatus.CLOSED) return
        val value = text.trim()
        if (value.isEmpty()) return
        // This is for a tapped character or word, not for reading whole lessons aloud.
        if (value.length > 40) {
            onMessage("请点按一个汉字或词语来听发音。")
            return
        }
        clipPlayer.stop()
        engine?.stop()
        currentUtterance = null
        pendingText = null
        if (clipPlayer.hasClip(value)) {
            if (clipPlayer.play(value) { playbackError() }) {
                changeStatus(SpeechStatus.READY)
            } else {
                playbackError()
            }
            return
        }
        pendingText = value
        if (noVoiceAvailable) {
            pendingText = null
            changeStatus(SpeechStatus.MISSING_VOICE)
            noOfflineVoice()
            return
        }
        if (ttsFailures >= 2) {
            pendingText = null
            changeStatus(SpeechStatus.ENGINE_ERROR)
            onMessage("离线语音连续两次失败。请家长检查设备的文字转语音设置。")
            return
        }
        if (selectedVoice) {
            speakPending()
            return
        }
        if (engineInitializing) return
        engineInitializing = true
        changeStatus(SpeechStatus.INITIALIZING)
        try {
            engine = engineFactory.create(::onEngineInitialized, ::onUtteranceError)
        } catch (_: Exception) {
            engineInitializing = false
            pendingText = null
            ttsFailures++
            changeStatus(SpeechStatus.ENGINE_ERROR)
            onMessage("语音引擎无法启动。请家长检查设备的文字转语音设置。")
        }
    }

    /** Stop at navigation, child switch, or Activity.onPause. */
    fun stop() {
        if (status == SpeechStatus.CLOSED) return
        pendingText = null
        currentUtterance = null
        clipPlayer.stop()
        engine?.stop()
    }

    /** Release the MediaPlayer and TTS engine in Activity.onDestroy. */
    fun close() {
        if (status == SpeechStatus.CLOSED) return
        stop()
        clipPlayer.close()
        engine?.close()
        engine = null
        changeStatus(SpeechStatus.CLOSED)
    }

    /** Offer this only behind the parent gate; never launch it automatically. */
    fun voiceSetupIntent(): Intent = Intent("com.android.settings.TTS_SETTINGS")

    private fun onEngineInitialized(success: Boolean) {
        if (status == SpeechStatus.CLOSED || !engineInitializing) return
        engineInitializing = false
        if (!success) {
            pendingText = null
            engine?.close()
            engine = null
            ttsFailures++
            changeStatus(SpeechStatus.ENGINE_ERROR)
            onMessage("语音引擎无法启动。请家长检查设备的文字转语音设置。")
            return
        }
        val available = try { engine?.voices().orEmpty() } catch (_: Exception) { emptyList() }
        val voice = available
            .filter { it.locale.language.equals("zh", ignoreCase = true) && !it.requiresNetwork }
            .sortedWith(
                compareByDescending<SpeechVoice> { it.locale.country.equals("CN", ignoreCase = true) }
                    .thenByDescending { it.quality }
                    .thenBy { it.name }
            )
            .firstOrNull()
        val selected = voice != null && try { engine?.setVoice(voice.name) == true } catch (_: Exception) { false }
        if (!selected) {
            pendingText = null
            engine?.close()
            engine = null
            noVoiceAvailable = true
            changeStatus(SpeechStatus.MISSING_VOICE)
            noOfflineVoice()
            return
        }
        selectedVoice = true
        changeStatus(SpeechStatus.READY)
        speakPending()
    }

    private fun speakPending() {
        val value = pendingText ?: return
        pendingText = null
        val utterance = "china-quest-${++utteranceNumber}"
        currentUtterance = utterance
        val queued = try { engine?.speak(value, utterance) == true } catch (_: Exception) { false }
        if (!queued) {
            currentUtterance = null
            ttsFailures++
            changeStatus(SpeechStatus.ENGINE_ERROR)
            onMessage("这次发音没有播放。请家长检查设备的语音设置。")
        }
    }

    private fun onUtteranceError(utteranceId: String) {
        if (status == SpeechStatus.CLOSED || utteranceId != currentUtterance) return
        currentUtterance = null
        ttsFailures++
        changeStatus(SpeechStatus.ENGINE_ERROR)
        onMessage("这次发音没有播放。请家长检查设备的语音设置。")
    }

    private fun playbackError() {
        if (status == SpeechStatus.CLOSED) return
        changeStatus(SpeechStatus.ENGINE_ERROR)
        onMessage("这次发音没有播放。请家长检查设备音量后再试。")
    }

    private fun noOfflineVoice() {
        onMessage("这台设备缺少离线中文语音。请家长打开语音设置安装中文语音。")
    }

    private fun changeStatus(next: SpeechStatus) {
        if (status == next) return
        status = next
        onState(next)
    }
}

internal interface SpeechClipPlayer {
    fun hasClip(text: String): Boolean
    fun play(text: String, onError: () -> Unit): Boolean
    fun stop()
    fun close()
}

internal data class SpeechVoice(
    val name: String,
    val locale: Locale,
    val requiresNetwork: Boolean,
    val quality: Int,
)

internal interface SpeechEngine {
    fun voices(): List<SpeechVoice>
    fun setVoice(name: String): Boolean
    fun speak(text: String, utteranceId: String): Boolean
    fun stop()
    fun close()
}

internal fun interface SpeechEngineFactory {
    fun create(onInitialized: (Boolean) -> Unit, onError: (String) -> Unit): SpeechEngine
}

/** Assets are copied to a single private cache file so playback also works if APK assets compress Ogg. */
internal class AssetClipPlayer(private val context: Context) : SpeechClipPlayer {
    private val paths: Map<String, String> = try {
        val json = JSONObject(context.assets.open("audio/manifest.json").bufferedReader().use { it.readText() })
        val entries = json.getJSONArray("clips")
        buildMap {
            for (i in 0 until entries.length()) {
                val clip = entries.getJSONObject(i)
                put(clip.getString("text"), clip.getString("path"))
            }
        }
    } catch (_: Exception) { emptyMap() }
    private var player: MediaPlayer? = null
    private val cacheFile = File(context.cacheDir, "china-quest-speech.ogg")

    override fun hasClip(text: String): Boolean = paths.containsKey(text)

    override fun play(text: String, onError: () -> Unit): Boolean {
        stop()
        val path = paths[text] ?: return false
        return try {
            context.assets.open(path).use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            val next = MediaPlayer()
            player = next
            next.setDataSource(cacheFile.absolutePath)
            next.setOnCompletionListener { if (player === next) stop() }
            next.setOnErrorListener { _, _, _ ->
                if (player === next) {
                    stop()
                    onError()
                }
                true
            }
            next.prepare()
            next.start()
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    override fun stop() {
        val old = player
        player = null
        if (old != null) {
            old.setOnCompletionListener(null)
            old.setOnErrorListener(null)
            try { old.stop() } catch (_: IllegalStateException) { }
            old.release()
        }
        cacheFile.delete()
    }

    override fun close() = stop()
}

private class AndroidSpeechEngineFactory(private val context: Context) : SpeechEngineFactory {
    override fun create(onInitialized: (Boolean) -> Unit, onError: (String) -> Unit): SpeechEngine =
        AndroidSpeechEngine(context, onInitialized, onError)
}

private class AndroidSpeechEngine(
    context: Context,
    onInitialized: (Boolean) -> Unit,
    onUtteranceError: (String) -> Unit,
) : SpeechEngine {
    private val main = Handler(Looper.getMainLooper())
    private val tts: TextToSpeech = TextToSpeech(context) { result ->
        main.post { onInitialized(result == TextToSpeech.SUCCESS) }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = Unit
            @Deprecated("Android callback without error code")
            override fun onError(utteranceId: String) { main.post { onUtteranceError(utteranceId) } }
            override fun onError(utteranceId: String, errorCode: Int) { main.post { onUtteranceError(utteranceId) } }
        })
    }

    override fun voices(): List<SpeechVoice> = tts.voices.orEmpty().map {
        SpeechVoice(it.name, it.locale, it.isNetworkConnectionRequired, it.quality)
    }

    override fun setVoice(name: String): Boolean {
        val voice = tts.voices?.firstOrNull { it.name == name } ?: return false
        return tts.setVoice(voice) == TextToSpeech.SUCCESS
    }

    override fun speak(text: String, utteranceId: String): Boolean =
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS

    override fun stop() { tts.stop() }
    override fun close() { tts.shutdown() }
}
