package yuku.alkitab.yes2.io;

import java.io.IOException;

public class MemoryRandomOutputStream extends RandomOutputStream {
	private int length = 0;
	private int pos = 0;
	private byte[] buf = new byte[16];
	
	void expandBufTo(int newLen) {
		if (buf.length < newLen) {
			byte[] buf2 = new byte[newLen];
			System.arraycopy(buf, 0, buf2, 0, buf.length);
			this.buf = buf2;
		}
	}

	@Override public void write(int oneByte) throws IOException {
		if (pos >= buf.length) {
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
	
	@Override public long getFilePointer() throws IOException {
		return pos;
	}
	
	@Override public void seek(long offset) throws IOException {
		pos = (int) offset;
	}

	@Override public void close() throws IOException {
	}

    /**
     * This may return a buffer with length larger than the actual size of this "memory file".
     * Call {@link #getBufferLength()} to find out the actual length of the buffer data.
     */
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

