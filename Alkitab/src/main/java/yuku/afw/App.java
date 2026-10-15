package yuku.afw;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import java.util.concurrent.atomic.AtomicBoolean;

public class App extends Application {
    private static final AtomicBoolean initted = new AtomicBoolean(false);

    @SuppressLint("StaticFieldLeak") // This is application context, safe to keep references to
    public static Context context;

    @Override
    public void onCreate() {
        super.onCreate();

        initWithAppContext(getApplicationContext());
    }

    public static void initWithAppContext(Context applicationContext) {
        if (initted.getAndSet(true)) return;

        if (applicationContext == null) {
            throw new RuntimeException("Application context can't be null");
        }

        context = applicationContext;
    }
}
