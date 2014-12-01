package yuku.kirimfidbek;

import android.os.SystemClock;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;

public class TangkapSemuaEror {
	public static final String TAG = TangkapSemuaEror.class.getSimpleName(); //$NON-NLS-1$
	
	final PengirimFidbek pengirimFidbek_;
	private UncaughtExceptionHandler defaultUEH_;
	
	private UncaughtExceptionHandler handler_ = new UncaughtExceptionHandler() {
		@Override public void uncaughtException(Thread t, Throwable e) {
			StringWriter sw = new StringWriter(4000);
			e.printStackTrace(new PrintWriter(sw, true));
			
			String pesanDueh = "[DUEH2] thread: " + t.getName() + " (" + t.getId() + ") " + e.getClass().getName() + ": " + e.getMessage() + "\n" + sw.toString(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			
			Log.w(TAG, pesanDueh);
			
			pengirimFidbek_.tambah(pesanDueh);
			pengirimFidbek_.cobaKirim();
			
			// Coba tunggu 3 detik sebelum ancur. Ato ancur aja ya?
			SystemClock.sleep(3000);
			
			Log.w(TAG, "DUEH selesai."); //$NON-NLS-1$
			
			// panggil yang lama (dialog force close)
			if (defaultUEH_ != null) {
				defaultUEH_.uncaughtException(t, e);
			}
		}
	};
	
	TangkapSemuaEror(PengirimFidbek pengirimFidbek) {
		pengirimFidbek_ = pengirimFidbek;
	}
	
	public void aktifkan() {
		defaultUEH_ = Thread.getDefaultUncaughtExceptionHandler();
		Log.d(TAG, "defaultUEH_: " + defaultUEH_);
		Thread.setDefaultUncaughtExceptionHandler(handler_);
	}
}
