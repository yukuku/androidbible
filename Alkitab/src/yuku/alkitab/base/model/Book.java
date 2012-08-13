package yuku.alkitab.base.model;


public class Book {
	public int[] nverses;
	public int nchapter;
	public int[] pasal_offset;
	public String nama;
	public String judul;
	public String file;
	public int bookId = -1;
	public int pdbBookNumber;
	/** Hanya dipake di YesPembaca */
	public int offset = -1;

	@Override
	public String toString() {
		return String.format("%s (%d pasal)", judul, nchapter); //$NON-NLS-1$
	}
}
