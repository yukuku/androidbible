package yuku.alkitab.yes2.compress;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import yuku.alkitab.base.util.IntArrayList;
import yuku.snappy.codec.Snappy;

public class SnappyOutputStream extends FilterOutputStream {
	public final String TAG = SnappyOutputStream.class.getSimpleName();
	
	private final Snappy snappy;
	private final int block_size; 
	private byte[] uncompressed_buf;
	private byte[] compressed_buf;
	private int uncompressed_offset;
	private IntArrayList compressed_block_sizes = new IntArrayList();

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
			compressed_block_sizes.add(compressed_len);
			uncompressed_offset = 0;
		}
	}
	
	@Override public void flush() throws IOException {
		dump();
		out.flush();
	}
	
	public int[] getCompressedBlockSizes() {
		int[] res = new int[compressed_block_sizes.size()];
		System.arraycopy(compressed_block_sizes.buffer(), 0, res, 0, res.length);
		return res;
	}
}