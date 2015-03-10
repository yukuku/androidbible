package yuku.afw.storage;

public class InternalDb {
	public static final String TAG = InternalDb.class.getSimpleName();

	protected final InternalDbHelper helper;

	public InternalDb(InternalDbHelper helper) {
		this.helper = helper;
	}
}
