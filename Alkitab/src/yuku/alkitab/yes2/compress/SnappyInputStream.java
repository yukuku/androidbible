package yuku.alkitab.yes2.compress;

import java.io.IOException;

import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.bintex.ValueMap;
import yuku.snappy.codec.Snappy;

public class SnappyInputStream extends RandomInputStream {
	public final String TAG = SnappyInputStream.class.getSimpleName();
	
	private final Snappy snappy;
	private final long baseOffset;
	private final int block_size;
	private final int[] compressed_block_sizes;
	private final int[] compressed_block_offsets;
	private int current_block_index = 0;
	private int current_block_skip = 0;
	private byte[] compressed_buf;
	private int uncompressed_block_index = -1;
	private byte[] uncompressed_buf;
	private int uncompressed_len = -1; // -1 means not initialized

	public SnappyInputStream(RandomInputStream input, long baseOffset, int block_size, int[] compressed_block_sizes, int[] compressed_block_offsets) throws IOException {
		super(input.getFile());
		
		this.block_size = block_size;
		this.snappy = new Snappy.Factory().newInstance();
		this.baseOffset = baseOffset;
		this.compressed_block_sizes = compressed_block_sizes;
		this.compressed_block_offsets = compressed_block_offsets;
		this.compressed_buf = new byte[snappy.maxCompressedLength(block_size)];
		this.uncompressed_buf = new byte[block_size];
	}

	@Override public void seek(long n) throws IOException {
		int offset = (int) n;
		int block_index = offset / block_size;
		int block_skip = offset - (block_index * block_size);
		
		this.current_block_index = block_index;
		this.current_block_skip = block_skip;
		prepareBuffer();
	}
	
	private void prepareBuffer() throws IOException {
		int block_index = current_block_index;
		
		// if uncompressed_block_index is already equal to the requested block_index
		// then we do not need to re-decompress again
		if (uncompressed_block_index != block_index) {
			super.seek(baseOffset + compressed_block_offsets[block_index]);
			super.read(compressed_buf, 0, compressed_block_sizes[block_index]);
			uncompressed_len = snappy.decompress(compressed_buf, 0, uncompressed_buf, 0, compressed_block_sizes[block_index]);
			if (uncompressed_len < 0) {
				throw new IOException("Error in decompressing: " + uncompressed_len);
			}
			uncompressed_block_index = block_index;
		}
	}

	@Override public int read() throws IOException {
		if (uncompressed_len == -1) {
			prepareBuffer();
		}
		
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
		if (uncompressed_len == -1) {
			prepareBuffer();
		}
		
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
	
	@Override public int read(byte[] buffer) throws IOException {
		return read(buffer, 0, buffer.length);
	}
	
	@Override public long getFilePointer() throws IOException {
		return current_block_index * block_size + current_block_skip;
	}
	
	@Override public long skip(long n) throws IOException {
		seek(getFilePointer() + n);
		return n;
	}
	
	public static SnappyInputStream getInstanceFromAttributes(RandomInputStream input, ValueMap sectionAttributes, long sectionContentOffset) throws IOException {
		int compressionVersion = sectionAttributes.getInt("compression.version", 0);
		if (compressionVersion > 1) {
			throw new IOException("Compression version " + compressionVersion + " is not supported");
		}
		ValueMap compressionInfo = sectionAttributes.getSimpleMap("compression.info");
		int block_size = compressionInfo.getInt("block_size");
		int[] compressed_block_sizes = compressionInfo.getIntArray("compressed_block_sizes");
		int[] compressed_block_offsets = new int[compressed_block_sizes.length + 1];
		{ // convert compressed_block_sizes into offsets
			int c = 0;
			for (int i = 0, len = compressed_block_sizes.length; i < len; i++) {
				compressed_block_offsets[i] = c;
				c += compressed_block_sizes[i];
			}
			compressed_block_offsets[compressed_block_sizes.length] = c;
		}
		return new SnappyInputStream(input, sectionContentOffset, block_size, compressed_block_sizes, compressed_block_offsets);
	}
}