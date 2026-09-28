package yuku.alkitabconverter.util;


public class Rec implements Comparable<Rec> {
	public int book_1;
	public int chapter_1;
	public int verse_1;
	public String text;
	
	@Override public int compareTo(Rec o) {
		if (this.book_1 != o.book_1) return this.book_1 - o.book_1;
		if (this.chapter_1 != o.chapter_1) return this.chapter_1 - o.chapter_1;
		if (this.verse_1 != o.verse_1) return this.verse_1 - o.verse_1;
		return 0;
	}
}