package yuku.alkitab.yes2.model;

import java.util.List;

import yuku.bintex.BintexWriter;

public class BookInfo implements SectionContent {
	public List<Book> books;

	@Override public void toBytes(BintexWriter writer) throws Exception {
		for (Book book: books) {
			if (book != null) {
				book.toBytes(writer);
			}
		}
	}
}
