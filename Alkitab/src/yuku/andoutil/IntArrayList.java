package yuku.andoutil;

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
}
