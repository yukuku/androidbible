
package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.NavUtils;
import android.support.v4.app.ShareCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
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
import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.model.VersionImpl;
import yuku.alkitab.base.pdbconvert.ConvertOptionsDialog;
import yuku.alkitab.base.pdbconvert.ConvertPdbToYes2;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.storage.YesReaderFactory;
import yuku.alkitab.base.util.AddonManager;
import yuku.alkitab.base.util.AddonManager.DownloadListener;
import yuku.alkitab.base.util.AddonManager.DownloadThread;
import yuku.alkitab.base.util.AddonManager.Element;
import yuku.alkitab.debug.R;
import yuku.alkitab.io.BibleReader;
import yuku.androidcrypto.DigestType;
import yuku.androidcrypto.Digester;
import yuku.filechooser.FileChooserActivity;
import yuku.filechooser.FileChooserConfig;
import yuku.filechooser.FileChooserConfig.Mode;
import yuku.filechooser.FileChooserResult;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class VersionsActivity extends BaseActivity {
	public static final String TAG = VersionsActivity.class.getSimpleName();

	private static final int REQCODE_openFile = 1;
	private static final int REQCODE_share = 2;
	
	ListView lsVersions;
	VersionAdapter adapter;
	
	Map<String, String> cache_displayLanguage = new HashMap<String, String>();
	
	public static Intent createIntent() {
		return new Intent(App.context, VersionsActivity.class);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_versions);
		setTitle(R.string.kelola_versi);

		adapter = new VersionAdapter();
		adapter.init();
		
		lsVersions = V.get(this, R.id.lsVersions);
		lsVersions.setAdapter(adapter);
		lsVersions.setOnItemClickListener(lsVersions_itemClick);
		
		registerForContextMenu(lsVersions);
		
		processIntent(getIntent(), "onCreate");
	}

	private void processIntent(Intent intent, String via) {
		Log.d(TAG, "Got intent via " + via);
		Log.d(TAG, "  action: " + intent.getAction());
		Log.d(TAG, "  data uri: " + intent.getData());
		Log.d(TAG, "  component: " + intent.getComponent());
		Log.d(TAG, "  flags: 0x" + Integer.toHexString(intent.getFlags()));
		Log.d(TAG, "  mime: " + intent.getType());
		Bundle extras = intent.getExtras();
		Log.d(TAG, "  extras: " + (extras == null? "null": extras.size()));
		if (extras != null) {
			for (String key: extras.keySet()) {
				Log.d(TAG, "    " + key + " = " + extras.get(key));
			}
		}
		
		checkAndProcessOpenFileIntent(intent);
	}

	private void checkAndProcessOpenFileIntent(Intent intent) {
		if (!U.equals(intent.getAction(), Intent.ACTION_VIEW)) return;

		Uri uri = intent.getData();
		
		final boolean isLocalFile = U.equals("file", uri.getScheme());
		final Boolean isYesFile; // false:pdb true:yes null:cannotdetermine
		final String filelastname;
		
		if (isLocalFile) {
			String pathlc = uri.getPath().toLowerCase(Locale.US);
			if (pathlc.endsWith(".yes")) {
				isYesFile = true;
			} else if (pathlc.endsWith(".pdb")) {
				isYesFile = false;
			} else {
				isYesFile = null;
			}
			filelastname = uri.getLastPathSegment();
		} else {
			// try to read display name from content
			Cursor c = getContentResolver().query(uri, null, null, null, null);
			String[] cns = c.getColumnNames();
			Log.d(TAG, Arrays.toString(cns));
			c.moveToNext();
			for (int i = 0, len = c.getColumnCount(); i < len; i++) {
				Log.d(TAG, cns[i] + ": " + c.getString(i));
			}
			
			int col = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
			if (col != -1) {
				String name = c.getString(col);
				String namelc = name.toLowerCase(Locale.US);
				if (namelc.endsWith(".yes")) {
					isYesFile = true;
				} else if (namelc.endsWith(".pdb")) {
					isYesFile = false;
				} else {
					isYesFile = null;
				}
				filelastname = name;
			} else {
				isYesFile = null;
				filelastname = null;
			}
			c.close();
		}
		
		try {
			if (isYesFile == null) { // can't be determined
				new AlertDialog.Builder(this)
				.setMessage(R.string.open_file_unknown_file_format)
				.setPositiveButton(R.string.ok, null)
				.show();
				return;
			} else if (!isYesFile) { // pdb file
				// copy the file to cache first
				File cacheFile = new File(getCacheDir(), "datafile");
				InputStream input = getContentResolver().openInputStream(uri);
				copyStreamToFile(input, cacheFile);
				input.close();
				
				handleFileOpenPdb(cacheFile.getAbsolutePath());
			} else if (isLocalFile) { // opening a local yes file
				handleFileOpenYes(uri.getPath(), null);
			} else { // opening a nonlocal yes file
				boolean mkdirOk = AddonManager.mkYesDir();
				if (!mkdirOk) {
					new AlertDialog.Builder(this)
					.setMessage(getString(R.string.tidak_bisa_membuat_folder, AddonManager.getYesPath()))
					.setPositiveButton(R.string.ok, null)
					.show();
					return;
				}

				File localFile = new File(AddonManager.getYesPath(), filelastname);
				if (localFile.exists()) {
					new AlertDialog.Builder(this)
					.setMessage(getString(R.string.open_yes_file_name_conflict, filelastname, AddonManager.getYesPath()))
					.setPositiveButton(R.string.ok, null)
					.show();
					return;
				}
				
				InputStream input = getContentResolver().openInputStream(uri);
				copyStreamToFile(input, localFile);
				input.close();
				
				handleFileOpenYes(localFile.getAbsolutePath(), null);
			}
		} catch (Exception e) {
			new AlertDialog.Builder(this)
			.setMessage(R.string.open_file_cant_read_source)
			.setPositiveButton(R.string.ok, null)
			.show();
			return;
		}
	}

	private static void copyStreamToFile(InputStream input, File file) throws IOException {
		OutputStream output = new BufferedOutputStream(new FileOutputStream(file));
		byte[] buf = new byte[4096];
		while (true) {
			int read = input.read(buf, 0, buf.length);
			if (read < 0) break;
			output.write(buf, 0, read);
		}
		output.close();
	}

	private void buildMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.activity_versions, menu);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		buildMenu(menu);
		
		return true;
	}
	
	@Override public boolean onPrepareOptionsMenu(Menu menu) {
		if (menu != null) {
			buildMenu(menu);
		}
		
		return true;
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuAdd:
			clickOnOpenFile();
			return true;
		case android.R.id.home:
			Intent upIntent = new Intent(this, IsiActivity.class);
            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                // This activity is not part of the application's task, so create a new task
                // with a synthesized back stack.
                TaskStackBuilder.create(this).addNextIntent(upIntent).startActivities();
                finish();
            } else {
                // This activity is part of the application's task, so simply
                // navigate up to the hierarchical parent activity.
                // sample code uses this: NavUtils.navigateUpTo(this, upIntent);
            	finish();
            }
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	private OnItemClickListener lsVersions_itemClick = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			MVersion item = adapter.getItem(position);
			
			if (item instanceof MVersionInternal) {
				// ga ngapa2in, wong internal ko
			} else if (item instanceof MVersionPreset) {
				clickOnPresetVersion(V.<CheckBox>get(v, R.id.cActive), (MVersionPreset) item);
			} else if (item instanceof MVersionYes) {
				clickOnYesVersion(V.<CheckBox>get(v, R.id.cActive), (MVersionYes) item);
			} else if (position == adapter.getCount() - 1) {
				clickOnOpenFile();
			}
			
			adapter.notifyDataSetChanged();
		}
	};
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		if (menu == null) return;
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		if (info.position == adapter.getCount() - 1) {
			// no context menu for "open pdb"
		} else {
			getMenuInflater().inflate(R.menu.context_version, menu);
		
			android.view.MenuItem menuDelete = menu.findItem(R.id.menuDelete);
			if (menuDelete != null) {
				MVersion mv = adapter.getItem(info.position);
				if (mv instanceof MVersionInternal) {
					menuDelete.setEnabled(false);
				} else if (mv instanceof MVersionPreset) {
					if (!AddonManager.hasVersion(((MVersionPreset) mv).presetFilename)) {
						menuDelete.setEnabled(false);
					}
				}
			}
			
			android.view.MenuItem menuSend = menu.findItem(R.id.menuShare);
			if (menuSend != null) {
				MVersion mv = adapter.getItem(info.position);
				if (mv instanceof MVersionInternal) {
					menuSend.setEnabled(false);
				} else if (mv instanceof MVersionPreset) {
					if (!AddonManager.hasVersion(((MVersionPreset) mv).presetFilename)) {
						menuSend.setEnabled(false);
					}
				} else if (mv instanceof MVersionYes) {
					if (!mv.hasDataFile()) {
						menuSend.setEnabled(false);
					}
				}
			}
		}
	}
	
	@Override public boolean onContextItemSelected(android.view.MenuItem item) {
		int itemId = item.getItemId();
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		final MVersion mv = adapter.getItem(info.position);
		
		if (itemId == R.id.menuDelete) {
			if (mv instanceof MVersionYes) {
				final MVersionYes mvYes = (MVersionYes) mv;
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.juga_hapus_file_datanya_file, mvYes.filename))
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						S.getDb().deleteYesVersion(mvYes);
						adapter.initYesVersionList();
						adapter.notifyDataSetChanged();
						new File(mvYes.filename).delete();
					}
				})
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						S.getDb().deleteYesVersion(mvYes);
						adapter.initYesVersionList();
						adapter.notifyDataSetChanged();
					}
				})
				.show();
			} else if (mv instanceof MVersionPreset) {
				final MVersionPreset mvPreset = (MVersionPreset) mv;
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.version_preset_delete_filename, mvPreset.presetFilename))
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						mvPreset.setActive(false);
						new File(AddonManager.getVersionPath(mvPreset.presetFilename)).delete();
						adapter.notifyDataSetChanged();
					}
				})
				.setNegativeButton(R.string.no, null)
				.show();
			}
			return true;
		} else if (itemId == R.id.menuDetails) {
			StringBuilder details = new StringBuilder();
			if (mv instanceof MVersionInternal) details.append(getString(R.string.ed_type_built_in) + '\n');
			if (mv instanceof MVersionPreset) details.append(getString(R.string.ed_type_preset) + '\n');
			if (mv instanceof MVersionYes) details.append(getString(R.string.ed_type_add_on) + '\n');
			if (mv.shortName != null) details.append(getString(R.string.ed_shortName_shortName, mv.shortName) + '\n');
			details.append(getString(R.string.ed_title_title, mv.longName) + '\n');
			if (mv instanceof MVersionPreset) {
				MVersionPreset preset = (MVersionPreset) mv;
				if (AddonManager.hasVersion(preset.presetFilename)) {
					details.append(getString(R.string.ed_stored_in_file, AddonManager.getVersionPath(preset.presetFilename)) + '\n');
				} else {
					details.append(getString(R.string.ed_default_filename_file, preset.presetFilename) + '\n');
					details.append(getString(R.string.ed_download_url_url, preset.url) + '\n');
				}
			}
			if (mv instanceof MVersionYes) {
				MVersionYes yes = (MVersionYes) mv;
				if (yes.originalPdbFilename != null) {
					details.append(getString(R.string.ed_pdb_file_name_original_file, yes.originalPdbFilename) + '\n');
				}
				details.append(getString(R.string.ed_stored_in_file, yes.filename) + '\n');
				if (yes.description != null) {
					details.append(getString(R.string.ed_version_info_info, yes.description) + '\n');
				}
			}
			new AlertDialog.Builder(this)
			.setTitle(R.string.ed_version_details)
			.setMessage(details)
			.setPositiveButton(R.string.ok, null)
			.show();
			return true;
		} else if (itemId == R.id.menuShare) {
			ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(this)
			.setType("application/octet-stream");
			
			if (mv instanceof MVersionYes) {
				MVersionYes mvYes = (MVersionYes) mv;
				builder.addStream(Uri.fromFile(new File(mvYes.filename)));
				startActivityForResult(ShareActivity.createIntent(builder.getIntent(), getString(R.string.version_share_title)), REQCODE_share);
			} else if (mv instanceof MVersionPreset) {
				MVersionPreset mvPreset = (MVersionPreset) mv;
				builder.addStream(Uri.fromFile(new File(AddonManager.getVersionPath(mvPreset.presetFilename))));
				startActivityForResult(ShareActivity.createIntent(builder.getIntent(), getString(R.string.version_share_title)), REQCODE_share);
			}
			
			return true;
		}
		
		return super.onContextItemSelected(item);
	}
	
	void clickOnPresetVersion(final CheckBox cActive, final MVersionPreset mv) {
		if (cActive.isChecked()) {
			mv.setActive(false);
		} else {
			// tergantung uda ada belum, kalo uda ada filenya sih centang aja
			if (mv.hasDataFile()) {
				mv.setActive(true);
			} else {
				DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
					final ProgressDialog pd = new ProgressDialog(VersionsActivity.this);
					DownloadListener downloadListener = new DownloadListener() {
						@Override
						public void onDownloadFinished(Element e) {
							VersionsActivity.this.runOnUiThread(new Runnable() {
								@Override public void run() {
									Toast.makeText(getApplicationContext(),
										getString(R.string.selesai_mengunduh_edisi_judul_disimpan_di_path, mv.longName, AddonManager.getVersionPath(mv.presetFilename)),
										Toast.LENGTH_LONG).show();
								}
							});
							pd.dismiss();
						}
						
						@Override
						public void onDownloadFailed(Element e, final String description, final Throwable t) {
							VersionsActivity.this.runOnUiThread(new Runnable() {
								@Override public void run() {
									Toast.makeText(
									getApplicationContext(),
									description != null ? description : getString(R.string.gagal_mengunduh_edisi_judul_ex_pastikan_internet, mv.longName,
										t == null ? "null" : t.getClass().getCanonicalName() + ": " + t.getMessage()), Toast.LENGTH_LONG).show(); //$NON-NLS-1$ //$NON-NLS-2$
								}
							});
							pd.dismiss();
						}
						
						@Override
						public void onDownloadProgress(Element e, final int sampe, int total) {
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
						public void onDownloadCancelled(Element e) {
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
						pd.setTitle(getString(R.string.mengunduh_nama, mv.presetFilename));
						pd.setMessage(getString(R.string.mulai_mengunduh));
						pd.setOnDismissListener(new DialogInterface.OnDismissListener() {
							@Override
							public void onDismiss(DialogInterface dialog) {
								if (AddonManager.hasVersion(mv.presetFilename)) {
									mv.setActive(true);
								}
								adapter.initYesVersionList();
								adapter.notifyDataSetChanged();
							}
						});
	
						DownloadThread downloadThread = AddonManager.getDownloadThread(getApplicationContext());
						final Element e = downloadThread.enqueue(mv.url, AddonManager.getVersionPath(mv.presetFilename), downloadListener);
						pd.show();
						pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
							@Override
							public void onCancel(DialogInterface dialog) {
								e.cancelled = true;
							}
						});
					}
				};
				
				new AlertDialog.Builder(VersionsActivity.this)
				.setMessage(getString(R.string.file_edisipath_tidak_ditemukan_apakah_anda_mau_mengunduhnya, AddonManager.getVersionPath(mv.presetFilename)))
				.setPositiveButton(R.string.yes, clickListener)
				.setNegativeButton(R.string.no, null)
				.show();
			}
		}
	}

	void clickOnYesVersion(final CheckBox cActive, final MVersionYes mv) {
		if (cActive.isChecked()) {
			mv.setActive(false);
		} else {
			if (mv.hasDataFile()) {
				mv.setActive(true);
			} else {
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.the_file_for_this_version_is_no_longer_available_file, mv.filename))
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						S.getDb().deleteYesVersion(mv);
						adapter.initYesVersionList();
						adapter.notifyDataSetChanged();
					}
				})
				.setNegativeButton(R.string.no, null)
				.show();
			}
		}
	}

	void clickOnOpenFile() {
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
			
			if (filename.toLowerCase(Locale.US).endsWith(".yes.gz")) { //$NON-NLS-1$
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
									throw new RuntimeException("Failed to rename!"); //$NON-NLS-1$
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
			} else if (filename.toLowerCase(Locale.US).endsWith(".yes")) { //$NON-NLS-1$
				handleFileOpenYes(filename, null);
			} else if (filename.toLowerCase(Locale.US).endsWith(".pdb")) { //$NON-NLS-1$
				handleFileOpenPdb(filename);
			} else {
				Toast.makeText(getApplicationContext(), R.string.ed_invalid_file_selected, Toast.LENGTH_SHORT).show();
			}
		} else if (requestCode == REQCODE_share) {
			ShareActivity.Result result = ShareActivity.obtainResult(data);
			if (result != null && result.chosenIntent != null) {
				startActivity(result.chosenIntent);
			}
		}
	}
	
	void handleFileOpenYes(String filename, String originalpdbname) {
		{ // cari dup
			boolean dup = false;
			AppConfig c = AppConfig.get();
			for (MVersionPreset preset: c.presets) {
				if (filename.equals(AddonManager.getVersionPath(preset.presetFilename))) {
					dup = true;
					// automatically activate it (THIS IS A SIDE EFFECT!)
					preset.setActive(true);
					adapter.notifyDataSetChanged();
					break;
				}
			}
			
			if (!dup) dup = S.getDb().hasYesVersionWithFilename(filename);
			
			if (dup) {
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.ed_file_file_sudah_ada_dalam_daftar_versi, filename))
				.setPositiveButton(R.string.ok, null)
				.show();
				return;
			}
		}
		
		try {
			BibleReader pembaca = YesReaderFactory.createYesReader(filename);
			int maxOrdering = S.getDb().getYesVersionMaxOrdering();
			if (maxOrdering == 0) maxOrdering = 100; // default
			
			MVersionYes yes = new MVersionYes();
			yes.type = Db.Version.kind_yes;
			yes.shortName = pembaca.getShortName();
			yes.longName = pembaca.getLongName();
			yes.description = pembaca.getDescription();
			yes.filename = filename;
			yes.originalPdbFilename = originalpdbname;
			yes.ordering = maxOrdering + 1;
			
			S.getDb().insertYesVersionWithActive(yes, true);
			adapter.initYesVersionList();
			adapter.notifyDataSetChanged();
		} catch (Exception e) {
			new AlertDialog.Builder(this)
			.setTitle(R.string.ed_error_encountered)
			.setMessage(e.getClass().getSimpleName() + ": " + e.getMessage()) //$NON-NLS-1$
			.setPositiveButton(R.string.ok, null)
			.show();
		}
	}

	private void handleFileOpenPdb(final String pdbFilename) {
		final String yesName = yesName(pdbFilename);
		
		// cek apakah sudah ada.
		if (S.getDb().hasYesVersionWithFilename(AddonManager.getVersionPath(yesName))) {
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
		
		ConvertOptionsDialog.ConvertOptionsCallback callback = new ConvertOptionsDialog.ConvertOptionsCallback() {
			private void showPdbReadErrorDialog(Throwable exception) {
				final StringWriter sw = new StringWriter(400);
				sw.append('(').append(exception.getClass().getName()).append("): ").append(exception.getMessage()).append('\n'); //$NON-NLS-1$
				exception.printStackTrace(new PrintWriter(sw));

				new AlertDialog.Builder(VersionsActivity.this)
				.setTitle(R.string.ed_error_reading_pdb_file)
				.setMessage(exception instanceof ConvertOptionsDialog.PdbKnownErrorException? exception.getMessage(): (getString(R.string.ed_details) + sw.toString()))
				.setPositiveButton(R.string.ok, null)
				.show();
			};

			private void showResult(final String filenamepdb, final String filenameyes, Throwable exception, List<String> wronglyConvertedBookNames) {
				if (exception != null) {
					showPdbReadErrorDialog(exception);
				} else {
					// sukses.
					handleFileOpenYes(filenameyes, new File(filenamepdb).getName());
					
					if (wronglyConvertedBookNames != null && wronglyConvertedBookNames.size() > 0) {
						StringBuilder msg = new StringBuilder(getString(R.string.ed_the_following_books_from_the_pdb_file_are_not_recognized) + '\n');
						for (String s: wronglyConvertedBookNames) {
							msg.append("- ").append(s).append('\n'); //$NON-NLS-1$
						}
						
						new AlertDialog.Builder(VersionsActivity.this)
						.setMessage(msg)
						.setPositiveButton(R.string.ok, null)
						.show();
					}
				}
			}

			@Override public void onPdbReadError(Throwable e) {
				showPdbReadErrorDialog(e);
			}
			
			@Override public void onOkYes2(final ConvertPdbToYes2.ConvertParams params) {
				final String yesFilename = AddonManager.getVersionPath(yesName);
				final ProgressDialog pd = ProgressDialog.show(VersionsActivity.this, null, getString(R.string.ed_reading_pdb_file), true, false);
				pd.setOnKeyListener(new OnKeyListener() {
					@Override public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
						if (keyCode == KeyEvent.KEYCODE_SEARCH) {
							return true;
						}
						return false;
					}
				});
				
				new AsyncTask<String, Object, ConvertPdbToYes2.ConvertResult>() {
					@Override protected ConvertPdbToYes2.ConvertResult doInBackground(String... _unused_) {
						ConvertPdbToYes2 converter = new ConvertPdbToYes2();
						converter.setConvertProgressListener(new ConvertPdbToYes2.ConvertProgressListener() {
							@Override public void onProgress(int at, String message) {
								Log.d(TAG, "Progress " + at + ": " + message); //$NON-NLS-1$ //$NON-NLS-2$
								publishProgress(at, message);
							}
							
							@Override public void onFinish() {
								Log.d(TAG, "Finish"); //$NON-NLS-1$
								publishProgress(null, null);
							}
						});
						return converter.convert(getApplicationContext(), pdbFilename, yesFilename, params);
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
					
					@Override protected void onPostExecute(ConvertPdbToYes2.ConvertResult result) {
						pd.dismiss();
						
						showResult(pdbFilename, yesFilename, result.exception, result.wronglyConvertedBookNames);
					}
				}.execute();
			}
		};
		
		ConvertOptionsDialog dialog = new ConvertOptionsDialog(this, pdbFilename, callback);
		dialog.show();
	}

	/**
	 * @return a filename for yes that will be converted from pdb file, such as "pdb-1234abcd-1.yes". Path not included.
	 */
	private String yesName(String filenamepdb) {
		byte[] digest = Digester.digestFile(DigestType.SHA1, new File(filenamepdb));
		if (digest == null) return null;
		String hash = Digester.toHex(digest).substring(0, 8);
		return "pdb-" + hash + "-1.yes";   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
	}

	// model
	public static abstract class MVersion {
		public String shortName;
		public String longName;
		public int type;
		public int ordering;
		
		/** unique id for comparison purposes */
		public abstract String getVersionId();
		/** return version so that it can be read. Null when not possible */
		public abstract Version getVersion();
		public abstract void setActive(boolean active);
		public abstract boolean getActive();
		public abstract boolean hasDataFile();
	}

	public static class MVersionInternal extends MVersion {
		public static String getVersionInternalId() {
			return "internal"; //$NON-NLS-1$
		}
		
		@Override public String getVersionId() {
			return getVersionInternalId();
		}

		@Override
		public Version getVersion() {
			return VersionImpl.getInternalVersion();
		}

		@Override
		public void setActive(boolean active) {
			// NOOP
		}

		@Override
		public boolean getActive() {
			return true; // always active
		}

		@Override public boolean hasDataFile() {
			return true; // always has
		}
	}
	
	public static class MVersionPreset extends MVersion {
		public String url;
		public String presetFilename;
		public String locale;
		
		@Override public boolean getActive() {
			return Preferences.getBoolean("edisi/preset/" + this.presetFilename + "/aktif", true); //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		@Override public void setActive(boolean active) {
			Preferences.setBoolean("edisi/preset/" + this.presetFilename + "/aktif", active); //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Override
		public String getVersionId() {
			return "preset/" + presetFilename; //$NON-NLS-1$
		}

		@Override
		public Version getVersion() {
			if (hasDataFile()) {
				final VersionImpl res = new VersionImpl(YesReaderFactory.createYesReader(AddonManager.getVersionPath(presetFilename)));
				res.setFallbackShortName(shortName);
				return res;
			} else {
				return null;
			}
		}

		@Override public boolean hasDataFile() {
			return AddonManager.hasVersion(presetFilename);
		}
	}
	
	public static class MVersionYes extends MVersion {
		public String description;
		public String filename;
		public String originalPdbFilename;
		public boolean cache_active; // so we don't need to keep reading/writing from/to db
		
		@Override
		public String getVersionId() {
			return "yes/" + filename; //$NON-NLS-1$
		}

		@Override
		public Version getVersion() {
			if (hasDataFile()) {
				BibleReader yesReader = YesReaderFactory.createYesReader(filename);
				if (yesReader == null) {
					Log.e(TAG, "YesReaderFactory failed to open the yes file");
					return null;
				}
				return new VersionImpl(yesReader);
			} else {
				return null;
			}
		}

		@Override
		public void setActive(boolean active) {
			this.cache_active = active;
			S.getDb().setYesVersionActive(this.filename, active);
		}

		@Override
		public boolean getActive() {
			return this.cache_active;
		}

		@Override public boolean hasDataFile() {
			File f = new File(filename);
			return f.exists() && f.canRead();
		}
	}
	
	public class VersionAdapter extends BaseAdapter {
		MVersionInternal internal;
		List<MVersionPreset> presets;
		List<MVersionYes> yeses;
		
		public void init() {
			AppConfig c = AppConfig.get();
			
			internal = new MVersionInternal();
			internal.setActive(true);
			internal.type = Db.Version.kind_internal;
			internal.longName = c.internalLongName;
			internal.ordering = 1;
			
			presets = new ArrayList<MVersionPreset>();
			presets.addAll(c.presets);
			
			// fix the active state based on whether the file exists and also preferences
			for (MVersionPreset preset: presets) {
				if (!AddonManager.hasVersion(preset.presetFilename)) {
					preset.setActive(false);
				}
			}
			
			initYesVersionList();
		}
		
		public void initYesVersionList() {
			yeses = S.getDb().listAllVersions();
		}

		@Override
		public int getCount() {
			return 1 /* internal */ + presets.size() + yeses.size() + 1 /* open */;
		}

		@Override
		public MVersion getItem(int position) {
			if (position < 1) return internal;
			if (position < 1 + presets.size()) return presets.get(position - 1);
			if (position < 1 + presets.size() + yeses.size()) return yeses.get(position - 1 - presets.size());
			if (position < 1 + presets.size() + yeses.size() + 1) return null; /* open */
			return null;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_version, null);
			
			CheckBox cActive = V.get(res, R.id.cActive);
			TextView lLongTitle = V.get(res, R.id.lLongTitle);
			TextView lDescription = V.get(res, R.id.lDescription);
			TextView lLanguage = V.get(res, R.id.lLanguage);
			
			if (position == getCount() - 1) { // open file
				cActive.setVisibility(View.GONE);
				lLongTitle.setText(R.string.ed_buka_file_pdb_yes_lainnya);
				lLongTitle.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_add_dark, 0, 0, 0);
				lDescription.setVisibility(View.GONE);
				lLanguage.setVisibility(View.GONE);
			} else { // one of the available versions
				MVersion mv = getItem(position);
				cActive.setVisibility(View.VISIBLE);
				cActive.setFocusable(false);
				cActive.setClickable(false);
				cActive.setChecked(mv.getActive());
				lLongTitle.setText(mv.longName);
				lLongTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
				if (mv instanceof MVersionInternal) {
					cActive.setEnabled(false);
					lDescription.setVisibility(View.GONE);
					lLanguage.setVisibility(View.GONE);
				} else if (mv instanceof MVersionPreset) {
					cActive.setEnabled(true);
					String locale = ((MVersionPreset) mv).locale;
					lDescription.setVisibility(View.GONE);
					
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
							lLanguage.setVisibility(View.VISIBLE);
							lLanguage.setText(display);
						} else {
							lLanguage.setVisibility(View.GONE);
						}
					} else {
						lLanguage.setVisibility(View.GONE);
					}
				} else if (mv instanceof MVersionYes) {
					cActive.setEnabled(true);
					lDescription.setVisibility(View.VISIBLE);
					lLanguage.setVisibility(View.GONE);
					MVersionYes yes = (MVersionYes) mv;
					String extra = ""; //$NON-NLS-1$
					if (yes.description != null) {
						extra += yes.description;
					}
					lDescription.setText(extra);
				}
			}
			
			return res;
		}
		
	}
}
