package yuku.alkitab.base.speech

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/** Android speech adapter pinned to Google Speech Services and offline voices. */
class GoogleTextToSpeechEngine(
    context: Context,
) : SpeechEngine {
    private val appContext = context.applicationContext
    private var listener: SpeechEngine.Listener? = null
    private var textToSpeech: TextToSpeech? = null
    private var lifecycle = Lifecycle.NEW
    private val prepareCallbacks = mutableListOf<(Boolean) -> Unit>()

    override fun setListener(listener: SpeechEngine.Listener) {
        this.listener = listener
    }

    override fun prepare(onResult: (Boolean) -> Unit) {
        when (lifecycle) {
            Lifecycle.READY -> onResult(true)
            Lifecycle.FAILED, Lifecycle.SHUTDOWN -> onResult(false)
            Lifecycle.INITIALIZING -> prepareCallbacks += onResult
            Lifecycle.NEW -> {
                if (!isGoogleEngineInstalled(appContext)) {
                    lifecycle = Lifecycle.FAILED
                    onResult(false)
                    return
                }
                prepareCallbacks += onResult
                lifecycle = Lifecycle.INITIALIZING
                val created = TextToSpeech(appContext, ::onInitialized, ENGINE_PACKAGE)
                textToSpeech = created
                if (lifecycle == Lifecycle.READY) attachProgressListener(created)
            }
        }
    }

    override fun isLanguageAvailable(languageTag: String): Boolean {
        val tts = readyEngine() ?: return false
        val locale = Locale.forLanguageTag(languageTag)
        return tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE &&
            selectOfflineVoice(tts, locale) != null
    }

    override fun setLanguage(languageTag: String): Boolean {
        val tts = readyEngine() ?: return false
        val voice = selectOfflineVoice(tts, Locale.forLanguageTag(languageTag)) ?: return false
        return tts.setVoice(voice) == TextToSpeech.SUCCESS
    }

    override fun speak(id: String, text: String): Boolean {
        val tts = readyEngine() ?: return false
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, id) == TextToSpeech.SUCCESS
    }

    override fun stop() {
        readyEngine()?.stop()
    }

    override fun shutdown() {
        if (lifecycle == Lifecycle.SHUTDOWN) return
        lifecycle = Lifecycle.SHUTDOWN
        prepareCallbacks.toList().also { prepareCallbacks.clear() }.forEach { it(false) }
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun onInitialized(status: Int) {
        if (lifecycle == Lifecycle.SHUTDOWN) return
        val ready = status == TextToSpeech.SUCCESS
        lifecycle = if (ready) Lifecycle.READY else Lifecycle.FAILED
        if (ready) textToSpeech?.let(::attachProgressListener)
        prepareCallbacks.toList().also { prepareCallbacks.clear() }.forEach { it(ready) }
    }

    private fun attachProgressListener(tts: TextToSpeech) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { listener?.onDone(it) }
            }

            @Deprecated("Deprecated by Android, retained for older platform callbacks")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { listener?.onError(it) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { listener?.onError(it) }
            }
        })
    }

    private fun readyEngine(): TextToSpeech? = textToSpeech.takeIf { lifecycle == Lifecycle.READY }

    private fun selectOfflineVoice(tts: TextToSpeech, requested: Locale): Voice? =
        tts.voices
            ?.asSequence()
            ?.filterNot { it.isNetworkConnectionRequired }
            ?.filter { it.locale.language.equals(requested.language, ignoreCase = true) }
            ?.filter {
                requested.country.isBlank() ||
                    it.locale.country.isBlank() ||
                    it.locale.country.equals(requested.country, ignoreCase = true)
            }
            ?.sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })
            ?.firstOrNull()

    private enum class Lifecycle { NEW, INITIALIZING, READY, FAILED, SHUTDOWN }

    companion object {
        const val ENGINE_PACKAGE = "com.google.android.tts"

        fun isGoogleEngineInstalled(context: Context): Boolean = try {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(ENGINE_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
