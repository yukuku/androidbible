package yuku.alkitab.base.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryTokenizer {
	public static final String TAG = QueryTokenizer.class.getSimpleName();

	static Pattern oneToken = Pattern.compile("(\\+?)((?:\".*?\"|\\S)+)");

	/**
	 * Convert a query string into tokens. Takes care of the quotes.
	 * A single word without quotes means: look for words that contain it. So 'word' matches 'sword'.
	 * A single word between quotes means: look for the exact word (aka whole-word). So '"word"' only matches 'word', not 'words' or 'sword'
	 * Multiple words between quotes: look for the exact ordering of those words, and all of them needs to be exact. So '"mama papa"' matches 'mama papa' but not 'papa mama' or 'mamas papas' or 'mama papaya'
	 *
	 * Examples:
	 *   a b => 'a', 'b'
	 *   "a b" c => '+a b', 'c'
	 *   a"bc"d => 'abcd'
	 *   "a bc" => '+a bc'
	 *   "a" bc => '+a', 'bc'
	 *   "a+b" => '+a+b'
	 *
	 * For compatibility, this also accepts + sign at the beginning of a word or quoted phrase to indicate whole-word matches.
	 *
	 * @return list of tokens, starting with the character '+' if it is to be matched in a whole-word/whole-phrase manner.
	 */
	public static String[] tokenize(String query) {
		List<String> raw_tokens = new ArrayList<>();

		Matcher matcher = QueryTokenizer.oneToken.matcher(query.toLowerCase(Locale.getDefault()));
		while (matcher.find()) {
			raw_tokens.add(matcher.group(1) + matcher.group(2));
		}

		//# process raw tokens
		List<String> processed = new ArrayList<>(raw_tokens.size());
		for (String raw_token : raw_tokens) {
			if (isPlussedToken(raw_token)) {
				String tokenWithoutPlus = tokenWithoutPlus(raw_token);
				if (tokenWithoutPlus.length() > 0) {
					processed.add("+" + tokenWithoutPlus.replace("\"", ""));
				}
			} else {
				if (raw_token.length() > 2 && raw_token.startsWith("\"") && raw_token.endsWith("\"")) {
					processed.add("+" + raw_token.replace("\"", ""));
				} else {
					processed.add(raw_token.replace("\"", ""));
				}
			}
		}

		return processed.toArray(new String[processed.size()]);
	}

	public static boolean isPlussedToken(String token) {
		return (token.startsWith("+"));
	}

	public static String tokenWithoutPlus(String token) {
		int pos = 0;
		int len = token.length();
		while (true) {
			if (pos >= len) break;
			if (token.charAt(pos) == '+') {
				pos++;
			} else {
				break;
			}
		}
		if (pos == 0) return token;
		return token.substring(pos);
	}

	public static Matcher[] matcherizeTokens(String[] tokens) {
		final Matcher[] res = new Matcher[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			final String token = tokens[i];
			if (isPlussedToken(token)) {
				res[i] = Pattern.compile("\\b" + Pattern.quote(tokenWithoutPlus(token)) + "\\b", Pattern.CASE_INSENSITIVE).matcher("");
			} else {
				res[i] = Pattern.compile(Pattern.quote(token), Pattern.CASE_INSENSITIVE).matcher("");
			}
		}
		return res;
	}

	static boolean isMultiwordToken(String token) {
		int start = 0;
		if (isPlussedToken(token)) {
			start = 1;
		}
		for (int i = start, len = token.length(); i < len; i++) {
			char c = token.charAt(i);
			if (! (Character.isLetter(c) || ((c=='-' || c=='\'') && i > start && i < len-1))) {
				return true;
			}
		}
		return false;
	}

	static Pattern pattern_letters = Pattern.compile("[\\p{javaLetter}'-]+");

	/**
	 * For tokens such as "abc.,- def", which will be re-tokenized to "abc" "def"
	 */
	static List<String> tokenizeMultiwordToken(String token) {
		List<String> res = new ArrayList<>();
		Matcher m = pattern_letters.matcher(token);
		while (m.find()) {
			res.add(m.group());
		}
		return res;
	}
}