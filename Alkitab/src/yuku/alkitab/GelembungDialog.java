package yuku.alkitab;

import yuku.alkitab.model.*;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;

public class GelembungDialog {
	AlertDialog alert;
	View dialogLayout;
	
	TextView lAlamat;
	EditText tCatatan;
	
	String alamat;

	public GelembungDialog(Context context) {
		dialogLayout = LayoutInflater.from(context).inflate(R.layout.dialog_gelembung, null);
		alert = new AlertDialog.Builder(context)
		.setView(dialogLayout)
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				bOk_click();
			}
		})
		.setNegativeButton("Hapus", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				//FIXME tanya dulu
				bHapus_click();
			}
		})
		.create();

		lAlamat = (TextView) dialogLayout.findViewById(R.id.lAlamat);
		tCatatan = (EditText) dialogLayout.findViewById(R.id.tCatatan);
	}

	public void tampilkan() {
		lAlamat.setText("Catatan " + alamat);
		
		alert.show();
	}

	protected void bHapus_click() {
		//FIXME hapus betulan
	}

	protected void bOk_click() {
		//FIXME simpen perubahannya
	}

	public void setKitabPasalAyat(Kitab kitab, int pasal_1, int ayat_1) {
		alamat = kitab.judul + " " + pasal_1 + ":" + ayat_1;
	}
}
