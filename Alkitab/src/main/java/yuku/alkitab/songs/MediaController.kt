package yuku.alkitab.songs

import android.app.Activity
import androidx.annotation.CallSuper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.ref.WeakReference
import yuku.alkitab.base.util.AppLog

private const val TAG = "MediaController"

abstract class MediaController {
    private var observableState by mutableStateOf(State.reset)

    var state: State
        get() = observableState
        set(value) {
            AppLog.d(TAG, "@@setState newState=$value")
            observableState = value
            updateMediaState()
        }

    protected var url by mutableStateOf<String?>(null)
    protected var activityRef: WeakReference<Activity>? = null

    private var mediaStateListenerRef: WeakReference<MediaStateListener>? = null

    fun setUI(activity: Activity, mediaStateListener: MediaStateListener) {
        activityRef = WeakReference(activity)
        mediaStateListenerRef = WeakReference(mediaStateListener)
    }

    fun updateMediaState() {
        mediaStateListenerRef?.get()?.onControllerStateChanged(state)
    }

    @CallSuper
    open fun reset() {
        state = State.reset
    }

    fun mediaKnownToExist(url: String) {
        state = State.reset_media_known_to_exist
        this.url = url
    }

    abstract fun playOrPause(playInLoop: Boolean)

    abstract fun getProgress(): LongArray

    fun canHaveNewUrl(): Boolean {
        return state == State.reset || state == State.reset_media_known_to_exist || state == State.error
    }

    enum class State {
        reset, reset_media_known_to_exist, preparing, playing, paused, complete, error
    }
}
