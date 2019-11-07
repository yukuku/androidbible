package yuku.alkitabintegration;

public class ConnectionResult {
	/** The connection was successful. */
	public static final int SUCCESS = 0;

	/** Alkitab app is missing on this device. */
	public static final int APP_MISSING = 1;
	
	/** The installed version of Alkitab app is out of date. */ 
	public static final int APP_VERSION_UPDATE_REQUIRED = 2;
	
	/** The provider component of Alkitab has been disabled on this device. */ 
	public static final int PROVIDER_DISABLED = 3;

	/** An internal error occurred. Retrying should resolve the problem. */
	public static final int INTERNAL_ERROR = 8;
}
