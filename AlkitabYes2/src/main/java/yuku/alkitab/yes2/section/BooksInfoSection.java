package yuku.alkitab.yes2.section;

import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.alkitab.yes2.model.Yes2Book;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexReader;
import yuku.bintex.BintexWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BooksInfoSection extends SectionContent implements SectionContent.Writer {
	public static final String SECTION_NAME = "booksInfo";
	
	public List<Yes2Book> yes2Books;

	public BooksInfoSection() {
		super(SECTION_NAME);
	}
	
	@Override public void write(RandomOutputStream output) throws IOException {
		BintexWriter bw = new BintexWriter(output);
		
		// int book_count
		bw.writeInt(yes2Books.size());
		
		// Book[book_count]
		for (Yes2Book yes2Book: yes2Books) {
			yes2Book.toBytes(bw);
		}
	}
	
	public static class Reader implements SectionContent.Reader<BooksInfoSection> {
		@Override public BooksInfoSection read(RandomInputStream input) throws Exception {
			BintexReader br = new BintexReader(input);
			
			BooksInfoSection res = new BooksInfoSection();
			int book_count = br.readInt();
			res.yes2Books = new ArrayList<>(book_count);
			for (int i = 0; i < book_count; i++) {
				res.yes2Books.add(Yes2Book.fromBytes(br));
			}
			return res;
		}
	}
}
