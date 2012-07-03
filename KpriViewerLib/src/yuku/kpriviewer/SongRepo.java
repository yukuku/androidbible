package yuku.kpriviewer;

import android.text.TextUtils;
import android.util.Log;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import yuku.kpri.model.Lyric;
import yuku.kpri.model.Song;
import yuku.kpri.model.Verse;

public class SongRepo {
	public static final String TAG = SongRepo.class.getSimpleName();

	private static Semaphore loading = new Semaphore(1, true);
	
	// all songs, and mapping between bookName and songs
	private static List<Song> allSongs = new ArrayList<Song>();
	private static LinkedHashMap<String, List<Song>> bookToSongs = new LinkedHashMap<String, List<Song>>();
	private static LinkedHashMap<Song, String> songToBook = new LinkedHashMap<Song, String>();
	
	public static List<Song> getAllSongs() {
		loading.acquireUninterruptibly();
		try {
			return allSongs;
		} finally {
			loading.release();
		}
	}
	
	@SuppressWarnings("unchecked") public static List<Song> loadSongData(String bookName, InputStream ser) {
		Log.d(TAG, "Going to load song book: " + bookName);
		loading.acquireUninterruptibly();
		try {
			List<Song> songsInBook = bookToSongs.get(bookName);
			if (songsInBook != null) {
				return songsInBook; // already loaded
			}
			
			bookToSongs.put(bookName, songsInBook = new ArrayList<Song>());
			
			Log.d(TAG, "Loading song book: " + bookName);
			ObjectInputStream ois = new ObjectInputStream(ser);
			List<Song> songs = (List<Song>) ois.readObject();
			ois.close();
			
			// must insert to 3 parts
			for (Song song: songs) {
				allSongs.add(song);
				songToBook.put(song, bookName);
				songsInBook.add(song);
			}
			
			return songs;
		} catch (Exception e) {
			Log.e(TAG, "reading ser error", e);
			return null;
		} finally {
			loading.release();
		}
	}

	public static List<Song> getSongsByBookNamesAndCodes(List<String> bookNames, List<String> codes) {
		List<Song> res = new ArrayList<Song>(codes.size());
		
		// create maps first
		Map<String, Song> map = new HashMap<String, Song>(); // bookName/code => song
		for (Song song: getAllSongs()) {
			map.put(songToBook.get(song) + "/" + song.code, song);
		}
		
		for (int i = 0; i < bookNames.size(); i++) {
			String bookName = bookNames.get(i);
			String code = codes.get(i);
			res.add(map.get(bookName + "/" + code));
		}
		
		return res;
	}
	
	public static String getBookNameBySong(Song song) {
		return songToBook.get(song);
	}
	
	public static List<Song> getSongsByBook(String bookName) {
		loading.acquireUninterruptibly();
		try {
			List<Song> res = bookToSongs.get(bookName);
			if (res != null) return res;
			return new ArrayList<Song>();
		} finally {
			loading.release();
		}
	}

	public static List<Song> filterSongByString(List<Song> songs, String filter_string) {
		List<Song> res = new ArrayList<Song>();
		
		if (filter_string == null) {
			res.addAll(songs);
		} else {
			String[] splits = TextUtils.split(filter_string, "\\s+");
			Pattern[] ps = new Pattern[splits.length];
			for (int i = 0; i < splits.length; i++) {
				ps[i] = Pattern.compile(Pattern.quote(splits[i]), Pattern.CASE_INSENSITIVE); 
			}
			
			for (Song song: songs) {
				int matches = 0;
				for (int i = 0; i < ps.length; i++) {
					if (match(song, ps[i])) matches++; 
				}
				if (matches == ps.length) res.add(song);
			}
		}
		
		Log.d(TAG, "@@loadInBackground res.size=" + res.size());
		
		return res;
	}
	
	private static boolean match(Song song, Pattern p) {
		Matcher m = p.matcher("");
		
		if (find(song.code, m)) return true;
		if (find(song.title, m)) return true;
		if (song.title_original != null && find(song.title_original, m)) return true;
		if (song.authors_lyric != null) for (String author_lyric: song.authors_lyric) {
			if (find(author_lyric, m)) return true;
		}
		if (song.authors_music != null) for (String author_music: song.authors_music) {
			if (find(author_music, m)) return true;
		}
		if (song.tune != null && find(song.tune, m)) return true;

		for (Lyric lyric: song.lyrics) {
			for (Verse verse: lyric.verses) {
				for (String line: verse.lines) {
					if (find(line, m)) return true;
				}
			}
		}
		return false;
	}
	
	private static boolean find(CharSequence s, Matcher m) {
		m.reset(s);
		return m.find();
	}
}
