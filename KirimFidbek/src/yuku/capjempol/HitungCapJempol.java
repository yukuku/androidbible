package yuku.capjempol;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;

import java.io.UnsupportedEncodingException;

public class HitungCapJempol {
	public static final String TAG = HitungCapJempol.class.getSimpleName();
	
	public static String hitung(Context context) {
		String packageName = context.getPackageName();
		PackageInfo packageInfo;
		try {
			packageInfo = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		
		Signature[] signatures = packageInfo.signatures;
		if (signatures == null || signatures.length == 0) return null;
		
		try {
			Signature s = signatures[0];
			
			int yrc_s = yrc1(s.toByteArray(), 0);
			int yrc_p = yrc1(packageName.getBytes("utf-8"), yrc_s);
			
			return String.format("c1:y1:%08x:%08x", yrc_s, yrc_p);
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	private static int yrc1(byte[] bytes, int initial) {
		int res = initial;
		
		// initial must only be 30 bit
		for (byte b: bytes) {
			int n = b & 0xff; // cast to unsigned
			
			// rotate left by 3 bit
			int l = res & 0x07ffffff; // last 27 bit
			int f = res & 0x38000000; // first 3 bit after 2 empty bit
			res = (l << 3) | (f >> 27);
			
			res += n + 1; // might be 31 bit
			res &= 0x3fffffff; // take 30 bit only
		}
		
		return res;
	}
}
