
package yuku.androidcrypto;

import java.io.*;
import java.security.*;

public class Digester {
	public static byte[] digest(DigestType type, byte[] data) {
		MessageDigest md = type.getMessageDigest();
		md.update(data);
		return md.digest();
	}
	
	/**
	 * String encoded in utf8 first
	 */
	public static byte[] digest(DigestType type, String data) {
		return digest(type, utf8Encode(data));
	}
	
	public static byte[] digestFile(DigestType type, File file) {
		BufferedInputStream is = null;
		try {
			is = new BufferedInputStream(new FileInputStream(file), 64*1024);
			byte[] buf = new byte[8192];
			
			MessageDigest md = type.getMessageDigest();
			while (true) {
				int read = is.read(buf);
				if (read < 0) break;
				md.update(buf, 0, read);
			}
			return md.digest();
		} catch (IOException e) {
			return null;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ignored) {
				}
			}
		}
	}
	
	public static byte[] utf8Encode(String s) {
		try {
			return s.getBytes("utf-8"); //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}
	
	public static String toHex(byte[] a) {
		if (a == null) return null;

		char[] d = new char[a.length * 2];
		int pos = 0;
		
		for (byte b: a) {
			int h = (b & 0xf0) >> 4;
			int l = b & 0x0f;
			d[pos++] = (char) (h < 10? ('0' + h): ('a' + h - 10)); 
			d[pos++] = (char) (l < 10? ('0' + l): ('a' + l - 10)); 
		}
		
		return new String(d);
	}
}
