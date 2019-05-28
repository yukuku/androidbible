package yuku.alkitab.songs;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.DrawableRes;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ShareCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.firebase.analytics.FirebaseAnalytics;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.AlertDialogActivity;
import yuku.alkitab.base.ac.HelpActivity;
import yuku.alkitab.base.ac.PatchTextActivity;
import yuku.alkitab.base.ac.ShareActivity;
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity;
import yuku.alkitab.base.dialog.VersesDialog;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.AlphanumComparator;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Background;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.base.util.Foreground;
import yuku.alkitab.base.util.OsisBookNames;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.base.widget.LeftDrawer;
import yuku.alkitab.base.widget.TwofingerLinearLayout;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;
import yuku.alkitab.tracking.Tracker;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.display.Launcher;
import yuku.kpri.model.Lyric;
import yuku.kpri.model.Song;
import yuku.kpri.model.Verse;
import yuku.kpri.model.VerseKind;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SongViewActivity extends BaseLeftDrawerActivity implements SongFragment.ShouldOverrideUrlLoadingHandler, LeftDrawer.Songs.Listener, MediaStateListener {
	static final String TAG = SongViewActivity.class.getSimpleName();

	private static final String BIBLE_PROTOCOL = "bible";
	private static final int REQCODE_songList = 1;
	private static final int REQCODE_share = 2;
	private static final int REQCODE_downloadSongBook = 3;
	private static final String FRAGMENT_TAG_SONG = "song";

	DrawerLayout drawerLayout;
	LeftDrawer.Songs leftDrawer;

	TwofingerLinearLayout root;
	ViewGroup no_song_data_container;
	View bDownload;
	View circular_progress;

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

	final TwofingerLinearLayout.Listener song_container_listener = new TwofingerLinearLayout.Listener() {
		int textZoom = 0; // stays at 0 if zooming is not ready

		@Override
		public void onOnefingerLeft() {
			goTo(+1);
		}

		@Override
		public void onOnefingerRight() {
			goTo(-1);
		}

		@Override
		public void onTwofingerStart() {
			final Fragment f = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_SONG);
			if (f instanceof SongFragment) {
				textZoom = ((SongFragment) f).getWebViewTextZoom();
			}
		}

		@Override
		public void onTwofingerScale(final float scale) {
			int newTextZoom = (int) (textZoom * scale);
			if (newTextZoom < 50) newTextZoom = 50;
			if (newTextZoom > 200) newTextZoom = 200;

			final Fragment f = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_SONG);
			if (f instanceof SongFragment) {
				((SongFragment) f).setWebViewTextZoom(newTextZoom);
			}
		}

		@Override
		public void onTwofingerDragX(final float dx) {
		}

		@Override
		public void onTwofingerDragY(final float dy) {
		}

		@Override
		public void onTwofingerEnd(final TwofingerLinearLayout.Mode mode) {
		}
	};

	@Override
	protected LeftDrawer getLeftDrawer() {
		return leftDrawer;
	}

	static class MediaState {
		boolean enabled;
		@DrawableRes
		int icon;
		@StringRes
		int label;
		boolean loading;
		@Nullable
		String progress;
	}

	@NonNull
	final MediaState mediaState = new MediaState();

	/**
	 * This method might be called from non-UI thread. Be careful when manipulating UI.
	 */
	@Override
	public void onControllerStateChanged(@NonNull final MediaPlayerController.State state) {
		switch (state) {
			case reset: {
				mediaState.enabled = false;
				mediaState.icon = R.drawable.ic_action_hollowplay;
				mediaState.label = R.string.menuPlay;
			}
			break;

			case reset_media_known_to_exist:
			case paused:
			case complete: {
				mediaState.enabled = true;
				mediaState.icon = R.drawable.ic_action_play;
				mediaState.label = R.string.menuPlay;
			}
			break;

			case playing: {
				// we start playing now
				if (currentBookName != null && currentSong != null) {
					Tracker.trackEvent("song_playing",
						FirebaseAnalytics.Param.ITEM_NAME, currentBookName + " " + currentSong.code,
						FirebaseAnalytics.Param.ITEM_CATEGORY, currentBookName,
						FirebaseAnalytics.Param.ITEM_VARIANT, currentSong.code
					);
				}

				mediaState.enabled = true;
				mediaState.icon = R.drawable.ic_action_pause;
				mediaState.label = R.string.menuPause;
			}
			break;
		}

		mediaState.loading = (state == MediaPlayerController.State.preparing);

		obtainSongProgress();

		//noinspection Convert2MethodRef
		runOnUiThread(() -> supportInvalidateOptionsMenu());
	}

	void obtainSongProgress() {
		final int[] progress = mediaPlayerController.getProgress();
		final int position = progress[0];
		if (position == -1) {
			mediaState.progress = null;
			return;
		}

		final int duration = progress[1];
		if (duration == -1) {
			mediaState.progress = String.format(Locale.US, "%d:%02d", position / 60000, position % 60000 / 1000);
			return;
		}

		mediaState.progress = String.format(Locale.US, "%d:%02d / %d:%02d", position / 60000, position % 60000 / 1000, duration / 60000, duration % 60000 / 1000);
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

		trackSongSelect(currentBookName, newCode);
		displaySong(currentBookName, newSong);
	}

	// this have to be static to prevent double media player
	static MediaPlayerController mediaPlayerController = new MediaPlayerController();

	public static Intent createIntent() {
		return new Intent(App.context, SongViewActivity.class);
	}

	@SuppressLint("HandlerLeak")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_song_view);

		circular_progress = findViewById(R.id.progress_circular);

		setCustomProgressBarIndeterminateVisible(false);

		drawerLayout = findViewById(R.id.drawerLayout);
		leftDrawer = findViewById(R.id.left_drawer);
		leftDrawer.configure(this, drawerLayout);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		final ActionBar actionBar = getSupportActionBar();
		assert actionBar != null;
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);

		drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
			@Override
			public void onDrawerOpened(final View drawerView) {
				drawer_opened();
			}
		});

		root = findViewById(R.id.root);
		no_song_data_container = findViewById(R.id.no_song_data_container);
		bDownload = findViewById(R.id.bDownload);

		root.setListener(song_container_listener);

		// Before KitKat, the WebView can zoom and rewrap text by itself,
		// so we do not need custom implementation of scaling.
		if (Build.VERSION.SDK_INT < 19) {
			root.setTwofingerEnabled(false);
		}

		bDownload.setOnClickListener(v -> openDownloadSongBookPage());

		// if no song books is downloaded, open download page immediately
		if (S.getSongDb().countSongBookInfos() == 0) {
			openDownloadSongBookPage();
		}

		new Handler() {
			@Override
			public void handleMessage(final Message msg) {
				if (isFinishing()) return;

				obtainSongProgress();
				supportInvalidateOptionsMenu();

				sendEmptyMessageDelayed(0, 1000);
			}
		}.sendEmptyMessage(0);
	}

	void openDownloadSongBookPage() {
		startActivityForResult(
			HelpActivity.createIntentWithOverflowMenu(
				BuildConfig.SERVER_HOST + "songs/downloads?app_versionCode=" + App.getVersionCode() + "&app_versionName=" + Uri.encode(App.getVersionName()),
				getString(R.string.sn_download_song_books),
				getString(R.string.sn_menu_private_song_book),
				AlertDialogActivity.createInputIntent(
					null,
					getString(R.string.sn_private_song_book_dialog_desc),
					getString(R.string.cancel),
					getString(R.string.ok),
					InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,
					getString(R.string.sn_private_song_book_name_hint)
				)
			),
			REQCODE_downloadSongBook
		);
	}

	@Override
	protected void onStart() {
		super.onStart();

		final S.CalculatedDimensions applied = S.applied();

		{ // apply background color, and clear window background to prevent overdraw
			getWindow().setBackgroundDrawableResource(android.R.color.transparent);
			findViewById(android.R.id.content).setBackgroundColor(applied.backgroundColor);
		}

		templateCustomVars = new Bundle();
		templateCustomVars.putString("background_color", String.format(Locale.US, "#%06x", applied.backgroundColor & 0xffffff));
		templateCustomVars.putString("text_color", String.format(Locale.US, "#%06x", applied.fontColor & 0xffffff));
		templateCustomVars.putString("verse_number_color", String.format(Locale.US, "#%06x", applied.verseNumberColor & 0xffffff));
		templateCustomVars.putString("text_size", applied.fontSize2dp + "px");
		templateCustomVars.putString("line_spacing_mult", String.valueOf(applied.lineSpacingMult));

		{
			String fontName = Preferences.getString(Prefkey.jenisHuruf, null);
			if (FontManager.isCustomFont(fontName)) {
				final String customFontUri = FontManager.getCustomFontUri(fontName);
				if (customFontUri != null) {
					templateCustomVars.putString("custom_font_loader", String.format(Locale.US, "@font-face{ font-family: '%s'; src: url('%s'); }", fontName, customFontUri));
				} else {
					templateCustomVars.putString("custom_font_loader", "");
				}
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

		getWindow().getDecorView().setKeepScreenOn(Preferences.getBoolean(getString(R.string.pref_keepScreenOn_key), getResources().getBoolean(R.bool.pref_keepScreenOn_default)));
	}

	/**
	 * Used after deleting a song, and the current song is no longer available
	 */
	void displayAnySongOrFinish() {
		final Pair<String, Song> pair = S.getSongDb().getAnySong();
		if (pair == null) {
			finish();
		} else {
			displaySong(pair.first, pair.second);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		mediaPlayerController.setUI(this, this);
		mediaPlayerController.updateMediaState();
	}

	static String getAudioFilename(String bookName, String code) {
		return String.format(Locale.US, "songs/v2/%s_%s", bookName, code);
	}

	void checkAudioExistance() {
		if (currentBookName == null || currentSong == null) return;

		final String checkedBookName = currentBookName;
		final String checkedCode = currentSong.code;

		Background.run(() -> {
			try {
				final String filename = getAudioFilename(checkedBookName, checkedCode);
				final String response = App.downloadString(BuildConfig.SERVER_HOST + "addon/audio/exists?filename=" + Uri.encode(filename));
				if (response.startsWith("OK")) {
					// make sure this is the correct one due to possible race condition
					if (U.equals(currentBookName, checkedBookName) && currentSong != null && U.equals(currentSong.code, checkedCode)) {
						runOnUiThread(() -> {
							if (mediaPlayerController.canHaveNewUrl()) {
								final String baseUrl = BuildConfig.SERVER_HOST + "addon/audio/";
								final String url = baseUrl + getAudioFilename(currentBookName, currentSong.code);
								if (response.contains("extension=mp3")) {
									mediaPlayerController.mediaKnownToExist(url, false);
								} else {
									mediaPlayerController.mediaKnownToExist(url, true);
								}
							} else {
								AppLog.d(TAG, "mediaPlayerController can't have new URL at this moment.");
							}
						});
					}
				} else {
					AppLog.d(TAG, "@@checkAudioExistance response: " + response);
				}
			} catch (IOException e) {
				AppLog.e(TAG, "@@checkAudioExistance", e);
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_song_view, menu);

		new Handler().post(() -> {
			final View view = findViewById(R.id.menuMediaControl);
			if (view == null) return;

			view.setOnLongClickListener(v -> {
				if (mediaState.icon == R.drawable.ic_action_play) {
					new MaterialDialog.Builder(this)
						.content(R.string.sn_play_in_loop)
						.negativeText(R.string.cancel)
						.positiveText(R.string.ok)
						.onPositive((dialog, which) -> mediaPlayerController.playOrPause(true))
						.show();
					return true;
				}
				return false;
			});
		});
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
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
		menuMediaControl.setTitle(mediaState.progress);

		final boolean songShown = currentBookName != null;

		final MenuItem menuCopy = menu.findItem(R.id.menuCopy);
		menuCopy.setVisible(songShown);
		final MenuItem menuShare = menu.findItem(R.id.menuShare);
		menuShare.setVisible(songShown);
		final MenuItem menuUpdateBook = menu.findItem(R.id.menuUpdateBook);
		menuUpdateBook.setVisible(songShown);
		final MenuItem menuDeleteSongBook = menu.findItem(R.id.menuDeleteSongBook);
		menuDeleteSongBook.setVisible(songShown);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home: {
				leftDrawer.toggleDrawer();
			}
			return true;

			case R.id.menuCopy: {
				if (currentSong != null) {
					U.copyToClipboard(convertSongToText(currentSong));

					Snackbar.make(root, R.string.sn_copied, Snackbar.LENGTH_SHORT).show();
				}
			}
			return true;

			case R.id.menuShare: {
				if (currentSong != null) {
					final Intent intent = ShareCompat.IntentBuilder.from(SongViewActivity.this)
						.setType("text/plain")
						.setSubject(SongBookUtil.escapeSongBookName(currentBookName).toString() + ' ' + currentSong.code + ' ' + currentSong.title)
						.setText(convertSongToText(currentSong).toString())
						.getIntent();
					startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.sn_share_title)), REQCODE_share);
				}
			}
			return true;

			case R.id.menuSearch: {
				startActivityForResult(SongListActivity.createIntent(last_searchState), REQCODE_songList);
			}
			return true;

			case R.id.menuMediaControl: {
				if (currentBookName != null && currentSong != null) {
					mediaPlayerController.playOrPause(false);
				}
			}
			return true;

			case R.id.menuUpdateBook: {
				new MaterialDialog.Builder(this)
					.content(TextUtils.expandTemplate(getText(R.string.sn_update_book_explanation), SongBookUtil.escapeSongBookName(currentBookName)))
					.positiveText(R.string.sn_update_book_confirm_button)
					.onPositive((dialog, which) -> updateSongBook())
					.negativeText(R.string.cancel)
					.show();
			}
			return true;

			case R.id.menuDeleteSongBook: {
				new MaterialDialog.Builder(this)
					.content(TextUtils.expandTemplate(getText(R.string.sn_delete_song_book_explanation), SongBookUtil.escapeSongBookName(currentBookName)))
					.positiveText(R.string.delete)
					.onPositive((dialog, which) -> deleteSongBook())
					.negativeText(R.string.cancel)
					.show();
			}
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	protected void updateSongBook() {
		final SongBookUtil.SongBookInfo songBookInfo = SongBookUtil.getSongBookInfo(currentBookName);
		if (songBookInfo == null) {
			throw new RuntimeException("SongBookInfo named " + currentBookName + " was not found");
		}

		final String currentSongCode = currentSong.code;
		final int dataFormatVersion = S.getSongDb().getDataFormatVersionForSongs(currentBookName);

		SongBookUtil.downloadSongBook(SongViewActivity.this, songBookInfo, dataFormatVersion, new SongBookUtil.OnDownloadSongBookListener() {
			@Override
			public void onFailedOrCancelled(SongBookUtil.SongBookInfo songBookInfo, Exception e) {
				showDownloadError(e);
			}

			@Override
			public void onDownloadedAndInserted(SongBookUtil.SongBookInfo songBookInfo) {
				final Song song = S.getSongDb().getSong(songBookInfo.name, currentSongCode);
				cache_codes.remove(songBookInfo.name);
				displaySong(songBookInfo.name, song);
			}
		});
	}

	void showDownloadError(final Exception e) {
		if (e == null) return;
		if (isFinishing()) return;

		if (e instanceof SongBookUtil.NotOkException) {
			new MaterialDialog.Builder(this)
				.content("HTTP error " + ((SongBookUtil.NotOkException) e).code)
				.positiveText(R.string.ok)
				.show();
		} else {
			new MaterialDialog.Builder(this)
				.content(e.getClass().getSimpleName() + ": " + e.getMessage())
				.positiveText(R.string.ok)
				.show();
		}
	}

	protected void deleteSongBook() {
		final MaterialDialog pd = new MaterialDialog.Builder(this)
			.content(R.string.please_wait_titik3)
			.cancelable(false)
			.progress(true, 0)
			.show();

		final String bookName = currentBookName;

		Background.run(() -> {
			final int count = S.getSongDb().deleteSongBook(bookName);

			Foreground.run(() -> {
				pd.dismiss();

				new MaterialDialog.Builder(this)
					.content(TextUtils.expandTemplate(getText(R.string.sn_delete_song_book_result), "" + count, SongBookUtil.escapeSongBookName(bookName)))
					.positiveText(R.string.ok)
					.dismissListener(dialog -> displayAnySongOrFinish())
					.show();
			});
		});
	}

	private StringBuilder convertSongToText(Song song) {
		// build text to copy
		final StringBuilder sb = new StringBuilder();
		sb.append(SongBookUtil.escapeSongBookName(currentBookName)).append(' ');
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
					sb.append(getString(R.string.sn_lyric_version_version, String.valueOf(i + 1))).append('\n');
				}
			}

			int verse_normal_no = 0;
			for (Verse verse : lyric.verses) {
				if (verse.kind == VerseKind.NORMAL) {
					verse_normal_no++;
				}

				boolean skipPad = false;
				if (verse.kind == VerseKind.REFRAIN) {
					sb.append(getString(R.string.sn_lyric_refrain_marker)).append('\n');
				} else {
					sb.append(String.format(Locale.US, "%2d: ", verse_normal_no));
					skipPad = true;
				}

				for (String line : verse.lines) {
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
	 *
	 * @param protocol null to output text
	 * @param line     scripture ref in osis
	 */
	String renderScriptureReferences(String protocol, String line) {
		if (line == null || line.trim().length() == 0) return "";

		StringBuilder sb = new StringBuilder();

		String[] ranges = line.split("\\s*;\\s*");
		for (String range : ranges) {
			String[] osisIds;
			if (range.indexOf('-') >= 0) {
				osisIds = range.split("\\s*-\\s*");
			} else {
				osisIds = new String[]{range};
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
	 * @param outBcv                if not null and length is >= 3, will be filled with parsed bcv
	 */
	private String osisIdToReadable(String line, String osisId, int[] compareWithRangeStart, int[] outBcv) {
		String res = null;

		String[] parts = osisId.split("\\.");
		if (parts.length != 2 && parts.length != 3) {
			AppLog.w(TAG, "osisId invalid: " + osisId + " in " + line);
		} else {
			String bookName = parts[0];
			int chapter_1 = Integer.parseInt(parts[1]);
			int verse_1 = parts.length < 3 ? 0 : Integer.parseInt(parts[2]);

			int bookId = OsisBookNames.osisBookNameToBookId(bookName);

			if (outBcv != null && outBcv.length >= 3) {
				outBcv[0] = bookId;
				outBcv[1] = chapter_1;
				outBcv[2] = verse_1;
			}

			if (bookId < 0) {
				AppLog.w(TAG, "osisBookName invalid: " + bookName + " in " + line);
			} else {
				Book book = S.activeVersion().getBook(bookId);

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
						res = verse_1 == 0 ? book.reference(chapter_1) : book.reference(chapter_1, verse_1);
					}
				}
			}
		}
		return res;
	}

	void displaySong(String bookName, @Nullable Song song) {
		displaySong(bookName, song, false);
	}

	void displaySong(String bookName, @Nullable Song song, boolean onCreate) {
		root.setVisibility(song != null ? View.VISIBLE : View.GONE);
		no_song_data_container.setVisibility(song != null ? View.GONE : View.VISIBLE);

		if (!onCreate) {
			mediaPlayerController.reset();
		}

		if (song == null) return;

		final LeftDrawer.Songs.Handle handle = leftDrawer.getHandle();
		handle.setBookName(SongBookUtil.escapeSongBookName(bookName));
		handle.setCode(song.code);

		setTitle(TextUtils.concat(SongBookUtil.escapeSongBookName(bookName), " ", song.code));

		// construct rendition of scripture references
		String scripture_references = renderScriptureReferences(BIBLE_PROTOCOL, song.scriptureReferences);
		templateCustomVars.putString("scripture_references", scripture_references);
		final String copyright = SongBookUtil.getCopyright(bookName);
		templateCustomVars.putString("copyright", copyright != null ? copyright : "");
		templateCustomVars.putString("patch_text_open_link", getString(R.string.patch_text_open_link));

		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		ft.replace(R.id.root, SongFragment.create(song, "templates/song.html", templateCustomVars), FRAGMENT_TAG_SONG);
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

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case REQCODE_songList: {
				if (resultCode == RESULT_OK) {
					final SongListActivity.Result result = SongListActivity.obtainResult(data);
					if (result != null) {
						trackSongSelect(result.bookName, result.code);
						displaySong(result.bookName, S.getSongDb().getSong(result.bookName, result.code));
						// store this for next search
						last_searchState = result.last_searchState;
					}
				}
			}
			return;
			case REQCODE_share: {
				if (resultCode == RESULT_OK) {
					ShareActivity.Result result = ShareActivity.obtainResult(data);
					if (result != null && result.chosenIntent != null) {
						startActivity(result.chosenIntent);
					}
				}
			}
			return;
			case REQCODE_downloadSongBook: {
				if (resultCode == RESULT_OK) {
					final Uri uri = data.getData();
					if (uri != null) {
						downloadByAlkitabUri(uri);
					} else {
						final String input = data.getStringExtra(AlertDialogActivity.EXTRA_INPUT);
						if (!TextUtils.isEmpty(input)) {
							downloadByAlkitabUri(Uri.parse("alkitab:///addon/download?kind=songbook&type=ser&dataFormatVersion=3&name=_" + Uri.encode(input.toUpperCase())));
						}
					}
					return;
				}
			}
			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	private static void trackSongSelect(final String bookName, final String code) {
		Tracker.trackEvent("song_select",
			FirebaseAnalytics.Param.ITEM_NAME, bookName + " " + code,
			FirebaseAnalytics.Param.ITEM_CATEGORY, bookName,
			FirebaseAnalytics.Param.ITEM_VARIANT, code
		);
	}

	private void downloadByAlkitabUri(final Uri uri) {
		if (!"alkitab".equals(uri.getScheme()) || !"/addon/download".equals(uri.getPath()) || !"songbook".equals(uri.getQueryParameter("kind")) || !"ser".equals(uri.getQueryParameter("type")) || uri.getQueryParameter("name") == null) {
			new MaterialDialog.Builder(this)
				.content("Invalid uri:\n\n" + uri)
				.positiveText(R.string.ok)
				.show();
			return;
		}

		final String dataFormatVersion_s = uri.getQueryParameter("dataFormatVersion");
		final int dataFormatVersion;
		try {
			dataFormatVersion = Integer.parseInt(dataFormatVersion_s);
		} catch (NumberFormatException | NullPointerException e) {
			new MaterialDialog.Builder(this)
				.content("Invalid uri:\n\n" + uri)
				.positiveText(R.string.ok)
				.show();
			return;
		}

		if (!SongBookUtil.isSupportedDataFormatVersion(dataFormatVersion)) {
			new MaterialDialog.Builder(this)
				.content("Unsupported data format version: " + dataFormatVersion)
				.positiveText(R.string.ok)
				.show();
			return;
		}

		final SongBookUtil.SongBookInfo info = new SongBookUtil.SongBookInfo();
		info.name = uri.getQueryParameter("name");
		info.title = uri.getQueryParameter("title");
		info.copyright = uri.getQueryParameter("copyright");

		SongBookUtil.downloadSongBook(this, info, dataFormatVersion, new SongBookUtil.OnDownloadSongBookListener() {
			@Override
			public void onDownloadedAndInserted(final SongBookUtil.SongBookInfo songBookInfo) {
				final String name = songBookInfo.name;
				final Song song = S.getSongDb().getFirstSongFromBook(name);
				displaySong(name, song);
			}

			@Override
			public void onFailedOrCancelled(final SongBookUtil.SongBookInfo songBookInfo, final Exception e) {
				showDownloadError(e);
			}
		});
	}

	@Keep
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

	@Override
	public boolean shouldOverrideUrlLoading(WebViewClient client, WebView view, String url) {
		Uri uri = Uri.parse(url);
		final String scheme = uri.getScheme();
		if ("patchtext".equals(scheme)) {
			final Song song = currentSong;

			// do not proceed if the song is too old
			final int updateTime = S.getSongDb().getSongUpdateTime(currentBookName, song.code);
			if (updateTime == 0 || Sqlitil.nowDateTime() - updateTime > 21 * 86400) {
				new MaterialDialog.Builder(this)
					.content(TextUtils.expandTemplate(getText(R.string.sn_update_book_because_too_old), SongBookUtil.escapeSongBookName(currentBookName)))
					.positiveText(R.string.sn_update_book_confirm_button)
					.negativeText(R.string.cancel)
					.onPositive((dialog, which) -> updateSongBook())
					.show();
			} else {
				final PatchTextExtraInfoJson extraInfo = new PatchTextExtraInfoJson();
				extraInfo.type = "song";
				extraInfo.bookName = currentBookName;
				extraInfo.code = song.code;

				final String songHeader = song.code + " " + song.title + nonullbr(song.title_original) + nonullbr(song.tune) + nonullbr(song.keySignature) + nonullbr(song.timeSignature) + nonullbr(song.authors_lyric) + nonullbr(song.authors_music);
				final String songHtml = SongFragment.songToHtml(song, true);
				final Spanned baseBody = Html.fromHtml(songHeader + "\n\n" + songHtml);
				startActivity(PatchTextActivity.createIntent(baseBody, App.getDefaultGson().toJson(extraInfo), null));
			}
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
		if (state_tempCode == null) return; // only possible if the user is so fast that he presses the buttons before drawer opens for the first time

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
		for (int i = 0; i < alphaIds.length; i++) if (id == alphaIds[i]) num = 10 + i; // special code for alpha
		if (id == R.id.bBackspace) num = 20; // special code for backspace

		final LeftDrawer.Songs.Handle handle = leftDrawer.getHandle();

		if (num >= 0) { // digits or letters or backspace
			if (num == 20) { // backspace
				if (state_tempCode.length() > 0) {
					state_tempCode = state_tempCode.substring(0, state_tempCode.length() - 1);
				}
			} else {
				if (state_tempCode.length() >= 4) state_tempCode = ""; // can't be more than 4 digits

				if (num <= 9) { // digits
					if (state_tempCode.length() == 0 && num == 0) {
						// nothing has been pressed and 0 is now pressed
					} else {
						state_tempCode += num;
					}
				} else if (num <= 19) { // letters
					final char letter = (char) ('A' + num - 10);
					if (state_tempCode.length() != 0) {
						state_tempCode += letter;
					}
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
					trackSongSelect(currentBookName, song.code);
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
	public void songBookSelected(final String name) {
		final Song song = S.getSongDb().getFirstSongFromBook(name);

		if (song != null) {
			displaySong(name, song);
		}

		state_tempCode = "";
	}

	@Override
	public void moreSelected() {
		openDownloadSongBookPage();
	}

	void setCustomProgressBarIndeterminateVisible(final boolean visible) {
		circular_progress.setVisibility(visible ? View.VISIBLE : View.GONE);
	}
}

