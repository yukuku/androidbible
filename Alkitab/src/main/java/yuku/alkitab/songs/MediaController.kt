package yuku.alkitab.songs

import android.app.Activity
import androidx.annotation.CallSuper
import yuku.alkitab.base.util.AppLog
import java.lang.ref.WeakReference

private const val TAG = "MediaController"

abstract class MediaController {
    var state = State.reset
        set(value) {
            AppLog.d(TAG, "@@setState newState=$value")
            field = value
            updateMediaState()
        }

    protected var url: String? = null
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

    abstract fun getProgress(): IntArray

    fun canHaveNewUrl(): Boolean {
        return state == State.reset || state == State.reset_media_known_to_exist || state == State.error
    }

    enum class State {
        reset, reset_media_known_to_exist, preparing, playing, paused, complete, error
    }
}
