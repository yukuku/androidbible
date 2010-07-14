package yuku.alkitab;

import java.util.*;

import yuku.alkitab.model.*;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;

public class BukmakEditor {
	private final Context context;
	private final AlkitabDb alkitabDb;

	private String alamat = null;
	private int ari = 0;

	private long id = -1;
	
	public BukmakEditor(Context context, AlkitabDb alkitabDb, String alamat, int ari) {
		this.context = context;
		this.alkitabDb = alkitabDb;
		this.alamat = alamat;
		this.ari = ari;
	}

	public BukmakEditor(Context context, AlkitabDb alkitabDb, long id) {
		this.context = context;
		this.alkitabDb = alkitabDb;
		this.alamat = null;
		this.id = id;
	}

	public void bukaDialog() {
		final Bukmak2 bukmak = this.ari == 0? alkitabDb.getBukmakById(id): alkitabDb.getBukmakByAri(ari, AlkitabDb.ENUM_Bukmak2_jenis_bukmak);
		if (alamat == null) {
			alamat = S.alamat(ari);
		}
		
		View dialogView = LayoutInflater.from(context).inflate(R.layout.bukmak_ubah_dialog, null);
		final EditText tTulisan = (EditText) dialogView.findViewById(R.id.tTulisan);
		tTulisan.setText(bukmak != null? bukmak.tulisan: alamat);
		
		new AlertDialog.Builder(context)
		.setView(dialogView)
		.setTitle(alamat)
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String tulisan = tTulisan.getText().toString();
				
				if (bukmak != null) {
					bukmak.tulisan = tulisan;
					bukmak.waktuUbah = new Date();
					alkitabDb.updateBukmak(bukmak);
				} else {
					Bukmak2 bukmakBaru = new Bukmak2(ari, AlkitabDb.ENUM_Bukmak2_jenis_bukmak, tulisan, new Date(), new Date());
					alkitabDb.insertBukmak(bukmakBaru);
				}
			}
		})
		.setNegativeButton("Batal", null)
		.create()
		.show();
	}
}
