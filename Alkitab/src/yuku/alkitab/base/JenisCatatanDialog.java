package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.view.*;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;

import java.util.*;

import yuku.alkitab.*;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.storage.*;

public class JenisCatatanDialog {
	final Context context;
	final AlertDialog dialog;
	final RefreshCallback refreshCallback;
	final Kitab kitab;
	final int pasal_1;
	final int ayat_1;
	
	EditText tCatatan;
	
	int ari;
	String alamat;
	Bukmak2 bukmak;

	public interface RefreshCallback {
		void udahan();
	}
	
	public JenisCatatanDialog(Context context, Kitab kitab, int pasal_1, int ayat_1, RefreshCallback refreshCallback) {
		this.kitab = kitab;
		this.pasal_1 = pasal_1;
		this.ayat_1 = ayat_1;
		this.ari = Ari.encode(kitab.pos, pasal_1, ayat_1);
		this.alamat = S.alamat(kitab, pasal_1, ayat_1);
		this.context = context;
		this.refreshCallback = refreshCallback;
		
		View dialogLayout = LayoutInflater.from(context).inflate(R.layout.dialog_catatan_ubah, null);
		
		this.dialog = new AlertDialog.Builder(context)
		.setView(dialogLayout)
		.setIcon(R.drawable.jenis_catatan)
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
			@Override public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					if (tCatatan.length() == 0) {
						dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
					}
				}
			}
		});
	}
	
	private void setCatatan(CharSequence catatan) {
		tCatatan.setText(catatan);
	}

	public void bukaDialog() {
		this.dialog.setTitle(context.getString(R.string.catatan_alamat, alamat));
		
		this.bukmak = S.getDb().getBukmakByAri(ari, Db.Bukmak2.jenis_catatan);
		if (bukmak != null) {
			tCatatan.setText(bukmak.tulisan);
		}
		
		dialog.getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		dialog.show();
	}

	protected void bOk_click() {
		String tulisan = tCatatan.getText().toString();
		Date kini = new Date();
		if (bukmak != null) {
			if (tulisan.length() == 0) {
				S.getDb().hapusBukmakByAri(ari, Db.Bukmak2.jenis_catatan);
			} else {
				bukmak.tulisan = tulisan;
				bukmak.waktuUbah = kini;
				S.getDb().updateBukmak(bukmak);
			}
		} else { // bukmak == null; belum ada sebelumnya, maka hanya insert kalo ada tulisan.
			if (tulisan.length() > 0) {
				bukmak = S.getDb().insertBukmak(ari, Db.Bukmak2.jenis_catatan, tulisan, kini, kini);
			}
		}
		
		if (refreshCallback != null) refreshCallback.udahan();
	}

	protected void bHapus_click() {
		// kalo emang ga ada, cek apakah udah ada teks, kalau udah ada, tanya dulu
		if (bukmak != null || (bukmak == null && tCatatan.length() > 0)) {
			new AlertDialog.Builder(context)
			.setTitle(R.string.hapus_catatan)
			.setMessage(R.string.anda_yakin_mau_menghapus_catatan_ini)
			.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					S.getDb().hapusBukmakByAri(ari, Db.Bukmak2.jenis_catatan);
					
					if (refreshCallback != null) refreshCallback.udahan();
				}
			})
			.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface _unused_, int which) {
					JenisCatatanDialog dialog = new JenisCatatanDialog(context, kitab, pasal_1, ayat_1, refreshCallback);
					dialog.setCatatan(tCatatan.getText());
					dialog.bukaDialog();
				}
			})
			.show();
		}
	}
}
