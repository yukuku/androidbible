package yuku.alkitab.base.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
	 * @return List of tokens, starting with the character '+' if it is to be matched in a whole-word/whole-phrase manner.
	 * No tokens will be an empty string or "+" (just a plus sign). After the optional '+', there will not be another '+'.
	 */
	public static String[] tokenize(String query) {
		final List<String> raw_tokens = new ArrayList<>();

		final Matcher matcher = QueryTokenizer.oneToken.matcher(query.toLowerCase(Locale.getDefault()));
		while (matcher.find()) {
			raw_tokens.add(matcher.group(1) + matcher.group(2));
		}

		// process raw tokens
		final List<String> processed = new ArrayList<>(raw_tokens.size());
		for (String raw_token : raw_tokens) {
			boolean plussed = false;

			while (true) {
				if (raw_token.length() >= 1 && raw_token.charAt(0) == '+') {
					// prefixed with '+'
					plussed = true;
					raw_token = raw_token.substring(1);
				} else if (raw_token.length() >= 2 && raw_token.charAt(0) == '"' && raw_token.charAt(raw_token.length() - 1) == '"') {
					// surrounded by quotes
					plussed = true;
					raw_token = raw_token.substring(1, raw_token.length() - 1);
				} else if (raw_token.length() >= 2 && raw_token.charAt(0) == '"') {
					// opening quote is present, but no closing quote. This is still considered as a complete quoted token.
					plussed = true;
					raw_token = raw_token.substring(1);
				} else {
					break;
				}
			}

			if (raw_token.length() > 0) {
				processed.add(plussed ? "+" + raw_token : raw_token);
			}
		}

		return processed.toArray(new String[processed.size()]);
	}

	public static boolean isPlussedToken(String token) {
		return token.length() >= 1 && token.charAt(0) == '+';
	}

	/**
	 * Removes a single '+' from the <code>token</code> if exists.
	 * @param token may start or not start with '+'
	 */
	public static String tokenWithoutPlus(@NonNull String token) {
		if (token.length() >= 1) {
			if (token.charAt(0) == '+') {
				return token.substring(1);
			}
		}
		return token;
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

	static Pattern pattern_letters = Pattern.compile("[\\p{javaLetterOrDigit}'-]+");

	/**
	 * For tokens such as "abc.,- def123", which will be re-tokenized to "abc" "def123"
	 * @return null if the token is not a multiword token (i.e. not an array with 1 element!).
	 */
	@Nullable static String[] tokenizeMultiwordToken(String token) {
		List<String> res = null;
		final Matcher m = pattern_letters.matcher(token);
		while (m.find()) {
			if (res == null) {
				res = new ArrayList<>();
			}
			res.add(m.group());
		}

		if (res == null || res.size() <= 1) {
			return null;
		}

		return res.toArray(new String[res.size()]);
	}
}