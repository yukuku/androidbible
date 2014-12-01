package yuku.androidcrypto;

import android.util.*;

import java.security.*;

public enum DigestType {
	MD5("MD5"), //$NON-NLS-1$
	SHA1("SHA1"), //$NON-NLS-1$
	SHA256("SHA256"), //$NON-NLS-1$
	SHA512("SHA512"); //$NON-NLS-1$
	
	private static final String TAG = MessageDigest.class.getSimpleName();
	private final String algo;
	
	DigestType(String algo) {
		this.algo = algo;
	}
	
	public MessageDigest getMessageDigest() {
		try {
			return MessageDigest.getInstance(algo);
		} catch (NoSuchAlgorithmException e) {
			Log.e(TAG, "NoSuchAlgorithmException: " + algo, e); //$NON-NLS-1$
			return null;
		}
	}
}
