package yuku.alkitab;

import java.io.*;
import java.util.*;

public class SimpleScanner {
	private final Reader in_;
	private char[] buf_;
	private int pos_ = 0;
	private boolean filled_ = false;
	
	public SimpleScanner(Reader in, int charBuf) {
		in_ = in;
		buf_ = new char[charBuf];
	}
	
	private void buffer() throws IOException {
		if (filled_ == false) {
			char c = 0;
			
			// buang sampah
			while (true) {
				int r = in_.read();
				if (r == ' ' || r == '\n' || r == '\t' || r == '\r') {
					continue; // sampah
				} else if (r == -1) {
					// eof, set buf null
					buf_ = null;
					return;
				}
				c = (char) r;
				break;
			}
			
			buf_[pos_++] = c; // kar pertama
			
			// konsumsi badan
			while (true) {
				int r = in_.read();
				if (r == ' ' || r == '\n' || r == '\t' || r == '\r' || r == -1) {
					// selesai
					break;
				}
				c = (char) r;
				
				buf_[pos_++] = c; // kar berikutnya
			}
			
			filled_ = true;
		}
	}
	
	/**
	 * only positive int!
	 */
	public int nextInt() throws IOException {
		buffer();
		
		if (buf_ != null) {
			int res = parseInt(buf_, pos_);
			filled_ = false; // consume
			pos_ = 0;
			return res;
		}
		
		throw new NoSuchElementException();
	}
	
	private int parseInt(char[] buf, int len) {
		int res = 0;
		
		for (int i = 0; i < len; i++) {
			res = res * 10 + (buf[i]-'0');
		}
		
		return res;
	}

	public String next() throws IOException {
		buffer();
		
		if (buf_ != null) {
			String res = new String(buf_, 0, pos_);
			filled_ = false; // consume
			pos_ = 0;
			return res;
		}
		
		throw new NoSuchElementException();
	}
	
	public boolean hasNext() throws IOException {
		buffer();
		
		return buf_ != null;
	}
}
