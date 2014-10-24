package yuku.alkitab.base.model;

public class SyncShadow {
	/** Markers and labels */
	public static final String SYNC_SET_MABEL = "mabel";

	public static final String[] ALL_SYNC_SETS = {
		SYNC_SET_MABEL
	};

	public String syncSetName;
	public int revno;
	public byte[] data;
}
