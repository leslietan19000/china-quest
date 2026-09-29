package org.chinaquest.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfflineChineseSpeechTest {
    private class Clips(var available: Set<String> = emptySet()) : SpeechClipPlayer {
        val played = mutableListOf<String>()
        var stops = 0
        var closed = false
        override fun hasClip(text: String) = text in available
        override fun play(text: String, onError: () -> Unit): Boolean {
            played += text
            return true
        }
        override fun stop() { stops++ }
        override fun close() { closed = true }
    }

    private class Engine : SpeechEngine {
        var available = emptyList<SpeechVoice>()
        val spoken = mutableListOf<Pair<String, String>>()
        var selected: String? = null
        var stops = 0
        var closed = false
        override fun voices() = available
        override fun setVoice(name: String): Boolean { selected = name; return true }
        override fun speak(text: String, utteranceId: String): Boolean {
            spoken += text to utteranceId
            return true
        }
        override fun stop() { stops++ }
        override fun close() { closed = true }
    }

    private class EngineFactory(val engine: Engine = Engine()) : SpeechEngineFactory {
        var creates = 0
        lateinit var ready: (Boolean) -> Unit
        lateinit var error: (String) -> Unit
        override fun create(onInitialized: (Boolean) -> Unit, onError: (String) -> Unit): SpeechEngine {
            creates++
            ready = onInitialized
            error = onError
            return engine
        }
    }

    private val localChinese = SpeechVoice("local-zh", Locale.SIMPLIFIED_CHINESE, false, 300)
    private val networkChinese = SpeechVoice("network-zh", Locale.SIMPLIFIED_CHINESE, true, 500)

    @Test fun bundledClipPlaysWithoutStartingAndroidTts() {
        val clips = Clips(setOf("山"))
        val factory = EngineFactory()
        val speech = OfflineChineseSpeech(clips, factory, {}, {})

        speech.speak("山")
        speech.speak("山")

        assertEquals(listOf("山", "山"), clips.played)
        assertEquals(0, factory.creates)
        assertEquals(SpeechStatus.READY, speech.status)
        assertTrue(clips.stops >= 2)
    }

    @Test fun packagedClipCanBeOpenedAndReleased() {
        val context = RuntimeEnvironment.getApplication()
        val clips = AssetClipPlayer(context)
        assertTrue(clips.hasClip("山"))
        val cacheFile = File(context.cacheDir, "china-quest-speech.ogg")
        // Robolectric simulates playback and requires media metadata for the exact local path.
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(cacheFile.absolutePath),
            ShadowMediaPlayer.MediaInfo(1500, 0),
        )
        var shadow: ShadowMediaPlayer? = null
        ShadowMediaPlayer.setCreateListener { _, created -> shadow = created }
        val errors = mutableListOf<String>()
        try {
            assertTrue(clips.play("山") { errors += "media error" })
            assertTrue(errors.isEmpty())
            assertTrue(cacheFile.exists())
            assertEquals("OggS", cacheFile.inputStream().use { input ->
                String(input.readNBytes(4), Charsets.US_ASCII)
            })
            assertEquals(ShadowMediaPlayer.State.STARTED, shadow?.state)
            clips.stop()
            assertEquals(ShadowMediaPlayer.State.END, shadow?.state)
            assertFalse(cacheFile.exists())
        } finally {
            clips.close()
            ShadowMediaPlayer.setCreateListener(null)
        }
    }

    @Test fun latestTapWinsWhileEngineInitializesAndUsesOnlyOfflineChinese() {
        val clips = Clips()
        val factory = EngineFactory()
        factory.engine.available = listOf(networkChinese, localChinese)
        val states = mutableListOf<SpeechStatus>()
        val speech = OfflineChineseSpeech(clips, factory, states::add, {})

        speech.speak("山")
        speech.speak("河")
        assertEquals(1, factory.creates)
        assertEquals(SpeechStatus.INITIALIZING, speech.status)

        factory.ready(true)
        assertEquals("local-zh", factory.engine.selected)
        assertEquals(listOf("河"), factory.engine.spoken.map { it.first })
        assertEquals(listOf(SpeechStatus.INITIALIZING, SpeechStatus.READY), states)
    }

    @Test fun networkOnlyChineseVoiceReportsParentSetupInsteadOfSpeaking() {
        val factory = EngineFactory()
        factory.engine.available = listOf(networkChinese)
        val messages = mutableListOf<String>()
        val speech = OfflineChineseSpeech(Clips(), factory, {}, messages::add)

        speech.speak("山")
        factory.ready(true)

        assertEquals(SpeechStatus.MISSING_VOICE, speech.status)
        assertTrue(factory.engine.closed)
        assertTrue(factory.engine.spoken.isEmpty())
        assertTrue(messages.single().contains("离线中文语音"))
    }

    @Test fun stopDropsPendingTapAndCloseIgnoresLateInitialization() {
        val clips = Clips()
        val factory = EngineFactory()
        factory.engine.available = listOf(localChinese)
        val states = mutableListOf<SpeechStatus>()
        val speech = OfflineChineseSpeech(clips, factory, states::add, {})

        speech.speak("山")
        speech.stop()
        factory.ready(true)
        assertTrue(factory.engine.spoken.isEmpty())

        speech.close()
        factory.ready(true)
        speech.speak("河")
        assertEquals(SpeechStatus.CLOSED, speech.status)
        assertTrue(factory.engine.closed)
        assertTrue(clips.closed)
        assertFalse(states.isEmpty())
    }

    @Test fun staleUtteranceErrorDoesNotReplaceNewTapStatus() {
        val factory = EngineFactory()
        factory.engine.available = listOf(localChinese)
        val messages = mutableListOf<String>()
        val speech = OfflineChineseSpeech(Clips(), factory, {}, messages::add)
        speech.speak("山")
        factory.ready(true)
        val first = factory.engine.spoken.single().second

        speech.speak("河")
        factory.error(first)

        assertEquals(SpeechStatus.READY, speech.status)
        assertTrue(messages.isEmpty())
        assertEquals(listOf("山", "河"), factory.engine.spoken.map { it.first })
    }

    @Test fun repeatedTtsErrorsStopFurtherFallbackAttempts() {
        val factory = EngineFactory()
        factory.engine.available = listOf(localChinese)
        val speech = OfflineChineseSpeech(Clips(), factory, {}, {})
        speech.speak("山")
        factory.ready(true)
        factory.error(factory.engine.spoken.last().second)

        speech.speak("河")
        factory.error(factory.engine.spoken.last().second)
        speech.speak("海")

        assertEquals(listOf("山", "河"), factory.engine.spoken.map { it.first })
        assertEquals(SpeechStatus.ENGINE_ERROR, speech.status)
    }
}
