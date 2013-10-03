package yuku.alkitabconverter.util;

import java.util.Arrays;

/** Non-parcelable version for desktop */
public class IntArrayList {
	int[] buf;
	int cap;
	int len;

	public IntArrayList() {
		this(16);
	}

	public IntArrayList(int cap) {
		buf = new int[cap];
		this.cap = cap;
		this.len = 0;
	}

	public int size() {
		return this.len;
	}

	private void perbesar() {
		this.cap <<= 1;
		int[] baru = new int[this.cap];
		System.arraycopy(this.buf, 0, baru, 0, this.len);
		this.buf = baru;
	}

	public void add(int a) {
		if (this.len >= this.cap) {
			perbesar();
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
		if (!Arrays.equals(buf, that.buf)) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int result = Arrays.hashCode(buf);
		result = 31 * result + len;
		return result;
	}
}
