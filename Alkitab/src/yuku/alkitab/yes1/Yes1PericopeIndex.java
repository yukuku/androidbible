package yuku.alkitab.yes1;

import android.util.Log;

import java.io.IOException;

import yuku.alkitab.base.model.PericopeIndex;
import yuku.bintex.BintexReader;

public class Yes1PericopeIndex extends PericopeIndex {
	public static final String TAG = Yes1PericopeIndex.class.getSimpleName();
	
	public static Yes1PericopeIndex read(BintexReader in) throws IOException {
		Yes1PericopeIndex pi = new Yes1PericopeIndex();
		
		int nentri = in.readInt();
		
		pi.aris = new int[nentri];
		pi.offsets = new int[nentri];
		
		for (int i = 0; i < nentri; i++) {
			pi.aris[i] = in.readInt();
			pi.offsets[i] = in.readInt();
		}
		
		return pi;
	}

	public Yes1PericopeBlock getBlock(BintexReader in, int index) {
		try {
			int ofset = offsets[index];
			int posKini = in.getPos();

			if (posKini > ofset) {
				throw new RuntimeException("posKini " + posKini + " > ofset " + ofset + ", ngaco!!!!"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			in.skip(ofset - posKini);
			
			return Yes1PericopeBlock.read(in);
		} catch (IOException e) {
			Log.e(TAG, "getBlok ngaco", e); //$NON-NLS-1$
			
			return null;
		}
	}
}
