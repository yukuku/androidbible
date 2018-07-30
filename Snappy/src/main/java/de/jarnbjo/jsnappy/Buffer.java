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
 * Reusable byte array buffer, which can be used for input/output
 * in the Snappy encoder oder decoder.
 *
 * @author Tor-Einar Jarnbjo
 * @since 1.0
 */
public class Buffer {

	private byte[] data;
	private int length;

	/**
	 * Creates an unitialized buffer.
	 */
	public Buffer() {
	}

	/**
	 * Creates an initialized buffer with the specified capacity.
	 *
	 * @param capacity initial buffer length in bytes
	 */
	public Buffer(int capacity) {
		data = new byte[capacity];
	}

	/**
	 * Ensures that the buffer has a capacity to store the number
	 * of bytes. If the internal buffer is too small, a new buffer
	 * is allocated and the content of the old buffer copied to the
	 * new buffer. If the old buffer is already large enough, no
	 * changes are made.
	 *
	 * @param capacity buffer length in bytes
	 */
	public void ensureCapacity(int capacity) {
		if (data == null) {
			data = new byte[capacity];
		} else if (data.length < capacity) {
			byte[] nb = new byte[capacity];
			System.arraycopy(data, 0, nb, 0, data.length);
			data = nb;
		}
	}

	/**
	 * Returns the byte array used as a backing store for this
	 * buffer. Note that invoking ensureCapacity can cause
	 * the backing array to be replaced.
	 *
	 * @return
	 */
	public byte[] getData() {
		return data;
	}

	/**
	 * Returns the length of this buffer. The backing array should
	 * contain valid data from index 0 to length - 1.
	 *
	 * @return
	 */
	public int getLength() {
		return length;
	}

	/**
	 * Sets the length of this buffer. The backing array should
	 * contain valid data from index 0 to length - 1.
	 *
	 * @param length buffer length
	 * @throws IllegalStateException    if the buffer is not yet initialized
	 * @throws IllegalArgumentException if the requested length exceeds the buffers capacity
	 */
	public void setLength(int length) throws IllegalStateException, IllegalArgumentException {
		if (data == null) {
			throw new IllegalStateException("Internal buffer not initialized");
		}
		if (length > data.length) {
			throw new IllegalArgumentException("Internal buffer length (" + data.length + ") is less than length argument (" + length + ")");
		}
		this.length = length;
	}

	/**
	 * Returns a copy of the buffers internal array in a newly allocated
	 * byte array with the buffer's length.
	 *
	 * @return
	 */
	public byte[] toByteArray() {
		byte[] res = new byte[length];
		System.arraycopy(data, 0, res, 0, length);
		return res;
	}

}
