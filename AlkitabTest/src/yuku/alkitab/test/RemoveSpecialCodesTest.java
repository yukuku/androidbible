package yuku.alkitab.test;

import android.test.AndroidTestCase;

import yuku.alkitab.base.U;

public class RemoveSpecialCodesTest extends AndroidTestCase {
	void testRemoveSpecialCodes(String formatted, String plain) {
		assertEquals(U.removeSpecialCodes(formatted), plain);
	}

	void testRemoveSpecialCodesForced(String formatted, String plain) {
		assertEquals(U.removeSpecialCodes(formatted, true), plain);
	}

	public void testRemoveSpecialCodes() throws Throwable {
		testRemoveSpecialCodes("abcdef ghijkl", "abcdef ghijkl");
		testRemoveSpecialCodes("abc@1def@2ghi@3jkl", "abc@1def@2ghi@3jkl"); // does not start with "@@"
		testRemoveSpecialCodes("@@abc@1def@2ghi@3jkl", "abcdefghijkl"); 
		testRemoveSpecialCodes("@@abc@1@2", "abc"); 
		testRemoveSpecialCodes("@@@1@2abc", "abc"); 
		testRemoveSpecialCodes("@@@1abc@2", "abc"); 
		
		testRemoveSpecialCodesForced("abcdef ghijkl", "abcdef ghijkl");
		testRemoveSpecialCodesForced("abc@1def@2ghi@3jkl", "abcdefghijkl"); // does not start with "@@"
		testRemoveSpecialCodesForced("@@abc@1def@2ghi@3jkl", "abcdefghijkl"); 
		
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
