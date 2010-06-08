package yuku.alkitab;

import java.io.File;
import java.util.Scanner;

public class CekKebenaranMenjorok {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		new CekKebenaranMenjorok().cek("/Users/Yuku/operasi/tb_k19-asli.txt", "/Users/Yuku/operasi/tb_k19-semua-proses.txt");
	}

	private void cek(String nfdari, String nfke) throws Exception {
		Scanner scdari = new Scanner(new File(nfdari));
		Scanner scke = new Scanner(new File(nfke));
		
		int bariske = 0;
		while (true) {
			if (!scdari.hasNextLine()) {
				break;
			}
			bariske++;
			
			String dr = scdari.nextLine();
			String ke = scke.nextLine();
			
			StringBuilder sb = new StringBuilder();
			int pos = 0;
			while (true) {
				char c = ke.charAt(pos++);
				if (c == '@') {
					char d = ke.charAt(pos++);
					if (d == '@' || d == '0' || d == '1' || d == '2' || d == '8') {
						//ok
					} else {
						throw new RuntimeException("karakter setelah @ dapetnya " + d + " di baris " + bariske);
					}
				} else {
					sb.append(c);
				}
				
				if (pos >= ke.length()) {
					break;
				}
			}
			
			if (!sb.toString().equals(dr)) {
				System.out.println("sb: " + sb);
				System.out.println("dr: " + dr);
				//throw new RuntimeException("baris " + bariske + " : beda");
				System.out.println("baris " + bariske + " : beda");
			}
		}
		
		System.out.println("Habis di baris " + bariske);
	}

}
