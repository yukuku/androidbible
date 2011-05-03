
package yuku.alkitab.base;

import java.io.File;
import java.util.*;

import yuku.alkitab.R;
import yuku.alkitab.base.AddonManager.DonlotListener;
import yuku.alkitab.base.AddonManager.DonlotThread;
import yuku.alkitab.base.AddonManager.Elemen;
import yuku.alkitab.base.config.BuildConfig;
import yuku.alkitab.base.model.Edisi;
import yuku.alkitab.base.storage.*;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.util.Log;
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
			
			final CheckBox cAktif = (CheckBox) v.findViewById(R.id.cAktif);
			if (item instanceof MEdisiInternal) {
				// ga ngapa2in, wong internal ko
			} else if (item instanceof MEdisiPreset) {
				final MEdisiPreset edisi = (MEdisiPreset) item;
				if (cAktif.isChecked()) {
					edisi.setAktif(false);
				} else {
					// tergantung uda ada belum, kalo uda ada filenya sih centang aja
					if (AddonManager.cekAdaEdisi(edisi.namafile_preset)) {
						edisi.setAktif(true);
					} else {
						DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
							final ProgressDialog pd = new ProgressDialog(EdisiActivity.this);
							DonlotListener donlotListener = new DonlotListener() {
								@Override
								public void onSelesaiDonlot(Elemen e) {
									EdisiActivity.this.runOnUiThread(new Runnable() {
										public void run() {
											Toast.makeText(getApplicationContext(),
												getString(R.string.selesai_mengunduh_edisi_judul_disimpan_di_path, edisi.judul, AddonManager.getEdisiPath(edisi.namafile_preset)),
												Toast.LENGTH_LONG).show();
										}
									});
									pd.dismiss();
								}
								
								@Override
								public void onGagalDonlot(Elemen e, final String keterangan, final Throwable t) {
									EdisiActivity.this.runOnUiThread(new Runnable() {
										public void run() {
											Toast.makeText(
												getApplicationContext(),
												keterangan != null ? keterangan : getString(R.string.gagal_mengunduh_edisi_judul_ex_pastikan_internet, edisi.judul,
													t == null ? "null" : t.getClass().getCanonicalName() + ": " + t.getMessage()), Toast.LENGTH_LONG).show(); //$NON-NLS-1$ //$NON-NLS-2$
										}
									});
									pd.dismiss();
								}
								
								@Override
								public void onProgress(Elemen e, final int sampe, int total) {
									EdisiActivity.this.runOnUiThread(new Runnable() {
										public void run() {
											if (sampe >= 0) {
												pd.setMessage(getString(R.string.terunduh_sampe_byte, sampe));
											} else {
												pd.setMessage(getString(R.string.sedang_mendekompres_harap_tunggu));
											}
										}
									});
									Log.d(TAG, "onProgress " + sampe); //$NON-NLS-1$
								}
								
								@Override
								public void onBatalDonlot(Elemen e) {
									EdisiActivity.this.runOnUiThread(new Runnable() {
										public void run() {
											Toast.makeText(getApplicationContext(), R.string.pengunduhan_dibatalkan, Toast.LENGTH_SHORT).show();
										}
									});
									pd.dismiss();
								}
							};
							
							@Override
							public void onClick(DialogInterface dialog, int which) {
								pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
								pd.setCancelable(true);
								pd.setIndeterminate(true);
								pd.setTitle(getString(R.string.mengunduh_nama, edisi.namafile_preset));
								pd.setMessage(getString(R.string.mulai_mengunduh));
								pd.setOnDismissListener(new DialogInterface.OnDismissListener() {
									@Override
									public void onDismiss(DialogInterface dialog) {
										if (AddonManager.cekAdaEdisi(edisi.namafile_preset)) {
											edisi.setAktif(true);
										}
										adapter.notifyDataSetChanged();
									}
								});

								DonlotThread donlotThread = AddonManager.getDonlotThread(getApplicationContext());
								final Elemen e = donlotThread.antrikan(edisi.url, AddonManager.getEdisiPath(edisi.namafile_preset), donlotListener);
								if (e != null) {
									pd.show();
								}

								pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
									@Override
									public void onCancel(DialogInterface dialog) {
										e.hentikan = true;
									}
								});
							}
						};
						
						new AlertDialog.Builder(EdisiActivity.this)
						.setTitle(R.string.mengunduh_tambahan)
						.setMessage(getString(R.string.file_edisipath_tidak_ditemukan_apakah_anda_mau_mengunduhnya, AddonManager.getEdisiPath(edisi.namafile_preset)))
						.setPositiveButton(R.string.yes, clickListener)
						.setNegativeButton(R.string.no, null)
						.show();
					}
				}
			} else if (item instanceof MEdisiYes) {
				final MEdisiYes edisi = (MEdisiYes) item;
				if (cAktif.isChecked()) {
					edisi.setAktif(false);
				} else {
					edisi.setAktif(true);
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
		public int urutan;
		
		/** id unik untuk dibandingkan */
		public abstract String getEdisiId();
		/** return edisi supaya bisa mulai dibaca. null kalau ga memungkinkan */
		public abstract Edisi getEdisi(Context context);
		public abstract void setAktif(boolean aktif);
		public abstract boolean getAktif();
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

		@Override
		public void setAktif(boolean aktif) {
			// NOOP
		}

		@Override
		public boolean getAktif() {
			return true; // selalu aktif
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
		public boolean cache_aktif; // supaya ga usa dibaca tulis terus dari db
		
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

		@Override
		public void setAktif(boolean aktif) {
			this.cache_aktif = aktif;
			S.getDb().setEdisiYesAktif(this.namafile, aktif);
		}

		@Override
		public boolean getAktif() {
			return this.cache_aktif;
		}
	}
	
	public class EdisiAdapter extends BaseAdapter {
		MEdisiInternal internal;
		List<MEdisiPreset> xpreset;
		List<MEdisiYes> xyes;
		
		public void init() {
			BuildConfig c = BuildConfig.get(getApplicationContext());
			
			internal = new MEdisiInternal();
			internal.setAktif(true);
			internal.jenis = Db.Edisi.jenis_internal;
			internal.judul = c.internalJudul;
			internal.urutan = 1;
			
			xpreset = new ArrayList<MEdisiPreset>();
			xpreset.addAll(c.xpreset);
			
			// betulin keaktifannya berdasarkan adanya file dan pref
			for (MEdisiPreset preset: xpreset) {
				if (!AddonManager.cekAdaEdisi(preset.namafile_preset)) {
					preset.setAktif(false);
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
			cAktif.setChecked(medisi.getAktif());
			lJudul.setText(medisi.judul);
			if (medisi instanceof MEdisiInternal) {
				lNamafile.setText("");
				cAktif.setEnabled(false);
			} else if (medisi instanceof MEdisiPreset) {
				String namafile_preset = ((MEdisiPreset) medisi).namafile_preset;
				if (AddonManager.cekAdaEdisi(namafile_preset)) {
					lNamafile.setText(AddonManager.getEdisiPath(namafile_preset));
				} else {
					lNamafile.setText("Tekan untuk mengunduh"); 
				}
				cAktif.setEnabled(true);
			} else if (medisi instanceof MEdisiYes) {
				lNamafile.setText(((MEdisiYes) medisi).namafile);
				cAktif.setEnabled(true);
			}
			
			return res;
		}
		
	}
	
}
