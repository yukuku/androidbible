/*
    Bible Plus A Bible Reader for Blackberry
    Copyright (C) 2010 Yohanes Nugroho

    Bibleplus is dual licensed under either:
    - Apache license, Version 2.0, (http://www.apache.org/licenses/LICENSE-2.0)
    - GPL version 3 or later (http://www.gnu.org/licenses/)

    Yohanes Nugroho (yohanes@gmail.com)
 */

package com.compactbyte.bibleplus.reader;

import java.io.*;

/**
 * Represents PDBHeader
 */
public class PDBHeader {
	/* 78 bytes total */
	// char name[ dmDBNameLength ]; /*0*/
	private byte[] _name;
	private String type_str; // 4 char (offset 60)
	private String creator_str; // 4 char (offset 64)
	private int num_records; // 76-78

	private byte[] headerdata;

	public PDBHeader(byte[] headerdata) {
		this.headerdata = headerdata;
	}

	public String getCreator() {
		return creator_str;
	}

	String getName(String encoding) {
		return Util.readStringTrimZero(_name, 0, 32, encoding);
	}

	public int getRecordCount() {
		return num_records;
	}

	public String getType() {
		return type_str;
	}

	public void load() throws IOException {
		ByteArrayInputStream bis = new ByteArrayInputStream(headerdata);
		DataInputStream dis = new DataInputStream(bis);
		_name = new byte[32];
		dis.read(_name);
		dis.readShort();
		dis.readShort();
		readInt(dis);
		readInt(dis);
		readInt(dis);
		readInt(dis);
		readInt(dis);
		readInt(dis);
		final byte[] type = new byte[4];
		dis.read(type, 0, 4);
		type_str = new String(type);
		final byte[] creator = new byte[4];
		dis.read(creator, 0, 4);
		creator_str = new String(creator);
		readInt(dis);
		readInt(dis);
		num_records = readShort(dis);
	}

	int readInt(DataInputStream dis) throws IOException {
		return dis.readInt();
	}

	int readShort(DataInputStream dis) throws IOException {
		return dis.readShort();
	}

	@Override
	public String toString() {
		return "Name: " + getName("UTF-8") + "\n" +
			"type_str: " + type_str + "\n" +
			"creator_str: " + creator_str + "\n" +
			"num records: " + num_records + "\n";
	}

}
