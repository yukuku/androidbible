package yuku.alkitab.base.storage;

import android.support.annotation.Keep;

import static yuku.alkitab.base.storage.Table.Type.*;

public class Table {
	@Keep
	public enum Type {
		integer,
		real,
		text,
		blob,
	}

	@Keep
	public enum SongInfo {
		bookName(text),
		code(text),
		title(text, "collate nocase"),
		title_original(text, "collate nocase"),
		ordering(integer),
		dataFormatVersion(integer),
		data(blob),
		updateTime(integer),
		;

		public final Type type;
		public final String suffix;

		SongInfo(Type type) {
			this(type, null);
		}

		SongInfo(Type type, String suffix) {
			this.type = type;
			this.suffix = suffix;
		}

		public static String tableName() {
			return "SongInfo";
		}
	}

	@Keep
	public enum SongBookInfo {
		name(text),
		title(text),
		copyright(text),
		;

		public final Type type;
		public final String suffix;

		SongBookInfo(Type type) {
			this(type, null);
		}

		SongBookInfo(Type type, String suffix) {
			this.type = type;
			this.suffix = suffix;
		}

		public static String tableName() {
			return "SongBookInfo";
		}
	}

	@Keep
	public enum SyncShadow {
		syncSetName(text),
		revno(integer),
		data(blob),
		;

		public final Type type;
		public final String suffix;

		SyncShadow(Type type) {
			this(type, null);
		}

		SyncShadow(Type type, String suffix) {
			this.type = type;
			this.suffix = suffix;
		}

		public static String tableName() {
			return "SyncShadow";
		}
	}

	@Keep
	public enum SyncLog {
		createTime(integer),
		kind(integer),
		syncSetName(text),
		params(text),
		;

		public final Type type;
		public final String suffix;

		SyncLog(Type type) {
			this(type, null);
		}

		SyncLog(Type type, String suffix) {
			this.type = type;
			this.suffix = suffix;
		}

		public static String tableName() {
			return "SyncLog";
		}
	}

	@Keep
	public enum Devotion {
		name(text),
		date(text),
		body(text),
		readyToUse(integer),
		touchTime(integer),
		dataFormatVersion(integer),
		;

		public final Type type;
		public final String suffix;

		Devotion(Type type) {
			this(type, null);
		}

		Devotion(Type type, String suffix) {
			this.type = type;
			this.suffix = suffix;
		}

		public static String tableName() {
			return "Devotion";
		}
	}

	@Keep
	public enum PerVersion {
		versionId(text),
		settings(text),
		;

		public final Type type;
		public final String suffix;

		PerVersion(Type type) {
			this(type, null);
		}

		PerVersion(Type type, String suffix) {
			this.type = type;
			this.suffix = suffix;
		}

		public static String tableName() {
			return "PerVersion";
		}
	}
}
