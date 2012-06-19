package yuku.alkitab.base.model;

import yuku.kpri.model.Song;


/** A concise record of a song. The field song may not be filled if it's not yet loaded */
public class SongInfo {
	public String bookName;
	public String code;
	public String title;
	public String title_original;
	public Song song;
	
	public SongInfo(String bookName, String code, String title, String title_original) {
		this.bookName = bookName;
		this.code = code;
		this.title = title;
		this.title_original = title_original;
	}
}
