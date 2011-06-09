
package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

import java.io.*;
import java.util.*;

import yuku.alkitab.R;
import yuku.alkitab.base.AddonManager.DonlotListener;
import yuku.alkitab.base.AddonManager.DonlotThread;
import yuku.alkitab.base.AddonManager.Elemen;
import yuku.alkitab.base.config.*;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.pdbconvert.*;
import yuku.alkitab.base.pdbconvert.ConvertOptionsDialog.ConvertOptionsCallback;
import yuku.alkitab.base.pdbconvert.ConvertPdbToYes.ConvertParams;
import yuku.alkitab.base.pdbconvert.ConvertPdbToYes.ConvertProgressListener;
import yuku.alkitab.base.pdbconvert.ConvertPdbToYes.ConvertResult;
import yuku.alkitab.base.storage.*;
import yuku.androidcrypto.*;
import yuku.filechooser.*;
import yuku.filechooser.FileChooserConfig.Mode;

public class EdisiActivity extends Activity {
	public static final String TAG = EdisiActivity.class.getSimpleName();

	private static final int REQCODE_openFile = 1;
	
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
		new MenuInflater(this).inflate(R.menu.activity_edisi, menu);
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

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuTambah:
			klikPadaBukaFile();
			return true;
		}
		return false;
	}
	
	private OnItemClickListener lsEdisi_itemClick = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			MEdisi item = adapter.getItem(position);
			
			if (item instanceof MEdisiInternal) {
				// ga ngapa2in, wong internal ko
			} else if (item instanceof MEdisiPreset) {
				klikPadaEdisiPreset((CheckBox) v.findViewById(R.id.cAktif), (MEdisiPreset) item);
			} else if (item instanceof MEdisiYes) {
				klikPadaEdisiYes((CheckBox) v.findViewById(R.id.cAktif), (MEdisiYes) item);
			} else if (position == adapter.getCount() - 1) {
				klikPadaBukaFile();
			}
			
			adapter.notifyDataSetChanged();
		}
	};
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		if (menu == null) return;
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		if (info.position == adapter.getCount() - 1) {
			// ga ada menu untuk Buka file
		} else {
			new MenuInflater(getApplicationContext()).inflate(R.menu.context_edisi, menu);
		
			MenuItem menuBuang = menu.findItem(R.id.menuBuang);
			if (menuBuang != null) {
				menuBuang.setEnabled(false);
				MEdisi item = adapter.getItem(info.position);
				if (item instanceof MEdisiYes) {
					menuBuang.setEnabled(true);
				}
			}
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuBuang: {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
			MEdisi edisi = adapter.getItem(info.position);
			if (edisi instanceof MEdisiYes) {
				S.getDb().hapusEdisiYes((MEdisiYes) edisi);
				adapter.initDaftarEdisiYes();
				adapter.notifyDataSetChanged();
			}
			return true;
		}
		case R.id.menuDetails: {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
			MEdisi edisi = adapter.getItem(info.position);
			StringBuilder details = new StringBuilder();
			if (edisi instanceof MEdisiInternal) details.append("Type: Built-in\n");
			if (edisi instanceof MEdisiPreset) details.append("Type: Preset\n");
			if (edisi instanceof MEdisiYes) details.append("Type: Add-on\n");
			details.append("Title: " + edisi.judul + '\n');
			if (edisi instanceof MEdisiPreset) {
				MEdisiPreset preset = (MEdisiPreset) edisi;
				if (AddonManager.cekAdaEdisi(preset.namafile_preset)) {
					details.append("Stored in: " + AddonManager.getEdisiPath(preset.namafile_preset) + '\n'); //$NON-NLS-2$
				} else {
					details.append("Default filename: " + preset.namafile_preset + '\n');
					details.append("Download URL: " + preset.url + '\n');
				}
			}
			if (edisi instanceof MEdisiYes) {
				MEdisiYes yes = (MEdisiYes) edisi;
				if (yes.namafile_pdbasal != null) {
					details.append("PDB file name (original): " + yes.namafile_pdbasal + '\n');
				}
				details.append("Stored in: " + yes.namafile + '\n');
				if (yes.keterangan != null) {
					details.append("Version info: " + yes.keterangan + '\n');
				}
			}
			
			new AlertDialog.Builder(this)
			.setTitle("Version details")
			.setMessage(details)
			.setPositiveButton(R.string.ok, null)
			.show();
			
			return true;
		}
		}
		return false;
	}
	
	private void klikPadaEdisiPreset(final CheckBox cAktif, final MEdisiPreset edisi) {
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
								adapter.initDaftarEdisiYes();
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
	}

	private void klikPadaEdisiYes(final CheckBox cAktif, final MEdisiYes edisi) {
		if (cAktif.isChecked()) {
			edisi.setAktif(false);
		} else {
			edisi.setAktif(true);
		}
	}

	private void klikPadaBukaFile() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			try {
				if (getPackageManager().getPackageInfo(getPackageName(), 0).versionCode <= 52) {
					new AlertDialog.Builder(this)
					.setMessage("Fitur membuka file PDB masih dalam percobaan. Jika Anda menemukan masalah, harap hubungi yukuku@gmail.com (lampirkan file PDB-nya jika ada).\n\n" +
							"Opening PDB files is still an experimental feature. If you encounter problems, please contact yukuku@gmail.com (attach the PDB file if possible).\n\n" +
							"Thanks to Yohanes Nugroho for the PDB-reader library.")
					.setPositiveButton(R.string.ok, new OnClickListener() {
						@Override public void onClick(DialogInterface dialog, int which) {
							FileChooserConfig config = new FileChooserConfig();
							config.mode = Mode.Open;
							config.initialDir = Environment.getExternalStorageDirectory().getAbsolutePath();
							config.title = "Pilih file .pdb atau .yes";
							config.pattern = ".*\\.(?i:pdb|yes)"; //$NON-NLS-1$
							
							startActivityForResult(FileChooserActivity.createIntent(getApplicationContext(), config), REQCODE_openFile);
						}
					})
					.show();
				}
			} catch (NameNotFoundException e) {
			}
		} else {
			new AlertDialog.Builder(this)
			.setMessage("Tidak ditemukan penyimpanan eksternal (seperti SD Card).")
			.setPositiveButton(R.string.ok, null)
			.show();
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_openFile) {
			FileChooserResult result = FileChooserActivity.obtainResult(data);
			if (result == null) {
				return;
			}
		
			String filename = result.firstFilename;
			if (filename.toLowerCase().endsWith(".yes")) { //$NON-NLS-1$
				handleFileOpenYes(filename, null);
			} else if (filename.toLowerCase().endsWith(".pdb")) { //$NON-NLS-1$
				handleFileOpenPdb(filename);
			} else {
				Toast.makeText(getApplicationContext(), "Invalid file selected.", Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	private void handleFileOpenYes(String filename, String namapdbasal) {
		{ // cari dup
			boolean dup = false;
			BuildConfig c = BuildConfig.get(getApplicationContext());
			for (MEdisiPreset preset: c.xpreset) {
				if (filename.equals(AddonManager.getEdisiPath(preset.namafile_preset))) {
					dup = true;
					break;
				}
			}
			
			if (!dup) dup = S.getDb().adakahEdisiYesDenganNamafile(filename);
			
			if (dup) {
				new AlertDialog.Builder(this)
				.setMessage("File " + filename + " sudah ada dalam daftar versi.")
				.setPositiveButton(R.string.ok, null)
				.show();
				return;
			}
		}
		
		try {
			YesPembaca pembaca = new YesPembaca(getApplicationContext(), filename);
			int urutanTerbesar = S.getDb().getUrutanTerbesarEdisiYes();
			if (urutanTerbesar == 0) urutanTerbesar = 100; // default
			
			MEdisiYes yes = new MEdisiYes();
			yes.jenis = Db.Edisi.jenis_yes;
			yes.judul = pembaca.getJudul();
			yes.keterangan = pembaca.getKeterangan();
			yes.namafile = filename;
			yes.namafile_pdbasal = namapdbasal;
			yes.urutan = urutanTerbesar + 1;
			
			S.getDb().tambahEdisiYesDenganAktif(yes, true);
			adapter.initDaftarEdisiYes();
			adapter.notifyDataSetChanged();
		} catch (Exception e) {
			new AlertDialog.Builder(this)
			.setTitle("Ada kesalahan")
			.setMessage(e.getClass().getSimpleName() + ": " + e.getMessage()) //$NON-NLS-1$
			.setPositiveButton(R.string.ok, null)
			.show();
		}
	}

	private void handleFileOpenPdb(final String namafilepdb) {
		final String namayes = namaYes(namafilepdb, ConvertPdbToYes.VERSI_CONVERTER);
		
		// cek apakah sudah ada.
		if (S.getDb().adakahEdisiYesDenganNamafile(AddonManager.getEdisiPath(namayes))) {
			new AlertDialog.Builder(this)
			.setMessage("This file is already on the list.")
			.setPositiveButton(R.string.ok, null)
			.show();
			return;
		}
		
		
		ConvertOptionsCallback callback = new ConvertOptionsCallback() {
			@Override public void onPdbReadError(Exception e) {
				showPdbReadErrorDialog(e);
			}
			
			@Override public void onOk(final ConvertParams params) {
				final String namafileyes = AddonManager.getEdisiPath(namayes);
				final ProgressDialog pd = ProgressDialog.show(EdisiActivity.this, null, "Reading PDB file...", true, false);
				
				new AsyncTask<String, Object, ConvertResult>() {
					@Override protected ConvertResult doInBackground(String... _unused_) {
						ConvertPdbToYes converter = new ConvertPdbToYes();
						converter.setConvertProgressListener(new ConvertProgressListener() {
							@Override public void onProgress(int at, String message) {
								Log.d(TAG, "Progress " + at + ": " + message);
								publishProgress(at, message);
							}
							
							@Override public void onFinish() {
								Log.d(TAG, "Finish");
								publishProgress(null, null);
							}
						});
						return converter.convert(getApplicationContext(), namafilepdb, namafileyes, params);
					}
					
					@Override protected void onProgressUpdate(Object... values) {
						if (values[0] == null) {
							pd.setMessage("Finished.");
						} else {
							int at = (Integer) values[0];
							String message = (String) values[1];
							pd.setMessage("(" + at + ") " + message + "...");  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
						}
					};
					
					@Override protected void onPostExecute(ConvertResult result) {
						pd.dismiss();
						
						if (result.exception != null) {
							showPdbReadErrorDialog(result.exception);
						} else {
							// sukses.
							handleFileOpenYes(namafileyes, new File(namafilepdb).getName());
							
							if (result.unconvertedBookNames != null && result.unconvertedBookNames.size() > 0) {
								StringBuilder msg = new StringBuilder("The following books from the PDB file are not recognized and therefore skipped. Please email yukuku@gmail.com if you think these should not have been skipped.\n");
								for (String s: result.unconvertedBookNames) {
									msg.append("- ").append(s).append('\n'); //$NON-NLS-1$
								}
								
								new AlertDialog.Builder(EdisiActivity.this)
								.setMessage(msg)
								.setPositiveButton(R.string.ok, null)
								.show();
							}
						}
					}
				}.execute();
			}
		};
		
		ConvertOptionsDialog dialog = new ConvertOptionsDialog(this, namafilepdb, callback);
		dialog.show();
	}
	
	private void showPdbReadErrorDialog(Exception exception) {
		new AlertDialog.Builder(EdisiActivity.this)
		.setTitle("Error reading PDB file")
		.setMessage("Details: " + U.tampilException(exception))
		.setPositiveButton(R.string.ok, null)
		.show();
	};

	/**
	 * @return nama file untuk yes yang dikonvert dari pdbnya, semacam "pdb-1234abcd-1.yes". Ga pake path.
	 */
	private String namaYes(String namafilepdb, int versi) {
		byte[] digest = Digester.digestFile(DigestType.SHA1, new File(namafilepdb));
		if (digest == null) return null;
		String hash = Digester.toHex(digest).substring(0, 8);
		return "pdb-" + hash + "-" + String.valueOf(versi) + ".yes";   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
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
			return "internal"; //$NON-NLS-1$
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
		
		@Override public boolean getAktif() {
			return Preferences.getBoolean("edisi/preset/" + this.namafile_preset + "/aktif", true); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		@Override public void setAktif(boolean aktif) {
			Preferences.setBoolean("edisi/preset/" + this.namafile_preset + "/aktif", aktif); //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Override
		public String getEdisiId() {
			return "preset/" + namafile_preset; //$NON-NLS-1$
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
		public String keterangan;
		public String namafile;
		public String namafile_pdbasal;
		public boolean cache_aktif; // supaya ga usa dibaca tulis terus dari db
		
		@Override
		public String getEdisiId() {
			return "yes/" + namafile; //$NON-NLS-1$
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
			
			initDaftarEdisiYes();
		}
		
		public void initDaftarEdisiYes() {
			xyes = S.getDb().listSemuaEdisi();
		}

		@Override
		public int getCount() {
			return 1 /* internal */ + xpreset.size() + xyes.size() + 1 /* open */;
		}

		@Override
		public MEdisi getItem(int position) {
			if (position < 1) return internal;
			if (position < 1 + xpreset.size()) return xpreset.get(position - 1);
			if (position < 1 + xpreset.size() + xyes.size()) return xyes.get(position - 1 - xpreset.size());
			if (position < 1 + xpreset.size() + xyes.size() + 1) return null; /* open */
			return null;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_edisi, null);
			
			CheckBox cAktif = (CheckBox) res.findViewById(R.id.cAktif);
			TextView lJudul = (TextView) res.findViewById(R.id.lJudul);
			TextView lNamafile = (TextView) res.findViewById(R.id.lNamafile);
			
			if (position == getCount() - 1) {
				// pilihan untuk open
				cAktif.setVisibility(View.GONE);
				lJudul.setText("Buka file .pdb/.yes lainnya...");
				lNamafile.setVisibility(View.GONE);
			} else {
				// salah satu dari edisi yang ada
				MEdisi medisi = getItem(position);
				cAktif.setVisibility(View.VISIBLE);
				cAktif.setFocusable(false);
				cAktif.setClickable(false);
				cAktif.setChecked(medisi.getAktif());
				lJudul.setText(medisi.judul);
				if (medisi instanceof MEdisiInternal) {
					cAktif.setEnabled(false);
					lNamafile.setVisibility(View.GONE);
				} else if (medisi instanceof MEdisiPreset) {
					cAktif.setEnabled(true);
					String namafile_preset = ((MEdisiPreset) medisi).namafile_preset;
					if (AddonManager.cekAdaEdisi(namafile_preset)) {
						lNamafile.setVisibility(View.GONE);
					} else {
						lNamafile.setVisibility(View.VISIBLE);
						lNamafile.setText("Tekan untuk mengunduh"); 
					}
				} else if (medisi instanceof MEdisiYes) {
					cAktif.setEnabled(true);
					lNamafile.setVisibility(View.VISIBLE);
					MEdisiYes yes = (MEdisiYes) medisi;
					String extra = ""; //$NON-NLS-1$
					if (yes.keterangan != null) {
						extra += yes.keterangan;
					}
					lNamafile.setText(extra);
				}
			}
			
			return res;
		}
		
	}
}
