package yuku.alkitab.base.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryTokenizer {
	public static final String TAG = QueryTokenizer.class.getSimpleName();
	
	static Pattern oneToken = Pattern.compile("(\\+?)((?:\".*?\"|\\S)+)");

	/**
	 * Convert a query string into tokens. Takes care of quotes and plus signs.
	 * Put quotes between words to make it one token, and optionally put plus sign before a token, to make it "whole phrase"
	 * 
	 * Examples:
	 *   a b => 'a', 'b'
	 *   "a b" c => 'a b', 'c'
	 *   a"bc"d => 'abcd' 
	 *   +"a bc" => '+abc'
	 *   +a bc => '+a', 'bc'
	 *   +a+b => '+a+b'
	 *   
	 * @return tokens with plus still intact but no quotes.
	 */
	public static String[] tokenize(String query) {
		List<String> raw_tokens = new ArrayList<String>();
		
		Matcher matcher = QueryTokenizer.oneToken.matcher(query);
		while (matcher.find()) {
			raw_tokens.add(matcher.group(1) + matcher.group(2));
		}
		
		//# process raw tokens
		List<String> processed = new ArrayList<String>(raw_tokens.size());
		for (int i = 0, len = raw_tokens.size(); i < len; i++) {
			String token = raw_tokens.get(i);
			if (token.length() > 0 && QueryTokenizer.tokenWithoutPlus(token).length() > 0) {
				processed.add(token.replace("\"", "")); // remove quotes
			}
		}
		return processed.toArray(new String[processed.size()]);
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
}
