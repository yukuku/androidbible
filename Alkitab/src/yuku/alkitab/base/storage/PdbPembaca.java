package yuku.alkitab.base.storage;

import java.io.IOException;
import java.util.ArrayList;

import yuku.alkitab.base.model.*;
import android.content.Context;
import android.util.Log;

import com.compactbyte.android.bible.PDBFileStream;
import com.compactbyte.bibleplus.reader.*;

public class PdbPembaca implements Pembaca {
	public static final String TAG = PdbPembaca.class.getSimpleName();
	
	private BiblePlusPDB pdb;

	public PdbPembaca() {
	}
	
	@Override
	public Kitab[] bacaInfoKitab(Context context, Edisi edisi) {
		try {
			pdb = new BiblePlusPDB(new PDBFileStream(edisi.judul), null, null);
			pdb.loadVersionInfo();
			pdb.loadWordIndex();
			
			ArrayList<Kitab> xkitab = new ArrayList<Kitab>();
			
			int nkitab = pdb.getBookCount();
			for (int i = 0; i < nkitab; i++) {
				BookInfo bookInfo = pdb.getBook(i);
				bookInfo.openBook();
				
				Kitab k = new Kitab();
				k.file = null;
				k.judul = bookInfo.getFullName();
				k.nama = bookInfo.getShortName();
				k.npasal = bookInfo.getChapterCount();
				k.pos = i;
				
				int npasal = bookInfo.getChapterCount();
				int[] nayat = new int[npasal];
				for (int j = 0; j < npasal; j++) {
					nayat[j] = bookInfo.getVerseCount(j + 1);
				}
				k.nayat = nayat;
				
				xkitab.add(k);
			}
			return xkitab.toArray(new Kitab[xkitab.size()]);
		} catch (IOException e) {
			Log.e(TAG, "Eror di bacaInfoKitab", e);
			return null;
		}
	}
	
	@Override
	public String[] muatTeks(Context context, Edisi edisi, Kitab kitab, int pasal_1, boolean janganPisahAyat, boolean hurufKecil) {
		try {
			BookInfo bookInfo = pdb.getBook(kitab.pos);
			bookInfo.openBook();
			int nayat = bookInfo.getVerseCount(pasal_1);
			
			String[] xayat = new String[nayat];
			for (int i = 0; i < nayat; i++) {
				xayat[i] = bookInfo.getVerse(pasal_1, i + 1);
			}
			
			if (hurufKecil) {
				for (int i = 0; i < nayat; i++) {
					xayat[i] = xayat[i].toLowerCase();
				}
			}
			
			if (janganPisahAyat) {
				StringBuilder sb = new StringBuilder(5000);
				for (int i = 0; i < nayat; i++) {
					sb.append(xayat[i]).append('\n');
				}
				return new String[] {sb.toString()};
			} else {
				return xayat;
			}
		} catch (IOException e) {
			Log.e(TAG, "Eror di muatTeks", e);
			return null;
		}
	}

	@Override
	public IndexPerikop bacaIndexPerikop(Context context, Edisi edisi) {
		return null;
	}

	@Override
	public int muatPerikop(Context context, Edisi edisi, int kitab, int pasal, int[] xari, Blok[] xblok, int max) {
		return 0;
	}
}
