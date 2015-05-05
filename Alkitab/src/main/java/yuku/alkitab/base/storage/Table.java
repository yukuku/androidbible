package yuku.alkitab.base.storage;

import static yuku.alkitab.base.storage.Table.Type.blob;
import static yuku.alkitab.base.storage.Table.Type.integer;
import static yuku.alkitab.base.storage.Table.Type.text;

public class Table {
	public static final String TAG = Table.class.getSimpleName();

	public enum Type {
		integer,
		real,
		text,
		blob,
	}
	
	public enum SongInfo {
		bookName(text),
		code(text),
		title(text, "collate nocase"), //$NON-NLS-1$
		title_original(text, "collate nocase"), //$NON-NLS-1$
		ordering(integer),
		dataFormatVersion(integer), 
		data(blob),
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
			return SongInfo.class.getSimpleName();
		}
	}

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
			return SongBookInfo.class.getSimpleName();
		}
	}

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
			return SyncShadow.class.getSimpleName();
		}
	}

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
			return SyncLog.class.getSimpleName();
		}
	}

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
			return Devotion.class.getSimpleName();
		}
	}
}
