package yuku.alkitab.base.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryTokenizer {
	public static final String TAG = QueryTokenizer.class.getSimpleName();
	
	static Pattern oneToken = Pattern.compile("(\\+?)((?:\".*?\"|\\S)+)"); //$NON-NLS-1$

	/**
	 * Convert a query string into tokens. Takes care of quotes and plus signs.
	 * Put quotes between words to make it one token, and optionally put plus sign before a token, to make it "whole phrase"
	 * 
	 * Examples:
	 *   a b => 'a', 'b'
	 *   "a b" c => 'a b', 'c'
	 *   a"bc"d => 'abcd' 
	 *   +"a bc" => '+a bc'
	 *   +a bc => '+a', 'bc'
	 *   +a+b => '+a+b'
	 *   
	 * @return tokens with plus still intact but no quotes.
	 */
	public static String[] tokenize(String query) {
		List<String> raw_tokens = new ArrayList<String>();

		Matcher matcher = QueryTokenizer.oneToken.matcher(toLowerCase(query));
		while (matcher.find()) {
			raw_tokens.add(matcher.group(1) + matcher.group(2));
		}
		
		//# process raw tokens
		List<String> processed = new ArrayList<String>(raw_tokens.size());
		for (int i = 0, len = raw_tokens.size(); i < len; i++) {
			String token = raw_tokens.get(i);
			if (token.length() > 0 && QueryTokenizer.tokenWithoutPlus(token).length() > 0) {
				processed.add(token.replace("\"", "")); // remove quotes //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return processed.toArray(new String[processed.size()]);
	}

	/**
	 * Implementation of toLowerCase for Latin A-Z only.
	 * This is done because the Bible text is also lowercased using this method for searching
	 * instead of using the more sophisticated {@link String#toLowerCase()} method.
	 */
	static String toLowerCase(final String s) {
		final char[] newString = new char[s.length()];
		for (int i = 0, len = s.length(); i < len; i++) {
			final char c = s.charAt(i);
			if (c >= 'A' && c <= 'Z') {
				newString[i] = (char) (c | 0x20);
			} else {
				newString[i] = c;
			}
		}
		return new String(newString);
	}

	static boolean isPlussedToken(String token) {
		return (token.startsWith("+")); //$NON-NLS-1$
	}

	static String tokenWithoutPlus(String token) {
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
		List<String> res = new ArrayList<String>();
		Matcher m = pattern_letters.matcher(token);
		while (m.find()) {
			res.add(m.group());
		}
		return res;
	}
}
