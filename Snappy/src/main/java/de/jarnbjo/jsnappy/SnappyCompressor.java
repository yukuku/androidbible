/*
 *  Copyright 2011 Tor-Einar Jarnbjo
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.jarnbjo.jsnappy;

/**
 * This class provide utility methods for compressing 
 * data blocks using the Snappy algorithm.
 * 
 * @author Tor-Einar Jarnbjo
 * @since 1.0
 */
public class SnappyCompressor {

	/**
	 * Default effort used for compression
	 */
	public static final int DEFAULT_EFFORT = 1;

	private SnappyCompressor() {
	}
	
	/**
	 * Equivalent to <code>compress(in, 0, in.length, null)</code>.
	 * @param in data to be compressed
	 * @return compressed data block
	 */
	public static Buffer compress(byte[] in) {
		return compress(in, 0, in.length, null);
	}

	/**
	 * Equivalent to <code>compress(in, 0, in.length, out)</code>.
	 * @param in data to be compressed
	 * @param out Buffer for compressed data block
	 * @return reference to <code>out</code>
	 */
	public static Buffer compress(byte[] in, Buffer out) {
		return compress(in, 0, in.length, out);
	}

	/**
	 * Equivalent to <code>compress(in, offset, length, null)</code>.
	 * @param in data to be compressed
	 * @param offset offset in <code>in<code>, on which encoding is started
	 * @param length number of bytes read from the input block 
	 * @return compressed data block
	 */
	public static Buffer compress(byte[] in, int offset, int length) {
		return compress(in, offset, length, null);
	}

	/**
	 * Equivalent to <code>compress(in.getData(), 0, in.getLength(), null)</code>.
	 * @param in data to be compressed
	 * @return compressed data block
	 */
	public static Buffer compress(Buffer in) {
		return compress(in.getData(), 0, in.getLength(), null);
	}

	/**
	 * Equivalent to <code>compress(in.getData(), 0, in.getLength(), out)</code>.
	 * @param in data to be compressed
	 * @param out buffer for decompressed data block
	 * @return reference to <code>out</code>
	 */
	public static Buffer compress(Buffer in, Buffer out) {
		return compress(in.getData(), 0, in.getLength(), out);
	}

	/**
	 * Equivalent to <code>compress(in.getData(), 0, in.getLength(), out, DEFAULT_EFFORT)</code>.
	 * @param in input data
	 * @param offset offset into input data
	 * @param length length of input data
	 * @param out output buffer or null (new buffer will be allocated)
	 * @return reference to <code>out</code>
	 */
	public static Buffer compress(byte[] in, int offset, int length, Buffer out) {
		return compress(in, offset, length, out, DEFAULT_EFFORT);
	}

	/**
	 * <p>
	 * Compress the data contained in <code>in</code> from <code>offset</code>
	 * and <code>length</code> bytes. If an output buffer is provided, the buffer
	 * is reused for the compressed data. If the buffer is too small, its capacity
	 * is expanded to fit the result. If a <code>null</code> argument is passed,
	 * a new buffer is allocated.
	 * </p>
	 * 
	 * <p>
	 * The compression effort can be set from 1 (fastest, less compression) to 100 (slowest, highest compression).
	 * </p>
	 * 
	 * @param in input data
	 * @param offset offset into input data
	 * @param length length of input data
	 * @param out output buffer or null (new buffer will be allocated)
	 * @param effort compression effort
	 * @return
	 */
	public static Buffer compress(byte[] in, int offset, int length, Buffer out, int effort) {

		if(effort < 1 || effort > 100) {
			throw new IllegalArgumentException("Compression effort must be an integer from 0 (fastest, less compression) to 100 (slowest, highest compression)");
		}
		
		if(effort < 30) {
			return TableBasedCompressor.compress(in, offset, length, out);
		}
		else if(effort < 70) {
			return MapBasedCompressor.compress(in, offset, length, out, true);
		}
		else {
			return MapBasedCompressor.compress(in, offset, length, out, false);
		}
	}

}
