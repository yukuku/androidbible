package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;

import java.util.*;

import yuku.alkitab.*;
import yuku.alkitab.base.model.*;

public class BukmakEditor {
	public interface Listener {
		void onOk();
	}
	
	final Context context;

	// init ini...
	String alamat = null;
	int ari = 0;
	//... atau ini
	long id = -1;
	
	// optional
	Listener listener;
	
	public BukmakEditor(Context context, String alamat, int ari) {
		// wajib
		this.context = context;
		
		// pilihan
		this.alamat = alamat;
		this.ari = ari;
	}

	public BukmakEditor(Context context, long id) {
		// wajib
		this.context = context;

		// pilihan
		this.alamat = null;
		this.id = id;
	}
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void bukaDialog() {
		final Bukmak2 bukmak = this.ari == 0? S.getDb().getBukmakById(id): S.getDb().getBukmakByAri(ari, yuku.alkitab.base.storage.Db.Bukmak2.jenis_bukmak);
		
		// set yang belum diset
		if (this.ari == 0 && bukmak != null) {
			this.ari = bukmak.ari;
			this.alamat = S.alamat(S.edisiAktif, bukmak.ari);
		}
		
		View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_bukmak_ubah, null);
		final EditText tTulisan = (EditText) dialogView.findViewById(R.id.tTulisan);
		tTulisan.setText(bukmak != null? bukmak.tulisan: alamat);
		
		new AlertDialog.Builder(context)
		.setView(dialogView)
		.setTitle(alamat)
		.setIcon(R.drawable.bukmak)
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String tulisan = tTulisan.getText().toString();
				
				// kalo ga ada tulisan, kasi alamat aja.
				if (tulisan.length() == 0 || tulisan.trim().length() == 0) {
					tulisan = alamat;
				}
				
				if (bukmak != null) {
					bukmak.tulisan = tulisan;
					bukmak.waktuUbah = new Date();
					S.getDb().updateBukmak(bukmak);
				} else {
					Bukmak2 bukmakBaru = new Bukmak2(ari, yuku.alkitab.base.storage.Db.Bukmak2.jenis_bukmak, tulisan, new Date(), new Date());
					S.getDb().insertBukmak(bukmakBaru);
				}
				
				if (listener != null) listener.onOk();
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.create()
		.show();
	}
}
