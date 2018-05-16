package yuku.alkitab.model;

import android.os.Parcel;
import android.os.Parcelable;
import yuku.alkitab.util.IntArrayList;


public class Book implements Parcelable {
	// Make sure to update parcelable methods
	public int bookId;
	public String shortName;
	public int chapter_count;
	public int[] verse_counts;
	public String abbreviation;

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(this.bookId);
		dest.writeString(this.shortName);
		dest.writeInt(this.chapter_count);
		dest.writeIntArray(this.verse_counts);
		dest.writeString(this.abbreviation);
	}

	public Book() {
	}

	Book(Parcel in) {
		this.bookId = in.readInt();
		this.shortName = in.readString();
		this.chapter_count = in.readInt();
		this.verse_counts = in.createIntArray();
		this.abbreviation = in.readString();
	}

	public static final Parcelable.Creator<Book> CREATOR = new Parcelable.Creator<Book>() {
		public Book createFromParcel(Parcel source) {
			return new Book(source);
		}

		public Book[] newArray(int size) {
			return new Book[size];
		}
	};

	public String reference(int chapter_1) {
		return reference(this.shortName, chapter_1);
	}

	public String reference(int chapter_1, int verse_1) {
		return reference(this.shortName, chapter_1, verse_1);
	}

	public static String reference(CharSequence bookName, int chapter_1) {
		return bookName + " " + chapter_1;
	}

	public static String reference(CharSequence bookName, int chapter_1, int verse_1) {
		return bookName + " " + chapter_1 + ":" + verse_1;
	}

	public CharSequence reference(int chapter_1, IntArrayList verses_1) {
		StringBuilder sb = new StringBuilder(this.shortName); 
		sb.append(' ').append(chapter_1);
		if (verses_1 == null || verses_1.size() == 0) {
			return sb;
		}
		sb.append(':');
		writeVerseRange(verses_1, sb);
		return sb;
	}

	public static void writeVerseRange(IntArrayList verses_1, StringBuilder sb) {
		int origLen = sb.length();
		int lastVerse_1 = 0;
		int beginVerse_1 = 0;
		
		for (int i = 0; i < verses_1.size(); i++) {
			int verse_1 = verses_1.get(i);
			
			if (lastVerse_1 == 0) {
				// not exist yet, pass
			} else if (lastVerse_1 == verse_1 - 1) {
				// still continuation, store the beginning
				if (beginVerse_1 == 0) beginVerse_1 = lastVerse_1;
			} else {
				// just jumped
				if (beginVerse_1 != 0) {
					sb.append(origLen == sb.length()? "": ", ").append(beginVerse_1).append('-').append(lastVerse_1);
					beginVerse_1 = 0;
				} else {
					sb.append(origLen == sb.length()? "": ", ").append(lastVerse_1);
				}
			}
			
			lastVerse_1 = verses_1.get(i);
		}
		
		// drain the remainings
		if (beginVerse_1 != 0) {
			sb.append(origLen == sb.length()? "": ", ").append(beginVerse_1).append('-').append(lastVerse_1);
			//noinspection UnusedAssignment
			beginVerse_1 = 0; // no need, only to make it consitent with above
		} else {
			sb.append(origLen == sb.length()? "": ", ").append(lastVerse_1);
		}
	}

	@Override
	public String toString() {
		return "Book{" +
			"bookId=" + bookId +
			", shortName='" + shortName + '\'' +
			'}';
	}
}
