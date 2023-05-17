package yuku.alkitab.base.util;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.SparseBooleanArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

public class SearchEngine {
    static final String TAG = SearchEngine.class.getSimpleName();

    /**
     * Contains processed tokens that is more efficient to be passed in to methods here such as
     * {@link #hilite(CharSequence, ReadyTokens, int)} and {@link #satisfiesTokens(String, ReadyTokens)}.
     */
    public static class ReadyTokens {
        final int token_count;
        final boolean[] hasPlusses;
        /**
         * Already without plusses
         */
        final String[] tokens;
        final String[][] multiwords_tokens;

        public ReadyTokens(final String[] tokens) {
            final int token_count = tokens.length;
            this.token_count = token_count;
            this.hasPlusses = new boolean[token_count];
            this.tokens = new String[token_count];
            this.multiwords_tokens = new String[token_count][];

            for (int i = 0; i < token_count; i++) {
                final String token = tokens[i];
                if (QueryTokenizer.isPlussedToken(token)) {
                    this.hasPlusses[i] = true;

                    final String tokenWithoutPlus = QueryTokenizer.tokenWithoutPlus(token);
                    this.tokens[i] = tokenWithoutPlus;

                    final String[] multiword = QueryTokenizer.tokenizeMultiwordToken(tokenWithoutPlus);
                    if (multiword != null) {
                        this.multiwords_tokens[i] = multiword;
                    }
                } else {
                    this.tokens[i] = token;
                }
            }
        }
    }

    public static IntArrayList searchByGrep(final Version version, final SearchEngineQuery query) {
        String[] tokens = QueryTokenizer.tokenize(query.query_string);

        // sort by word length, then alphabetically
        Arrays.sort(tokens, (object1, object2) -> {
            final int len1 = object1.length();
            final int len2 = object2.length();

            if (len1 > len2) return -1;
            if (len1 == len2) {
                return object1.compareTo(object2);
            }
            return 1;
        });

        // remove duplicates
        {
            final ArrayList<String> atokens = new ArrayList<>();
            String last = null;
            for (String token : tokens) {
                if (!token.equals(last)) {
                    atokens.add(token);
                }
                last = token;
            }
            tokens = atokens.toArray(new String[0]);
            AppLog.d(TAG, "tokens = " + Arrays.toString(tokens));
        }

        // really search
        IntArrayList result = null;

        for (final String token : tokens) {
            final IntArrayList prev = result;

            {
                long ms = System.currentTimeMillis();
                result = searchByGrepInside(version, token, prev, query.bookIds);
                AppLog.d(TAG, "search token '" + token + "' needed: " + (System.currentTimeMillis() - ms) + " ms");
            }

            if (prev != null) {
                AppLog.d(TAG, "Will intersect " + prev.size() + " elements with " + result.size() + " elements...");
                result = intersect(prev, result);
                AppLog.d(TAG, "... the result is " + result.size() + " elements");
            }
        }

        return result;
    }

    private static IntArrayList intersect(IntArrayList a, IntArrayList b) {
        IntArrayList res = new IntArrayList(a.size());

        int[] aa = a.buffer();
        int[] bb = b.buffer();
        int alen = a.size();
        int blen = b.size();

        int apos = 0;
        int bpos = 0;

        while (true) {
            if (apos >= alen) break;
            if (bpos >= blen) break;

            int av = aa[apos];
            int bv = bb[bpos];

            if (av == bv) {
                res.add(av);
                apos++;
                bpos++;
            } else if (av > bv) {
                bpos++;
            } else { // av < bv
                apos++;
            }
        }

        return res;
    }

    /**
     * Return the next ari (with only book and chapter) after the lastAriBc by scanning the source starting from pos.
     *
     * @param ppos pointer to pos. pos will be changed to ONE AFTER THE FOUND POSITION. So do not do another increment (++) outside this method.
     */
    private static int nextAri(IntArrayList source, int[] ppos, int lastAriBc) {
        int[] s = source.buffer();
        int len = source.size();
        int pos = ppos[0];

        while (true) {
            if (pos >= len) return 0x0;

            int curAri = s[pos];
            int curAriBc = Ari.toBookChapter(curAri);

            if (curAriBc != lastAriBc) {
                // found!
                pos++;
                ppos[0] = pos;
                return curAriBc;
            } else {
                // still the same one, move to next.
                pos++;
            }
        }
    }

    static IntArrayList searchByGrepInside(final Version version, String token, final IntArrayList source, final SparseBooleanArray bookIds) {
        final IntArrayList res = new IntArrayList();
        final boolean hasPlus = QueryTokenizer.isPlussedToken(token);

        if (hasPlus) {
            token = QueryTokenizer.tokenWithoutPlus(token);
        }

        if (source == null) {
            for (Book book : version.getConsecutiveBooks()) {
                if (!bookIds.get(book.bookId, false)) {
                    continue; // the book is not included in selected books to be searched
                }

                for (int chapter_1 = 1; chapter_1 <= book.chapter_count; chapter_1++) {
                    // try to find it wholly in a chapter
                    final int ariBc = Ari.encode(book.bookId, chapter_1, 0);
                    searchByGrepForOneChapter(version, book, chapter_1, token, hasPlus, ariBc, res);
                }

                if (BuildConfig.DEBUG) AppLog.d(TAG, "searchByGrepInside book " + book.shortName + " done. res.size = " + res.size());
            }
        } else {
            // search only on book-chapters that are in the source
            int count = 0; // for stats

            int[] ppos = new int[1];
            int curAriBc = 0x000000;

            while (true) {
                curAriBc = nextAri(source, ppos, curAriBc);
                if (curAriBc == 0) break; // no more

                // No need to check null book, because we go here only after searching a previous token which is based on
                // getConsecutiveBooks, which is impossible to have null books.
                final Book book = version.getBook(Ari.toBook(curAriBc));
                final int chapter_1 = Ari.toChapter(curAriBc);

                searchByGrepForOneChapter(version, book, chapter_1, token, hasPlus, curAriBc, res);

                count++;
            }

            if (BuildConfig.DEBUG) AppLog.d(TAG, "searchByGrepInside book with source " + source.size() + " needed to read as many as " + count + " book-chapter. res.size=" + res.size());
        }

        return res;
    }

    /**
     * @param token searched token without plusses
     * @param res (output) result aris
     * @param ariBc book-chapter ari, with verse must be set to 0
     * @param hasPlus whether the token had plus
     */
    private static void searchByGrepForOneChapter(final Version version, final Book book, final int chapter_1, final String token, final boolean hasPlus, final int ariBc, final IntArrayList res) {
        // This is a string of one chapter with verses joined by 0x0a ('\n')
        final String oneChapter = version.loadChapterTextLowercasedWithoutSplit(book, chapter_1);
        if (oneChapter == null) {
            return;
        }

        int verse_0 = 0;
        int lastV = -1;

        // Initial search
        String[] multiword = null;
        final int[] consumedLengthPtr = {0};

        // posToken is the last found position of the searched token
        // consumedLength is how much characters in the oneChapter was consumed when searching for the token.
        // Both of these variables must be set together in all cases.
        int posToken;
        int consumedLength;

        if (hasPlus) {
            multiword = QueryTokenizer.tokenizeMultiwordToken(token);

            if (multiword != null) {
                posToken = indexOfWholeMultiword(oneChapter, multiword, 0, true, consumedLengthPtr);
                consumedLength = consumedLengthPtr[0];
            } else {
                posToken = indexOfWholeWord(oneChapter, token, 0);
                consumedLength = token.length();
            }
        } else {
            posToken = oneChapter.indexOf(token);
            consumedLength = token.length();
        }

        if (posToken == -1) {
            // initial search does not return results. It means the whole chapter does not contain the token.
            return;
        }

        int posN = oneChapter.indexOf(0x0a);

        while (true) {
            if (posN < posToken) {
                verse_0++;
                posN = oneChapter.indexOf(0x0a, posN + 1);
                if (posN == -1) {
                    return;
                }
            } else {
                if (verse_0 != lastV) {
                    res.add(ariBc + verse_0 + 1); // +1 to make it verse_1
                    lastV = verse_0;
                }
                if (hasPlus) {
                    if (multiword != null) {
                        posToken = indexOfWholeMultiword(oneChapter, multiword, posToken + consumedLength, true, consumedLengthPtr);
                        consumedLength = consumedLengthPtr[0];
                    } else {
                        posToken = indexOfWholeWord(oneChapter, token, posToken + consumedLength);
                        consumedLength = token.length();
                    }
                } else {
                    posToken = oneChapter.indexOf(token, posToken + consumedLength);
                    consumedLength = token.length();
                }
                if (posToken == -1) {
                    return;
                }
            }
        }
    }

    /**
     * Case sensitive! Make sure <code>s</code> and <code>rt</code> tokens have been lowercased (or normalized).
     */
    public static boolean satisfiesTokens(final String s, @NonNull final ReadyTokens rt) {
        for (int i = 0; i < rt.token_count; i++) {
            final boolean hasPlus = rt.hasPlusses[i];

            final int posToken;
            if (hasPlus) {
                final String[] multiword_tokens = rt.multiwords_tokens[i];
                if (multiword_tokens != null) {
                    posToken = indexOfWholeMultiword(s, multiword_tokens, 0, false, null);
                } else {
                    posToken = indexOfWholeWord(s, rt.tokens[i], 0);
                }
            } else {
                posToken = s.indexOf(rt.tokens[i]);
            }

            if (posToken == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * This looks for a word that is surrounded by non-letter-or-digit characters.
     * This works well only if the word is not a multiword.
     *
     * @param text haystack
     * @param word needle
     * @param start start at character
     * @return -1 or position of the word
     */
    private static int indexOfWholeWord(String text, String word, int start) {
        final int len = text.length();

        while (true) {
            final int pos = text.indexOf(word, start);
            if (pos == -1) return -1;

            // check left
            // [pos] [charat pos-1] [charat pos-2]
            //  0                                    ok
            // >1       alnum            '@'         ok
            // >1       alnum          not '@'       ng
            // >0       alnum                        ng
            // >0     not alnum                      ok
            if (pos != 0 && Character.isLetterOrDigit(text.charAt(pos - 1))) {
                if (pos != 1 && text.charAt(pos - 2) == '@') {
                    // oh, before this word there is a tag. Then it is OK.
                } else {
                    start = pos + 1; // give up
                    continue;
                }
            }

            // check right
            int end = pos + word.length();
            // [end]   [charat end]
            // len         *         ok
            // != len    alnum       ng
            // != len  not alnum     ok
            if (end != len && Character.isLetterOrDigit(text.charAt(end))) {
                start = pos + 1; // give up
                continue;
            }

            // passed
            return pos;
        }
    }

    /**
     * This looks for a multiword that is surrounded by non-letter characters.
     * This works for multiword because it tries to strip tags and punctuations from the text before matching.
     *
     * @param text haystack.
     * @param multiword multiword that has been split into words. Must have at least one element.
     * @param start character index of text to start searching from
     * @param isNewlineDelimitedText <code>text</code> has '\n' as delimiter between verses. <code>multiword</code> cannot be searched across different verses.
     * @param consumedLengthPtr (length-1 array output) how many characters matched from the source text to satisfy the multiword. Will be 0 if this method returns -1.
     * @return -1 or position of the multiword.
     */
    private static int indexOfWholeMultiword(String text, String[] multiword, int start, boolean isNewlineDelimitedText, @Nullable int[] consumedLengthPtr) {
        final int len = text.length();
        final String firstWord = multiword[0];

        findAllWords:
        while (true) {
            final int firstPos = indexOfWholeWord(text, firstWord, start);
            if (firstPos == -1) {
                // not even the first word is found, so we give up
                if (consumedLengthPtr != null) consumedLengthPtr[0] = 0;
                return -1;
            }

            int pos = firstPos + firstWord.length();

            // find the next words, but we want to consume everything after the previous word that is
            // not eligible as search characters, which are tags and non-letters.
            for (int i = 1, multiwordLen = multiword.length; i < multiwordLen; i++) {
                final int posBeforeConsume = pos;
                // consume!
                while (pos < len) {
                    final char c = text.charAt(pos);
                    if (c == '@') {
                        if (pos == len - 1) {
                            // bad data (nothing after '@')
                        } else {
                            pos++;
                            final char d = text.charAt(pos);
                            if (d == '<') {
                                final int closingTagStart = text.indexOf("@>", pos + 1);
                                if (closingTagStart == -1) {
                                    // bad data (no closing tag)
                                } else {
                                    pos = closingTagStart + 1;
                                }
                            } else {
                                // single-letter formatting code, move on...
                            }
                        }
                    } else if (Character.isLetterOrDigit(c)) {
                        break;
                    } else if (isNewlineDelimitedText && c == '\n') {
                        // can't cross verse boundary, so we give up and try from beginning again
                        start = pos + 1;
                        continue findAllWords;
                    } else {
                        // non-letter, move on...
                    }

                    pos++;
                }

                if (BuildConfig.DEBUG) {
                    AppLog.d(TAG, "=========================");
                    AppLog.d(TAG, "multiword: " + Arrays.toString(multiword));
                    AppLog.d(TAG, "text     : #" + text.substring(Math.max(0, posBeforeConsume - multiword[i - 1].length()), Math.min(len, posBeforeConsume + 80)) + "#");
                    AppLog.d(TAG, "skipped  : #" + text.substring(posBeforeConsume, pos) + "#");
                    AppLog.d(TAG, "=========================////");
                }

                final String word = multiword[i];

                final int foundWordStart = indexOfWholeWord(text, word, pos);
                if (foundWordStart == -1 /* Not found... */ || foundWordStart != pos /* ...or another word comes */) {
                    // subsequent words is not found at the correct position, so loop from beginning again
                    start = pos;
                    continue findAllWords;
                }

                // prepare for next iteration
                pos = foundWordStart + word.length();
            }

            // all words are found!
            if (consumedLengthPtr != null) consumedLengthPtr[0] = pos - firstPos;
            return firstPos;
        }
    }

    public static SpannableStringBuilder hilite(final CharSequence s, final ReadyTokens rt, int hiliteColor) {
        final SpannableStringBuilder res = new SpannableStringBuilder(s);

        if (rt == null) {
            return res;
        }

        final int token_count = rt.token_count;

        // from source text, produce a plain text lowercased
        final char[] newString = new char[s.length()];
        for (int i = 0, len = s.length(); i < len; i++) {
            final char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                newString[i] = (char) (c | 0x20);
            } else {
                newString[i] = Character.toLowerCase(c);
            }
        }
        final String plainText = new String(newString);

        int pos = 0;
        final int[] attempts = new int[token_count];
        final int[] consumedLengths = new int[token_count];

        // local vars for optimizations
        final boolean[] hasPlusses = rt.hasPlusses;
        final String[] tokens = rt.tokens;
        final String[][] multiwords_tokens = rt.multiwords_tokens;

        // temp buf
        final int[] consumedLengthPtr = {0};
        while (true) {
            for (int i = 0; i < token_count; i++) {
                if (hasPlusses[i]) {
                    if (multiwords_tokens[i] != null) {
                        attempts[i] = indexOfWholeMultiword(plainText, multiwords_tokens[i], pos, false, consumedLengthPtr);
                        consumedLengths[i] = consumedLengthPtr[0];
                    } else {
                        attempts[i] = indexOfWholeWord(plainText, tokens[i], pos);
                        consumedLengths[i] = tokens[i].length();
                    }
                } else {
                    attempts[i] = plainText.indexOf(tokens[i], pos);
                    consumedLengths[i] = tokens[i].length();
                }
            }

            // from the attempts above, find the earliest
            int minpos = Integer.MAX_VALUE;
            int mintokenindex = -1;

            for (int i = 0; i < token_count; i++) {
                if (attempts[i] >= 0) { // not -1 which means not found
                    if (attempts[i] < minpos) {
                        minpos = attempts[i];
                        mintokenindex = i;
                    }
                }
            }

            if (mintokenindex == -1) {
                break; // no more
            }

            final int topos = minpos + consumedLengths[mintokenindex];
            res.setSpan(new StyleSpan(Typeface.BOLD), minpos, topos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            res.setSpan(new ForegroundColorSpan(hiliteColor), minpos, topos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            pos = topos;
        }

        return res;
    }
}
