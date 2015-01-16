package yuku.alkitab.base.ac;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ShareCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity;
import yuku.alkitab.base.dialog.VersesDialog;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.AlphanumComparator;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.base.util.OsisBookNames;
import yuku.alkitab.base.util.SongBookUtil;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.base.widget.LeftDrawer;
import yuku.alkitab.base.widget.TwofingerLinearLayout;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.SongInfo;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.display.Launcher;
import yuku.kpri.model.Lyric;
import yuku.kpri.model.Song;
import yuku.kpri.model.Verse;
import yuku.kpri.model.VerseKind;
import yuku.kpriviewer.fr.SongFragment;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SongViewActivity extends BaseLeftDrawerActivity implements SongFragment.ShouldOverrideUrlLoadingHandler, LeftDrawer.Songs.Listener, MediaStateListener {
	public static final String TAG = SongViewActivity.class.getSimpleName();

	private static final String BIBLE_PROTOCOL = "bible";
	private static final int REQCODE_songList = 1;
	private static final int REQCODE_share = 2;

	DrawerLayout drawerLayout;
	ActionBarDrawerToggle drawerToggle;
	LeftDrawer.Songs leftDrawer;

	TwofingerLinearLayout song_container;
	ViewGroup no_song_data_container;
	View bDownload;
	View circular_progress;

	ActionBar actionBar;

	Bundle templateCustomVars;
	String currentBookName;
	Song currentSong;

	// for initially populating the search song activity
	SongListActivity.SearchState last_searchState = null;

	// state for keypad
	String state_originalCode;
	String state_tempCode;

	// cache of song codes for each book
	Map<String /* bookName */, List<String> /* ordered codes */> cache_codes = new HashMap<>();

	TwofingerLinearLayout.Listener song_container_listener = new TwofingerLinearLayout.OnefingerListener() {
		@Override
		public void onOnefingerLeft() {
			goTo(+1);
		}

		@Override
		public void onOnefingerRight() {
			goTo(-1);
		}
	};

	@Override
	protected LeftDrawer getLeftDrawer() {
		return leftDrawer;
	}

	static class MediaState {
		boolean enabled;
		int icon;
		int label;
		boolean loading;
	}

	final MediaState mediaState = new MediaState();

	/** This method might be called from non-UI thread. Be careful when manipulating UI. */
	@Override
	public void setMediaState(final MediaPlayerController.ControllerState state) {
		if (state == MediaPlayerController.ControllerState.reset) {
			mediaState.enabled = false;
			mediaState.icon = R.drawable.ic_action_hollowplay;
			mediaState.label = R.string.menuPlay;
		} else if (state == MediaPlayerController.ControllerState.reset_media_known_to_exist || state == MediaPlayerController.ControllerState.paused || state == MediaPlayerController.ControllerState.complete) {
			mediaState.enabled = true;
			mediaState.icon = R.drawable.ic_action_play;
			mediaState.label = R.string.menuPlay;
		} else if (state == MediaPlayerController.ControllerState.playing) {
			mediaState.enabled = true;
			mediaState.icon = R.drawable.ic_action_pause;
			mediaState.label = R.string.menuPause;
		}

		mediaState.loading = (state == MediaPlayerController.ControllerState.preparing);

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				invalidateOptionsMenu();
			}
		});
	}

	void goTo(final int dir) {
		if (currentBookName == null) return;
		if (currentSong == null) return;

		List<String> codes = cache_codes.get(currentBookName);
		if (codes == null) {
			final List<SongInfo> songInfos = S.getSongDb().listSongInfosByBookName(currentBookName);
			codes = new ArrayList<>();
			for (SongInfo songInfo : songInfos) {
				codes.add(songInfo.code);
			}
			// sort codes based on numeric
			Collections.sort(codes, new AlphanumComparator());
			cache_codes.put(currentBookName, codes);
		}

		// find index of current song
		final int pos = codes.indexOf(currentSong.code);
		if (pos == -1) {
			return; // should not happen
		}

		final int newPos = pos + dir;
		if (newPos < 0 || newPos >= codes.size()) {
			return; // can't go left or right
		}

		final String newCode = codes.get(newPos);
		final Song newSong = S.getSongDb().getSong(currentBookName, newCode);
		if (newSong == null) {
			return; // should not happen
		}

		displaySong(currentBookName, newSong);
	}

	static class MediaPlayerController {
		MediaPlayer mp = new MediaPlayer();

		enum ControllerState {
			reset,
			reset_media_known_to_exist,
			preparing,
			playing,
			paused,
			complete,
			error,
		}

		ControllerState state = ControllerState.reset;

		// if this is a midi file, we need to manually download to local first
		boolean isMidiFile;

		String url;
		WeakReference<Activity> activityRef;
		WeakReference<MediaStateListener> mediaStateListenerRef;

		void setUI(Activity activity, MediaStateListener mediaStateListener) {
			activityRef = new WeakReference<>(activity);
			mediaStateListenerRef = new WeakReference<>(mediaStateListener);
		}

		private void setState(final ControllerState newState) {
			Log.d(TAG, "@@setState newState=" + newState);
			state = newState;
			updateMediaState();
		}

		void updateMediaState() {
			if (mediaStateListenerRef == null) return;

			final MediaStateListener mediaStateListener = mediaStateListenerRef.get();
			if (mediaStateListener == null) return;

			mediaStateListener.setMediaState(state);
		}

		void reset() {
			setState(ControllerState.reset);
			mp.reset();
		}

		void mediaKnownToExist(String url, boolean isMidiFile) {
			setState(ControllerState.reset_media_known_to_exist);
			this.url = url;
			this.isMidiFile = isMidiFile;
		}

		void playOrPause() {
			if (state == ControllerState.reset) {
				// play button should be disabled
			} else if (state == ControllerState.reset_media_known_to_exist || state == ControllerState.complete || state == ControllerState.error) {
				if (isMidiFile) {
					final Handler handler = new Handler();

					new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								setState(ControllerState.preparing);

								final byte[] bytes = App.downloadBytes(url);

								final File cacheFile = new File(App.context.getCacheDir(), "song_player_local_cache.mid");
								final OutputStream output = new FileOutputStream(cacheFile);
								output.write(bytes);
								output.close();

								// this is a background thread. We must go back to main thread, and check again if state is OK to prepare.
								handler.post(new Runnable() {
									@Override
									public void run() {
										if (state == ControllerState.preparing) {
											// the following should be synchronous, since we are loading from local.
											mediaPlayerPrepare(true, cacheFile.getAbsolutePath());
										} else {
											Log.d(TAG, "wrong state after downloading song file: " + state);
										}
									}
								});
							} catch (IOException e) {
								Log.e(TAG, "buffering to local cache", e);
								setState(ControllerState.error);
							}
						}
					}).start();
				} else {
					mediaPlayerPrepare(false, url);
				}
			} else if (state == ControllerState.preparing) {
				// this is preparing. Don't do anything.
			} else if (state == ControllerState.playing) {
				// pause button pressed
				mp.pause();
				setState(ControllerState.paused);
			} else if (state == ControllerState.paused) {
				// play button pressed when paused
				mp.start();
				setState(ControllerState.playing);
			}
		}

		/**
		 * @param url local path if isLocalPath is true, url (http/https) if isLocalPath is false
		 */
		private void mediaPlayerPrepare(boolean isLocalPath, final String url) {
			try {
				setState(ControllerState.preparing);

				mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
					@Override
					public void onPrepared(final MediaPlayer mp) {
						// only start playing if the current state is preparing, i.e., not error or reset.
						if (state == ControllerState.preparing) {
							mp.start();
							setState(ControllerState.playing);
						}
					}
				});
				mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
					@Override
					public void onCompletion(final MediaPlayer mp) {
						mp.reset();
						setState(ControllerState.complete);
					}
				});
				mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
					@Override
					public boolean onError(final MediaPlayer mp, final int what, final int extra) {
						Log.e(TAG, "@@onError controller_state=" + state + " what=" + what + " extra=" + extra);

						if (state != ControllerState.reset) { // Errors can happen if we call MediaPlayer#reset when MediaPlayer state is Preparing. In this case, do not show error message.
							final Activity activity = activityRef.get();
							if (activity != null) {
								if (!activity.isFinishing()) {
									new AlertDialog.Builder(activity)
										.setMessage(activity.getString(R.string.song_player_error_description, what, extra))
										.setPositiveButton(R.string.ok, null)
										.show();
								}
							}
						}

						setState(ControllerState.error);
						return false; // let OnCompletionListener be called.
					}
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
				Log.e(TAG, "mp setDataSource", e);
				setState(ControllerState.error);
			}
		}

		boolean canHaveNewUrl() {
			return state == ControllerState.reset || state == ControllerState.reset_media_known_to_exist || state == ControllerState.error;
		}
	}

	// this have to be static to prevent double media player
	static MediaPlayerController mediaPlayerController = new MediaPlayerController();

	public static Intent createIntent() {
		return new Intent(App.context, SongViewActivity.class);
	}

	@Override protected void onCreate(Bundle savedInstanceState) {
		supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_song_view);

		circular_progress = V.get(this, R.id.progress_circular);

		setSupportProgressBarIndeterminate(true);
		setCustomProgressBarIndeterminateVisible(false);

		setTitle(R.string.sn_songs_activity_title);

		drawerLayout = V.get(this, R.id.drawerLayout);
		leftDrawer = V.get(this, R.id.left_drawer);
		leftDrawer.configure(this, drawerLayout);

		final Toolbar toolbar = V.get(this, R.id.toolbar);
		setSupportActionBar(toolbar);

		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close) {
			@Override
			public void onDrawerOpened(final View drawerView) {
				drawer_opened();
			}
		};
		drawerLayout.setDrawerListener(drawerToggle);

		song_container = V.get(this, R.id.song_container);
		no_song_data_container = V.get(this, R.id.no_song_data_container);
		bDownload = V.get(this, R.id.bDownload);

		song_container.setTwofingerEnabled(false);
		song_container.setListener(song_container_listener);

		actionBar = getSupportActionBar();
		actionBar.setDisplayShowHomeEnabled(Build.VERSION.SDK_INT < 18);
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);

		bDownload.setOnClickListener(v -> SongBookUtil.getSongBookDialog(this, SongBookUtil.getSongBookOnDialogClickListener(this::songBookSelected)).show());
	}

	@Override
	protected void onPostCreate(final Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(final Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		drawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	protected void onStart() {
		super.onStart();

		// for colors of bg, text, etc
		V.get(this, android.R.id.content).setBackgroundColor(S.applied.backgroundColor);

		templateCustomVars = new Bundle();
		templateCustomVars.putString("background_color", String.format("#%06x", S.applied.backgroundColor & 0xffffff));
		templateCustomVars.putString("text_color", String.format("#%06x", S.applied.fontColor & 0xffffff));
		templateCustomVars.putString("verse_number_color", String.format("#%06x", S.applied.verseNumberColor & 0xffffff));
		templateCustomVars.putString("text_size", S.applied.fontSize2dp + "px");
		templateCustomVars.putString("line_spacing_mult", String.valueOf(S.applied.lineSpacingMult));

		{
			String fontName = Preferences.getString(Prefkey.jenisHuruf, null);
			if (FontManager.isCustomFont(fontName)) {
				templateCustomVars.putString("custom_font_loader", String.format("@font-face{ font-family: '%s'; src: url('%s'); }", fontName, FontManager.getCustomFontUri(fontName)));
			} else {
				templateCustomVars.putString("custom_font_loader", "");
			}
			templateCustomVars.putString("text_font", fontName);
		}

		{ // show latest viewed song
			String bookName = Preferences.getString(Prefkey.song_last_bookName, null);
			String code = Preferences.getString(Prefkey.song_last_code, null);

			if (bookName == null || code == null) {
				displaySong(null, null, true);
			} else {
				displaySong(bookName, S.getSongDb().getSong(bookName, code), true);
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		mediaPlayerController.setUI(this, this);
		mediaPlayerController.updateMediaState();
	}

	static String getAudioFilename(String bookName, String code) {
		return String.format("songs/v2/%s_%s", bookName, code);
	}

	void checkAudioExistance() {
		if (currentBookName == null || currentSong == null) return;

		final String checkedBookName = currentBookName;
		final String checkedCode = currentSong.code;

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final String filename = getAudioFilename(checkedBookName, checkedCode);
					final String response = App.downloadString("https://alkitab-host.appspot.com/addon/audio/exists?filename=" + Uri.encode(filename));
					if (response.startsWith("OK")) {
						// make sure this is the correct one due to possible race condition
						if (U.equals(currentBookName, checkedBookName) && currentSong != null && U.equals(currentSong.code, checkedCode)) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (mediaPlayerController.canHaveNewUrl()) {
										final String baseUrl;
										if (Build.VERSION.SDK_INT >= 14) {
											baseUrl = "https://alkitab-host.appspot.com/addon/audio/";
										} else {
											baseUrl = "http://alkitab-host.appspot.com/addon/audio/"; // no streaming https support in old Android
										}
										final String url = baseUrl + getAudioFilename(currentBookName, currentSong.code);
										if (response.contains("extension=mp3")) {
											mediaPlayerController.mediaKnownToExist(url, false);
										} else {
											mediaPlayerController.mediaKnownToExist(url, true);
										}
									} else {
										Log.d(TAG, "mediaPlayerController can't have new URL at this moment.");
									}
								}
							});
						}
					} else {
						Log.d(TAG, "@@checkAudioExistance response: " + response);
					}
				} catch (IOException e) {
					Log.e(TAG, "@@checkAudioExistance", e);
				}
			}
		}).start();
	}

	@Override public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_song_view, menu);
		return true;
	}
	
	@Override public boolean onPrepareOptionsMenu(Menu menu) {
		final MenuItem menuMediaControl = menu.findItem(R.id.menuMediaControl);
		menuMediaControl.setEnabled(mediaState.enabled);
		if (mediaState.icon != 0) menuMediaControl.setIcon(mediaState.icon);
		if (mediaState.label != 0) menuMediaControl.setTitle(mediaState.label);
		if (mediaState.loading) {
			setCustomProgressBarIndeterminateVisible(true);
			menuMediaControl.setVisible(false);
		} else {
			setCustomProgressBarIndeterminateVisible(false);
			menuMediaControl.setVisible(true);
		}

		final boolean songShown = currentBookName != null;

		final MenuItem menuCopy = menu.findItem(R.id.menuCopy);
		menuCopy.setVisible(songShown);
		final MenuItem menuShare = menu.findItem(R.id.menuShare);
		menuShare.setVisible(songShown);
		final MenuItem menuUpdateBook = menu.findItem(R.id.menuUpdateBook);
		menuUpdateBook.setVisible(songShown);
		final MenuItem menuDeleteAll = menu.findItem(R.id.menuDeleteAll);
		menuDeleteAll.setVisible(songShown);

		return true;
	}
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		if (drawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		switch (item.getItemId()) {
		case R.id.menuCopy: {
			if (currentSong != null) {
				U.copyToClipboard(convertSongToText(currentSong));

				Toast.makeText(this, R.string.sn_copied, Toast.LENGTH_SHORT).show();
			}
		} return true;

		case R.id.menuShare: {
			if (currentSong != null) {
				Intent intent = ShareCompat.IntentBuilder.from(SongViewActivity.this)
				.setType("text/plain")
				.setSubject(currentBookName + ' ' + currentSong.code + ' ' + currentSong.title)
				.setText(convertSongToText(currentSong).toString())
				.getIntent();
				startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.sn_share_title)), REQCODE_share);
			}
		} return true;

		case R.id.menuSearch: {
			startActivityForResult(SongListActivity.createIntent(last_searchState), REQCODE_songList);
		} return true;

		case R.id.menuMediaControl: {
			if (currentBookName != null && currentSong != null) {
				mediaPlayerController.playOrPause();
			}
		} return true;

        case R.id.menuUpdateBook: {
			new AlertDialog.Builder(this)
				.setMessage(TextUtils.expandTemplate(getString(R.string.sn_update_book_explanation), currentBookName))
				.setPositiveButton(R.string.ok, (dialog, which) -> updateSongBook())
				.setNegativeButton(R.string.cancel, null)
				.show();
		} return true;

		case R.id.menuDeleteAll: {
			new AlertDialog.Builder(this)
				.setMessage(R.string.sn_delete_all_songs_explanation)
				.setPositiveButton(R.string.ok, (dialog, which) -> deleteAllSongs())
				.setNegativeButton(R.string.cancel, null)
				.show();
		} return true;
		}
		
		return super.onOptionsItemSelected(item);
	}

    protected void updateSongBook() {
		final SongBookUtil.SongBookInfo songBookInfo = SongBookUtil.getSongBookInfo(currentBookName);
		if (songBookInfo == null) {
			throw new RuntimeException("SongBookInfo named " + currentBookName + " was not found");
		}

		final String currentSongCode = currentSong.code;

		SongBookUtil.downloadSongBook(SongViewActivity.this, songBookInfo, new SongBookUtil.OnDownloadSongBookListener() {
			@Override
			public void onFailedOrCancelled(SongBookUtil.SongBookInfo songBookInfo, Exception e) {
				if (e != null) {
					new AlertDialog.Builder(SongViewActivity.this)
						.setMessage(e.getClass().getSimpleName() + ' ' + e.getMessage())
						.setPositiveButton(R.string.ok, null)
						.show();
				}
			}

			@Override
			public void onDownloadedAndInserted(SongBookUtil.SongBookInfo songBookInfo) {
				final Song song = S.getSongDb().getSong(songBookInfo.bookName, currentSongCode);
				cache_codes.remove(songBookInfo.bookName);
				displaySong(songBookInfo.bookName, song);
			}
		});
	}

    protected void deleteAllSongs() {
        final ProgressDialog pd = ProgressDialog.show(this, null, getString(R.string.please_wait_titik3), true, false);

        new Thread() {
            @Override public void run() {
                final int count = S.getSongDb().deleteAllSongs();

                runOnUiThread(() -> {
                    pd.dismiss();

                    finish(); // TODO

                    new AlertDialog.Builder(SongViewActivity.this)
                            .setMessage(getString(R.string.sn_delete_all_songs_result, count))
                            .setPositiveButton(R.string.ok, null)
                            .show();
                });
            }
        }.start();
    }

	private StringBuilder convertSongToText(Song song) {
		// build text to copy
		StringBuilder sb = new StringBuilder();
		sb.append(song.code).append(". ");
		sb.append(song.title).append('\n');
		if (song.title_original != null) sb.append('(').append(song.title_original).append(')').append('\n');
		sb.append('\n');
		
		if (song.authors_lyric != null && song.authors_lyric.size() > 0) sb.append(TextUtils.join("; ", song.authors_lyric)).append('\n');
		if (song.authors_music != null && song.authors_music.size() > 0) sb.append(TextUtils.join("; ", song.authors_music)).append('\n');
		if (song.tune != null) sb.append(song.tune.toUpperCase(Locale.getDefault())).append('\n');
		sb.append('\n');
		
		if (song.scriptureReferences != null) sb.append(renderScriptureReferences(null, song.scriptureReferences)).append('\n');

		if (song.keySignature != null) sb.append(song.keySignature).append('\n');
		if (song.timeSignature != null) sb.append(song.timeSignature).append('\n');
		sb.append('\n');

		for (int i = 0; i < song.lyrics.size(); i++) {
			Lyric lyric = song.lyrics.get(i);
			
			if (song.lyrics.size() > 1 || lyric.caption != null) { // otherwise, only lyric and has no name
				if (lyric.caption != null) {
					sb.append(lyric.caption).append('\n');
				} else {
					sb.append(getString(R.string.sn_lyric_version_version, i+1)).append('\n');
				}
			}
			
			int verse_normal_no = 0;
			for (Verse verse: lyric.verses) {
				if (verse.kind == VerseKind.NORMAL) {
					verse_normal_no++;
				}
				
				boolean skipPad = false;
				if (verse.kind == VerseKind.REFRAIN) {
					sb.append(getString(R.string.sn_lyric_refrain_marker)).append('\n');
				} else {
					sb.append(String.format("%2d: ", verse_normal_no));
					skipPad = true;
				}

				for (String line: verse.lines) {
					if (!skipPad) {
						sb.append("    ");
					} else {
						skipPad = false;
					}
					sb.append(line).append("\n"); 
				}
				sb.append('\n');
			}
			sb.append('\n');
		}
		return sb;
	}
	
	/**
	 * Convert scripture ref lines like
	 * B1.C1.V1-B2.C2.V2; B3.C3.V3 to:
	 * <a href="protocol:B1.C1.V1-B2.C2.V2">Book 1 c1:v1-v2</a>; <a href="protocol:B3.C3.V3>Book 3 c3:v3</a>
	 * @param protocol null to output text
	 * @param line scripture ref in osis
	 */
	String renderScriptureReferences(String protocol, String line) {
		if (line == null || line.trim().length() == 0) return "";
		
		StringBuilder sb = new StringBuilder();

		String[] ranges = line.split("\\s*;\\s*");
		for (String range: ranges) {
			String[] osisIds;
			if (range.indexOf('-') >= 0) {
				osisIds = range.split("\\s*-\\s*");
			} else {
				osisIds = new String[] {range};
			}
			
			if (osisIds.length == 1) {
				if (sb.length() != 0) {
					sb.append("; ");
				}
				
				String osisId = osisIds[0];
				String readable = osisIdToReadable(line, osisId, null, null);
				if (readable != null) {
					appendScriptureReferenceLink(sb, protocol, osisId, readable);
				}
			} else if (osisIds.length == 2) {
				if (sb.length() != 0) {
					sb.append("; ");
				}

				int[] bcv = {-1, 0, 0};
				
				String osisId0 = osisIds[0];
				String readable0 = osisIdToReadable(line, osisId0, null, bcv);
				String osisId1 = osisIds[1];
				String readable1 = osisIdToReadable(line, osisId1, bcv, null);
				if (readable0 != null && readable1 != null) {
					appendScriptureReferenceLink(sb, protocol, osisId0 + '-' + osisId1, readable0 + '-' + readable1);
				}
			}
		}
		
		return sb.toString();
	}

	private void appendScriptureReferenceLink(StringBuilder sb, String protocol, String osisId, String readable) {
		if (protocol != null) {
			sb.append("<a href='");
			sb.append(protocol);
			sb.append(':');
			sb.append(osisId);
			sb.append("'>");
		}
		sb.append(readable);
		if (protocol != null) {
			sb.append("</a>");
		}
	}

	/**
	 * @param compareWithRangeStart if this is the second part of a range, set this to non-null, with [0] is bookId and [1] chapter_1.
	 * @param outBcv if not null and length is >= 3, will be filled with parsed bcv
	 */
	private String osisIdToReadable(String line, String osisId, int[] compareWithRangeStart, int[] outBcv) {
		String res = null;
		
		String[] parts = osisId.split("\\.");
		if (parts.length != 2 && parts.length != 3) {
			Log.w(TAG, "osisId invalid: " + osisId + " in " + line);
		} else {
			String bookName = parts[0];
			int chapter_1 = Integer.parseInt(parts[1]);
			int verse_1 = parts.length < 3? 0: Integer.parseInt(parts[2]);
			
			int bookId = OsisBookNames.osisBookNameToBookId(bookName);
			
			if (outBcv != null && outBcv.length >= 3) {
				outBcv[0] = bookId;
				outBcv[1] = chapter_1;
				outBcv[2] = verse_1;
			}
			
			if (bookId < 0) {
				Log.w(TAG, "osisBookName invalid: " + bookName + " in " + line);
			} else {
				Book book = S.activeVersion.getBook(bookId);
				
				if (book != null) {
					boolean full = true;
					if (compareWithRangeStart != null) {
						if (compareWithRangeStart[0] == bookId) {
							if (compareWithRangeStart[1] == chapter_1) {
								res = String.valueOf(verse_1);
								full = false;
							} else {
								res = String.valueOf(chapter_1) + ':' + String.valueOf(verse_1);
								full = false;
							}
						}
					}
					
					if (full) {
						res = verse_1 == 0? book.reference(chapter_1): book.reference(chapter_1, verse_1);
					}
				}
			}
		}
		return res;
	}

	void displaySong(String bookName, Song song) {
		displaySong(bookName, song, false);
	}

	void displaySong(String bookName, Song song, boolean onCreate) {
		song_container.setVisibility(song != null? View.VISIBLE: View.GONE);
		no_song_data_container.setVisibility(song != null? View.GONE: View.VISIBLE);

		if (!onCreate) {
			mediaPlayerController.reset();
		}

		if (song == null) return;

		final LeftDrawer.Songs.Handle handle = leftDrawer.getHandle();
		handle.setBookName(bookName);
		handle.setCode(song.code);

		setTitle(bookName + ' ' + song.code);

		// construct rendition of scripture references
		String scripture_references = renderScriptureReferences(BIBLE_PROTOCOL, song.scriptureReferences);
		templateCustomVars.putString("scripture_references", scripture_references);
		templateCustomVars.putString("copyright", SongBookUtil.getCopyright(bookName));

		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.replace(R.id.song_container, SongFragment.create(song, "templates/song.html", templateCustomVars));
		ft.commitAllowingStateLoss();

		currentBookName = bookName;
		currentSong = song;

		{ // save latest viewed song
			Preferences.setString(Prefkey.song_last_bookName, bookName);
			Preferences.setString(Prefkey.song_last_code, song.code);
		}

		checkAudioExistance();
	}

	void drawer_opened() {
		if (currentBookName == null) return;

		final LeftDrawer.Songs.Handle handle = leftDrawer.getHandle();
		handle.setOkButtonEnabled(false);
		handle.setAButtonEnabled(false);
		handle.setBButtonEnabled(false);
		handle.setCButtonEnabled(false);

		final String originalCode = currentSong == null ? "––––" : currentSong.code;
		handle.setCode(originalCode);

		state_originalCode = originalCode;
		state_tempCode = "";
	}

	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_songList) {
			if (resultCode == RESULT_OK) {
				SongListActivity.Result result = SongListActivity.obtainResult(data);
				if (result != null) {
					displaySong(result.bookName, S.getSongDb().getSong(result.bookName, result.code));
					// store this for next search
					last_searchState = result.last_searchState;
				}
			}
		} else if (requestCode == REQCODE_share) {
			if (resultCode == RESULT_OK) {
				ShareActivity.Result result = ShareActivity.obtainResult(data);
				if (result != null && result.chosenIntent != null) {
					startActivity(result.chosenIntent);
				}
			}
		}
	}

	static class PatchTextExtraInfoJson {
		String type;
		String bookName;
		String code;
	}

	static String nonullbr(String s) {
		if (s == null) return "";
		return "<br/>" + s;
	}

	static String nonullbr(List<String> s) {
		if (s == null || s.size() == 0) return "";
		return "<br/>" + s.toString();
	}

	@Override public boolean shouldOverrideUrlLoading(WebViewClient client, WebView view, String url) {
		Uri uri = Uri.parse(url);
		final String scheme = uri.getScheme();
		if ("patchtext".equals(scheme)) {
			final Song song = currentSong;

			final PatchTextExtraInfoJson extraInfo = new PatchTextExtraInfoJson();
			extraInfo.type = "song";
			extraInfo.bookName = currentBookName;
			extraInfo.code = song.code;

			final String songHeader = song.code + " " + song.title + nonullbr(song.title_original) + nonullbr(song.tune) + nonullbr(song.keySignature) + nonullbr(song.timeSignature) + nonullbr(song.authors_lyric) + nonullbr(song.authors_music);
			final String songHtml = SongFragment.songToHtml(song, true);
			final Spanned baseBody = Html.fromHtml(songHeader + "\n\n" + songHtml);
			startActivity(PatchTextActivity.createIntent(baseBody, App.getDefaultGson().toJson(extraInfo), null));
			return true;
		} else if (BIBLE_PROTOCOL.equals(scheme)) {
			final IntArrayList ariRanges = TargetDecoder.decode("o:" + uri.getSchemeSpecificPart());
			final VersesDialog versesDialog = VersesDialog.newInstance(ariRanges);
			versesDialog.setListener(new VersesDialog.VersesDialogListener() {
				@Override
				public void onVerseSelected(final VersesDialog dialog, final int ari) {
					startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(ari));
				}
			});
			versesDialog.show(getSupportFragmentManager(), VersesDialog.class.getSimpleName());
			return true;
		}
		return false;
	}

	@Override
	public void songKeypadButton_click(final View v) {
		if (currentBookName == null) return;

		final int[] numIds = {
			R.id.bDigit0, R.id.bDigit1, R.id.bDigit2, R.id.bDigit3, R.id.bDigit4,
			R.id.bDigit5, R.id.bDigit6, R.id.bDigit7, R.id.bDigit8, R.id.bDigit9,
		};

		final int[] alphaIds = {
			R.id.bDigitA, R.id.bDigitB, R.id.bDigitC,
			// num = 10, 11, 12
		};

		final int id = v.getId();
		int num = -1;
		for (int i = 0; i < numIds.length; i++) if (id == numIds[i]) num = i;
		for (int i = 0; i < alphaIds.length; i++) if (id == alphaIds[i]) num = 10 + i;

		final LeftDrawer.Songs.Handle handle = leftDrawer.getHandle();

		if (num >= 0) { // digits or letters
			if (state_tempCode.length() >= 4) state_tempCode = ""; // can't be more than 4 digits

			if (num <= 9) { // digits
				//noinspection StatementWithEmptyBody
				if (state_tempCode.length() == 0 && num == 0) { // nothing has been pressed and 0 is now pressed
				} else {
					state_tempCode += num;
				}
			} else { // letters
				final char letter = (char) ('A' + num - 10);
				if (state_tempCode.length() != 0) {
					state_tempCode += letter;
				}
			}

			handle.setCode(state_tempCode);

			handle.setOkButtonEnabled(S.getSongDb().songExists(currentBookName, state_tempCode));
			handle.setAButtonEnabled(state_tempCode.length() <= 3 && S.getSongDb().songExists(currentBookName, state_tempCode + "A"));
			handle.setBButtonEnabled(state_tempCode.length() <= 3 && S.getSongDb().songExists(currentBookName, state_tempCode + "B"));
			handle.setCButtonEnabled(state_tempCode.length() <= 3 && S.getSongDb().songExists(currentBookName, state_tempCode + "C"));
		} else if (id == R.id.bOk) {
			if (state_tempCode.length() > 0) {
				final Song song = S.getSongDb().getSong(currentBookName, state_tempCode);
				if (song != null) {
					displaySong(currentBookName, song);
				} else {
					handle.setCode(state_originalCode); // revert
				}
			} else {
				handle.setCode(state_originalCode); // revert
			}
			leftDrawer.closeDrawer();
		}
	}

	@Override
	public void songBookSelected(final boolean all, final SongBookUtil.SongBookInfo songBookInfo) {
		if (all) return; // should not happen

		final Song song = S.getSongDb().getFirstSongFromBook(songBookInfo.bookName);

		if (song != null) {
			displaySong(songBookInfo.bookName, song);
		} else {
			SongBookUtil.downloadSongBook(SongViewActivity.this, songBookInfo, new SongBookUtil.OnDownloadSongBookListener() {
				@Override public void onFailedOrCancelled(SongBookUtil.SongBookInfo songBookInfo, Exception e) {
					if (e != null) {
						new AlertDialog.Builder(SongViewActivity.this)
							.setMessage(e.getClass().getSimpleName() + ' ' + e.getMessage())
							.setPositiveButton(R.string.ok, null)
							.show();
					}
				}

				@Override public void onDownloadedAndInserted(SongBookUtil.SongBookInfo songBookInfo) {
					Song song = S.getSongDb().getFirstSongFromBook(songBookInfo.bookName);
					displaySong(songBookInfo.bookName, song);
				}
			});
		}

		state_tempCode = "";
	}

	void setCustomProgressBarIndeterminateVisible(final boolean visible) {
		circular_progress.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
	}
}

interface MediaStateListener {
	void setMediaState(SongViewActivity.MediaPlayerController.ControllerState state);
}

