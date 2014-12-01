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

class IntIterator {

	int[] data;
	int offset;
	int key;

	IntIterator() {
	}

	IntIterator(int[] data, int key) {
		this.data = data;
		this.key = key;
		this.offset = data[0] * 2 + 1;
	}

	boolean next() {
		while(offset > 2) {
			offset -= 2;
			if(data[offset] == key) {
				return true;
			}
		}
		return false;
	}

	int get() {
		return data[offset+1];
	}

	static IntIterator EMTPY_ITERATOR = new IntIterator() {

		@Override
		boolean next() {
			return false;
		}

		@Override
		int get() {
			return 0;
		}

	};

}
