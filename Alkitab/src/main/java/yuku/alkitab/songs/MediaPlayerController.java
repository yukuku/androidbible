package yuku.alkitab.songs;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Handler;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.alkitab.base.App;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Background;
import yuku.alkitab.debug.R;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

public class MediaPlayerController {
	static final String TAG = MediaPlayerController.class.getSimpleName();

	MediaPlayer mp = new MediaPlayer();

	public enum State {
		reset,
		reset_media_known_to_exist,
		preparing,
		playing,
		paused,
		complete,
		error,
	}

	State state = State.reset;

	// if this is a midi file, we need to manually download to local first
	boolean isMidiFile;

	String url;
	WeakReference<Activity> activityRef;
	WeakReference<MediaStateListener> mediaStateListenerRef;

	public void setUI(Activity activity, MediaStateListener mediaStateListener) {
		activityRef = new WeakReference<>(activity);
		mediaStateListenerRef = new WeakReference<>(mediaStateListener);
	}

	private void setState(final State newState) {
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

	public void reset() {
		setState(State.reset);
		mp.reset();
	}

	public void mediaKnownToExist(String url, boolean isMidiFile) {
		setState(State.reset_media_known_to_exist);
		this.url = url;
		this.isMidiFile = isMidiFile;
	}

	public void playOrPause(final boolean playInLoop) {
		switch (state) {
			case reset:
				// play button should be disabled
				break;
			case reset_media_known_to_exist:
			case complete:
			case error: {
				if (isMidiFile) {
					final Handler handler = new Handler();

					Background.run(() -> {
						try {
							setState(State.preparing);

							final byte[] bytes = App.downloadBytes(url);

							final File cacheFile = new File(App.context./**/getCacheDir(), "song_player_local_cache.mid");
							final OutputStream output = new FileOutputStream(cacheFile);
							output.write(bytes);
							output.close();

							// this is a background thread. We must go back to main thread, and check again if state is OK to prepare.
							handler.post(() -> {
								if (state == State.preparing) {
									// the following should be synchronous, since we are loading from local.
									mediaPlayerPrepare(true, cacheFile.getAbsolutePath(), playInLoop);
								} else {
									AppLog.d(TAG, "wrong state after downloading song file: " + state);
								}
							});
						} catch (IOException e) {
							AppLog.e(TAG, "buffering to local cache", e);
							setState(State.error);
						}
					});
				} else {
					mediaPlayerPrepare(false, url, playInLoop);
				}
			}
			break;
			case preparing:
				// this is preparing. Don't do anything.
				break;
			case playing:
				// pause button pressed
				if (playInLoop) {
					// looping play is selected but we are already playing. So just set looping parameter.
					mp.setLooping(true);
				} else {
					mp.pause();
					setState(State.paused);
				}
				break;
			case paused:
				// play button pressed when paused
				mp.setLooping(playInLoop);
				mp.start();
				setState(State.playing);
				break;
		}
	}

	/**
	 * @param url local path if isLocalPath is true, url (http/https) if isLocalPath is false
	 */
	private void mediaPlayerPrepare(boolean isLocalPath, final String url, final boolean playInLoop) {
		try {
			setState(State.preparing);

			mp.setOnPreparedListener(player -> {
				// only start playing if the current state is preparing, i.e., not error or reset.
				if (state == State.preparing) {
					player.setLooping(playInLoop);
					player.start();
					setState(State.playing);
				}
			});
			mp.setOnCompletionListener(player -> {
				AppLog.d(TAG, "@@onCompletion looping=" + player.isLooping());
				if (!player.isLooping()) {
					player.reset();
					setState(State.complete);
				}
			});
			mp.setOnErrorListener((mp1, what, extra) -> {
				AppLog.e(TAG, "@@onError controller_state=" + state + " what=" + what + " extra=" + extra);

				if (state != State.reset) { // Errors can happen if we call MediaPlayer#reset when MediaPlayer state is Preparing. In this case, do not show error message.
					final Activity activity = activityRef.get();
					if (activity != null) {
						if (!activity.isFinishing()) {
							new MaterialDialog.Builder(activity)
								.content(activity.getString(R.string.song_player_error_description, what, extra))
								.positiveText(R.string.ok)
								.show();
						}
					}
				}

				setState(State.error);
				return false; // let OnCompletionListener be called.
			});

			if (isLocalPath) {
				final FileInputStream fis = new FileInputStream(url);
				final FileDescriptor fd = fis.getFD();
				mp.setDataSource(fd);
				fis.close();
				mp.prepare();
			} else {
				mp.setDataSource(url);
				mp.prepareAsync();
			}
		} catch (IOException e) {
			AppLog.e(TAG, "mp setDataSource", e);
			setState(State.error);
		}
	}

	public boolean canHaveNewUrl() {
		return state == State.reset || state == State.reset_media_known_to_exist || state == State.error;
	}
}
