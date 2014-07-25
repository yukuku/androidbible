package yuku.alkitab.model;

import yuku.alkitab.model.util.Gid;

import java.util.Date;

public class Marker {

	public long _id;
	public String gid;
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

	private Marker() {}

	/**
	 * Create without _id
	 */
	public static Marker createNewMarker(int ari, Kind kind, String caption, int verseCount, Date createTime, Date modifyTime) {
		final Marker res = new Marker();
		res.gid = Gid.newGid();
		res.ari = ari;
		res.kind = kind;
		res.caption = caption;
		res.verseCount = verseCount;
		res.createTime = createTime;
		res.modifyTime = modifyTime;
		return res;
	}

	public static Marker createEmptyMarker() {
		return new Marker();
	}
}
