package yuku.alkitab.model;

import yuku.andoutil.Sqlitil;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.SQLException;
import android.database.sqlite.*;
import android.util.Log;

public class AlkitabDb extends SQLiteOpenHelper {
	public static final String KOLOM_waktuBukmak = "waktuBukmak";
	public static final String KOLOM_cuplikan = "cuplikan";
	public static final String KOLOM_alamat = "alamat";
	public static final String TABEL_Bukmak = "Bukmak";

	public static int getVersionCode(Context context) {
		PackageInfo packageInfo;
		try {
			packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
		} catch (NameNotFoundException e) {
			Log.w("db", "pake versi 0 databesnya!");
			return 0;
		}
		return packageInfo.versionCode;
	}

	public AlkitabDb(Context context) {
		super(context, AlkitabDb.class.getSimpleName(), null, getVersionCode(context));
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.d("db", "onCreate dipanggil");
		
		try {
			db.execSQL(String.format("create table %s (" +
					"_id integer primary key autoincrement, " +
					"%s text, " +
					"%s text, " +
					"%s integer)", TABEL_Bukmak, KOLOM_alamat, KOLOM_cuplikan, KOLOM_waktuBukmak));
			
			ContentValues values = new ContentValues();
			values.put(KOLOM_alamat, "Kej 1:1");
			values.put(KOLOM_cuplikan, "Pada mulanya Allah menciptakan langit dan bumi.");
			values.put(KOLOM_waktuBukmak, Sqlitil.nowDateTime());
			
			db.insert(TABEL_Bukmak, null, values);
		} catch (SQLException e) {
			Log.e("db", "ngaco!", e);
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// belum ada rencana apa2, biarkanlah
		Log.d("db", "onUpgrade dipanggil");
	}

}
