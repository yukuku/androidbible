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

import java.util.Arrays;

/**
 * This class provide utility methods for decompressing
 * data blocks using the Snappy algorithm.
 *
 * @author Tor-Einar Jarnbjo
 * @since 1.0
 */
public class SnappyDecompressor {

	private SnappyDecompressor() {
	}

	/**
	 * Equivalent to <code>decompress(in, 0, in.length, null)</code>.
	 *
	 * @param in compressed data block
	 * @return decompressed data block
	 */
	public static Buffer decompress(byte[] in) {
		return decompress(in, 0, in.length, null);
	}

	/**
	 * Equivalent to <code>decompress(in, 0, in.length, out)</code>.
	 *
	 * @param in  compressed data block
	 * @param out Buffer for decompressed data block
	 * @return reference to <code>out</code>
	 */
	public static Buffer decompress(byte[] in, Buffer out) {
		return decompress(in, 0, in.length, out);
	}

	/**
	 * Equivalent to <code>decompress(in, offset, length, null)</code>.
	 *
	 * @param in     byte array containing the compressed data block
	 * @param offset offset in <code>in<code>, on which decoding is started
	 * @param length length of compressed data block
	 * @return decompressed data block
	 */
	public static Buffer decompress(byte[] in, int offset, int length) {
		return decompress(in, offset, length, null);
	}

	/**
	 * Equivalent to <code>decompress(in.getData(), 0, in.getLength(), null)</code>.
	 *
	 * @param in compressed data block
	 * @return decompressed data block
	 */
	public static Buffer decompress(Buffer in) {
		return decompress(in.getData(), 0, in.getLength());
	}

	/**
	 * Equivalent to <code>decompress(in.getData(), 0, in.getLength(), out)</code>.
	 *
	 * @param in  compressed data block
	 * @param out Buffer for decompressed data block
	 * @return reference to <code>out</code>
	 */
	public static Buffer decompress(Buffer in, Buffer out) {
		return decompress(in.getData(), 0, in.getLength(), out);
	}

	/**
	 * Decompress the data contained in <code>in</code> from <code>offset</code>
	 * and <code>length</code> bytes. If an output buffer is provided, the buffer
	 * is reused for the decompressed data. If the buffer is too small, its capacity
	 * is expanded to fit the result. If a <code>null</code> argument is passed,
	 * a new buffer is allocated.
	 *
	 * @param in
	 * @param offset
	 * @param length
	 * @param out
	 * @return
	 * @throws FormatViolationException if the input data is invalid
	 */
	public static Buffer decompress(byte[] in, int offset, int length, Buffer out) throws FormatViolationException {

		int i, l, o, c;
		int sourceIndex = offset, targetIndex = 0;
		int targetLength = 0;

		i = 0;
		do {
			targetLength += (in[sourceIndex] & 0x7f) << (i++ * 7);
		} while ((in[sourceIndex++] & 0x80) == 0x80);

		if (out == null) {
			out = new Buffer(targetLength);
		} else {
			out.ensureCapacity(targetLength);
		}

		out.setLength(targetLength);
		byte[] outBuffer = out.getData();

		while (sourceIndex < offset + length) {

			if (targetIndex >= targetLength) {
				throw new FormatViolationException("Superfluous input data encountered on offset " + sourceIndex, sourceIndex);
			}

			switch (in[sourceIndex] & 3) {
				case 0:
					l = (in[sourceIndex++] >> 2) & 0x3f;
					switch (l) {
						case 60:
							l = in[sourceIndex++] & 0xff;
							l++;
							break;
						case 61:
							l = in[sourceIndex++] & 0xff;
							l |= (in[sourceIndex++] & 0xff) << 8;
							l++;
							break;
						case 62:
							l = in[sourceIndex++] & 0xff;
							l |= (in[sourceIndex++] & 0xff) << 8;
							l |= (in[sourceIndex++] & 0xff) << 16;
							l++;
							break;
						case 63:
							l = in[sourceIndex++] & 0xff;
							l |= (in[sourceIndex++] & 0xff) << 8;
							l |= (in[sourceIndex++] & 0xff) << 16;
							l |= (in[sourceIndex++] & 0xff) << 24;
							l++;
							break;
						default:
							l++;
							break;
					}
					System.arraycopy(in, sourceIndex, outBuffer, targetIndex, l);
					sourceIndex += l;
					targetIndex += l;
					break;
				case 1:
					l = 4 + ((in[sourceIndex] >> 2) & 7);
					o = (in[sourceIndex++] & 0xe0) << 3;
					o |= in[sourceIndex++] & 0xff;
					if (l < o) {
						System.arraycopy(outBuffer, targetIndex - o, outBuffer, targetIndex, l);
						targetIndex += l;
					} else {
						if (o == 1) {
							Arrays.fill(outBuffer, targetIndex, targetIndex + l, outBuffer[targetIndex - 1]);
							targetIndex += l;
						} else {
							while (l > 0) {
								c = l > o ? o : l;
								System.arraycopy(outBuffer, targetIndex - o, outBuffer, targetIndex, c);
								targetIndex += c;
								l -= c;
							}
						}
					}
					break;
				case 2:
					l = ((in[sourceIndex++] >> 2) & 0x3f) + 1;
					o = in[sourceIndex++] & 0xff;
					o |= (in[sourceIndex++] & 0xff) << 8;
					if (l < o) {
						System.arraycopy(outBuffer, targetIndex - o, outBuffer, targetIndex, l);
						targetIndex += l;
					} else {
						while (l > 0) {
							c = l > o ? o : l;
							System.arraycopy(outBuffer, targetIndex - o, outBuffer, targetIndex, c);
							targetIndex += c;
							l -= c;
						}
					}
					break;
				case 3:
					l = ((in[sourceIndex++] >> 2) & 0x3f) + 1;
					o = in[sourceIndex++] & 0xff;
					o |= (in[sourceIndex++] & 0xff) << 8;
					o |= (in[sourceIndex++] & 0xff) << 16;
					o |= (in[sourceIndex++] & 0xff) << 24;
					if (l < o) {
						System.arraycopy(outBuffer, targetIndex - o, outBuffer, targetIndex, l);
						targetIndex += l;
					} else {
						if (o == 1) {
							Arrays.fill(outBuffer, targetIndex, targetIndex + l, outBuffer[targetIndex - 1]);
							targetIndex += l;
						} else {
							while (l > 0) {
								c = l > o ? o : l;
								System.arraycopy(outBuffer, targetIndex - o, outBuffer, targetIndex, c);
								targetIndex += c;
								l -= c;
							}
						}
					}
					break;
			}
		}

		return out;
	}

}
