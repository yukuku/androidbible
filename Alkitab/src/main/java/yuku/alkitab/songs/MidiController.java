package yuku.alkitab.songs;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Handler;
import com.afollestad.materialdialogs.MaterialDialog;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import yuku.alkitab.base.App;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Background;
import yuku.alkitab.debug.R;

/**
 * We will use Exoplayer for non-MIDI files.
 * One of the reason is that Exoplayer allows us to use OkHttp as the downloader.
 */
public class MidiController extends MediaController {
	static final String TAG = MidiController.class.getSimpleName();

	final MediaPlayer mp = new MediaPlayer();

	@Override
	public void reset() {
		super.reset();
		mp.reset();
	}

	@Override
	public void playOrPause(final boolean playInLoop) {
		switch (state) {
			case reset:
				// play button should be disabled
				break;
			case reset_media_known_to_exist:
			case complete:
			case error: {
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
								mediaPlayerPrepare(cacheFile.getAbsolutePath(), playInLoop);
							} else {
								AppLog.d(TAG, "wrong state after downloading song file: " + state);
							}
						});
					} catch (IOException e) {
						AppLog.e(TAG, "buffering to local cache", e);
						setState(State.error);
					}
				});
			}
			break;
			case preparing:
				// this is preparing. Don't do anything.
				break;
			case playing:
				// pause button pressed
				if (playInLoop) {
					// looping play is selected, but we are already playing. So just set looping parameter.
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


	private void mediaPlayerPrepare(final String localFilename, final boolean playInLoop) {
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
					if (activity != null && !activity.isFinishing()) {
						new MaterialDialog.Builder(activity)
							.content(activity.getString(R.string.song_player_error_description, what, extra))
							.positiveText(R.string.ok)
							.show();
					}
				}

				setState(State.error);
				return false; // let OnCompletionListener be called.
			});

			final FileInputStream fis = new FileInputStream(localFilename);
			final FileDescriptor fd = fis.getFD();
			mp.reset();
			mp.setDataSource(fd);
			fis.close();
			mp.prepare();
		} catch (IOException e) {
			AppLog.e(TAG, "mp setDataSource", e);
			setState(State.error);
		}
	}

	/**
	 * @return current position and duration in ms. Any of them can be -1 if unknown.
	 */
	@Override
	public int[] getProgress() {
		switch (state) {
			case playing:
			case paused:
			case complete: {
				int position = -1;
				try {
					position = mp.getCurrentPosition();
				} catch (Exception e) {
					AppLog.e(TAG, "@@getProgress getCurrentPosition", e);
				}

				int duration = -1;
				try {
					duration = mp.getDuration();
				} catch (Exception e) {
					AppLog.e(TAG, "@@getProgress getCurrentPosition", e);
				}

				return new int[]{position, duration};
			}
		}

		return new int[]{-1, -1};
	}
}
