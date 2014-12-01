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

class IntListHashMap {

	private int[][] content;
	private int buckets;

	private IntIterator iterator = new IntIterator();

	IntListHashMap(int buckets) {
		this.buckets = buckets;
		content = new int[buckets][];
	}

	void put(int key, int value) {
		int bucket = key % buckets;
		if(bucket < 0) {
			bucket = -bucket;
		}
		int[] data = content[bucket];
		if(data == null) {
			data = new int[33];
			content[bucket] = data;

		}
		int off = data[0] * 2 + 1;
		// eliminate duiplicates
		if(off == 1 || data[off-2] != key || data[off-1] != value) {
			if(off >= data.length) {
				int[] ndata = new int[(data.length - 1) * 2 + 1];
				System.arraycopy(data, 0, ndata, 0, off);
				data = ndata;
				content[bucket] = data;
			}
			data[off++] = key;
			data[off++] = value;
			data[0]++;
		}
	}

	IntIterator getReverse(int key) {
		int bucket = key % buckets;
		if(bucket < 0) {
			bucket = -bucket;
		}
		int[] data = content[bucket];

		if(data == null) {
			return IntIterator.EMTPY_ITERATOR;
		}
		else {
			//return new IntIterator(data, key);
			iterator.data = data;
			iterator.offset = data[0] * 2 + 1;
			iterator.key = key;
			return iterator;
		}

	}

	int getFirstHit(int key, int maxValue) {		
		int bucket = key % buckets;
		if(bucket < 0) {
			bucket = -bucket;
		}
		int[] data = content[bucket];

		if(data == null) {
			return -1;
		}
		else {
			int offset = data[0] * 2 - 1;
			while(offset > 0) {
				if(data[offset] == key && data[offset+1] <= maxValue) {
					return data[offset+1];
				}
				offset -= 2;
			}
		}
		return -1;
	}
}
