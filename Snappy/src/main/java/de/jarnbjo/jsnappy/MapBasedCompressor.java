package de.jarnbjo.jsnappy;

class MapBasedCompressor {

	public static final int DEFAULT_MAX_OFFSET = 64*1024;
	
	static Buffer compress(byte[] in, int offset, int length, Buffer out, boolean useFirstHit) {

		if(out == null) {
			out = new Buffer(length * 6 / 5);
		}
		else {
			out.ensureCapacity(length * 6 / 5);
		}

		byte[] target = out.getData();
		int targetIndex = 0;
		int lasthit = offset;

		int l = length;
		while(l>0) {
			if(l>=128) {
				target[targetIndex++] = (byte)(0x80 | (l&0x7f));
			}
			else {
				target[targetIndex++] = (byte)l;
			}
			l >>= 7;
		}

		IntListHashMap ilhm = new IntListHashMap(length / 13);

		for(int i = offset; i+4 < length && i < offset+4; i++) {
			ilhm.put(toInt(in, i), i);
		}

		for(int i = offset+4; i < offset + length; i++) {
			Hit h = search(in, i, length, ilhm, useFirstHit);
			if(i+4 < offset + length) {
				ilhm.put(toInt(in, i), i);
			}
			if(h != null) {
				if(lasthit < i) {
					int len = i - lasthit - 1;
					if (len < 60) {
						target[targetIndex++] = (byte)(len<<2);
					}
					else if (len < 0x100) {
						target[targetIndex++] = (byte)(60<<2);
						target[targetIndex++] = (byte)len;
					}
					else if (len < 0x10000) {
						target[targetIndex++] = (byte)(61<<2);
						target[targetIndex++] = (byte)len;
						target[targetIndex++] = (byte)(len>>8);
					}
					else if (len < 0x1000000) {
						target[targetIndex++] = (byte)(62<<2);
						target[targetIndex++] = (byte)len;
						target[targetIndex++] = (byte)(len>>8);
						target[targetIndex++] = (byte)(len>>16);
					}
					else {
						target[targetIndex++] = (byte)(63<<2);
						target[targetIndex++] = (byte)len;
						target[targetIndex++] = (byte)(len>>8);
						target[targetIndex++] = (byte)(len>>16);
						target[targetIndex++] = (byte)(len>>24);
					}
					System.arraycopy(in, lasthit, target, targetIndex, i-lasthit);
					targetIndex += i - lasthit;
					lasthit = i;
				}
				if(h.length <= 11 && h.offset < 2048) {
					target[targetIndex] = 1;
					target[targetIndex] |= ((h.length-4)<<2);
					target[targetIndex++] |= (h.offset>>3)&0xe0;
					target[targetIndex++] = (byte)(h.offset&0xff);
				}
				else if (h.offset < 65536) {
					target[targetIndex] = 2;
					target[targetIndex++] |= ((h.length-1)<<2);
					target[targetIndex++] = (byte)(h.offset);
					target[targetIndex++] = (byte)(h.offset>>8);
				}
				else {
					target[targetIndex] = 3;
					target[targetIndex++] |= ((h.length-1)<<2);
					target[targetIndex++] = (byte)(h.offset);
					target[targetIndex++] = (byte)(h.offset>>8);
					target[targetIndex++] = (byte)(h.offset>>16);
					target[targetIndex++] = (byte)(h.offset>>24);
				}
				for(; i < lasthit; i++) {
					if(i + 4 < in.length) {
						ilhm.put(toInt(in, i), i);
					}
				}
				lasthit = i + h.length;
				while(i<lasthit-1) {
					if(i + 4 < in.length) {
						ilhm.put(toInt(in, i), i);
					}
					i++;
				}
			}
			else {
				if(i+4 < length) {
					ilhm.put(toInt(in, i), i);
				}
			}
		}

		if (lasthit < offset + length) {
			int len = (offset+length) - lasthit - 1;
			if (len < 60) {
				target[targetIndex++] = (byte)(len<<2);
			}
			else if (len < 0x100) {
				target[targetIndex++] = (byte)(60<<2);
				target[targetIndex++] = (byte)len;
			}
			else if (len < 0x10000) {
				target[targetIndex++] = (byte)(61<<2);
				target[targetIndex++] = (byte)len;
				target[targetIndex++] = (byte)(len>>8);
			}
			else if (len < 0x1000000) {
				target[targetIndex++] = (byte)(62<<2);
				target[targetIndex++] = (byte)len;
				target[targetIndex++] = (byte)(len>>8);
				target[targetIndex++] = (byte)(len>>16);
			}
			else {
				target[targetIndex++] = (byte)(63<<2);
				target[targetIndex++] = (byte)len;
				target[targetIndex++] = (byte)(len>>8);
				target[targetIndex++] = (byte)(len>>16);
				target[targetIndex++] = (byte)(len>>24);
			}
			System.arraycopy(in, lasthit, target, targetIndex, length - lasthit);
			targetIndex += length - lasthit;
		}

		out.setLength(targetIndex);
		return out;
	}

	private static Hit search(byte[] source, int index, int length, IntListHashMap map, boolean useFirstHit) {

		if(index + 4 >= length) {
			// We won't search for backward references if there are less than
			// four bytes left to encode, since no relevant compression can be
			// achieved and the map used to store possible back references uses
			// a four byte key.
			return null;
		}

		if(index > 0 &&
				source[index] == source[index-1] &&
				source[index] == source[index+1] &&
		        source[index] == source[index+2] &&
		        source[index] == source[index+3]) {

			// at least five consecutive bytes, so we do
			// run-length-encoding of the last four
			// (three bytes are required for the encoding,
			// so less than four bytes cannot be compressed)

			int len = 0;
			for(int i = index; len < 64 && i < length && source[index] == source[i]; i++, len++);
			return new Hit(1, len);
		}

		if(useFirstHit) {
			int fp = map.getFirstHit(toInt(source, index), index-4);
			if(fp < 0) {
				return null;
			}
			int offset = index - fp;
			int l = 0;
			for(int o = fp, io = index; io < length && source[o] == source[io] && o < index && l < 64; o++, io++) {
				l++;
			}
			return new Hit(offset, l);
		}
		else {
			IntIterator ii = map.getReverse(toInt(source, index));
			if(ii == null) {
				return null;
			}
	
			Hit res = null;
	
			while(ii.next()) {
				int fp = ii.get();
				int offset = index - fp;
				if(offset > DEFAULT_MAX_OFFSET) {
					break;
				}
				if(offset >= 4) {
					int l = 0;
					for(int o = index - offset, io = index; io < length && source[o] == source[io] && o < index && l < 64; o++, io++) {
						l++;
					}
					if (res == null) {
						res = new Hit();
						res.offset = offset;
						res.length = l;
					}
					else if(l > res.length) {
						res.offset = offset;
						res.length = l;
					}
				}
			}
	
			return res;
		}
	}

	private static int toInt(byte[] data, int offset) {
		return
			((data[offset]&0xff)<<24) |
			((data[offset+1]&0xff)<<16) |
			((data[offset+2]&0xff)<<8) |
			(data[offset+3]&0xff);
	}

	private static class Hit {
		int offset, length;
		Hit() {
		}
		Hit(int offset, int length) {
			this.offset = offset;
			this.length = length;
		}
	}

}