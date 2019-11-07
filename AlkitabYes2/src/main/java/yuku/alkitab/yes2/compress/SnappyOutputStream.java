package yuku.alkitab.yes2.compress;

import yuku.snappy.codec.Snappy;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class SnappyOutputStream extends FilterOutputStream {
	private final Snappy snappy;
	private final int block_size; 
	private byte[] uncompressed_buf;
	private byte[] compressed_buf;
	private int uncompressed_offset;
	private int[] compressed_block_sizes = new int[100];
	private int compressed_block_sizes_length = 0;

	public SnappyOutputStream(OutputStream out, int block_size) {
		super(out);
		this.snappy = new Snappy.Factory().newInstance();
		this.block_size = block_size;
		this.uncompressed_buf = new byte[block_size];
		this.compressed_buf = new byte[snappy.maxCompressedLength(uncompressed_buf.length)];
	}

	@Override public void write(int b) throws IOException {
		if (uncompressed_offset >= block_size) {
			dump();
		}
		
		uncompressed_buf[uncompressed_offset++] = (byte) b;
	}
	
	@Override public void write(byte[] b, int off, int len) throws IOException {
		int remaining = len;
		int src_off = off;
		
		while (remaining > 0) {
			int can_write = block_size - uncompressed_offset;
			int will_write = Math.min(remaining, can_write);
			
			System.arraycopy(b, src_off, uncompressed_buf, uncompressed_offset, will_write);
			uncompressed_offset += will_write;
			src_off += will_write;
			remaining -= will_write;
			
			if (uncompressed_offset >= block_size) {
				dump();
			}
		}
		
		assert src_off == off + len;
	}
	
	private void dump() throws IOException {
		if (uncompressed_offset > 0) {
			int compressed_len = snappy.compress(uncompressed_buf, 0, compressed_buf, 0, uncompressed_offset);
			out.write(compressed_buf, 0, compressed_len);
			// check for need of expanding compressed_block_sizes array
			while (compressed_block_sizes_length >= compressed_block_sizes.length) {
				final int[] newArray = new int[compressed_block_sizes.length << 1];
				System.arraycopy(compressed_block_sizes, 0, newArray, 0, compressed_block_sizes_length);
				compressed_block_sizes = newArray;
			}
			compressed_block_sizes[compressed_block_sizes_length] = compressed_len;
			compressed_block_sizes_length++;
			uncompressed_offset = 0;
		}
	}
	
	@Override public void flush() throws IOException {
		dump();
		out.flush();
	}
	
	public int[] getCompressedBlockSizes() {
		int[] res = new int[compressed_block_sizes_length];
		System.arraycopy(compressed_block_sizes, 0, res, 0, compressed_block_sizes_length);
		return res;
	}
}