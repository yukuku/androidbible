package yuku.alkitab.yes2.section;

import java.util.ArrayList;
import java.util.List;

import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.model.YesBook;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexReader;
import yuku.bintex.BintexWriter;

public class BooksInfo implements SectionContent, SectionContent.Writer {
	public List<YesBook> yesBooks;

	@Override public void toBytes(BintexWriter writer) throws Exception {
		int c = 0;
		
		for (YesBook yesBook: yesBooks) {
			if (yesBook != null) {
				c++;
			}
		}

		// int book_count
		writer.writeInt(c);
		
		// Book[book_count]
		for (YesBook yesBook: yesBooks) {
			if (yesBook != null) {
				yesBook.toBytes(writer);
			}
		}
	}
	
	public static class Reader implements SectionContent.Reader<BooksInfo> {
		@Override public BooksInfo toSection(RandomInputStream input) throws Exception {
			BintexReader br = new BintexReader(input);
			
			BooksInfo res = new BooksInfo();
			int book_count = br.readInt();
			res.yesBooks = new ArrayList<YesBook>(book_count);
			for (int i = 0; i < book_count; i++) {
				res.yesBooks.add(YesBook.fromBytes(br));
			}
			return res;
		}
	}
}
