package yuku.alkitab.base.model;

public class SyncShadow {
	/** Markers and labels */
	public static final String SYNC_SET_MABEL = "mabel";
	/** History (recent verses) */
	public static final String SYNC_SET_HISTORY = "history";
	/** Pins (progress marks) */
	public static final String SYNC_SET_PINS = "pins";
	/** Reading plan progress */
	public static final String SYNC_SET_RP = "rp";

	public static final String[] ALL_SYNC_SET_NAMES = {
		SYNC_SET_MABEL,
		SYNC_SET_RP,
		SYNC_SET_HISTORY,
		SYNC_SET_PINS,
	};

	public String syncSetName;
	public int revno;
	public byte[] data;
}
