package yuku.alkitab.base.model;

public class SyncShadow {
	/** Markers and labels */
	public static final String SYNC_SET_MABEL = "mabel";
	/** History (recent verses) */
	public static final String SYNC_SET_HISTORY = "history";

	public static final String[] ALL_SYNC_SET_NAMES = {
		SYNC_SET_MABEL,
		SYNC_SET_HISTORY,
	};

	public String syncSetName;
	public int revno;
	public byte[] data;
}
