package yuku.alkitab.base.model;

import java.util.Date;

public class Bookmark2 {
	public long _id;
	public int ari;
	public int kind;
	public String caption;
	public Date addTime;
	public Date modifyTime;
	
	/**
	 * Create without _id
	 */
	public Bookmark2(int ari, int kind, String caption, Date addTime, Date modifyTime) {
		this.ari = ari;
		this.kind = kind;
		this.caption = caption;
		this.addTime = addTime;
		this.modifyTime = modifyTime;
	}
}
