package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;

import java.util.*;

import yuku.alkitab.*;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.storage.*;

public class GelembungDialog {
	final Context context;
	final AlertDialog alert;
	final RefreshCallback refreshCallback;
	
	EditText tCatatan;
	
	int ari;
	String alamat;
	Bukmak2 bukmak;

	public interface RefreshCallback {
		void udahan();
	}
	
	public GelembungDialog(Context context, RefreshCallback refreshCallback) {
		this.context = context;
		this.refreshCallback = refreshCallback;
		
		View dialogLayout = LayoutInflater.from(context).inflate(R.layout.dialog_gelembung, null);
		
		this.alert = new AlertDialog.Builder(context)
		.setView(dialogLayout)
		.setIcon(R.drawable.gelembung)
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				bOk_click();
			}
		})
		.setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				bHapus_click();
			}
		})
		.create();

		tCatatan = (EditText) dialogLayout.findViewById(R.id.tCatatan);
		
		tCatatan.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					if (tCatatan.getText().length() == 0) {
						alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
					}
				}
			}
		});
	}

	public void tampilkan() {
		this.alert.setTitle(context.getString(R.string.catatan_alamat, alamat));
		
		this.bukmak = S.getDb().getBukmakByAri(ari, Db.Bukmak2.jenis_catatan);
		if (bukmak != null) {
			tCatatan.setText(bukmak.tulisan);
		}
		
		alert.show();
	}

	protected void bOk_click() {
		String tulisan = tCatatan.getText().toString();
		if (bukmak != null) {
			if (tulisan.length() == 0) {
				S.getDb().hapusBukmakByAri(ari, Db.Bukmak2.jenis_catatan);
			} else {
				bukmak.tulisan = tulisan;
				bukmak.waktuUbah = new Date();
				S.getDb().updateBukmak(bukmak);
			}
		} else { // bukmak == null; belum ada sebelumnya, maka hanya insert kalo ada tulisan.
			if (tulisan.length() > 0) {
				bukmak = new Bukmak2(ari, Db.Bukmak2.jenis_catatan, tulisan, new Date(), new Date());
				S.getDb().insertBukmak(bukmak);
			}
		}
		
		if (refreshCallback != null) {
			refreshCallback.udahan();
		}
	}

	protected void bHapus_click() {
		// kalo emang ga ada, biarkan saja, seperti tombol cancel jadinya.
		if (bukmak == null) return;
		
		new AlertDialog.Builder(context)
		.setTitle(R.string.hapus_catatan)
		.setMessage(R.string.anda_yakin_mau_menghapus_catatan_ini)
		.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				S.getDb().hapusBukmakByAri(ari, Db.Bukmak2.jenis_catatan);
				
				if (refreshCallback != null) {
					refreshCallback.udahan();
				}
			}
		})
		.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (refreshCallback != null) {
					refreshCallback.udahan();
				}
			}
		})
		.create()
		.show();
	}

	public void setDbKitabPasalAyat(Kitab kitab, int pasal_1, int ayat_1) {
		this.ari = Ari.encode(kitab.pos, pasal_1, ayat_1);
		this.alamat = S.alamat(kitab, pasal_1, ayat_1);
	}
}
