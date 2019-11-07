package yuku.afw;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

public class App extends Application {
	private static boolean initted = false;
	private static PackageInfo packageInfo;
	
	public static Context context;
	
	@Override public void onCreate() {
		super.onCreate();
		
		initWithAppContext(getApplicationContext());
	}

	public static void initWithAppContext(Context applicationContext) {
		if (initted) return;
		
		if (applicationContext == null) {
			throw new RuntimeException("Application context can't be null");
		}
		
		initted = true;
		context = applicationContext;
	}
	
	private static void initPackageInfo() {
		if (packageInfo == null) {
			try {
				packageInfo = App.context.getPackageManager().getPackageInfo(App.context.getPackageName(), 0);
			} catch (NameNotFoundException e) {
				throw new RuntimeException("NameNotFoundException when querying own package. Should not happen", e);
			}
		}
	}
	
	public static String getVersionName() {
		initPackageInfo();
		return packageInfo.versionName;
	}
	
	public static int getVersionCode() {
		initPackageInfo();
		return packageInfo.versionCode;
	}
}
