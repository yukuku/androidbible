package yuku.alkitab.yes2.compress;

import java.io.IOException;
import java.io.InputStream;

import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.snappy.codec.Snappy;

public class SnappyInputStream extends InputStream {
	public final String TAG = SnappyInputStream.class.getSimpleName();
	
	private final Snappy snappy;
	private final RandomInputStream file;
	private final long baseOffset;
	private final int block_size;
	private final int[] compressed_block_sizes;
	private final int[] compressed_block_offsets;
	private int current_block_index;
	private int current_block_skip;
	private byte[] compressed_buf;
	private int uncompressed_block_index = -1;
	private byte[] uncompressed_buf;
	private int uncompressed_len;

	public SnappyInputStream(RandomInputStream file, long baseOffset, int block_size, int[] compressed_block_sizes, int[] compressed_block_offsets) {
		this.block_size = block_size;
		this.snappy = new Snappy.Factory().newInstance();
		this.file = file;
		this.baseOffset = baseOffset;
		this.compressed_block_sizes = compressed_block_sizes;
		this.compressed_block_offsets = compressed_block_offsets;
		if (compressed_buf == null || compressed_buf.length < snappy.maxCompressedLength(block_size)) {
			compressed_buf = new byte[snappy.maxCompressedLength(block_size)];
		}
		if (uncompressed_buf == null || uncompressed_buf.length != block_size) {
			uncompressed_buf = new byte[block_size];
		}
	}

	public void seek(int offset) throws IOException {
		int block_index = offset / block_size;
		int block_skip = offset - (block_index * block_size);
		
//		Log.d(TAG, "want to read contentOffset=" + contentOffset + " but compressed"); 
//		Log.d(TAG, "so going to block " + block_index + " where compressed offset is " + compressed_block_offsets[block_index]);
//		Log.d(TAG, "skipping " + block_skip + " uncompressed bytes");
		
		this.current_block_index = block_index;
		this.current_block_skip = block_skip;
		prepareBuffer();
	}
	
	private void prepareBuffer() throws IOException {
		int block_index = current_block_index;
		
		// if uncompressed_block_index is already equal to the requested block_index
		// then we do not need to re-decompress again
		if (uncompressed_block_index != block_index) {
			file.seek(baseOffset + compressed_block_offsets[block_index]);
			file.read(compressed_buf, 0, compressed_block_sizes[block_index]);
			uncompressed_len = snappy.decompress(compressed_buf, 0, uncompressed_buf, 0, compressed_block_sizes[block_index]);
			if (uncompressed_len < 0) {
				throw new IOException("Error in decompressing: " + uncompressed_len);
			}
			uncompressed_block_index = block_index;
		}
	}

	@Override public int read() throws IOException {
		int can_read = uncompressed_len - current_block_skip;
		if (can_read == 0) {
			if (current_block_index >= compressed_block_sizes.length) {
				return -1; // EOF
			} else {
				// need to move to the next block
				current_block_index++;
				current_block_skip = 0;
				prepareBuffer();
			}
		}
		int res = /* need to convert to uint8: */ 0xff & uncompressed_buf[current_block_skip];
		current_block_skip++;
		return res;
	}

	@Override public int read(byte[] buffer, int offset, int length) throws IOException {
		int res = 0;
		int want_read = length; 
		
		while (want_read > 0) {
			int can_read = uncompressed_len - current_block_skip;
			if (can_read == 0) {
				if (current_block_index >= compressed_block_sizes.length) { // EOF
					if (res == 0) return -1; // we didn't manage to read any
					return res;
				} else {
					// need to move to the next block
					current_block_index++;
					current_block_skip = 0;
					prepareBuffer();
					can_read = uncompressed_len;
				}
			}
			
			int will_read = want_read > can_read? can_read: want_read;
			System.arraycopy(uncompressed_buf, current_block_skip, buffer, offset, will_read);
			current_block_skip += will_read;
			offset += will_read;
			want_read -= will_read;
			res += will_read;
		}
		
		return res;
	}
}