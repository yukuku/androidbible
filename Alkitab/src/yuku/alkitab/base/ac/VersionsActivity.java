
package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.config.BuildConfig;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.pdbconvert.ConvertOptionsDialog;
import yuku.alkitab.base.pdbconvert.ConvertOptionsDialog.ConvertOptionsCallback;
import yuku.alkitab.base.pdbconvert.ConvertPdbToYes;
import yuku.alkitab.base.pdbconvert.ConvertPdbToYes.ConvertParams;
import yuku.alkitab.base.pdbconvert.ConvertPdbToYes.ConvertProgressListener;
import yuku.alkitab.base.pdbconvert.ConvertPdbToYes.ConvertResult;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.storage.YesReader;
import yuku.alkitab.base.util.AddonManager;
import yuku.alkitab.base.util.AddonManager.DonlotListener;
import yuku.alkitab.base.util.AddonManager.DonlotThread;
import yuku.alkitab.base.util.AddonManager.Elemen;
import yuku.androidcrypto.DigestType;
import yuku.androidcrypto.Digester;
import yuku.filechooser.FileChooserActivity;
import yuku.filechooser.FileChooserConfig;
import yuku.filechooser.FileChooserConfig.Mode;
import yuku.filechooser.FileChooserResult;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class VersionsActivity extends BaseActivity {
	public static final String TAG = VersionsActivity.class.getSimpleName();

	private static final int REQCODE_openFile = 1;
	
	ListView lsEdisi;
	EdisiAdapter adapter;
	
	Map<String, String> cache_displayLanguage = new HashMap<String, String>();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_versions);
		setTitle(R.string.kelola_versi);

		adapter = new EdisiAdapter();
		adapter.init();
		
		lsEdisi = (ListView) findViewById(R.id.lsEdisi);
		lsEdisi.setAdapter(adapter);
		lsEdisi.setOnItemClickListener(lsEdisi_itemClick);
		
		registerForContextMenu(lsEdisi);
	}
	
	private void bikinMenu(Menu menu) {
		menu.clear();
		getSupportMenuInflater().inflate(R.menu.activity_versions, menu);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		bikinMenu(menu);
		
		return true;
	}
	
	@Override public boolean onPrepareOptionsMenu(Menu menu) {
		if (menu != null) {
			bikinMenu(menu);
		}
		
		return true;
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuTambah:
			klikPadaBukaFile();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private OnItemClickListener lsEdisi_itemClick = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			MVersion item = adapter.getItem(position);
			
			if (item instanceof MEdisiInternal) {
				// ga ngapa2in, wong internal ko
			} else if (item instanceof MVersionPreset) {
				klikPadaEdisiPreset((CheckBox) v.findViewById(R.id.cAktif), (MVersionPreset) item);
			} else if (item instanceof MVersionYes) {
				klikPadaEdisiYes((CheckBox) v.findViewById(R.id.cAktif), (MVersionYes) item);
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
			getMenuInflater().inflate(R.menu.context_version, menu);
		
			android.view.MenuItem menuBuang = menu.findItem(R.id.menuBuang);
			if (menuBuang != null) {
				menuBuang.setEnabled(false);
				MVersion item = adapter.getItem(info.position);
				if (item instanceof MVersionYes) {
					menuBuang.setEnabled(true);
				}
			}
		}
	}
	
	@Override public boolean onContextItemSelected(android.view.MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuBuang: {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
			final MVersion edisi = adapter.getItem(info.position);
			if (edisi instanceof MVersionYes) {
				final MVersionYes edisiYes = (MVersionYes) edisi;
				new AlertDialog.Builder(VersionsActivity.this)
				.setMessage(getString(R.string.juga_hapus_file_datanya_file, edisiYes.namafile))
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						S.getDb().hapusEdisiYes(edisiYes);
						adapter.initDaftarEdisiYes();
						adapter.notifyDataSetChanged();
						new File(edisiYes.namafile).delete();
					}
				})
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						S.getDb().hapusEdisiYes(edisiYes);
						adapter.initDaftarEdisiYes();
						adapter.notifyDataSetChanged();
					}
				})
				.show();
			}
			return true;
		}
		case R.id.menuDetails: {
			AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
			MVersion edisi = adapter.getItem(info.position);
			StringBuilder details = new StringBuilder();
			if (edisi instanceof MEdisiInternal) details.append(getString(R.string.ed_type_built_in) + '\n');
			if (edisi instanceof MVersionPreset) details.append(getString(R.string.ed_type_preset) + '\n');
			if (edisi instanceof MVersionYes) details.append(getString(R.string.ed_type_add_on) + '\n');
			details.append(getString(R.string.ed_title_title, edisi.judul) + '\n');
			if (edisi instanceof MVersionPreset) {
				MVersionPreset preset = (MVersionPreset) edisi;
				if (AddonManager.cekAdaEdisi(preset.namafile_preset)) {
					details.append(getString(R.string.ed_stored_in_file, AddonManager.getEdisiPath(preset.namafile_preset)) + '\n'); 
				} else {
					details.append(getString(R.string.ed_default_filename_file, preset.namafile_preset) + '\n');
					details.append(getString(R.string.ed_download_url_url, preset.url) + '\n');
				}
			}
			if (edisi instanceof MVersionYes) {
				MVersionYes yes = (MVersionYes) edisi;
				if (yes.namafile_pdbasal != null) {
					details.append(getString(R.string.ed_pdb_file_name_original_file, yes.namafile_pdbasal) + '\n');
				}
				details.append(getString(R.string.ed_stored_in_file, yes.namafile) + '\n');
				if (yes.keterangan != null) {
					details.append(getString(R.string.ed_version_info_info, yes.keterangan) + '\n');
				}
			}
			
			new AlertDialog.Builder(this)
			.setTitle(R.string.ed_version_details)
			.setMessage(details)
			.setPositiveButton(R.string.ok, null)
			.show();
			
			return true;
		}
		}
		return false;
	}
	
	void klikPadaEdisiPreset(final CheckBox cAktif, final MVersionPreset edisi) {
		if (cAktif.isChecked()) {
			edisi.setAktif(false);
		} else {
			// tergantung uda ada belum, kalo uda ada filenya sih centang aja
			if (edisi.adaFileDatanya()) {
				edisi.setAktif(true);
			} else {
				DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
					final ProgressDialog pd = new ProgressDialog(VersionsActivity.this);
					DonlotListener donlotListener = new DonlotListener() {
						@Override
						public void onSelesaiDonlot(Elemen e) {
							VersionsActivity.this.runOnUiThread(new Runnable() {
								@Override public void run() {
									Toast.makeText(getApplicationContext(),
										getString(R.string.selesai_mengunduh_edisi_judul_disimpan_di_path, edisi.judul, AddonManager.getEdisiPath(edisi.namafile_preset)),
										Toast.LENGTH_LONG).show();
								}
							});
							pd.dismiss();
						}
						
						@Override
						public void onGagalDonlot(Elemen e, final String keterangan, final Throwable t) {
							VersionsActivity.this.runOnUiThread(new Runnable() {
								@Override public void run() {
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
							VersionsActivity.this.runOnUiThread(new Runnable() {
								@Override public void run() {
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
							VersionsActivity.this.runOnUiThread(new Runnable() {
								@Override public void run() {
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
				
				new AlertDialog.Builder(VersionsActivity.this)
				.setTitle(R.string.mengunduh_tambahan)
				.setMessage(getString(R.string.file_edisipath_tidak_ditemukan_apakah_anda_mau_mengunduhnya, AddonManager.getEdisiPath(edisi.namafile_preset)))
				.setPositiveButton(R.string.yes, clickListener)
				.setNegativeButton(R.string.no, null)
				.show();
			}
		}
	}

	void klikPadaEdisiYes(final CheckBox cAktif, final MVersionYes edisi) {
		if (cAktif.isChecked()) {
			edisi.setAktif(false);
		} else {
			if (edisi.adaFileDatanya()) {
				edisi.setAktif(true);
			} else {
				new AlertDialog.Builder(this)
				.setTitle(R.string.cannot_find_data_file)
				.setMessage(getString(R.string.the_file_for_this_version_is_no_longer_available_file, edisi.namafile))
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						S.getDb().hapusEdisiYes(edisi);
						adapter.initDaftarEdisiYes();
						adapter.notifyDataSetChanged();
					}
				})
				.setNegativeButton(R.string.no, null)
				.show();
			}
		}
	}

	void klikPadaBukaFile() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			FileChooserConfig config = new FileChooserConfig();
			config.mode = Mode.Open;
			config.initialDir = Environment.getExternalStorageDirectory().getAbsolutePath();
			config.title = getString(R.string.ed_choose_pdb_or_yes_file);
			config.pattern = ".*\\.(?i:pdb|yes|yes\\.gz)"; //$NON-NLS-1$
			
			startActivityForResult(FileChooserActivity.createIntent(getApplicationContext(), config), REQCODE_openFile);
		} else {
			new AlertDialog.Builder(this)
			.setMessage(R.string.ed_no_external_storage)
			.setPositiveButton(R.string.ok, null)
			.show();
		}
	}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_openFile) {
			FileChooserResult result = FileChooserActivity.obtainResult(data);
			if (result == null) {
				return;
			}
		
			final String filename = result.firstFilename;
			
			if (filename.toLowerCase().endsWith(".yes.gz")) { //$NON-NLS-1$
				// decompress or see if the same filename without .gz exists
				final File maybeDecompressed = new File(filename.substring(0, filename.length() - 3));
				if (maybeDecompressed.exists() && !maybeDecompressed.isDirectory() && maybeDecompressed.canRead()) {
					handleFileOpenYes(maybeDecompressed.getAbsolutePath(), null);
				} else {
					final ProgressDialog pd = ProgressDialog.show(VersionsActivity.this, null, getString(R.string.sedang_mendekompres_harap_tunggu), true, false);
					new AsyncTask<Void, Void, File>() {
						@Override protected File doInBackground(Void... params) {
							String tmpfile3 = filename + "-" + (int)(Math.random() * 100000) + ".tmp3"; //$NON-NLS-1$ //$NON-NLS-2$
							try {
								GZIPInputStream in = new GZIPInputStream(new FileInputStream(filename));
								FileOutputStream out = new FileOutputStream(tmpfile3); // decompressed file
								
								// Transfer bytes from the compressed file to the output file
								byte[] buf = new byte[4096 * 4];
								while (true) {
									int len = in.read(buf);
									if (len <= 0) break;
									out.write(buf, 0, len);
								}
								out.close();
								in.close();
								
								boolean renameOk = new File(tmpfile3).renameTo(maybeDecompressed);
								if (!renameOk) {
									throw new RuntimeException("Gagal rename!"); //$NON-NLS-1$
								}
							} catch (Exception e) {
								return null;
							} finally {
								Log.d(TAG, "menghapus tmpfile3: " + tmpfile3); //$NON-NLS-1$
								new File(tmpfile3).delete();
							}
							return maybeDecompressed;
						}
						
						@Override protected void onPostExecute(File result) {
							pd.dismiss();
							
							handleFileOpenYes(result.getAbsolutePath(), null);
						};
					}.execute();
				}
			} else if (filename.toLowerCase().endsWith(".yes")) { //$NON-NLS-1$
				handleFileOpenYes(filename, null);
			} else if (filename.toLowerCase().endsWith(".pdb")) { //$NON-NLS-1$
				handleFileOpenPdb(filename);
			} else {
				Toast.makeText(getApplicationContext(), R.string.ed_invalid_file_selected, Toast.LENGTH_SHORT).show();
			}
		}
	}
	
	void handleFileOpenYes(String filename, String namapdbasal) {
		{ // cari dup
			boolean dup = false;
			BuildConfig c = BuildConfig.get(getApplicationContext());
			for (MVersionPreset preset: c.presets) {
				if (filename.equals(AddonManager.getEdisiPath(preset.namafile_preset))) {
					dup = true;
					break;
				}
			}
			
			if (!dup) dup = S.getDb().adakahEdisiYesDenganNamafile(filename);
			
			if (dup) {
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.ed_file_file_sudah_ada_dalam_daftar_versi, filename))
				.setPositiveButton(R.string.ok, null)
				.show();
				return;
			}
		}
		
		try {
			YesReader pembaca = new YesReader(getApplicationContext(), filename);
			int urutanTerbesar = S.getDb().getUrutanTerbesarEdisiYes();
			if (urutanTerbesar == 0) urutanTerbesar = 100; // default
			
			MVersionYes yes = new MVersionYes();
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
			.setTitle(R.string.ed_error_encountered)
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
			.setMessage(R.string.ed_this_file_is_already_on_the_list)
			.setPositiveButton(R.string.ok, null)
			.show();
			return;
		}
		
		boolean mkdirOk = AddonManager.mkYesDir();
		if (!mkdirOk) {
			new AlertDialog.Builder(this)
			.setMessage(getString(R.string.tidak_bisa_membuat_folder, AddonManager.getYesPath()))
			.setPositiveButton(R.string.ok, null)
			.show();
			return;
		}
		
		ConvertOptionsCallback callback = new ConvertOptionsCallback() {
			@Override public void onPdbReadError(Throwable e) {
				showPdbReadErrorDialog(e);
			}
			
			@Override public void onOk(final ConvertParams params) {
				final String namafileyes = AddonManager.getEdisiPath(namayes);
				final ProgressDialog pd = ProgressDialog.show(VersionsActivity.this, null, getString(R.string.ed_reading_pdb_file), true, false);
				pd.setOnKeyListener(new OnKeyListener() {
					@Override public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
						if (keyCode == KeyEvent.KEYCODE_SEARCH) {
							return true;
						}
						return false;
					}
				});
				
				new AsyncTask<String, Object, ConvertResult>() {
					@Override protected ConvertResult doInBackground(String... _unused_) {
						ConvertPdbToYes converter = new ConvertPdbToYes();
						converter.setConvertProgressListener(new ConvertProgressListener() {
							@Override public void onProgress(int at, String message) {
								Log.d(TAG, "Progress " + at + ": " + message); //$NON-NLS-1$ //$NON-NLS-2$
								publishProgress(at, message);
							}
							
							@Override public void onFinish() {
								Log.d(TAG, "Finish"); //$NON-NLS-1$
								publishProgress(null, null);
							}
						});
						return converter.convert(getApplicationContext(), namafilepdb, namafileyes, params);
					}
					
					@Override protected void onProgressUpdate(Object... values) {
						if (values[0] == null) {
							pd.setMessage(getString(R.string.ed_finished));
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
							
							if (result.wronglyConvertedBookNames != null && result.wronglyConvertedBookNames.size() > 0) {
								StringBuilder msg = new StringBuilder(getString(R.string.ed_the_following_books_from_the_pdb_file_are_not_recognized) + '\n');
								for (String s: result.wronglyConvertedBookNames) {
									msg.append("- ").append(s).append('\n'); //$NON-NLS-1$
								}
								
								new AlertDialog.Builder(VersionsActivity.this)
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
	
	void showPdbReadErrorDialog(Throwable exception) {
		new AlertDialog.Builder(VersionsActivity.this)
		.setTitle(R.string.ed_error_reading_pdb_file)
		.setMessage(getString(R.string.ed_details) + U.showException(exception))
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
	public static abstract class MVersion {
		public String judul;
		public int jenis;
		public int urutan;
		
		/** id unik untuk dibandingkan */
		public abstract String getEdisiId();
		/** return edisi supaya bisa mulai dibaca. null kalau ga memungkinkan */
		public abstract Version getVersion(Context context);
		public abstract void setAktif(boolean aktif);
		public abstract boolean getAktif();
		public abstract boolean adaFileDatanya();
	}

	public static class MEdisiInternal extends MVersion {
		public static String getVersionInternalId() {
			return "internal"; //$NON-NLS-1$
		}
		
		@Override public String getEdisiId() {
			return getVersionInternalId();
		}

		@Override
		public Version getVersion(Context context) {
			return S.getInternalVersion();
		}

		@Override
		public void setAktif(boolean aktif) {
			// NOOP
		}

		@Override
		public boolean getAktif() {
			return true; // selalu aktif
		}

		@Override public boolean adaFileDatanya() {
			return true; // selalu ada
		}
	}
	
	public static class MVersionPreset extends MVersion {
		public String url;
		public String namafile_preset;
		public String locale;
		
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
		public Version getVersion(Context context) {
			if (adaFileDatanya()) {
				return new Version(new YesReader(context, AddonManager.getEdisiPath(namafile_preset)));
			} else {
				return null;
			}
		}

		@Override public boolean adaFileDatanya() {
			return AddonManager.cekAdaEdisi(namafile_preset);
		}
	}
	
	public static class MVersionYes extends MVersion {
		public String keterangan;
		public String namafile;
		public String namafile_pdbasal;
		public boolean cache_aktif; // supaya ga usa dibaca tulis terus dari db
		
		@Override
		public String getEdisiId() {
			return "yes/" + namafile; //$NON-NLS-1$
		}

		@Override
		public Version getVersion(Context context) {
			if (adaFileDatanya()) {
				return new Version(new YesReader(context, namafile));
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

		@Override public boolean adaFileDatanya() {
			File f = new File(namafile);
			return f.exists() && f.canRead();
		}
	}
	
	public class EdisiAdapter extends BaseAdapter {
		MEdisiInternal internal;
		List<MVersionPreset> xpreset;
		List<MVersionYes> xyes;
		
		public void init() {
			BuildConfig c = BuildConfig.get(getApplicationContext());
			
			internal = new MEdisiInternal();
			internal.setAktif(true);
			internal.jenis = Db.Edisi.jenis_internal;
			internal.judul = c.internalJudul;
			internal.urutan = 1;
			
			xpreset = new ArrayList<MVersionPreset>();
			xpreset.addAll(c.presets);
			
			// betulin keaktifannya berdasarkan adanya file dan pref
			for (MVersionPreset preset: xpreset) {
				if (!AddonManager.cekAdaEdisi(preset.namafile_preset)) {
					preset.setAktif(false);
				}
			}
			
			initDaftarEdisiYes();
		}
		
		public void initDaftarEdisiYes() {
			xyes = S.getDb().listAllVersions();
		}

		@Override
		public int getCount() {
			return 1 /* internal */ + xpreset.size() + xyes.size() + 1 /* open */;
		}

		@Override
		public MVersion getItem(int position) {
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
			View res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_version, null);
			
			CheckBox cAktif = V.get(res, R.id.cAktif);
			TextView lJudul = V.get(res, R.id.lJudul);
			TextView lNamafile = V.get(res, R.id.lNamafile);
			TextView lBahasa = V.get(res, R.id.lBahasa);
			
			if (position == getCount() - 1) {
				// pilihan untuk open
				cAktif.setVisibility(View.GONE);
				lJudul.setText(R.string.ed_buka_file_pdb_yes_lainnya);
				lJudul.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_add, 0, 0, 0);
				lNamafile.setVisibility(View.GONE);
				lBahasa.setVisibility(View.GONE);
			} else {
				// salah satu dari edisi yang ada
				MVersion medisi = getItem(position);
				cAktif.setVisibility(View.VISIBLE);
				cAktif.setFocusable(false);
				cAktif.setClickable(false);
				cAktif.setChecked(medisi.getAktif());
				lJudul.setText(medisi.judul);
				lJudul.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
				if (medisi instanceof MEdisiInternal) {
					cAktif.setEnabled(false);
					lNamafile.setVisibility(View.GONE);
					lBahasa.setVisibility(View.GONE);
				} else if (medisi instanceof MVersionPreset) {
					cAktif.setEnabled(true);
					String namafile_preset = ((MVersionPreset) medisi).namafile_preset;
					String locale = ((MVersionPreset) medisi).locale;
					if (AddonManager.cekAdaEdisi(namafile_preset)) {
						lNamafile.setVisibility(View.GONE);
					} else {
						lNamafile.setVisibility(View.VISIBLE);
						lNamafile.setText(R.string.ed_tekan_untuk_mengunduh); 
					}
					if (locale != null && locale.length() > 0) {
						String display = cache_displayLanguage.get(locale);
						if (display == null) {
							display = new Locale(locale).getDisplayLanguage();
							if (U.equals(display, locale)) {
								display = null; // no data, let's just hide it
							}
							cache_displayLanguage.put(locale, display);
						}
						
						if (display != null) {
							lBahasa.setVisibility(View.VISIBLE);
							lBahasa.setText(display);
						} else {
							lBahasa.setVisibility(View.GONE);
						}
					} else {
						lBahasa.setVisibility(View.GONE);
					}
				} else if (medisi instanceof MVersionYes) {
					cAktif.setEnabled(true);
					lNamafile.setVisibility(View.VISIBLE);
					lBahasa.setVisibility(View.GONE);
					MVersionYes yes = (MVersionYes) medisi;
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
