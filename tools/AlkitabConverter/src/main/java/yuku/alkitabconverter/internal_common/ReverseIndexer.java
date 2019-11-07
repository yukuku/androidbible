package yuku.alkitabconverter.internal_common;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.TextDb;
import yuku.bintex.BintexWriter;

public class ReverseIndexer {
	static final String TAG = ReverseIndexer.class.getSimpleName();

	public final static Charset ascii = Charset.forName("ascii");
	public final static Charset utf8 = Charset.forName("utf8");

	public static void createReverseIndex(File outDir, String prefix, TextDb teksDb) {
		Pattern p_word = Pattern.compile("[A-Za-z]+(?:[-'][A-Za-z]+)*");

		Map<String, Set<Integer>> map = new TreeMap<>(new Comparator<String>() {
			@Override public int compare(String o1, String o2) {
				int lenc = o1.length() - o2.length();
				if (lenc == 0) {
					return o1.compareTo(o2);
				} else {
					return lenc;
				}
			}
		});
		
		{
			int lid = 0;
			for (Rec rec: teksDb.toRecList()) {
				lid++;
				
				String text = Normalizer.normalize(rec.text, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
				text = text.toLowerCase();
				Matcher m = p_word.matcher(text);
				while (m.find()) {
					String word = m.group();
					Set<Integer> locations = map.get(word);
					if (locations == null) {
						locations = new TreeSet<>();
						map.put(word, locations);
					}
					locations.add(lid);
				}
			}
			System.out.println("Last lid = " + lid);
		}
		
		int maxwordlen = 0;
		for (Map.Entry<String, Set<Integer>> e: map.entrySet()) {
			String word = e.getKey();
			System.out.println("word " + word + " lids=" + e.getValue());
			if (word.length() > maxwordlen) maxwordlen = word.length();
		}
		
		System.out.println("Number of words: " + map.size());
		System.out.println("Longest word: " + maxwordlen);
		
		int stat_lid_absolute = 0;
		int stat_lid_delta = 0;
		
		try {
			BintexWriter bw = new BintexWriter(new FileOutputStream(new File(outDir, String.format("%s_revindex_bt.bt", prefix))));

			// :: int word_count
			bw.writeInt(map.size());
			
			// split based on word length
			for (int i = 1; i <= maxwordlen; i++) {
				Map<String, Set<Integer>> lenmap = new TreeMap<>();
				for (Map.Entry<String, Set<Integer>> e: map.entrySet()) {
					String word = e.getKey();
					if (i == word.length()) {
						lenmap.put(word, e.getValue());
					}
				}
				
				int cnt = lenmap.size();
				System.out.println("Words with length " + i + ": " + cnt);
				
				if (cnt != 0) {
					// :: uint8 word_len
					// :: int word_by_len_count
					bw.writeUint8(i);
					bw.writeInt(cnt);
					
					for (Map.Entry<String, Set<Integer>> e: lenmap.entrySet()) {
						String word = e.getKey();
						Set<Integer> lids = e.getValue();
						
						// :: byte[word_len] word
						// :: uint16 lid_count
						bw.writeRaw(word.getBytes(ascii));
						bw.writeUint16(lids.size());
						
						int last_lid = 0;
						for (int lid: lids) {
							int delta = lid - last_lid;
							if (delta <= 0x7f) {
								bw.writeUint8(delta);
								stat_lid_delta++;
							} else {
								bw.writeChar((char) (0x8000 | lid));
								stat_lid_absolute++;
							}
							
							last_lid = lid;
						}
					}
				}
			}
			
			bw.close();
			
			System.out.println("Lid written using delta = " + stat_lid_delta);
			System.out.println("Lid written using absolute = " + stat_lid_absolute);
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}			
}

