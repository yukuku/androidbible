package yuku.afw;

import android.app.Application;
import android.content.Context;

public class App extends Application {
    private static boolean initted = false;

    public static Context context;

    @Override
    public void onCreate() {
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
}
