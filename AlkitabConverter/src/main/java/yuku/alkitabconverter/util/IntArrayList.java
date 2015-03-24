package yuku.alkitabconverter.util;

import java.util.Arrays;

/** Non-parcelable version for desktop */
public class IntArrayList {
	int[] buf;
	int len;

	public IntArrayList() {
		this(16);
	}

	public IntArrayList(int cap) {
		buf = new int[cap];
		this.len = 0;
	}

	public int size() {
		return this.len;
	}

	private void expand() {
		int[] newArray = new int[this.buf.length << 1];
		System.arraycopy(this.buf, 0, newArray, 0, this.len);
		this.buf = newArray;
	}

	public void add(int a) {
		if (this.len >= this.buf.length) {
			expand();
		}

		this.buf[this.len++] = a;
	}

	public int pop() {
		return this.buf[--this.len];
	}

	public int get(int i) {
		return this.buf[i];
	}

	public void set(int i, int a) {
		this.buf[i] = a;
	}

	/**
	 * DANGEROUS. Do not mess with this buffer carelessly.
	 * Use this for faster access to the underlying buffer only.
	 * The length of the returned array will be the same or larger than {@link #size()}.
	 */
	public int[] buffer() {
		return buf;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(this.len * 8);
		sb.append('[');
		for (int i = 0; i < len; i++) {
			sb.append(buf[i]);
			if (i != this.len - 1) {
				sb.append(", "); //$NON-NLS-1$
			}
		}
		sb.append(']');
		return sb.toString();
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		final IntArrayList that = (IntArrayList) o;

		if (len != that.len) return false;
		for (int i = 0; i < len; i++) {
			if (buf[i] != that.buf[i]) return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		int result = Arrays.hashCode(buf);
		result = 31 * result + len;
		return result;
	}
}
