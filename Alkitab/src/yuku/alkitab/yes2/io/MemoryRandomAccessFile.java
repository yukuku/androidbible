package yuku.alkitab.yes2.io;

import java.io.IOException;
import java.io.RandomAccessFile;

public class MemoryRandomAccessFile extends RandomAccessFile {
	public static final String TAG = MemoryRandomAccessFile.class.getSimpleName();

	private int length = 0;
	private int pos = 0;
	private byte[] buf = new byte[16];
	
	public MemoryRandomAccessFile() throws IOException {
		super("/dev/null", "r");
	}
	
	@Override public void setLength(long newLength) throws IOException {
		length = (int) newLength;
		expandBufTo(length);
	}
	
	void expandBufTo(int newLen) {
		if (buf.length < newLen) {
			byte[] buf2 = new byte[newLen];
			System.arraycopy(buf, 0, buf2, 0, buf.length);
			this.buf = buf2;
		}
	}

	@Override public int read() throws IOException {
		if (pos >= length) {
			return -1;
		}
		return buf[pos++];
	}
	
	@Override public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
		int can_read = length - byteOffset;
		int will_read = byteCount > can_read? can_read: byteCount;
		if (will_read == 0 && byteCount > 0) return -1; // EOF
		System.arraycopy(this.buf, this.pos, buffer, byteOffset, will_read);
		this.pos += will_read;
		return will_read;
	}
	
	@Override public int read(byte[] buffer) throws IOException {
		return read(buffer, 0, buffer.length);
	}
	
	@Override public void write(int oneByte) throws IOException {
		if (pos >= length) {
			expandBufTo((int) (length * 1.5) + 1);
		}
		
		this.buf[this.pos++] = (byte) oneByte;
		if (pos >= length) {
			length = pos;
		}
	}
	
	@Override public void write(byte[] buffer, int byteOffset, int byteCount) throws IOException {
		for (int i = byteOffset, to = byteOffset + byteCount; i < to; i++) {
			write(buffer[i]);
		}
	}
	
	@Override public void write(byte[] buffer) throws IOException {
		write(buffer, 0, buffer.length);
	}
	
	@Override public long length() throws IOException {
		return length;
	}
	
	@Override public long getFilePointer() throws IOException {
		return pos;
	}
	
	@Override public void seek(long offset) throws IOException {
		pos = (int) offset;
	}
	
	@Override public int skipBytes(int count) throws IOException {
		pos += count;
		return count;
	}
	
	@Override public void close() throws IOException {
		return;
	}
	
	@Override protected void finalize() throws Throwable {
		return;
	}

	public byte[] getBuffer() {
		return this.buf;
	}

	public int getBufferOffset() {
		return 0;
	}

	public int getBufferLength() {
		return length;
	}
}

