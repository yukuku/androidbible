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
		
		private SongInfo(Type type) {
			this(type, null);
		}
		
		private SongInfo(Type type, String suffix) {
			this.type = type;
			this.suffix = suffix;
		}
		
		public static String tableName() {
			return SongInfo.class.getSimpleName();
		}
	}

	public enum SyncShadow {
		syncSetName(text),
		revno(integer),
		data(blob),
		;

		public final Type type;
		public final String suffix;

		private SyncShadow(Type type) {
			this(type, null);
		}

		private SyncShadow(Type type, String suffix) {
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

		private SyncLog(Type type) {
			this(type, null);
		}

		private SyncLog(Type type, String suffix) {
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
		;

		public final Type type;
		public final String suffix;

		private Devotion(Type type) {
			this(type, null);
		}

		private Devotion(Type type, String suffix) {
			this.type = type;
			this.suffix = suffix;
		}

		public static String tableName() {
			return Devotion.class.getSimpleName();
		}
	}
}
