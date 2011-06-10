/*
    Bible Plus A Bible Reader for Blackberry
    Copyright (C) 2010 Yohanes Nugroho

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

    Yohanes Nugroho (yohanes@gmail.com)
 */

package com.compactbyte.bibleplus.reader;

import java.io.*;

/**
 * Represents PDBHeader
 */
class PDBHeader {
	/* 78 bytes total */
	// char name[ dmDBNameLength ]; /*0*/
	private byte[] _name;
	private int attributes; // 32
	private int version; // 34
	private int create_time; // 36
	private int modify_time; // 40
	private int backup_time; // 44
	private int modification_number; // 48
	private int app_info_id; // 52
	private int sort_info_id; // 56
	private String type_str; // 4 char (offset 60)
	private byte type[];
	private String creator_str; // 4 char (offset 64)
	private byte creator[];
	private int id_seed; // 68
	private int next_record_list; // 72
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
		attributes = dis.readShort();
		version = dis.readShort();
		create_time = readInt(dis);
		modify_time = readInt(dis);
		backup_time = readInt(dis);
		modification_number = readInt(dis);
		app_info_id = readInt(dis);
		sort_info_id = readInt(dis);
		type = new byte[4];
		dis.read(type, 0, 4);
		type_str = new String(type);
		creator = new byte[4];
		dis.read(creator, 0, 4);
		creator_str = new String(creator);
		id_seed = readInt(dis);
		next_record_list = readInt(dis);
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
		StringBuffer sb = new StringBuffer();
		sb.append("Name: ").append(getName("UTF-8")).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		sb.append("type_str: ").append(type_str).append("\n");  //$NON-NLS-1$//$NON-NLS-2$
		sb.append("creator_str: ").append(creator_str).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("num records: ").append(num_records).append("\n");  //$NON-NLS-1$//$NON-NLS-2$
		return sb.toString();
	}

}
