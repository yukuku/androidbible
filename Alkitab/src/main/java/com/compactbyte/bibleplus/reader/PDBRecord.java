/*
    Bible Plus A Bible Reader for Blackberry
    Copyright (C) 2010 Yohanes Nugroho

    Bibleplus is dual licensed under either:
    - Apache license, Version 2.0, (http://www.apache.org/licenses/LICENSE-2.0)
    - GPL version 3 or later (http://www.gnu.org/licenses/)

    Yohanes Nugroho (yohanes@gmail.com)
 */

package com.compactbyte.bibleplus.reader;

/**
 * Represents one record inside PDB File
 */
class PDBRecord {

	private byte[] data;

	public PDBRecord(byte[] data) {
		this.data = data;
	}

	public byte[] getData() {
		return data;
	}
}
