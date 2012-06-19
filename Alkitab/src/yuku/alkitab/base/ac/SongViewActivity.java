package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;

import net.londatiga.android.QuickAction;
import yuku.afw.App;
import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.storage.Preferences;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.SongBookUtil;
import yuku.alkitab.base.util.SongBookUtil.OnDownloadSongBookListener;
import yuku.alkitab.base.util.SongBookUtil.OnSongBookSelectedListener;
import yuku.alkitab.base.util.SongBookUtil.SongBookInfo;
import yuku.kpri.model.Song;
import yuku.kpriviewer.fr.SongFragment;

public class SongViewActivity extends BaseActivity {
	public static final String TAG = SongViewActivity.class.getSimpleName();

	private static final int REQCODE_songList = 1;
	
	ViewGroup song_container;
	ViewGroup no_song_data_container;
	Button bChangeBook;
	Button bChangeCode;
	View bSearch;
	View bDownload;
	QuickAction qaChangeBook;
	
	Bundle templateCustomVars;

	public static Intent createIntent() {
		Intent res = new Intent(App.context, SongViewActivity.class);
		return res;
	}

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_song_view);
		
		setTitle("Songs");
		
		song_container = V.get(this, R.id.song_container);
		no_song_data_container = V.get(this, R.id.no_song_data_container);
		bChangeBook = V.get(this, R.id.bChangeBook);
		bChangeCode = V.get(this, R.id.bChangeCode);
		bSearch = V.get(this, R.id.bSearch);
		bDownload = V.get(this, R.id.bDownload);
		
		qaChangeBook = SongBookUtil.getSongBookQuickAction(this, false);
		qaChangeBook.setOnActionItemClickListener(SongBookUtil.getOnActionItemConverter(songBookSelected));
		
		bChangeBook.setOnClickListener(bChangeBook_click);
		bSearch.setOnClickListener(bSearch_click);
		bDownload.setOnClickListener(bDownload_click);
		
		// for colors of bg, text, etc
		S.hitungPenerapanBerdasarkanPengaturan();
		V.get(this, android.R.id.content).setBackgroundColor(S.penerapan.warnaLatar);
		
		templateCustomVars = new Bundle();
		templateCustomVars.putString("background_color", String.format("#%06x", S.penerapan.warnaLatar & 0xffffff));
		templateCustomVars.putString("text_color", String.format("#%06x", S.penerapan.warnaHuruf & 0xffffff));
		templateCustomVars.putString("verse_number_color", String.format("#%06x", S.penerapan.warnaNomerAyat & 0xffffff));
		
		{ // show latest viewed song
			String bookName = Preferences.getString(Prefkey.song_last_bookName, "KJ"); // let KJ become the default.
			String code = Preferences.getString(Prefkey.song_last_code, "1");
			
			displaySong(bookName, S.getSongDb().getSong(bookName, code, SongBookUtil.getSongDataFormatVersion()));
		}
	}
	
	private void displaySong(String bookName, Song song) {
		song_container.setVisibility(song != null? View.VISIBLE: View.GONE);
		no_song_data_container.setVisibility(song != null? View.GONE: View.VISIBLE);
		
		if (song != null) {
			bChangeBook.setText(bookName);
			bChangeCode.setText(song.code);

			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
			ft.replace(R.id.song_container, SongFragment.create(song, "templates/song.html", templateCustomVars));
			ft.commitAllowingStateLoss();

			{ // save latest viewed song TODO optimize using new preferences fw
				Preferences.setString(Prefkey.song_last_bookName, bookName);
				Preferences.setString(Prefkey.song_last_code, song.code);
			}
		}
	}

	OnClickListener bSearch_click = new OnClickListener() {
		@Override public void onClick(View v) {
			startActivityForResult(SongListActivity.createIntent(), REQCODE_songList);
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
	
	OnSongBookSelectedListener songBookSelected = new OnSongBookSelectedListener() {
		@Override public void onSongBookSelected(boolean all, SongBookInfo songBookInfo) {
			if (all) return; // should not happen
			
			Song song = S.getSongDb().getFirstSongFromBook(songBookInfo.bookName, SongBookUtil.getSongDataFormatVersion());
			
			if (song != null) {
				displaySong(songBookInfo.bookName, song);
			} else {
				SongBookUtil.downloadSongBook(SongViewActivity.this, songBookInfo, new OnDownloadSongBookListener() {
					@Override public void onFailedOrCancelled(SongBookInfo songBookInfo, Exception e) {
						if (e != null) {
							new AlertDialog.Builder(SongViewActivity.this)
							.setMessage(e.getClass().getSimpleName() + " " + e.getMessage())
							.setPositiveButton(R.string.ok, null)
							.show();
						}
					}
					
					@Override public void onDownloadedAndInserted(SongBookInfo songBookInfo) {
						Song song = S.getSongDb().getFirstSongFromBook(songBookInfo.bookName, SongBookUtil.getSongDataFormatVersion());
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
					displaySong(result.bookName, S.getSongDb().getSong(result.bookName, result.code, SongBookUtil.getSongDataFormatVersion()));
				}
			}
		}
	}
}
