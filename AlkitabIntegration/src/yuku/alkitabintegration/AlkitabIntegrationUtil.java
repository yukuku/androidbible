package yuku.alkitabintegration;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.util.Log;

public class AlkitabIntegrationUtil {
	public static final String TAG = AlkitabIntegrationUtil.class.getSimpleName();
	
	public static final String DEFAULT_ALKITAB_PROVIDER_AUTHORITY = "yuku.alkitab.provider";
	private static String overridenProviderAuthority = null;
	
	/**
	 * Minimum Alkitab app package version (declared in AndroidManifest.xml android:versionCode) 
	 * in order to be compatible with this client version.
	 */
	public static final int ALKITAB_APP_VERSION_CODE = 101;
	
	public static String getProviderAuthority() {
		return overridenProviderAuthority != null? overridenProviderAuthority: DEFAULT_ALKITAB_PROVIDER_AUTHORITY;
	}
	
	/** Verifies that Alkitab app is installed and enabled on this device, 
	 * and that the version installed on this device is no older than the one required by this client.
	 * @return status code indicating whether there was an error. Can be one of following: {@link ConnectionResult#SUCCESS}, {@link ConnectionResult#APP_MISSING}, {@link ConnectionResult#APP_VERSION_UPDATE_REQUIRED}, {@link ConnectionResult#PROVIDER_DISABLED}, {@link ConnectionResult#INTERNAL_ERROR}
	 */
	public static int isIntegrationAvailable(Context context) {
		PackageManager pm = context.getPackageManager();
		
		ProviderInfo providerInfo = pm.resolveContentProvider(getProviderAuthority(), 0);
		if (providerInfo == null) {
			return ConnectionResult.APP_MISSING;
		}
		
		if (! providerInfo.enabled) {
			return ConnectionResult.PROVIDER_DISABLED;
		}
		
		try {
			PackageInfo packageInfo = pm.getPackageInfo(providerInfo.packageName, 0);
			if (packageInfo.versionCode < ALKITAB_APP_VERSION_CODE) {
				return ConnectionResult.APP_VERSION_UPDATE_REQUIRED;
			}
			
			return ConnectionResult.SUCCESS;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Having a provider should have a correct package also", e);
			return ConnectionResult.INTERNAL_ERROR;
		}
	}

	public static void setOverridenProviderAuthority(final String overridenProviderAuthority) {
		AlkitabIntegrationUtil.overridenProviderAuthority = overridenProviderAuthority;
	}
}
