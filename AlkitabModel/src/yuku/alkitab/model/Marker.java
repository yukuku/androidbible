package yuku.alkitab.model;

import java.util.Date;

public class Marker {

	public long _id;
	public int ari;
	public Kind kind;
	public String caption;
	public int verseCount;
	public Date createTime;
	public Date modifyTime;

	public enum Kind {
		bookmark(1),
		note(2),
		highlight(3),
		;

		public final int code;
		Kind(final int code) {
			this.code = code;
		}

		public static Kind fromCode(int code) {
			for (Kind kind : values()) {
				if (kind.code == code) return kind;
			}
			return null;
		}
	}

	/**
	 * Create without _id
	 */
	public Marker(int ari, Kind kind, String caption, int verseCount, Date createTime, Date modifyTime) {
		this.ari = ari;
		this.kind = kind;
		this.caption = caption;
		this.verseCount = verseCount;
		this.createTime = createTime;
		this.modifyTime = modifyTime;
	}
}
