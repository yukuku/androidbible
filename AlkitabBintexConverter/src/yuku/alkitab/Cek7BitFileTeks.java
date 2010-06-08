package yuku.alkitab;

import java.io.FileInputStream;

public class Cek7BitFileTeks {
	public static void main(String[] args) throws Exception {
		new Cek7BitFileTeks().u(args);
	}
	
	public void u(String[] args) throws Exception {
		FileInputStream fis = new FileInputStream("/Users/Yuku/android/Alkitab/prog/b_indonesian_baru.txt");
		int len = fis.available();
		
		System.out.println("avail = " + len);
		
		for (int i = 0; i < len; i++) {
			int c = fis.read();
			if (c >= 0x80) {
				throw new RuntimeException("ada byte " + c + " di pos " + i);
			} else if (c < 0) {
				throw new RuntimeException("eof kecepetan");
			}
		}
	}
}
