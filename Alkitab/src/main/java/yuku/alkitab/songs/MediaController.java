package yuku.alkitab.songs;

import android.app.Activity;
import androidx.annotation.CallSuper;
import java.lang.ref.WeakReference;
import yuku.alkitab.base.util.AppLog;

public abstract class MediaController {
	static final String TAG = MediaController.class.getSimpleName();

	State state = State.reset;
	String url;
	WeakReference<Activity> activityRef;
	WeakReference<MediaStateListener> mediaStateListenerRef;

	public void setUI(Activity activity, MediaStateListener mediaStateListener) {
		activityRef = new WeakReference<>(activity);
		mediaStateListenerRef = new WeakReference<>(mediaStateListener);
	}

	public void setState(final State newState) {
		AppLog.d(TAG, "@@setState newState=" + newState);
		state = newState;
		updateMediaState();
	}

	public void updateMediaState() {
		if (mediaStateListenerRef == null) return;

		final MediaStateListener mediaStateListener = mediaStateListenerRef.get();
		if (mediaStateListener == null) return;

		mediaStateListener.onControllerStateChanged(state);
	}

	@CallSuper
	public void reset() {
		setState(State.reset);
	}

	public void mediaKnownToExist(String url) {
		setState(State.reset_media_known_to_exist);
		this.url = url;
	}

	public abstract void playOrPause(final boolean playInLoop);

	public abstract int[] getProgress();

	public boolean canHaveNewUrl() {
		return state == State.reset || state == State.reset_media_known_to_exist || state == State.error;
	}

	public enum State {
		reset,
		reset_media_known_to_exist,
		preparing,
		playing,
		paused,
		complete,
		error,
	}
}
