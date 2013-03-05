package yuku.alkitabconverter.util;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DesktopVerseParser {
	public static final String TAG = DesktopVerseParser.class.getSimpleName();

	///////////////////////////////////// 1 complete verse address (book chapter verse)
	/////////////////////////////////////  2 book name with optional period and spaces after it
	/////////////////////////////////////   3 book name                
	///////////////////////////////////// ... 4 numbers (chapter or chapter:verse, with ',' or ';' or 'dan') which is not followed by nofollow
	static Pattern reg = Pattern.compile("(((kejadian|kej|kel|keluaran|im|imamat|bil|bilangan|ul|ulangan|yos|yosua|hak|hakim-hakim|rut|ru|1\\s+samuel|1samuel|1\\s+sam|1sam|1\\s+sa|1sa|i\\s+samuel|i\\s+sam|i\\s+sa|2\\s+samuel|2samuel|2\\s+sam|2sam|2\\s+sa|2sa|ii\\s+samuel|ii\\s+sam|ii\\s+sa|1\\s+raj|1\\s+raja|1raj|1raja|1\\s+raja-raja|1raja-raja|2\\s+raj|2\\s+raja|2raj|2raja|2\\s+raja-raja|2raja-raja|i\\s+raj|i\\s+raja|iraj|iraja|i\\s+raja-raja|iraja-raja|ii\\s+raj|ii\\s+raja|iiraj|iiraja|ii\\s+raja-raja|iiraja-raja|1\\s+tawarikh|1tawarikh|1\\s+taw|1taw|i\\s+tawarikh|i\\s+taw|2\\s+tawarikh|2tawarikh|2\\s+taw|2taw|ii\\s+tawarikh|ii\\s+taw|ezra|ezr|neh|nh|ne|nehemia|est|es|ester|ayub|ayb|ay|mazmur|maz|mzm|amsal|ams|pengkhotbah|pkh|kidung\\s+agung|kidungagung|kid|yesaya|yes|yeremia|yer|ratapan|rat|yehezkiel|yeh|daniel|dan|dn|hosea|hos|ho|yoel|yl|amos|amo|am|obaja|oba|ob|yunus|yun|mikha|mik|mi|nahum|nah|na|habakkuk|habakuk|hab|zefanya|zef|haggai|hagai|hag|zakharia|za|maleakhi|mal|matius|mat|mt|markus|mark|mar|mrk|mr|mk|lukas|luk|lu|lk|yohanes|yoh|kisah\\s+para\\s+rasul|kisah\\s+rasul|kis|roma|rom|rm|ro|1\\s+korintus|1korintus|1\\s+kor|1kor|2\\s+korintus|2korintus|2\\s+kor|2kor|i\\s+korintus|ikorintus|i\\s+kor|ikor|ii\\s+korintus|iikorintus|ii\\s+kor|iikor|galatia|gal|ga|efesus|ef|filipi|flp|fil|kolose|kol|1\\s+tesalonika|1tesalonika|1\\s+tes|1tes|i\\s+tesalonika|i\\s+tes|2\\s+tesalonika|2tesalonika|2\\s+tes|2tes|ii\\s+tesalonika|ii\\s+tes|1timotius|1\\s+timotius|1\\s+tim|1tim|1\\s+ti|1ti|i\\s+tim|i\\s+ti|i\\s+timotius|i\\s+tim|i\\s+ti|2timotius|2\\s+timotius|2\\s+tim|2tim|2\\s+ti|2ti|ii\\s+timotius|ii\\s+tim|ii\\s+ti|titus|tit|filemon|flm|ibrani|ibr|yakobus|yak|1\\s+pet|1pet|1\\s+pe|1pe|i\\s+peter|i\\s+pet|i\\s+pe|1\\s+petrus|1petrus|1\\s+ptr|1ptr|2\\s+pet|2pet|2\\s+pe|2pe|ii\\s+peter|ii\\s+pet|ii\\s+pe|2\\s+petrus|2petrus|2\\s+ptr|2ptr|1\\s+yohanes|1yohanes|1yoh|1\\s+yoh|i\\s+yohanes|i\\s+yoh|2\\s+yohanes|2yohanes|ii\\s+yohanes|ii\\s+yoh|2yoh|2\\s+yoh|3\\s+yohanes|3yohanes|3yoh|3\\s+yoh|iii\\s+yohanes|iii\\s+yoh|yudas|yud|wahyu|why)(?:\\.?\\s+|\\.))(\\d+(?:(?:-|:|(?:;\\s*\\d+:\\s*)|,|\\.|\\d|dan|\\s)+\\d+)?))", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	
	static Pattern numberRangeSplitter = Pattern.compile("\\s*(;|,|dan)\\s*" /* NOT case insensitive */);
	
	static Pattern numberStartEndSplitter = Pattern.compile("\\s*--?\\s*");

	static Pattern chapterVerse = Pattern.compile("(\\d+)\\s*[:.]\\s*(\\d+)");

	static Pattern numbersOnly = Pattern.compile("[0-9]+");
	
	static String[] orderedBooks = {
		"kejadian|kej",
		"kel|keluaran",
		"im|imamat",
		"bil|bilangan",
		"ul|ulangan",
		"yos|yosua",
		"hak|hakim-hakim",
		"rut|ru",
		"1 samuel|1samuel|1 sam|1sam|1 sa|1sa|i samuel|i sam|i sa",
		"2 samuel|2samuel|2 sam|2sam|2 sa|2sa|ii samuel|ii sam|ii sa",
		"1 raj|1 raja|1raj|1raja|1 raja-raja|1raja-raja|i raj|i raja|iraj|iraja|i raja-raja|iraja-raja",
		"2 raj|2 raja|2raj|2raja|2 raja-raja|2raja-raja|ii raj|ii raja|iiraj|iiraja|ii raja-raja|iiraja-raja",
		"1 tawarikh|1tawarikh|1 taw|1taw|i tawarikh|i taw",
		"2 tawarikh|2tawarikh|2 taw|2taw|ii tawarikh|ii taw",
		"ezra|ezr",
		"neh|nh|ne|nehemia",
		"est|es|ester",
		"ayub|ayb|ay",
		"mazmur|maz|mzm",
		"amsal|ams",
		"pengkhotbah|pkh",
		"kidung agung|kidungagung|kid",
		"yesaya|yes",
		"yeremia|yer",
		"ratapan|rat",
		"yehezkiel|yeh",
		"daniel|dan|dn",
		"hosea|hos|ho",
		"yoel|yl",
		"amos|amo|am",
		"obaja|oba|ob",
		"yunus|yun",
		"mikha|mik|mi",
		"nahum|nah|na",
		"habakkuk|habakuk|hab",
		"zefanya|zef",
		"haggai|hagai|hag",
		"zakharia|za",
		"maleakhi|mal",
		"matius|mat|mt",
		"markus|mark|mar|mrk|mr|mk",
		"lukas|luk|lu|lk",
		"yohanes|yoh",
		"kisah para rasul|kisah rasul|kis",
		"roma|rom|rm|ro",
		"1 korintus|1korintus|1 kor|1kor|i korintus|ikorintus|i kor|ikor",
		"2 korintus|2korintus|2 kor|2kor|ii korintus|iikorintus|ii kor|iikor",
		"galatia|gal|ga",
		"efesus|ef",
		"filipi|flp|fil",
		"kolose|kol",
		"1 tesalonika|1tesalonika|1 tes|1tes|i tesalonika|i tes",
		"2 tesalonika|2tesalonika|2 tes|2tes|ii tesalonika|ii tes",
		"1timotius|1 timotius|1 tim|1tim|1 ti|1ti|i tim|i ti|i timotius|i tim|i ti",
		"2timotius|2 timotius|2 tim|2tim|2 ti|2ti|ii timotius|ii tim|ii ti",
		"titus|tit",
		"filemon|flm",
		"ibrani|ibr",
		"yakobus|yak",
		"1 pet|1pet|1 pe|1pe|i peter|i pet|i pe|1 petrus|1petrus|1 ptr|1ptr",
		"2 pet|2pet|2 pe|2pe|ii peter|ii pet|ii pe|2 petrus|2petrus|2 ptr|2ptr",
		"1 yohanes|1yohanes|1yoh|1 yoh|i yohanes|i yoh",
		"2 yohanes|2yohanes|ii yohanes|ii yoh|2yoh|2 yoh",
		"3 yohanes|3yohanes|3yoh|3 yoh|iii yohanes|iii yoh",
		"yudas|yud",
		"wahyu|why",
	};
	
	static HashMap<String, Integer> bookNameToId = new HashMap<String, Integer>(512);
	
	static {
		for (int i = 0, len = orderedBooks.length; i < len; i++) {
			for (String bookName: orderedBooks[i].split("\\|")) {
				bookNameToId.put(bookName, i);
			}
		}
	}
	
	/**
	 * If succeeded, will return start-end pairs [start, end, start, end, ...]. Single verses will have the same values for both start and end.
	 * @return null when failed 
	 */
	public static IntArrayList verseStringToAri(String verse) {
		Matcher m = reg.matcher(verse);
		
		if (!m.find()) {
			return null;
		}
		
		String bookName = m.group(3).toLowerCase();
		Integer bookId = bookNameToId.get(bookName);
		if (bookId == null) {
			return null;
		}
		
		int book_0 = bookId;
		boolean singleChapterBook = (book_0 == 30 /* obaja */
			|| book_0 == 56 /* filemon */
			|| book_0 == 62 /* 2yoh */
			|| book_0 == 63 /* 3yoh */
			|| book_0 == 64 /* yudas */
		);
		
		int lastChapter = 0;
		
		int book_0_shifted = book_0 << 16;
		
		IntArrayList res = new IntArrayList();
		
		String numbers = m.group(4);
		String[] ranges = numberRangeSplitter.split(numbers);
		for (String range: ranges) {
			String[] startend = numberStartEndSplitter.split(range);
			if (startend.length == 1) {
				int cv = parseCv(startend[0], singleChapterBook, lastChapter);
				if (cv != 0) {
					res.add(book_0_shifted | cv); // start
					res.add(book_0_shifted | cv); // end same as start
					lastChapter = (cv >> 8) & 0xff;
				}
			} else if (startend.length == 2) {
				int cvStart = parseCv(startend[0], singleChapterBook, lastChapter);
				if (cvStart != 0) {
					int cvEnd; 
					String startend_1_trim = startend[1].trim();
					if (numbersOnly.matcher(startend_1_trim).matches()) { // check for cases like "2:3-14"
						cvEnd = (cvStart & 0xff00) | Integer.parseInt(startend_1_trim);
					} else {
						cvEnd = parseCv(startend[1], singleChapterBook, lastChapter);
					}
					if (cvEnd != 0) {
						if (cvEnd >= cvStart) {
							res.add(book_0_shifted | cvStart);
							res.add(book_0_shifted | cvEnd);
							lastChapter = (cvEnd >> 8) & 0xff;
						}
					}
				}
			}
		}
		
		return res;
	}

	/** Similar to {@link #verseStringToAri(String)} but with shift from TB ari */
	public static IntArrayList verseStringToAriWithShiftTb(String verse) {
		IntArrayList ariRanges = verseStringToAri(verse);
		if (ariRanges == null) return null;
		
		return DesktopShiftTb.shiftFromTb(ariRanges);
	}

	private static int parseCv(String cv, boolean singleChapterBook, int previousChapter) {
		if (numbersOnly.matcher(cv).matches()) { // either c:0 or 1:v
			int n = Integer.parseInt(cv);
			if (singleChapterBook) {
				return 0x0100 | (n & 0xff);
			} else if (previousChapter != 0) {
				return ((previousChapter & 0xff) << 8) | (n & 0xff);
			} else {
				return (n & 0xff) << 8;
			}
		} else {
			Matcher m = chapterVerse.matcher(cv);
			if (m.matches()) {
				int c = Integer.parseInt(m.group(1));
				int v = Integer.parseInt(m.group(2));
				return ((c & 0xff) << 8) | (v & 0xff);
			}
		}
		return 0;
	}
}
