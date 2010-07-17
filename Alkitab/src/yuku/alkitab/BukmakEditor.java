package yuku.alkitab;

import java.util.*;

import yuku.alkitab.model.*;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;

public class BukmakEditor {
	public interface Listener {
		void onOk();
	}
	
	private final Context context;
	private final AlkitabDb alkitabDb;

	// init ini...
	private String alamat = null;
	private int ari = 0;
	//... atau ini
	private long id = -1;
	
	// optional
	private Listener listener;
	
	public BukmakEditor(Context context, AlkitabDb alkitabDb, String alamat, int ari) {
		// wajib
		this.context = context;
		this.alkitabDb = alkitabDb;
		
		// pilihan
		this.alamat = alamat;
		this.ari = ari;
	}

	public BukmakEditor(Context context, AlkitabDb alkitabDb, long id) {
		// wajib
		this.context = context;
		this.alkitabDb = alkitabDb;

		// pilihan
		this.alamat = null;
		this.id = id;
	}
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void bukaDialog() {
		final Bukmak2 bukmak = this.ari == 0? alkitabDb.getBukmakById(id): alkitabDb.getBukmakByAri(ari, AlkitabDb.ENUM_Bukmak2_jenis_bukmak);
		
		// set yang belum diset
		if (this.ari == 0 && bukmak != null) {
			this.ari = bukmak.ari;
			this.alamat = S.alamat(bukmak.ari);
		}
		
		View dialogView = LayoutInflater.from(context).inflate(R.layout.bukmak_ubah_dialog, null);
		final EditText tTulisan = (EditText) dialogView.findViewById(R.id.tTulisan);
		tTulisan.setText(bukmak != null? bukmak.tulisan: alamat);
		
		new AlertDialog.Builder(context)
		.setView(dialogView)
		.setTitle(alamat)
		.setIcon(R.drawable.bukmak)
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
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
					alkitabDb.updateBukmak(bukmak);
				} else {
					Bukmak2 bukmakBaru = new Bukmak2(ari, AlkitabDb.ENUM_Bukmak2_jenis_bukmak, tulisan, new Date(), new Date());
					alkitabDb.insertBukmak(bukmakBaru);
				}
				
				if (listener != null) listener.onOk();
			}
		})
		.setNegativeButton("Batal", null)
		.create()
		.show();
	}
}
