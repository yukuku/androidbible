package yuku.alkitab.model;

import java.util.Date;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.SQLException;
import android.database.sqlite.*;
import android.util.Log;

public class AlkitabDb extends SQLiteOpenHelper {
	public static final String TABEL_Bukmak = "Bukmak";
	public static final String KOLOM_alamat = "alamat";
	public static final String KOLOM_cuplikan = "cuplikan";
	public static final String KOLOM_waktuTambah = "waktuTambah";
	public static final String KOLOM_kitab = "kitab";
	public static final String KOLOM_pasal = "pasal";
	public static final String KOLOM_ayat = "ayat";

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
					"%s integer, " +
					"%s integer, " +
					"%s integer, " +
					"%s integer)", TABEL_Bukmak, KOLOM_alamat, KOLOM_cuplikan, KOLOM_waktuTambah, KOLOM_kitab, KOLOM_pasal, KOLOM_ayat));
			
			// Kej 1:1 buat percobaan doang
			Bukmak kej1 = new Bukmak("Kej 1:1", "Pada mulanya Allah menciptakan langit dan bumi", new Date(), 0, 1, 1);
			db.insert(TABEL_Bukmak, null, kej1.toContentValues());
			
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
