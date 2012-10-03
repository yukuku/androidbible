package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import net.londatiga.android.QuickAction;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.SongListActivity.SearchState;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.base.util.OsisBookNames;
import yuku.alkitab.base.util.SongBookUtil;
import yuku.alkitab.base.util.SongBookUtil.OnDownloadSongBookListener;
import yuku.alkitab.base.util.SongBookUtil.OnSongBookSelectedListener;
import yuku.alkitab.base.util.SongBookUtil.SongBookInfo;
import yuku.alkitab.base.widget.SongCodePopup;
import yuku.alkitab.base.widget.SongCodePopup.SongCodePopupListener;
import yuku.kpri.model.Lyric;
import yuku.kpri.model.Song;
import yuku.kpri.model.Verse;
import yuku.kpri.model.VerseKind;
import yuku.kpriviewer.fr.SongFragment;
import yuku.kpriviewer.fr.SongFragment.ShouldOverrideUrlLoadingHandler;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class SongViewActivity extends BaseActivity implements ShouldOverrideUrlLoadingHandler {
	public static final String TAG = SongViewActivity.class.getSimpleName();

	private static final String PROTOCOL = "bible"; //$NON-NLS-1$
	private static final int REQCODE_songList = 1;
	private static final int REQCODE_share = 2;

	public static final int RESULT_gotoScripture = 1;

	public static final String EXTRA_ref = "ref"; //$NON-NLS-1$
	
	ViewGroup song_container;
	ViewGroup no_song_data_container;
	Button bChangeBook;
	Button bChangeCode;
	View bSearch;
	View bDownload;
	QuickAction qaChangeBook;
	SongCodePopup codeKeypad;
	
	Bundle templateCustomVars;
	String currentBookName;
	Song currentSong;

	// for initially populating the search song activity
	SearchState last_searchState = null;

	public static Intent createIntent() {
		Intent res = new Intent(App.context, SongViewActivity.class);
		return res;
	}

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_song_view);
		
		setTitle(R.string.sn_songs_activity_title);
		
		song_container = V.get(this, R.id.song_container);
		no_song_data_container = V.get(this, R.id.no_song_data_container);
		bChangeBook = V.get(this, R.id.bChangeBook);
		bChangeCode = V.get(this, R.id.bChangeCode);
		bSearch = V.get(this, R.id.bSearch);
		bDownload = V.get(this, R.id.bDownload);
		
		qaChangeBook = SongBookUtil.getSongBookQuickAction(this, false);
		qaChangeBook.setOnActionItemClickListener(SongBookUtil.getOnActionItemConverter(songBookSelected));
		
		codeKeypad = new SongCodePopup(this);
		
		bChangeBook.setOnClickListener(bChangeBook_click);
		bChangeCode.setOnClickListener(bChangeCode_click);
		bSearch.setOnClickListener(bSearch_click);
		bDownload.setOnClickListener(bDownload_click);
		
		// for colors of bg, text, etc
		V.get(this, android.R.id.content).setBackgroundColor(S.applied.backgroundColor);
		
		templateCustomVars = new Bundle();
		templateCustomVars.putString("background_color", String.format("#%06x", S.applied.backgroundColor & 0xffffff)); //$NON-NLS-1$ //$NON-NLS-2$
		templateCustomVars.putString("text_color", String.format("#%06x", S.applied.fontColor & 0xffffff)); //$NON-NLS-1$ //$NON-NLS-2$
		templateCustomVars.putString("verse_number_color", String.format("#%06x", S.applied.verseNumberColor & 0xffffff)); //$NON-NLS-1$ //$NON-NLS-2$
		templateCustomVars.putString("text_size", S.applied.fontSize2dp + "px"); // somehow this is automatically scaled to dp. //$NON-NLS-1$ //$NON-NLS-2$
		templateCustomVars.putString("line_spacing_mult", String.valueOf(S.applied.lineSpacingMult)); //$NON-NLS-1$
		
		{
			String fontName = Preferences.getString(getString(R.string.pref_jenisHuruf_key), null);
			if (FontManager.isCustomFont(fontName)) {
				templateCustomVars.putString("custom_font_loader", String.format("@font-face{ font-family: '%s'; src: url('%s'); }", fontName, FontManager.getCustomFontUri(fontName))); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				templateCustomVars.putString("custom_font_loader", ""); //$NON-NLS-1$ //$NON-NLS-2$
			}
			templateCustomVars.putString("text_font", fontName); //$NON-NLS-1$
		}
		
		{ // show latest viewed song
			String bookName = Preferences.getString(Prefkey.song_last_bookName, null); // let KJ become the default.
			String code = Preferences.getString(Prefkey.song_last_code, null);
			
			if (bookName == null || code == null) {
				displaySong(null, null);
			} else {
				displaySong(bookName, S.getSongDb().getSong(bookName, code));
			}
		}
	}
	
	private void bikinMenu(Menu menu) {
		menu.clear();
		getSupportMenuInflater().inflate(R.menu.activity_song_view, menu);
	}
	
	@Override public boolean onCreateOptionsMenu(Menu menu) {
		bikinMenu(menu);
		return true;
	}
	
	@Override public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		if (menu != null) bikinMenu(menu);
		return true;
	}
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuSalin: {
			if (currentSong != null) {
				U.copyToClipboard(convertSongToText(currentSong));

				Toast.makeText(this, R.string.sn_copied, Toast.LENGTH_SHORT).show();
			}
		} return true;
		case R.id.menuBagikan: {
			if (currentSong != null) {
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain"); //$NON-NLS-1$
				intent.putExtra(Intent.EXTRA_SUBJECT, currentBookName + ' ' + currentSong.code + ' ' + currentSong.title);
				intent.putExtra(Intent.EXTRA_TEXT, (CharSequence) convertSongToText(currentSong)); 
				startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.sn_share_title)), REQCODE_share);
			}
		} return true;
		}
		return false;
	}

	private StringBuilder convertSongToText(Song song) {
		// build text to copy
		StringBuilder sb = new StringBuilder();
		sb.append(song.code).append(". "); //$NON-NLS-1$
		sb.append(song.title).append('\n');
		if (song.title_original != null) sb.append('(').append(song.title_original).append(')').append('\n');
		sb.append('\n');
		
		if (song.authors_lyric != null && song.authors_lyric.size() > 0) sb.append(TextUtils.join("; ", song.authors_lyric)).append('\n'); //$NON-NLS-1$
		if (song.authors_music != null && song.authors_music.size() > 0) sb.append(TextUtils.join("; ", song.authors_music)).append('\n'); //$NON-NLS-1$
		if (song.tune != null) sb.append(song.tune.toUpperCase()).append('\n');
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
					sb.append(String.format("%2d: ", verse_normal_no)); //$NON-NLS-1$
					skipPad = true;
				}

				for (String line: verse.lines) {
					if (!skipPad) {
						sb.append("    "); //$NON-NLS-1$
					} else {
						skipPad = false;
					}
					sb.append(line).append("\n");  //$NON-NLS-1$
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
		if (line == null || line.trim().length() == 0) return ""; //$NON-NLS-1$
		
		StringBuilder sb = new StringBuilder();

		String[] ranges = line.split("\\s*;\\s*"); //$NON-NLS-1$
		for (String range: ranges) {
			String[] osisIds;
			if (range.indexOf('-') >= 0) {
				osisIds = range.split("\\s*-\\s*"); //$NON-NLS-1$
			} else {
				osisIds = new String[] {range};
			}
			
			if (osisIds.length == 1) {
				if (sb.length() != 0) {
					sb.append("; "); //$NON-NLS-1$
				}
				
				String osisId = osisIds[0];
				String readable = osisIdToReadable(line, osisId, null, null);
				if (readable != null) {
					appendScriptureReferenceLink(sb, protocol, osisId, readable);
				}
			} else if (osisIds.length == 2) {
				if (sb.length() != 0) {
					sb.append("; "); //$NON-NLS-1$
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
			sb.append("<a href='"); //$NON-NLS-1$
			sb.append(protocol);
			sb.append(':');
			sb.append(osisId);
			sb.append("'>"); //$NON-NLS-1$
		}
		sb.append(readable);
		if (protocol != null) {
			sb.append("</a>"); //$NON-NLS-1$
		}
	}

	/**
	 * @param compareWithRangeStart if this is the second part of a range, set this to non-null, with [0] is bookId and [1] chapter_1.
	 * @param outBcv if not null and length is >= 3, will be filled with parsed bcv
	 */
	private String osisIdToReadable(String line, String osisId, int[] compareWithRangeStart, int[] outBcv) {
		String res = null;
		
		String[] parts = osisId.split("\\."); //$NON-NLS-1$
		if (parts.length != 2 && parts.length != 3) {
			Log.w(TAG, "osisId invalid: " + osisId + " in " + line); //$NON-NLS-1$ //$NON-NLS-2$
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
				Log.w(TAG, "osisBookName invalid: " + bookName + " in " + line); //$NON-NLS-1$ //$NON-NLS-2$
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
						res = verse_1 == 0? S.reference(book, chapter_1): S.reference(book, chapter_1, verse_1);
					}
				}
			}
		}
		return res;
	}

	void displaySong(String bookName, Song song) {
		song_container.setVisibility(song != null? View.VISIBLE: View.GONE);
		no_song_data_container.setVisibility(song != null? View.GONE: View.VISIBLE);
		
		if (song != null) {
			bChangeBook.setText(bookName);
			bChangeCode.setText(song.code);

			// construct rendition of scripture references
			String scripture_references = renderScriptureReferences(PROTOCOL, song.scriptureReferences);
			templateCustomVars.putString("scripture_references", scripture_references); //$NON-NLS-1$
			
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
			ft.replace(R.id.song_container, SongFragment.create(song, "templates/song.html", templateCustomVars)); //$NON-NLS-1$
			ft.commitAllowingStateLoss();

			currentBookName = bookName;
			currentSong = song;
			
			{ // save latest viewed song TODO optimize using new preferences fw
				Preferences.setString(Prefkey.song_last_bookName, bookName);
				Preferences.setString(Prefkey.song_last_code, song.code);
			}
		}
	}

	OnClickListener bSearch_click = new OnClickListener() {
		@Override public void onClick(View v) {
			startActivityForResult(SongListActivity.createIntent(last_searchState), REQCODE_songList);
		}
	};
	
	OnClickListener bDownload_click = new OnClickListener() {
		@Override public void onClick(View v) { // just a proxy to bChangeBook
			bChangeBook.performClick();
		}
	};
	
	OnClickListener bChangeBook_click = new OnClickListener() {
		@Override public void onClick(View v) {
			qaChangeBook.show(v);
		}
	};
	
	OnClickListener bChangeCode_click = new OnClickListener() {
		@Override public void onClick(View v) {
			if (currentBookName == null) return;
			
			codeKeypad.show(v);
			codeKeypad.setOkButtonEnabled(false); 
			
			codeKeypad.setSongCodePopupListener(new SongCodePopupListener() { // do not make this a field. Need to create a new instance to init fields correctly.
				CharSequence originalCode = bChangeCode.getText();
				String tempCode = ""; //$NON-NLS-1$
				String bookName = currentBookName;
				boolean jumped = false;
				
				@Override public void onDismiss(SongCodePopup songCodePopup) {
					if (!jumped) {
						bChangeCode.setText(originalCode);
					}
				}
				
				@Override public void onButtonClick(SongCodePopup songCodePopup, View v) {
					int[] numIds = {
					R.id.bAngka0, R.id.bAngka1, R.id.bAngka2, R.id.bAngka3, R.id.bAngka4,
					R.id.bAngka5, R.id.bAngka6, R.id.bAngka7, R.id.bAngka8, R.id.bAngka9,
					};
					
					int id = v.getId();
					int num = -1;
					for (int i = 0; i < numIds.length; i++) if (id == numIds[i]) num = i;
					
					if (num >= 0) { // digits
						if (tempCode.length() >= 4) tempCode = ""; // can't be more than 4 digits //$NON-NLS-1$
						if (tempCode.length() == 0 && num == 0) { // nothing has been pressed and 0 is now pressed
						} else {
							tempCode += num;
						}
						bChangeCode.setText(tempCode);
						
						songCodePopup.setOkButtonEnabled(S.getSongDb().songExists(bookName, tempCode));
					} else if (id == R.id.bOk) {
						if (tempCode.length() > 0) {
							Song song = S.getSongDb().getSong(bookName, tempCode);
							if (song != null) {
								displaySong(bookName, song);
								jumped = true;
							} else {
								bChangeCode.setText(originalCode); // revert
							}
						} else {
							bChangeCode.setText(originalCode); // revert
						}
						songCodePopup.dismiss();
					}
				}
			});
		}
	};
	
	OnSongBookSelectedListener songBookSelected = new OnSongBookSelectedListener() {
		@Override public void onSongBookSelected(boolean all, SongBookInfo songBookInfo) {
			if (all) return; // should not happen
			
			Song song = S.getSongDb().getFirstSongFromBook(songBookInfo.bookName);
			
			if (song != null) {
				displaySong(songBookInfo.bookName, song);
			} else {
				SongBookUtil.downloadSongBook(SongViewActivity.this, songBookInfo, new OnDownloadSongBookListener() {
					@Override public void onFailedOrCancelled(SongBookInfo songBookInfo, Exception e) {
						if (e != null) {
							new AlertDialog.Builder(SongViewActivity.this)
							.setMessage(e.getClass().getSimpleName() + ' ' + e.getMessage())
							.setPositiveButton(R.string.ok, null)
							.show();
						}
					}
					
					@Override public void onDownloadedAndInserted(SongBookInfo songBookInfo) {
						Song song = S.getSongDb().getFirstSongFromBook(songBookInfo.bookName);
						displaySong(songBookInfo.bookName, song);
					}
				});
			}
		}
	};

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

	@Override public boolean shouldOverrideUrlLoading(WebViewClient client, WebView view, String url) {
		Uri uri = Uri.parse(url);
		if (U.equals(uri.getScheme(), PROTOCOL)) {
			Intent data = new Intent();
			data.putExtra(EXTRA_ref, uri.getSchemeSpecificPart());
			setResult(RESULT_gotoScripture, data);
			finish();
			return true;
		}
		return false;
	}
}
