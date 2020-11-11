package yuku.alkitab.base.util;

import junit.framework.TestCase;

public class RemoveSpecialCodesTest extends TestCase {
	void testRemoveSpecialCodes(String formatted, String plain) {
		assertEquals(plain, FormattedVerseText.removeSpecialCodes(formatted));
	}

	void testRemoveSpecialCodesForced(String formatted, String plain) {
		assertEquals(plain, FormattedVerseText.removeSpecialCodes(formatted, true));
	}

	public void testRemoveSpecialCodes() {
		testRemoveSpecialCodes("abcdef ghijkl", "abcdef ghijkl");
		testRemoveSpecialCodes("abc@1def@2ghi@3jkl", "abc@1def@2ghi@3jkl"); // does not start with "@@"
		testRemoveSpecialCodes("@@abc@1def@2ghi@3jkl", "abc def ghi jkl");
		testRemoveSpecialCodes("@@abc @1def @2ghi.\n@3jkl", "abc def ghi.\njkl"); // do not double space
		testRemoveSpecialCodes("@@abc@1", "abc ");
		testRemoveSpecialCodes("@@abc@1@2", "abc "); // do not double space
		testRemoveSpecialCodes("@@@1@2abc", "abc"); // do not add space at beginning
		testRemoveSpecialCodes("@@@1abc@2", "abc ");

		testRemoveSpecialCodesForced("abcdef ghijkl", "abcdef ghijkl");
		testRemoveSpecialCodesForced("abc@1def@2ghi@3jkl", "abc def ghi jkl"); // does not start with "@@"
		testRemoveSpecialCodesForced("@@abc@1def@2ghi@3jkl", "abc def ghi jkl");

		// adding space
		testRemoveSpecialCodes("@@a@8b@^c", "a b c");
		testRemoveSpecialCodes("@@a@0b@1c@2d@3e@4f", "a b c d e f");
		testRemoveSpecialCodes("@@a@5b@6c@7d@9e@8f", "abcde f");
		testRemoveSpecialCodes("@@a@8", "a ");
		testRemoveSpecialCodes("@@@8a", "a");

		// start and end tags
		testRemoveSpecialCodes("@@abc@<whatever@>def", "abcdef");
		testRemoveSpecialCodes("@@abc@<whatever@>def@/", "abcdef");
		testRemoveSpecialCodes("@@abc@<should not crash", "abcshould not crash");
		testRemoveSpecialCodes("@@abc@>should not crash", "abcshould not crash");
		testRemoveSpecialCodes("@@abc@<should not@>crash@>", "abccrash");
		testRemoveSpecialCodes("@@abc@<", "abc");
		testRemoveSpecialCodes("@@@<abc@>", "");
		testRemoveSpecialCodes("@@@>abc@<", "abc");

		testRemoveSpecialCodesForced("@<abc@>", "");
		testRemoveSpecialCodesForced("@>abc@<", "abc");
	}
}
