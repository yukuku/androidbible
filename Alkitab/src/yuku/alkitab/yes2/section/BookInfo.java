package yuku.alkitab.yes2.section;

import java.util.List;

import yuku.alkitab.yes2.model.Book;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexWriter;

public class BookInfo implements SectionContent.Writer {
	public List<Book> books;

	@Override public void toBytes(BintexWriter writer) throws Exception {
		int c = 0;
		
		for (Book book: books) {
			if (book != null) {
				c++;
			}
		}

		// int book_count
		writer.writeInt(c);
		
		// Book[book_count]
		for (Book book: books) {
			if (book != null) {
				book.toBytes(writer);
			}
		}
	}
}
