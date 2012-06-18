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
	
	public enum Song {
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
		
		private Song(Type type) {
			this(type, null);
		}
		
		private Song(Type type, String suffix) {
			this.type = type;
			this.suffix = suffix;
		}
		
		public static String tableName() {
			return Song.class.getSimpleName();
		}
	}
}
