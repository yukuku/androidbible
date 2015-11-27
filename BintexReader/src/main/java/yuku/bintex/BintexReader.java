package yuku.bintex;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class BintexReader implements Closeable {
	private static final int[] SUPPORTED_TYPE_MAP = { // 1 = int; 2 = string; 3 = int[]; 4 = simple map
		//.1 .2 .3 .4 .5 .6 .7 .8 .9 .a .b .c .d .e .f
		0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 2, 2, 1, 1, // 0. 
		1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 1. 
		1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 2. 
		1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 3. 
		1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 4. 
		0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, // 5. 
		0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, // 6. 
		2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 7. 
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 8. 
		4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 9. 
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // a. 
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // b. 
		3, 3, 0, 0, 3, 0, 0, 0, 3, 3, 0, 0, 3, 0, 0, 0, // c. 
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // d. 
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // e. 
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // f. 
	};
	
	private InputStream is_;
	
	private int pos_ = 0;
	
	private static ThreadLocal<byte[]> buf_byte_ = new ThreadLocal<byte[]>() {
		@Override protected byte[] initialValue() {
			return new byte[2048];
		}
	};
	
	private static ThreadLocal<char[]> buf_char_ = new ThreadLocal<char[]>() {
		@Override protected char[] initialValue() {
			return new char[1024];
		}
	};
	
	public BintexReader(InputStream is) {
		this.is_ = is;
	}
	
	/**
	 * Acts as if you were instantiating this object, only without memory allocations.
	 * Make sure that you have done with the current object before reusing this object.
	 * @return this object so that you can use ternary operator
	 */
	public BintexReader reuse(InputStream is) {
		this.is_ = is;
		this.pos_ = 0;
		return this;
	}
	
	public String readShortString() throws IOException {
		int len = is_.read();
		pos_++;
		
		if (len < 0) {
			throw new EOFException();
		} else if (len == 0) {
			return "";
		}
		
		// max len = 255, maka buf pasti cukup
		char[] _buf = buf_char_.get();
		for (int i = 0; i < len; i++) {
			_buf[i] = readCharWithoutIncreasingPos();
		}
		pos_ += len + len;
		
		return new String(_buf, 0, len);
	}
	
	public String readLongString() throws IOException {
		int len = readInt();
		if (len == 0) {
			return "";
		}
		
		char[] buf_char = buf_char_.get();
		if (len > buf_char.length) {
			buf_char = new char[len + 1024];
			buf_char_.set(buf_char);
		}

		for (int i = 0; i < len; i++) {
			buf_char[i] = readCharWithoutIncreasingPos();
		}
		pos_ += len + len;
		
		return new String(buf_char, 0, len);
	}
	
	/**
	 * Read 8-bit or 16-bit string.
	 * 
	 * The first byte determines:
	 * 0x01 = 8 bit short
	 * 0x02 = 16 bit short
	 * 0x11 = 8 bit long
	 * 0x12 = 16 bit long
	 */
	public String readAutoString() throws IOException {
		int kind = readUint8();
		int len = 0;
		if (kind == 0x01 || kind == 0x02) {
			len = readUint8();
		} else if (kind == 0x11 || kind == 0x12) {
			len = readInt();
		}
		
		char[] buf_char = buf_char_.get();
		if (len > buf_char.length) {
			buf_char = new char[len + 1024];
			buf_char_.set(buf_char);
		}
		
		if (kind == 0x01 || kind == 0x11) {
			for (int i = 0; i < len; i++) {
				buf_char[i] = (char) is_.read();
			}
			pos_ += len;
			
			return new String(buf_char, 0, len);
		} else if (kind == 0x02 || kind == 0x12) {
			for (int i = 0; i < len; i++) {
				buf_char[i] = readCharWithoutIncreasingPos();
			}
			pos_ += len + len;
			
			return new String(buf_char, 0, len);
		} else {
			return null;
		}
	}
	
	public int readInt() throws IOException {
		int res = (is_.read() << 24) | (is_.read() << 16) | (is_.read() << 8) | (is_.read());
		pos_ += 4;
		return res;
	}

	public char readChar() throws IOException {
		char res = (char) ((is_.read() << 8) | (is_.read()));
		pos_ += 2;
		return res;
	}
	
	private char readCharWithoutIncreasingPos() throws IOException {
		return (char) ((is_.read() << 8) | (is_.read()));
	}

	public int readUint8() throws IOException {
		int res = is_.read();
		pos_++;
		return res;
	}
	
	public int readUint16() throws IOException {
		int res = (is_.read() << 8) | (is_.read());
		pos_ += 2;
		return res;
	}
	
	public float readFloat() throws IOException {
		int a = (is_.read() << 24) | (is_.read() << 16) | (is_.read() << 8) | (is_.read());
		pos_ += 4;
		return Float.intBitsToFloat(a);
	}

	public int readRaw(byte[] buf) throws IOException {
		return readRaw(buf, 0, buf.length);
	}
	
	public int readRaw(byte[] buf, int off, int len) throws IOException {
		int total = 0;
		int _off = off;
		int _len = len;
		
		while (true) {
			int read = is_.read(buf, _off, _len);
			if (read < 0) {
				if (total == 0) total = -1;
				break;
			}
			total += read;
			if (total >= len) {
				break;
			}
			_off += read;
			_len -= read;
		}
		pos_ += total;

		return total;
	}

	public int readVarUint() throws IOException {
		int first = is_.read();
		if ((first & 0x80) == 0) { // 0xxxxxxx
			pos_ += 1;
			return first;
		} else if ((first & 0xc0) == 0x80) { // 10xxxxxx
			int next0 = is_.read();
			pos_ += 2;
			return ((first & 0x3f) << 8) | (next0);
		} else if ((first & 0xe0) == 0xc0) { // 110xxxxx
			int next1 = is_.read();
			int next0 = is_.read();
			pos_ += 3;
			return ((first & 0x1f) << 16) | (next1 << 8) | (next0);
		} else if ((first & 0xf0) == 0xe0) { // 1110xxxx
			int next2 = is_.read();
			int next1 = is_.read();
			int next0 = is_.read();
			pos_ += 4;
			return ((first & 0x0f) << 24) | (next2 << 16) | (next1 << 8) | (next0);
		} else if (first == 0xf0) { // 11110000
			int next3 = is_.read();
			int next2 = is_.read();
			int next1 = is_.read();
			int next0 = is_.read();
			pos_ += 5;
			return (next3 << 24) | (next2 << 16) | (next1 << 8) | (next0);
		} else {
			pos_ += 1;
			throw new RuntimeException("unknown first byte in varuint: " + first);
		}
	}
	
	public int readValueInt() throws IOException {
		int t = is_.read();
		pos_++;
		return _readValueInt(t);
	}
	
	private int _readValueInt(int t) throws IOException {
		switch (t) {
		case 0x0e: // special value 0
			return 0;
		case 0x01:
		case 0x02:
		case 0x03:
		case 0x04:
		case 0x05:
		case 0x06:
		case 0x07: // immediate 1-7
			return t;
		case 0x0f: // special value -1
			return -1;
		case 0x10: 
		case 0x11: { 
			int a = is_.read();
			pos_++;
			return t == 0x11? ~a: a;
		}
		case 0x20: 
		case 0x21: { 
			int a = (is_.read() << 8) | (is_.read());
			pos_ += 2;
			return t == 0x21? ~a: a;
		}
		case 0x30: 
		case 0x31: { 
			int a = (is_.read() << 16) | (is_.read() << 8) | (is_.read());
			pos_ += 3;
			return t == 0x31? ~a: a;
		}
		case 0x40: 
		case 0x41: { 
			int a = (is_.read() << 24) | (is_.read() << 16) | (is_.read() << 8) | (is_.read());
			pos_ += 4;
			return t == 0x41? ~a: a;
		}
		default: {
			throw new IOException(String.format("value is not int: type=%02x", t));
		}
		}
	}
	
	public String readValueString() throws IOException {
		int t = is_.read();
		pos_++;
		return _readValueString(t);
	}
	
	private String _readValueString(int t) throws IOException {
		switch (t) {
		case 0x0c: // null
			return null;
		case 0x0d:
			return "";
		case 0x51:
		case 0x52:
		case 0x53:
		case 0x54:
		case 0x55:
		case 0x56:
		case 0x57:
		case 0x58:
		case 0x59:
		case 0x5a:
		case 0x5b:
		case 0x5c:
		case 0x5d:
		case 0x5e:
		case 0x5f: { // 8-bit string with len 1-15
			int len = t & 0x0f;
			return _read8BitString(len);
		}
		case 0x61:
		case 0x62:
		case 0x63:
		case 0x64:
		case 0x65:
		case 0x66:
		case 0x67:
		case 0x68:
		case 0x69:
		case 0x6a:
		case 0x6b:
		case 0x6c:
		case 0x6d:
		case 0x6e:
		case 0x6f: { // 16-bit string with len 1-15
			int len = t & 0x0f;
			return _read16BitString(len);
		}
		case 0x70: { // 8-bit string with len < 256
			int len = is_.read();
			pos_++;
			return _read8BitString(len);
		}
		case 0x71: { // 16-bit string with len < 256
			int len = is_.read();
			pos_++;
			return _read16BitString(len);
		}
		case 0x72: { // long 8-bit string
			int len = readInt();
			return _read8BitString(len);
		}
		case 0x73: { // long 16-bit string
			int len = readInt();
			return _read16BitString(len);
		}
		default: 
			throw new IOException(String.format("value is not string: type=%02x", t));
		}
	}

	private String _read8BitString(int len) throws IOException {
		byte[] buf1 = buf_byte_.get();
		if (len > buf1.length) {
			buf1 = new byte[len + 100];
			buf_byte_.set(buf1);
		}
		
		is_.read(buf1, 0, len);
		pos_ += len;
		return new String(buf1, 0x00, 0, len);
	}
	
	private String _read16BitString(int len) throws IOException {
		int bytes = len << 1;
		char[] buf2 = buf_char_.get();
		if (len > buf2.length) {
			buf2 = new char[len + 100];
			buf_char_.set(buf2);
		}

		for (int i = 0; i < len; i++) {
			buf2[i] = readCharWithoutIncreasingPos();
		}
		pos_ += bytes;
		return new String(buf2, 0, len);
	}

	public int[] readValueUint8Array() throws IOException {
		int t = is_.read();
		pos_++;
		return _readValueUint8Array(t);
	}
	
	private int[] _readValueUint8Array(int t) throws IOException {
		int len;
		if (t == 0xc0) { // len < 256
			len = is_.read();
			pos_++;
		} else if (t == 0xc8) {
			len = readInt();
		} else {
			throw new IOException(String.format("value is not uint8 array: type=%02x", t));
		}
		
		byte[] buf1 = buf_byte_.get();
		if (len > buf1.length) {
			buf1 = new byte[len + 100];
			buf_byte_.set(buf1);
		}
		
		is_.read(buf1, 0, len);
		pos_ += len;
		
		int[] res = new int[len];
		for (int i = 0; i < len; i++) {
			res[i] = buf1[i] & 0xff;
		}
		return res;
	}
	
	public int[] readValueUint16Array() throws IOException {
		int t = is_.read();
		pos_++;
		return _readValueUint16Array(t);
	}
	
	private int[] _readValueUint16Array(int t) throws IOException {
		int len;
		if (t == 0xc1) { // len < 256
			len = is_.read();
			pos_++;
		} else if (t == 0xc9) {
			len = readInt();
		} else {
			throw new IOException(String.format("value is not uint16 array: type=%02x", t));
		}
		
		int[] res = new int[len];
		byte[] buf = new byte[2];
		for (int i = 0; i < len; i++) {
			is_.read(buf, 0, 2);
			res[i] = ((buf[0] & 0xff) << 8) | (buf[1] & 0xff);
		}
		pos_ += len + len;
		
		return res;
	}

	/** also returns correctly if the data is of type uint8 or uint16 array */ 
	public int[] readValueIntArray() throws IOException {
		int t = is_.read();
		pos_++;
		return _readValueIntArray(t);
	}
	
	private int[] _readValueIntArray(int t) throws IOException {
		int len;
		if (t == 0xc0 || t == 0xc8) {
			return _readValueUint8Array(t);
		} else if (t == 0xc1 || t == 0xc9) {
			return _readValueUint16Array(t);
		} else if (t == 0xc4) { // len < 256
			len = is_.read();
			pos_++;
		} else if (t == 0xcc) {
			len = readInt();
		} else {
			throw new IOException(String.format("value is not int array: type=%02x", t));
		}
		
		int[] res = new int[len];
		byte[] buf = new byte[4];
		for (int i = 0; i < len; i++) {
			is_.read(buf, 0, 4);
			res[i] = ((buf[0] & 0xff) << 24) | ((buf[1] & 0xff) << 16) | ((buf[2] & 0xff) << 8) | (buf[3] & 0xff);
		}
		pos_ += len << 2;
		
		return res;
	}
	
	public ValueMap readValueSimpleMap() throws IOException {
		int t = is_.read();
		pos_++;
		return _readValueSimpleMap(t);
	}

	private ValueMap _readValueSimpleMap(int t) throws IOException {
		if (t == 0x90) {
			return new ValueMap();
		}
		
		if (t != 0x91) {
			throw new IOException(String.format("value is not simple map: type=%02x", t));
		}
		
		// t must be 0x91 by here
		
		int size = is_.read();
		pos_++;
		
		ValueMap res = new ValueMap();
		
		for (int i = 0; i < size; i++) {
			int key_len = is_.read();
			pos_++;
			
			String k = _read8BitString(key_len);
			Object v = readValue();
			
			res.put(k, v);
		}
		
		return res;
	}

	public Object readValue() throws IOException {
		int t = is_.read();
		pos_++;
		
		// ints
		int type = SUPPORTED_TYPE_MAP[t];
		if (type == 1) {
			return _readValueInt(t);
		} else if (type == 2) {
			return _readValueString(t);
		} else if (type == 3) {
			return _readValueIntArray(t);
		} else if (type == 4) {
			return _readValueSimpleMap(t);
		} else {
			throw new IOException(String.format("value has unknown type: type=%02x", t));
		}
	}

	public long skip(long n) throws IOException {
		long res = is_.skip(n);
		pos_ += (int) res;
		return res;
	}
	
	public int getPos() {
		return pos_;
	}
	
	@Override public void close() {
		try {
			is_.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
