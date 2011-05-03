
package yuku.alkitab.base;

import java.io.File;
import java.util.*;

import yuku.alkitab.R;
import yuku.alkitab.base.config.BuildConfig;
import yuku.alkitab.base.model.Edisi;
import yuku.alkitab.base.storage.*;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.*;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

public class EdisiActivity extends Activity {
	public static final String TAG = EdisiActivity.class.getSimpleName();
	
	ListView lsEdisi;

	Handler handler = new Handler();
	EdisiAdapter adapter;
	
	private boolean perluReloadMenuWaktuOnMenuOpened = false;

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		S.siapinKitab();
		S.bacaPengaturan(this);
		S.terapkanPengaturanBahasa(this, handler, 2);
		S.siapinPengirimFidbek(this);
		
		setContentView(R.layout.activity_edisi);
		setTitle(R.string.kelola_versi);

		adapter = new EdisiAdapter();
		adapter.init();
		
		lsEdisi = (ListView) findViewById(R.id.lsEdisi);
		lsEdisi.setAdapter(adapter);
		lsEdisi.setOnItemClickListener(lsEdisi_itemClick);
		
		registerForContextMenu(lsEdisi);
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		S.terapkanPengaturanBahasa(this, handler, 2);
		perluReloadMenuWaktuOnMenuOpened = true;
		
		super.onConfigurationChanged(newConfig);
	}

	private void bikinMenu(Menu menu) {
		menu.clear();
		// FIXME new MenuInflater(this).inflate(R.menu.bukmak, menu);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		bikinMenu(menu);
		
		return true;
	}
	
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		if (menu != null) {
			if (perluReloadMenuWaktuOnMenuOpened) {
				bikinMenu(menu);
				perluReloadMenuWaktuOnMenuOpened = false;
			}
		}
		
		return true;
	}

	private OnItemClickListener lsEdisi_itemClick = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			MEdisi item = adapter.getItem(position);
			if (item == null) return;
			
			if (item instanceof MEdisiInternal) {
				// ga ngapa2in, wong internal ko
			} else if (item instanceof MEdisiPreset) {
				MEdisiPreset edisi = (MEdisiPreset) item;
				CheckBox cAktif = (CheckBox) v.findViewById(R.id.cAktif);
				if (cAktif.isChecked()) {
					edisi.aktif = false;
					cAktif.setSelected(false);
					edisi.setAktif(false);
				} else {
					// tergantung uda ada belum, kalo uda ada filenya sih centang aja
					if (AddonManager.cekAdaEdisi(edisi.namafile_preset)) {
						edisi.aktif = true;
						cAktif.setSelected(true);
						edisi.setAktif(true);
					} else {
						// FIXME DONLOT
					}
				}
			}
			
			adapter.notifyDataSetChanged();
		}
	};
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		// FIXME
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		
		return super.onContextItemSelected(item);
	}
	
	// model
	public static abstract class MEdisi {
		public String judul;
		public int jenis;
		public boolean aktif;
		public int urutan;
		
		/** id unik untuk dibandingkan */
		public abstract String getEdisiId();
		/** return edisi supaya bisa mulai dibaca. null kalau ga memungkinkan */
		public abstract Edisi getEdisi(Context context);
	}

	public static class MEdisiInternal extends MEdisi {
		@Override
		public String getEdisiId() {
			return "internal";
		}

		@Override
		public Edisi getEdisi(Context context) {
			return S.getEdisiInternal();
		}
	}
	
	public static class MEdisiPreset extends MEdisi {
		public String url;
		public String namafile_preset;
		
		public boolean getAktif() {
			return Preferences.getBoolean("edisi/preset/" + this.namafile_preset + "/aktif", true);
		}
		
		public void setAktif(boolean aktif) {
			Preferences.setBoolean("edisi/preset/" + this.namafile_preset + "/aktif", aktif);
		}

		@Override
		public String getEdisiId() {
			return "preset/" + namafile_preset;
		}

		@Override
		public Edisi getEdisi(Context context) {
			if (AddonManager.cekAdaEdisi(namafile_preset)) {
				return new Edisi(new YesPembaca(context, AddonManager.getEdisiPath(namafile_preset)));
			} else {
				return null;
			}
		}
	}
	
	public static class MEdisiYes extends MEdisi {
		public String namafile;
		public String namafile_pdbasal;
		
		@Override
		public String getEdisiId() {
			return "yes/" + namafile;
		}

		@Override
		public Edisi getEdisi(Context context) {
			File f = new File(namafile);
			if (f.exists() && f.canRead()) {
				return new Edisi(new YesPembaca(context, namafile));
			} else {
				return null;
			}
		}
	}
	
	public class EdisiAdapter extends BaseAdapter {
		MEdisiInternal internal;
		List<MEdisiPreset> xpreset;
		List<MEdisiYes> xyes;
		
		public void init() {
			BuildConfig c = BuildConfig.get(getApplicationContext());
			
			internal = new MEdisiInternal();
			internal.aktif = true;
			internal.jenis = Db.Edisi.jenis_internal;
			internal.judul = c.internalJudul;
			internal.urutan = 1;
			
			xpreset = new ArrayList<MEdisiPreset>();
			xpreset.addAll(c.xpreset);
			
			// betulin keaktifannya berdasarkan adanya file dan pref
			for (MEdisiPreset preset: xpreset) {
				if (!AddonManager.cekAdaEdisi(preset.namafile_preset)) {
					preset.aktif = false;
				} else {
					// tergantung pref, default true
					preset.aktif = preset.getAktif();
				}
			}
			
			xyes = S.getDb().listSemuaEdisi();
		}
		
		@Override
		public int getCount() {
			return 1 + xpreset.size() + xyes.size();
		}

		@Override
		public MEdisi getItem(int position) {
			if (position < 1) return internal;
			if (position < 1 + xpreset.size()) return xpreset.get(position - 1);
			if (position < 1 + xpreset.size() + xyes.size()) return xyes.get(position - 1 - xpreset.size());
			return null;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_edisi, null);
			
			TextView lJudul = (TextView) res.findViewById(R.id.lJudul);
			TextView lNamafile = (TextView) res.findViewById(R.id.lNamafile);
			CheckBox cAktif = (CheckBox) res.findViewById(R.id.cAktif);
			
			MEdisi medisi = getItem(position);
			cAktif.setFocusable(false);
			cAktif.setClickable(false);
			if (medisi instanceof MEdisiInternal) {
				//FIXME
				lJudul.setText(medisi.judul);
				lNamafile.setText("");
				cAktif.setChecked(true);
				cAktif.setEnabled(false);
			} else if (medisi instanceof MEdisiPreset) {
				String namafile_preset = ((MEdisiPreset) medisi).namafile_preset;
				lJudul.setText(medisi.judul);
				if (AddonManager.cekAdaEdisi(namafile_preset)) {
					lNamafile.setText(AddonManager.getEdisiPath(namafile_preset));
				} else {
					lNamafile.setText("Tekan untuk mengunduh"); 
				}
				cAktif.setChecked(medisi.aktif);
				cAktif.setEnabled(true);
			} else if (medisi instanceof MEdisiYes) {
				lJudul.setText(medisi.judul);
				lNamafile.setText(((MEdisiYes) medisi).namafile);
				cAktif.setChecked(medisi.aktif);
				cAktif.setEnabled(true);
			}
			
			return res;
		}
		
	}
	
}
