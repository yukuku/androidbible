package yuku.alkitab.base.syncadapter;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class SyncService extends Service {
	public static final String TAG = SyncService.class.getSimpleName();

	SyncAdapter syncAdapter;

	@Override
	public IBinder onBind(final Intent intent) {
		syncAdapter = new SyncAdapter(this, true);
		return syncAdapter.getSyncAdapterBinder();
	}
}
