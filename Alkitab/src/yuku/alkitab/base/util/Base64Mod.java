package yuku.alkitab.base.util;

public class Base64Mod {
	public static final String TAG = Base64Mod.class.getSimpleName();
	
	// Uses _ and ~ for the 2 extra characters. 
	// Not using - because it might be used to indicate ranges.
	// Not using . because it might be used to separate hierarchy structures
    private static final char ENCODE[] = {
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
        'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
        'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
        'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '?', '?',
    };
    
    private static final int DECODE[] = {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -2, -1, -1,
        -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,
        15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1,
        -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
        41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1, // index 0x7f
    };
    
    static {
    	ENCODE[62] = '_';
    	DECODE['_'] = 62;
    	ENCODE[63] = '~';
    	DECODE['~'] = 63;
    }
    
    public static char[] encodeToOneChar(int value) {
    	if (value < 0 || value > 0x3f) throw new IllegalArgumentException("value out of range: " + value);
    	return new char[] {ENCODE[value]};
    }
    
    public static char[] encodeToTwoChars(int value) {
    	if (value < 0 || value > 0xfff) throw new IllegalArgumentException("value out of range: " + value);
    	char c1 = ENCODE[value >>> 6];
    	char c0 = ENCODE[value & 0x3f];
    	return new char[] {c1, c0};
    }
    
    public static char[] encodeToThreeChars(int value) {
    	if (value < 0 || value > 0x3ffff) throw new IllegalArgumentException("value out of range: " + value);
    	char c2 = ENCODE[value >>> 12];
    	char c1 = ENCODE[(value & 0x7c0) >>> 6];
    	char c0 = ENCODE[value & 0x3f];
    	return new char[] {c2, c1, c0};
    }

    public static int decodeFromChars(char[] chars, int start, int len) {
    	int res = 0;
    	for (int i = start + len - 1; i >= start; i--) {
    		if (res != 0) res <<= 6;
    		char c = chars[i];
    		if (c > 0x7f) throw new IllegalArgumentException("invalid char in base64mod: " + c);
    		int v = DECODE[c];
    		if (v == -1) throw new IllegalArgumentException("invalid char in base64mod: " + c);
    		res |= v;
    	}
    	return res;
    }
}
