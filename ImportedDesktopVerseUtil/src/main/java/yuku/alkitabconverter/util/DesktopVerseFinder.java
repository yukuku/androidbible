package yuku.alkitabconverter.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DesktopVerseFinder {
	public interface DetectorListener {
		/**
		 * A verse reference is detected.
		 * @param start Start position of the found verse reference
		 * @param end End position of the found verse reference
		 * @param verse The string containing verse reference
		 * @return true if you want to continue
		 */
		boolean onVerseDetected(int start, int end, String verse);

		/**
		 * We have scanned till the end of text and found no more verse reference.
		 */
		void onNoMoreDetected();
	}
	
	// this array contains books that start with number, ex: 1 Kor
	// "the" is removed from here, because texts like "Rom 10:16 the xyz" is linked as "<a>Rom 10:1</a>6 the xyz"
	static final String nofollow = "ch|chr|chron|chronicles|co|cor|corinthians|jhn|jn|jo|joh|john|kgs|ki|kin|kings|kor|korintus|pe|pet|peter|petrus|ptr|raj|raja|raja-raja|sa|sam|samuel|taw|tawarikh|tes|tesalonika|th|thes|thess|thessalonians|ti|tim|timothy|timotius|yoh|yohanes";

	static final String WHITESPACE_NON_NEWLINE_CHAR = "[ \\t\\x0B\\f]";

	// this array contains all of book names, english and indonesian
	static final String bookNames = (
		"genesis|gen|ge|gn|exodus|exod|exo|ex|leviticus|lev|lv|le|numbers|num|nmb|nu|deuteronomy|deut|deu|dt|de|joshua|josh|jos|judges|judg|jdg|ruth|rut|rth|ru|1 samuel|1samuel|1 sam|1sam|1 sa|1sa|i samuel|i sam|i sa|2 samuel|2samuel|2 sam|2sam|2 sa|2sa|ii samuel|ii sam|ii sa|1 kings|1kings|1 kin|1kin|1 kgs|1kgs|1 ki|1ki|i kings|i kin|i kgs|i ki|2 kings|2kings|2 kin|2kin|2 kgs|2kgs|2 ki|2ki|ii kings|ii kin|ii kgs|ii ki|1 chronicles|1chronicles|1 chron|1chron|1 chr|1chr|1 ch|1ch|i chronicles|i chron|i chr|i ch|2 chronicles|2chronicles|2 chron|2chron|2 chr|2chr|2 ch|2ch|ii chronicles|ii chron|ii chr|ii ch|ezra|ezr|nehemiah|neh|nh|ne|nehemia|esther|esth|est|es|ester|job|jb|psalms|psalm|psa|pss|ps|proverbs|proverb|prov|pro|pr|ecclesiastes|eccl|ecc|ec|songs of solomon|songsofsolomon|song of solomon|songofsolomon|song of songs|songofsongs|songs|song|son|sos|so|isaiah|isa|is|jeremiah|jer|je|lamentations|lam|la|ezekiel|ezek|eze|daniel|dan|dn|da|hosea|hos|ho|joel|joe|yl|amos|amo|am|obadiah|oba|ob|jonah|jon|micah|mikha|mic|mi|nahum|nah|na|habakkuk|habakuk|hab|zephaniah|zeph|zep|haggai|hagai|hag|zechariah|zech|zec|za|malachi|mal|matthew|mathew|matt|mat|mt|markus|mark|mar|mrk|mr|mk|luke|luk|lu|lk|john|joh|jhn|jn|acts of the apostles|actsoftheapostles|acts|act|ac|romans|rom|rm|ro|1 corinthians|1corinthians|1 cor|1cor|1 co|1co|i corinthians|i cor|i co|2 corinthians|2corinthians|2 cor|2cor|2 co|2co|ii corinthians|ii cor|ii co|galatians|galatia|gal|ga|ephesians|eph|ep|phillippians|philippians|phill|phil|phi|php|ph|colossians|col|co|1 thessalonians|1thessalonians|1 thess|1thess|1 thes|1thes|1 the|1the|1 th|1th|i thessalonians|i thess|i thes|i the|i th|2 thessalonians|2thessalonians|2 thess|2thess|2 thes|2thes|2 the|2the|2 th|2th|ii thessalonians|ii thess|ii thes|ii the|ii th|1 timothy|1timothy|1 tim|1tim|1 ti|1ti|i timothy|i tim|i ti|2 timothy|2timothy|2 tim|2tim|2 ti|2ti|ii timothy|ii tim|ii ti|titus|tit|philemon|phile|phm|hebrews|heb|he|james|jam|jas|jms|ja|jm|1 peter|1peter|1 pet|1pet|1 pe|1pe|i peter|i pet|i pe|1 ptr|1ptr|2 peter|2peter|2 pet|2pet|2 pe|2pe|ii peter|ii pet|ii pe|2 ptr|2ptr|1 john|1john|1 joh|1joh|1 jhn|1jhn|1 jo|1jo|1 jn|1jn|i john|i joh|i jhn|i jo|i jn|2 john|2john|2 joh|2joh|2 jhn|2jhn|2 jo|2jo|2 jn|2jn|ii john|ii joh|ii jhn|ii jo|ii jn|3 john|3john|3 joh|3joh|3 jhn|3jhn|3 jo|3jo|3 jn|3jn|iii john|iii joh|iii jhn|iii jo|iii jn|jude|jud|ju|revelations|revelation|rev|re|rv"
			+ "|"
			+ "kejadian|kej|kel|keluaran|im|imamat|bil|bilangan|ul|ulangan|yos|yosua|hak|hakim-hakim|rut|ru|1 samuel|1samuel|1 sam|1sam|1 sa|1sa|i samuel|i sam|i sa|2 samuel|2samuel|2 sam|2sam|2 sa|2sa|ii samuel|ii sam|ii sa|1 raj|1 raja|1raj|1raja|1 raja-raja|1raja-raja|2 raj|2 raja|2raj|2raja|2 raja-raja|2raja-raja|i raj|i raja|iraj|iraja|i raja-raja|iraja-raja|ii raj|ii raja|iiraj|iiraja|ii raja-raja|iiraja-raja|1 tawarikh|1tawarikh|1 taw|1taw|i tawarikh|i taw|2 tawarikh|2tawarikh|2 taw|2taw|ii tawarikh|ii taw|ezra|ezr|neh|nh|ne|nehemia|est|es|ester|ayub|ayb|ay|mazmur|maz|mzm|amsal|ams|pengkhotbah|pkh|kidung agung|kidungagung|kid|yesaya|yes|yeremia|yer|ratapan|rat|yehezkiel|yeh|hosea|hos|ho|yoel|yl|amos|amo|am|obaja|oba|ob|yunus|yun|mikha|mik|mi|nahum|nah|na|habakkuk|habakuk|hab|zefanya|zef|haggai|hagai|hag|zakharia|za|zak|maleakhi|mal|matius|mat|mt|markus|mark|mar|mrk|mr|mk|lukas|luk|lu|lk|yohanes|yoh|kisah para rasul|kisah rasul|kis|roma|rom|rm|ro|1 korintus|1korintus|1 kor|1kor|2 korintus|2korintus|2 kor|2kor|i korintus|ikorintus|i kor|ikor|ii korintus|iikorintus|ii kor|iikor|galatia|gal|ga|efesus|ef|filipi|flp|fil|kolose|kol|1 tesalonika|1tesalonika|1 tes|1tes|i tesalonika|i tes|2 tesalonika|2tesalonika|2 tes|2tes|ii tesalonika|ii tes|1timotius|1 timotius|1 tim|1tim|1 ti|1ti|i tim|i ti|i timotius|i tim|i ti|2timotius|2 timotius|2 tim|2tim|2 ti|2ti|ii timotius|ii tim|ii ti|titus|tit|filemon|flm|ibrani|ibr|yakobus|yak|1 pet|1pet|1 pe|1pe|1 petrus|1petrus|1 ptr|1ptr|2 pet|2pet|2 pe|2pe|ii peter|ii pet|ii pe|2 petrus|2petrus|2 ptr|2ptr|1 yohanes|1yohanes|1yoh|1 yoh|i yohanes|i yoh|2 yohanes|2yohanes|ii yohanes|ii yoh|2yoh|2 yoh|3 yohanes|3yohanes|3yoh|3 yoh|iii yohanes|iii yoh|yudas|yud|wahyu|why|wah"
	).replace(" ", WHITESPACE_NON_NEWLINE_CHAR + "+"); // no \r or \n allowed in between e.g. "1" and "John" as a book name

	/////////////////////////////////////////// 1 something before (or nothing)
	/////////////////////////////////////////// |    2 complete verse address (book chapter verse)
	/////////////////////////////////////////// |    |3 book name with optional period and spaces after it
	/////////////////////////////////////////// |    | 4 book name                                                 5 numbers (chapter or chapter:verse, with ',' or ';' or 'dan') which is not followed by nofollow
	static final Pattern reg = Pattern.compile("(\\b)(((" + bookNames + ")\\.?" + WHITESPACE_NON_NEWLINE_CHAR + "+)(\\d+(?:(?:" + WHITESPACE_NON_NEWLINE_CHAR + "*(?:,|\\.|:|-|;|dan)" + WHITESPACE_NON_NEWLINE_CHAR + "*)\\d+)*)(?!" + WHITESPACE_NON_NEWLINE_CHAR + "*(?:" + nofollow + ")\\.?\\s))", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

	public static void findInText(CharSequence input, DetectorListener detectorListener) {
		final Matcher match_1 = reg.matcher(input);

		while (match_1.find()) {
			// to solve the problem of "Dan" book
			if (!"dan".equals(match_1.group(4))) {
				int beginVerse = match_1.start(2);
				int endVerse = match_1.end(2);

				// pola yang tertangkap akan di split nodenya untuk disisip tag "a"
				// node2 = node3.splitText(node3.length - (match_1[6].length + match_1[2].length));
				// node3 = node2.splitText(match_1[2].length);

				// newPattern to replace Kej.5:4 pattern become Kej 5:4
				String newPattern = match_1.group(2).replaceFirst("\\.", " ");
				// if there is word "dan" in pattern, "dan" will remove
				if (newPattern.contains("dan")) {
					if (Pattern.matches(".*dan\\s*\\d*:.*", newPattern)) {
						newPattern = newPattern.replaceFirst("dan", ";");
					} else {
						newPattern = newPattern.replaceFirst("dan", ",");
					}
				}

				// to repair false pattern
				newPattern = newPattern.replaceAll(",\\s*;", ";");
				newPattern = newPattern.replaceAll("\\s+", " ");
				newPattern = newPattern.replaceAll("\n+", " ");

				if (! detectorListener.onVerseDetected(beginVerse, endVerse, newPattern)) {
					return;
				}
			}
		}
		
		detectorListener.onNoMoreDetected();
	}
}
