package yuku.alkitab.yes1;

import java.io.IOException;

import yuku.alkitab.base.model.Book;
import yuku.bintex.BintexReader;
import yuku.bintex.ValueMap;

public class Yes1Book extends Book {
	public int offset = -1;
	public int[] chapter_offsets;
}
