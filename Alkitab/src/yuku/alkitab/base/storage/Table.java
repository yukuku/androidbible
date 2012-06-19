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
		title(text, "collate nocase"),
		title_original(text, "collate nocase"),
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
}
