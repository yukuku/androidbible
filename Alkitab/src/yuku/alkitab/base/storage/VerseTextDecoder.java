package yuku.alkitab.base.storage;


public interface VerseTextDecoder {
	String[] separateIntoVerses(byte[] ba, boolean lowercased);
	String makeIntoSingleString(byte[] ba, boolean lowercased);
}
