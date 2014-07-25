
package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
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
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;
import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.config.VersionConfig;
import yuku.alkitab.base.model.MVersion;
import yuku.alkitab.base.model.MVersionDb;
import yuku.alkitab.base.model.MVersionInternal;
import yuku.alkitab.base.model.MVersionPreset;
import yuku.alkitab.base.pdbconvert.ConvertOptionsDialog;
import yuku.alkitab.base.pdbconvert.ConvertPdbToYes2;
import yuku.alkitab.base.storage.YesReaderFactory;
import yuku.alkitab.base.util.AddonManager;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.io.BibleReader;
import yuku.alkitab.util.IntArrayList;
import yuku.filechooser.FileChooserActivity;
import yuku.filechooser.FileChooserConfig;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class VersionsActivity extends BaseActivity {
	public static final String TAG = VersionsActivity.class.getSimpleName();

	private static final int REQCODE_openFile = 1;
	private static final int REQCODE_share = 2;
	
	ListView lsVersions;
	VersionAdapter adapter;
	
	Map<String, String> cache_displayLanguage = new HashMap<>();
	
	String getDisplayLanguage(String locale) {
		if (TextUtils.isEmpty(locale)) {
			return "not specified";
		}

		String display = cache_displayLanguage.get(locale);
		if (display != null) {
			return display;
		}

		display = new Locale(locale).getDisplayLanguage();
		if (display == null || U.equals(display, locale)) {

			// try asking version config locale display
			display = VersionConfig.get().locale_display.get(locale);

			if (display == null) {
				display = locale; // can't be null now
			}
		}
		cache_displayLanguage.put(locale, display);

		return display;
	}

	static class Item {
		MVersion mv;
		boolean firstInGroup;

		public Item(final MVersion mv) {
			this.mv = mv;
		}
	}
	
	public static Intent createIntent() {
		return new Intent(App.context, VersionsActivity.class);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_versions);
		setTitle(R.string.kelola_versi);

		adapter = new VersionAdapter();

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
				if (name == null) {
					isYesFile = null;
				} else {
					final String namelc = name.toLowerCase(Locale.US);
					if (namelc.endsWith(".yes")) {
						isYesFile = true;
					} else if (namelc.endsWith(".pdb")) {
						isYesFile = false;
					} else {
						isYesFile = null;
					}
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
			}

			if (!isYesFile) { // pdb file
				// copy the file to cache first
				File cacheFile = new File(getCacheDir(), "datafile");
				InputStream input = getContentResolver().openInputStream(uri);
				copyStreamToFile(input, cacheFile);
				input.close();

				handleFileOpenPdb(cacheFile.getAbsolutePath());
				return;
			}

			if (isLocalFile) { // opening a local yes file
				handleFileOpenYes(uri.getPath());
				return;
			}

			// opening a nonlocal yes file
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

			handleFileOpenYes(localFile.getAbsolutePath());

		} catch (Exception e) {
			new AlertDialog.Builder(this)
			.setMessage(R.string.open_file_cant_read_source)
			.setPositiveButton(R.string.ok, null)
			.show();
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

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_versions, menu);
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
	
	private AdapterView.OnItemClickListener lsVersions_itemClick = new AdapterView.OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			final Item item = adapter.getItem(position);
			final MVersion mv = item.mv;
			
			if (mv instanceof MVersionPreset) {
				clickOnPresetVersion(V.<CheckBox>get(v, R.id.cActive), (MVersionPreset) mv);
			} else if (mv instanceof MVersionDb) {
				clickOnDbVersion(V.<CheckBox>get(v, R.id.cActive), (MVersionDb) mv);
			} else if (position == adapter.getCount() - 1) {
				clickOnOpenFile();
			}
			
			adapter.notifyDataSetChanged();
		}
	};
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		if (menu == null) return;

		getMenuInflater().inflate(R.menu.context_version, menu);

		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final Item item = adapter.getItem(info.position);
		final MVersion mv = item.mv;

		final MenuItem menuDelete = menu.findItem(R.id.menuDelete);
		if (menuDelete != null) {
			if (mv instanceof MVersionInternal) {
				menuDelete.setEnabled(false);
			} else if (mv instanceof MVersionPreset) {
				menuDelete.setEnabled(false);
			} else if (mv instanceof MVersionDb) {
				menuDelete.setEnabled(true);
			}
		}

		final MenuItem menuShare = menu.findItem(R.id.menuShare);
		if (menuShare != null) {
			if (mv instanceof MVersionInternal) {
				menuShare.setEnabled(false);
			} else if (mv instanceof MVersionPreset) {
				menuShare.setEnabled(false);
			} else if (mv instanceof MVersionDb) {
				menuShare.setEnabled(mv.hasDataFile());
			}
		}
	}
	
	@Override public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		final MVersion mv = adapter.getItem(info.position).mv;

		final int itemId = item.getItemId();
		if (itemId == R.id.menuDelete) {
			if (mv instanceof MVersionDb) {
				final MVersionDb mvDb = (MVersionDb) mv;
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.juga_hapus_file_datanya_file, mvDb.filename))
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						S.getDb().deleteVersion(mvDb);
						adapter.reload();
						new File(mvDb.filename).delete();
					}
				})
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						S.getDb().deleteVersion(mvDb);
						adapter.reload();
					}
				})
				.show();
			}
			return true;
		} else if (itemId == R.id.menuDetails) {
			StringBuilder details = new StringBuilder();
			if (mv instanceof MVersionInternal) details.append(getString(R.string.ed_type_built_in)).append('\n');
			if (mv instanceof MVersionPreset) details.append(getString(R.string.ed_type_preset)).append('\n');
			if (mv instanceof MVersionDb) details.append(getString(R.string.ed_type_add_on)).append('\n');
			if (mv.locale != null) details.append(getString(R.string.ed_locale_locale, mv.locale)).append('\n');
			if (mv.shortName != null) details.append(getString(R.string.ed_shortName_shortName, mv.shortName)).append('\n');
			details.append(getString(R.string.ed_title_title, mv.longName)).append('\n');

			if (mv instanceof MVersionPreset) {
				final MVersionPreset preset = (MVersionPreset) mv;
				details.append(getString(R.string.ed_default_filename_file, preset.preset_name)).append('\n');
				if (AddonManager.hasVersion(preset.preset_name + ".yes")) {
					details.append("THIS SHOULD NOT HAPPEN\n"); // because a version with the file should be MVersionDb
				} else {
					details.append(getString(R.string.ed_download_url_url, preset.download_url)).append('\n');
				}
			}
			if (mv instanceof MVersionDb) {
				MVersionDb mvDb = (MVersionDb) mv;
				details.append(getString(R.string.ed_stored_in_file, mvDb.filename)).append('\n');
			}
			if (mv.description != null) details.append('\n').append(mv.description).append('\n');

			new AlertDialog.Builder(this)
				.setTitle(R.string.ed_version_details)
				.setMessage(details)
				.setPositiveButton(R.string.ok, null)
				.show();

			return true;
		} else if (itemId == R.id.menuShare) {
			if (mv instanceof MVersionDb) {
				final MVersionDb mvDb = (MVersionDb) mv;

				final Intent intent = ShareCompat.IntentBuilder.from(this)
					.setType("application/octet-stream")
					.addStream(Uri.fromFile(new File(mvDb.filename)))
					.getIntent();

				startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.version_share_title)), REQCODE_share);
			}
			
			return true;
		}
		
		return super.onContextItemSelected(item);
	}
	
	void clickOnPresetVersion(final CheckBox cActive, final MVersionPreset mv) {
		if (cActive.isChecked()) {
			throw new RuntimeException("THIS SHOULD NOT HAPPEN: preset may not have the active checkbox checked.");
		}

		final ProgressDialog pd = ProgressDialog.show(this, getString(R.string.mengunduh_nama, mv.longName), getString(R.string.mulai_mengunduh), true, true);

		final AddonManager.DownloadListener downloadListener = new AddonManager.DownloadListener() {
			@Override
			public void onDownloadFinished(final AddonManager.Element e) {
				pd.dismiss();

				final BibleReader reader = YesReaderFactory.createYesReader(e.dest);
				if (reader == null) {
					new File(e.dest).delete();

					new AlertDialog.Builder(VersionsActivity.this)
						.setMessage(R.string.version_download_corrupted_file)
						.setPositiveButton(R.string.ok, null)
						.show();

					return;
				}

				// success!
				Toast.makeText(App.context, TextUtils.expandTemplate(getText(R.string.version_download_complete), mv.longName), Toast.LENGTH_LONG).show();

				int maxOrdering = S.getDb().getVersionMaxOrdering();
				if (maxOrdering == 0) maxOrdering = 100; // default

				final MVersionDb mvDb = new MVersionDb();
				mvDb.locale = reader.getLocale();
				mvDb.shortName = reader.getShortName();
				mvDb.longName = reader.getLongName();
				mvDb.description = reader.getDescription();
				mvDb.filename = e.dest;
				mvDb.preset_name = mv.preset_name;
				mvDb.modifyTime = mv.modifyTime;
				mvDb.ordering = maxOrdering + 1;

				S.getDb().insertVersionWithActive(mvDb, true);

				final String locale = mv.locale;
				if ("ta".equals(locale) || "te".equals(locale) || "my".equals(locale) || "el".equals(locale)) {
					new AlertDialog.Builder(VersionsActivity.this)
						.setMessage(R.string.version_download_need_fonts)
						.setPositiveButton(R.string.ok, null)
						.show();
				}
			}

			@Override
			public void onDownloadFailed(AddonManager.Element e, final String description, final Throwable t) {
				Toast.makeText(
					App.context,
					description != null ? description : getString(R.string.gagal_mengunduh_edisi_judul_ex_pastikan_internet, mv.longName,
						t == null ? "null" : t.getClass().getCanonicalName() + ": " + t.getMessage()), Toast.LENGTH_LONG
				).show();

				pd.dismiss();
			}

			@Override
			public void onDownloadProgress(final AddonManager.Element e, final int progress) {
				if (progress >= 0) {
					pd.setMessage(getString(R.string.terunduh_sampe_byte, progress));
				} else {
					pd.setMessage(getString(R.string.sedang_mendekompres_harap_tunggu));
				}
			}

			@Override
			public void onDownloadCancelled(AddonManager.Element e) {
				Toast.makeText(App.context, R.string.pengunduhan_dibatalkan, Toast.LENGTH_SHORT).show();
				pd.dismiss();
			}
		};

		final AddonManager.DownloadThread downloadThread = AddonManager.getDownloadThread();
		final AddonManager.Element e = downloadThread.enqueue(mv.download_url, AddonManager.getVersionPath(mv.preset_name + ".yes"), downloadListener);

		downloadThread.start();

		pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				e.cancelled = true;
			}
		});
		pd.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				adapter.reload();
			}
		});
	}

	void clickOnDbVersion(final CheckBox cActive, final MVersionDb mv) {
		if (cActive.isChecked()) {
			mv.setActive(false);
		} else {
			if (mv.hasDataFile()) {
				mv.setActive(true);
			} else {
				new AlertDialog.Builder(this)
					.setMessage(getString(R.string.the_file_for_this_version_is_no_longer_available_file, mv.filename))
					.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							S.getDb().deleteVersion(mv);
							adapter.reload();
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
			config.mode = FileChooserConfig.Mode.Open;
			config.initialDir = Environment.getExternalStorageDirectory().getAbsolutePath();
			config.title = getString(R.string.ed_choose_pdb_or_yes_file);
			config.pattern = ".*\\.(?i:pdb|yes|yes\\.gz)"; //$NON-NLS-1$

			startActivityForResult(FileChooserActivity.createIntent(App.context, config), REQCODE_openFile);
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
					handleFileOpenYes(maybeDecompressed.getAbsolutePath());
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
							
							handleFileOpenYes(result.getAbsolutePath());
						}
					}.execute();
				}
			} else if (filename.toLowerCase(Locale.US).endsWith(".yes")) { //$NON-NLS-1$
				handleFileOpenYes(filename);
			} else if (filename.toLowerCase(Locale.US).endsWith(".pdb")) { //$NON-NLS-1$
				handleFileOpenPdb(filename);
			} else {
				Toast.makeText(App.context, R.string.ed_invalid_file_selected, Toast.LENGTH_SHORT).show();
			}
		} else if (requestCode == REQCODE_share) {
			ShareActivity.Result result = ShareActivity.obtainResult(data);
			if (result != null && result.chosenIntent != null) {
				startActivity(result.chosenIntent);
			}
		}
	}
	
	void handleFileOpenYes(String filename) {
		{ // look for duplicates
			if (S.getDb().hasVersionWithFilename(filename)) {
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.ed_file_file_sudah_ada_dalam_daftar_versi, filename))
				.setPositiveButton(R.string.ok, null)
				.show();
				return;
			}
		}
		
		try {
			final BibleReader reader = YesReaderFactory.createYesReader(filename);
			if (reader == null) {
				throw new Exception("Not a valid YES file.");
			}

			int maxOrdering = S.getDb().getVersionMaxOrdering();
			if (maxOrdering == 0) maxOrdering = 100; // default

			final MVersionDb mvDb = new MVersionDb();
			mvDb.locale = reader.getLocale();
			mvDb.shortName = reader.getShortName();
			mvDb.longName = reader.getLongName();
			mvDb.description = reader.getDescription();
			mvDb.filename = filename;
			mvDb.ordering = maxOrdering + 1;

			// check if this yes file is one already mentioned in the preset list
			String preset_name = null;
			for (MVersionPreset preset : VersionConfig.get().presets) {
				if (U.equals(AddonManager.getVersionPath(preset.preset_name + ".yes"), filename)) {
					preset_name = preset.preset_name;
				}
			}
			mvDb.preset_name = preset_name;

			S.getDb().insertVersionWithActive(mvDb, true);
			adapter.reload();
		} catch (Exception e) {
			new AlertDialog.Builder(this)
			.setTitle(R.string.ed_error_encountered)
			.setMessage(e.getClass().getSimpleName() + ": " + e.getMessage()) //$NON-NLS-1$
			.setPositiveButton(R.string.ok, null)
			.show();
		}
	}

	private void handleFileOpenPdb(final String pdbFilename) {
		final String yesName = yesNameForPdb(pdbFilename);
		
		// check if it exists previously
		if (S.getDb().hasVersionWithFilename(AddonManager.getVersionPath(yesName))) {
			new AlertDialog.Builder(this)
				.setMessage(R.string.ed_this_file_is_already_on_the_list)
				.setPositiveButton(R.string.ok, null)
				.show();
			return;
		}

		if (!AddonManager.mkYesDir()) {
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
			}

			private void showResult(final String filenameyes, Throwable exception, List<String> wronglyConvertedBookNames) {
				if (exception != null) {
					showPdbReadErrorDialog(exception);
				} else {
					// sukses.
					handleFileOpenYes(filenameyes);
					
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
						return converter.convert(App.context, pdbFilename, yesFilename, params);
					}
					
					@Override protected void onProgressUpdate(Object... values) {
						if (values[0] == null) {
							pd.setMessage(getString(R.string.ed_finished));
						} else {
							int at = (Integer) values[0];
							String message = (String) values[1];
							pd.setMessage("(" + at + ") " + message + "...");  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
						}
					}
					
					@Override protected void onPostExecute(ConvertPdbToYes2.ConvertResult result) {
						pd.dismiss();
						
						showResult(yesFilename, result.exception, result.wronglyConvertedBookNames);
					}
				}.execute();
			}
		};
		
		ConvertOptionsDialog dialog = new ConvertOptionsDialog(this, pdbFilename, callback);
		dialog.show();
	}

	/**
	 * @return a filename for yes that will be converted from pdb file, such as "pdb-XXX.yes"
	 * XXX is the original filename without the .pdb or .PDB ending, converted to lowercase.
	 * All except alphanumeric and . - _ are stripped.
	 * Path not included.
	 *
	 * Previously it was like "pdb-1234abcd-1.yes".
	 */
	private String yesNameForPdb(String filenamepdb) {
		String base = filenamepdb.toLowerCase(Locale.US);
		if (base.endsWith(".pdb")) {
			base = base.substring(0, base.length() - 4);
		}
		base = base.replaceAll("[^0-9A-Z_\\.-]", "");
		return "pdb-" + base + ".yes";
	}

	public class VersionAdapter extends EasyAdapter implements SectionIndexer {
		final List<Item> items = new ArrayList<>();
		String[] section_labels;
		int[] section_indexes;

		VersionAdapter() {
			reload();
		}

		/**
		 * The list of versions are loaded as follows:
		 * - Internal version {@link yuku.alkitab.base.model.MVersionInternal}, is always there
		 * - Versions stored in database {@link yuku.alkitab.base.model.MVersionDb} is all loaded
		 * - For each {@link yuku.alkitab.base.model.MVersionPreset} defined in {@link yuku.alkitab.base.config.VersionConfig},
		 *   check if the {@link yuku.alkitab.base.model.MVersionPreset#preset_name} corresponds to one of the
		 *   database version above. If it does, do not add to the resulting list. Otherwise, add it so user can download it.
		 *
		 * Note: Downloaded preset version will become database version after added.
		 */
		void reload() {
			items.clear();

			{ // internal
				final AppConfig ac = AppConfig.get();
				final MVersionInternal internal = new MVersionInternal();
				internal.locale = ac.internalLocale;
				internal.shortName = ac.internalShortName;
				internal.longName = ac.internalLongName;
				internal.description = null;
				internal.ordering = 1;

				items.add(new Item(internal));
			}

			final Set<String> presetNamesInDb = new HashSet<>();

			// db
			for (MVersionDb mv : S.getDb().listAllVersions()) {
				items.add(new Item(mv));
				if (mv.preset_name != null) {
					presetNamesInDb.add(mv.preset_name);
				}
			}

			// presets
			for (MVersionPreset preset : VersionConfig.get().presets) {
				if (presetNamesInDb.contains(preset.preset_name)) continue;

				items.add(new Item(preset));
			}

			// sort items
			Collections.sort(items, new Comparator<Item>() {
				@Override
				public int compare(final Item a, final Item b) {
					final String locale_a = a.mv.locale;
					final String locale_b = b.mv.locale;
					if (U.equals(locale_a, locale_b)) {
						return a.mv.longName.compareToIgnoreCase(b.mv.longName);
					}
					if (locale_a == null) {
						return +1;
					} else if (locale_b == null) {
						return -1;
					}

					return getDisplayLanguage(locale_a).compareToIgnoreCase(getDisplayLanguage(locale_b));
				}
			});
			
			// mark first item in each group
			String lastLocale = "<sentinel>";
			for (Item item : items) {
				item.firstInGroup = !U.equals(item.mv.locale, lastLocale);
				lastLocale = item.mv.locale;
			}

			// generate sections
			final List<String> section_labels = new ArrayList<>();
			final IntArrayList section_indexes = new IntArrayList();
			char lastChar = 0;
			for (int i = 0; i < items.size(); i++) {
				final Item item = items.get(i);
				final char c;
				if (!TextUtils.isEmpty(item.mv.locale)) {
					final String display = getDisplayLanguage(item.mv.locale);
					c = Character.toUpperCase(display.charAt(0));
				} else {
					c = 1; // special value
				}

				if (lastChar != c) {
					section_labels.add(c == 1? "…": ("" + c));
					section_indexes.add(i);
					lastChar = c;
				}
			}

			if (BuildConfig.DEBUG) {
				Log.d(TAG, "section labels: " + section_labels);
				Log.d(TAG, "section indexes: " + section_indexes);
			}

			this.section_labels = section_labels.toArray(new String[section_labels.size()]);
			this.section_indexes = new int[section_indexes.size()];
			System.arraycopy(section_indexes.buffer(), 0, this.section_indexes, 0, section_indexes.size());

			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return items.size();
		}

		@Override
		public Item getItem(int position) {
			return items.get(position);
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(R.layout.item_version, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			final CheckBox cActive = V.get(view, R.id.cActive);
			final TextView tLongName = V.get(view, R.id.tLongName);
			final View header = V.get(view, R.id.header);
			final TextView tLanguage = V.get(view, R.id.tLanguage);

			final Item item = getItem(position);
			final MVersion mv = item.mv;

			cActive.setChecked(mv.getActive());
			
			tLongName.setText(mv.longName);
			tLanguage.setText(getDisplayLanguage(mv.locale));
			
			if (mv instanceof MVersionInternal) {
				cActive.setEnabled(false);
			} else if (mv instanceof MVersionPreset) {
				cActive.setEnabled(true);
			} else if (mv instanceof MVersionDb) {
				cActive.setEnabled(true);
			}

			if (item.firstInGroup) {
				header.setVisibility(View.VISIBLE);
			} else {
				header.setVisibility(View.GONE);
			}
		}

		@Override
		public String[] getSections() {
			return section_labels;
		}

		@Override
		public int getPositionForSection(final int sectionIndex) {
			return section_indexes[sectionIndex];
		}

		@Override
		public int getSectionForPosition(final int position) {
			final int pos = Arrays.binarySearch(section_indexes, position);
			return pos >= 0 ? pos : (-pos - 1);
		}
	}
}
