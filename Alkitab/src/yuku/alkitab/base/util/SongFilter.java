package yuku.alkitab.base.util;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import yuku.alkitab.base.model.SongInfo;
import yuku.kpri.model.Lyric;
import yuku.kpri.model.Song;
import yuku.kpri.model.Verse;


public class SongFilter {
	public static final String TAG = SongFilter.class.getSimpleName();

	public static List<SongInfo> filterSongInfoByString(List<SongInfo> songInfos, String filter_string) {
		List<SongInfo> res = new ArrayList<SongInfo>();
		
		if (filter_string == null) {
			res.addAll(songInfos);
		} else {
			Pattern[] ps = extractPatterns(filter_string);
			
			for (SongInfo songInfo: songInfos) {
				int matches = 0;
				for (int i = 0; i < ps.length; i++) {
					if (match(songInfo, ps[i])) matches++; 
				}
				if (matches == ps.length) res.add(songInfo);
			}
		}
		
		return res;		
	}
	
	public static List<Song> filterSongByString(List<Song> songs, String filter_string) {
		List<Song> res = new ArrayList<Song>();
		
		if (filter_string == null) {
			res.addAll(songs);
		} else {
			Pattern[] ps = extractPatterns(filter_string);
			
			for (Song song: songs) {
				int matches = 0;
				for (int i = 0; i < ps.length; i++) {
					if (match(song, ps[i])) matches++; 
				}
				if (matches == ps.length) res.add(song);
			}
		}
		
		return res;
	}
	
	private static Pattern[] extractPatterns(String filter_string) {
		String[] splits = TextUtils.split(filter_string, "\\s+");
		Pattern[] ps = new Pattern[splits.length];
		for (int i = 0; i < splits.length; i++) {
			ps[i] = Pattern.compile(Pattern.quote(splits[i]), Pattern.CASE_INSENSITIVE); 
		}
		return ps;
	}
	
	private static boolean match(SongInfo song, Pattern p) {
		Matcher m = p.matcher("");
		
		if (find(song.code, m)) return true;
		if (find(song.title, m)) return true;
		if (song.title_original != null && find(song.title_original, m)) return true;
		
		return false;
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
