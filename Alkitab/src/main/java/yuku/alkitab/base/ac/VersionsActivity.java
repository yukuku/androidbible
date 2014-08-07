package yuku.alkitab.base.ac;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.app.NavUtils;
import android.support.v4.app.ShareCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.ViewPager;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
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
import yuku.alkitab.base.util.DownloadMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;


public class VersionsActivity extends Activity implements ActionBar.TabListener {
	public static final String TAG = VersionsActivity.class.getSimpleName();

	private static final int REQCODE_openFile = 1;

	/**
	 * The {@link android.support.v4.view.PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link android.support.v13.app.FragmentPagerAdapter} derivative, which will keep every
	 * loaded fragment in memory. If this becomes too memory intensive, it
	 * may be best to switch to a
	 * {@link android.support.v13.app.FragmentStatePagerAdapter}.
	 */
	SectionsPagerAdapter mSectionsPagerAdapter;

	/**
	 * The {@link android.support.v4.view.ViewPager} that will host the section contents.
	 */
	ViewPager mViewPager;

	public static Intent createIntent() {
		return new Intent(App.context, VersionsActivity.class);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_versions);

		setTitle(R.string.kelola_versi);

		// Set up the action bar.
		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		// Create the adapter that will return a fragment for each of the three
		// primary sections of the activity.
		mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager());

		// Set up the ViewPager with the sections adapter.
		mViewPager = V.get(this, R.id.viewPager);
		mViewPager.setAdapter(mSectionsPagerAdapter);

		// When swiping between different sections, select the corresponding
		// tab. We can also use ActionBar.Tab#select() to do this if we have
		// a reference to the Tab.
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				actionBar.setSelectedNavigationItem(position);
			}
		});

		// For each of the sections in the app, add a tab to the action bar.
		for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
			// Create a tab with text corresponding to the page title defined by
			// the adapter. Also specify this Activity object, which implements
			// the TabListener interface, as the callback (listener) for when
			// this tab is selected.
			actionBar.addTab(actionBar.newTab().setText(mSectionsPagerAdapter.getPageTitle(i)).setTabListener(this));
		}

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

	@Override
	public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
		// When the given tab is selected, switch to the corresponding page in
		// the ViewPager.
		mViewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
	}

	@Override
	public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
	}

	/**
	 * A {@link android.support.v13.app.FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the sections/tabs/pages.
	 */
	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			return VersionListFragment.newInstance(position == 1);
		}

		@Override
		public int getCount() {
			return 2;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) {
				case 0:
					return getString(R.string.ed_section_all);
				case 1:
					return getString(R.string.ed_section_downloaded);
			}
			return null;
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

			App.getLbm().sendBroadcast(new Intent(VersionListFragment.ACTION_RELOAD));
		} catch (Exception e) {
			new AlertDialog.Builder(this)
				.setTitle(R.string.ed_error_encountered)
				.setMessage(e.getClass().getSimpleName() + ": " + e.getMessage()) //$NON-NLS-1$
				.setPositiveButton(R.string.ok, null)
				.show();
		}
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


	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_openFile) {
			final FileChooserResult result = FileChooserActivity.obtainResult(data);
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
					final ProgressDialog pd = ProgressDialog.show(this, null, getString(R.string.sedang_mendekompres_harap_tunggu), true, false);
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

			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}


	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class VersionListFragment extends Fragment {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		private static final String ARG_DOWNLOADED_ONLY = "downloaded_only";

		public static final String ACTION_RELOAD = VersionListFragment.class.getName() + ".action.RELOAD";

		private static final int REQCODE_share = 2;
		private LayoutInflater inflater;

		ListView lsVersions;
		VersionAdapter adapter;
		private boolean downloadedOnly;

		/**
		 * Returns a new instance of this fragment for the given section
		 * number.
		 */
		public static VersionListFragment newInstance(final boolean downloadedOnly) {
			final VersionListFragment res = new VersionListFragment();
			final Bundle args = new Bundle();
			args.putBoolean(ARG_DOWNLOADED_ONLY, downloadedOnly);
			res.setArguments(args);
			return res;
		}

		public VersionListFragment() {
		}

		final BroadcastReceiver br = new BroadcastReceiver() {
			@Override
			public void onReceive(final Context context, final Intent intent) {
				if (ACTION_RELOAD.equals(intent.getAction())) {
					if (adapter != null) adapter.reload();
				}
			}
		};

		@Override
		public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			App.getLbm().registerReceiver(br, new IntentFilter(ACTION_RELOAD));

			downloadedOnly = getArguments().getBoolean(ARG_DOWNLOADED_ONLY);
		}

		@Override
		public void onDestroy() {
			super.onDestroy();

			App.getLbm().unregisterReceiver(br);
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			this.inflater = inflater;
			final View rootView = inflater.inflate(R.layout.fragment_versions, container, false);

			adapter = new VersionAdapter();

			lsVersions = V.get(rootView, R.id.lsVersions);
			lsVersions.setAdapter(adapter);

			return rootView;
		}

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

		void itemCheckboxClick(final Item item, final View itemView) {
			final MVersion mv = item.mv;

			if (mv instanceof MVersionPreset) {
				clickOnPresetVersion(V.<CheckBox>get(itemView, R.id.cActive), (MVersionPreset) mv);
			} else if (mv instanceof MVersionDb) {
				clickOnDbVersion(V.<CheckBox>get(itemView, R.id.cActive), (MVersionDb) mv);
			}

			App.getLbm().sendBroadcast(new Intent(ACTION_RELOAD));
		}

		static void addDetail(final SpannableStringBuilder sb, String key, String value) {
			int sb_len = sb.length();
			sb.append(key.toUpperCase(Locale.getDefault()) + ": ");
			sb.setSpan(new ForegroundColorSpan(0xffaaaaaa), sb_len, sb.length(), 0);
			sb.setSpan(new RelativeSizeSpan(0.7f), sb_len, sb.length(), 0);
			sb.setSpan(new StyleSpan(Typeface.BOLD), sb_len, sb.length(), 0);
			sb.append(value);
			sb.append("\n");
		}

		void itemNameClick(final Item item) {
			final MVersion mv = item.mv;

			final SpannableStringBuilder details = new SpannableStringBuilder();

			if (mv instanceof MVersionInternal) addDetail(details, getString(R.string.ed_type_key), getString(R.string.ed_type_internal));
			if (mv instanceof MVersionPreset) addDetail(details, getString(R.string.ed_type_key), getString(R.string.ed_type_preset));
			if (mv instanceof MVersionDb) addDetail(details, getString(R.string.ed_type_key), getString(R.string.ed_type_db));

			if (mv.locale != null) addDetail(details, getString(R.string.ed_locale_locale), mv.locale);

			if (mv.shortName != null) addDetail(details, getString(R.string.ed_shortName_shortName), mv.shortName);

			addDetail(details, getString(R.string.ed_title_title), mv.longName);

			if (mv instanceof MVersionPreset) {
				final MVersionPreset preset = (MVersionPreset) mv;
				addDetail(details, getString(R.string.ed_default_filename_file), preset.preset_name);
				addDetail(details, getString(R.string.ed_download_url_url), preset.download_url);
			}

			if (mv instanceof MVersionDb) {
				final MVersionDb mvDb = (MVersionDb) mv;
				addDetail(details, getString(R.string.ed_stored_in_file), mvDb.filename);
			}

			if (mv.description != null) details.append('\n').append(mv.description).append('\n');

			final AlertDialog.Builder b = new AlertDialog.Builder(getActivity());

			int button_count = 0;

			// can we update?
			if (hasUpdateAvailable(mv)) {
				button_count++;
				b.setPositiveButton(R.string.ed_update_button, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						startDownload(VersionConfig.get().getPreset(((MVersionDb) mv).preset_name));
					}
				});

				details.append("\n");
				final int details_len = details.length();
				details.append("  ");
				details.setSpan(new ImageSpan(App.context, R.drawable.ic_version_update, DynamicDrawableSpan.ALIGN_BASELINE), details_len, details_len + 1, 0);
				details.append(getString(R.string.ed_details_update_available));
			}

			// can we share?
			if (mv instanceof MVersionDb && mv.hasDataFile()) {
				button_count++;
				b.setNeutralButton(R.string.version_menu_share, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						final MVersionDb mvDb = (MVersionDb) mv;

						final Intent intent = ShareCompat.IntentBuilder.from(getActivity())
							.setType("application/octet-stream")
							.addStream(Uri.fromFile(new File(mvDb.filename)))
							.getIntent();

						startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.version_share_title)), REQCODE_share);
					}
				});
			}

			// can we delete?
			if (mv instanceof MVersionDb) {
				button_count++;
				b.setNegativeButton(R.string.buang_dari_daftar, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						final MVersionDb mvDb = (MVersionDb) mv;
						new AlertDialog.Builder(getActivity())
							.setMessage(getString(R.string.juga_hapus_file_datanya_file, mvDb.filename))
							.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
								@Override public void onClick(DialogInterface dialog, int which) {
									S.getDb().deleteVersion(mvDb);
									App.getLbm().sendBroadcast(new Intent(ACTION_RELOAD));
									new File(mvDb.filename).delete();
								}
							})
							.setNeutralButton(R.string.no, new DialogInterface.OnClickListener() {
								@Override public void onClick(DialogInterface dialog, int which) {
									S.getDb().deleteVersion(mvDb);
									App.getLbm().sendBroadcast(new Intent(ACTION_RELOAD));
								}
							})
							.setNegativeButton(R.string.cancel, null)
							.show();
					}
				});
			}

			// can we download?
			if (mv instanceof MVersionPreset) {
				button_count++;
				b.setPositiveButton(R.string.ed_download_button, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						startDownload((MVersionPreset) mv);
					}
				});
			}

			// if we have no buttons at all, add a no-op OK
			if (button_count == 0) {
				b.setPositiveButton(R.string.ok, null);
			}

			b.setTitle(R.string.ed_version_details);
			b.setMessage(details);
			b.show();
		}

		void clickOnPresetVersion(final CheckBox cActive, final MVersionPreset mv) {
			if (cActive.isChecked()) {
				throw new RuntimeException("THIS SHOULD NOT HAPPEN: preset may not have the active checkbox checked.");
			}

			startDownload(mv);
		}

		void startDownload(final MVersionPreset mv) {
			final String downloadKey = "version:preset_name:" + mv.preset_name;

			final int status = DownloadMapper.instance.getStatus(downloadKey);
			if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING) {
				// it's downloading!
				return;
			}

			final DownloadManager.Request req = new DownloadManager.Request(Uri.parse(mv.download_url))
				.setTitle(mv.longName)
				.setVisibleInDownloadsUi(false)
				.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

			final Map<String, String> attrs = new LinkedHashMap<>();
			attrs.put("preset_name", mv.preset_name);
			attrs.put("modifyTime", "" + mv.modifyTime);

			DownloadMapper.instance.enqueue(downloadKey, req, attrs);

			App.getLbm().sendBroadcast(new Intent(ACTION_RELOAD));
		}

		void clickOnDbVersion(final CheckBox cActive, final MVersionDb mv) {
			if (cActive.isChecked()) {
				mv.setActive(false);
			} else {
				if (mv.hasDataFile()) {
					mv.setActive(true);
				} else {
					new AlertDialog.Builder(getActivity())
						.setMessage(getString(R.string.the_file_for_this_version_is_no_longer_available_file, mv.filename))
						.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								S.getDb().deleteVersion(mv);
								App.getLbm().sendBroadcast(new Intent(ACTION_RELOAD));
							}
						})
						.setNegativeButton(R.string.no, null)
						.show();
				}
			}
		}

		@Override
		public void onActivityResult(int requestCode, int resultCode, Intent data) {
			if (requestCode == REQCODE_share) {
				ShareActivity.Result result = ShareActivity.obtainResult(data);
				if (result != null && result.chosenIntent != null) {
					startActivity(result.chosenIntent);
				}

				return;
			}

			super.onActivityResult(requestCode, resultCode, data);
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

				// presets (only for "all" tab)
				if (!downloadedOnly) {
					for (MVersionPreset preset : VersionConfig.get().presets) {
						if (presetNamesInDb.contains(preset.preset_name)) continue;

						items.add(new Item(preset));
					}
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
				return inflater.inflate(R.layout.item_version, parent, false);
			}

			@Override
			public void bindView(final View view, final int position, final ViewGroup parent) {
				final View panelRight = V.get(view, R.id.panelRight);
				final CheckBox cActive = V.get(view, R.id.cActive);
				final View progress = V.get(view, R.id.progress);
				final Button bLongName = V.get(view, R.id.bLongName);
				final View header = V.get(view, R.id.header);
				final TextView tLanguage = V.get(view, R.id.tLanguage);

				final Item item = getItem(position);
				final MVersion mv = item.mv;

				bLongName.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(final View v) {
						itemNameClick(item);
					}
				});

				panelRight.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(final View v) {
						itemCheckboxClick(item, view);
					}
				});

				cActive.setChecked(mv.getActive());

				bLongName.setText(mv.longName);
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

				// Update icon
				if (hasUpdateAvailable(mv)) {
					bLongName.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_version_update, 0, 0, 0);
				} else {
					bLongName.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
				}

				// downloading or not?
				final boolean downloading;
				if (mv instanceof MVersionInternal) {
					downloading = false;
				} else if (mv instanceof MVersionPreset) {
					final String downloadKey = "version:preset_name:" + ((MVersionPreset) mv).preset_name;
					final int status = DownloadMapper.instance.getStatus(downloadKey);
					downloading = (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING);
				} else if (mv instanceof MVersionDb && ((MVersionDb) mv).preset_name != null) { // probably downloading, in case of updating
					final String downloadKey = "version:preset_name:" + ((MVersionDb) mv).preset_name;
					final int status = DownloadMapper.instance.getStatus(downloadKey);
					downloading = (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING);
				} else {
					downloading = false;
				}

				if (downloading) {
					cActive.setVisibility(View.INVISIBLE);
					progress.setVisibility(View.VISIBLE);
				} else {
					cActive.setVisibility(View.VISIBLE);
					progress.setVisibility(View.INVISIBLE);
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

		private boolean hasUpdateAvailable(final MVersion mv) {
			final boolean updateIcon;
			if (mv instanceof MVersionDb) {
				final MVersionDb mvDb = (MVersionDb) mv;
				if (mvDb.preset_name == null || mvDb.modifyTime == 0) {
					updateIcon = false;
				} else {
					final int available = VersionConfig.get().getModifyTime(mvDb.preset_name);
					if (available == 0 || available <= mvDb.modifyTime) {
						updateIcon = false;
					} else {
						updateIcon = true;
					}
				}
			} else {
				updateIcon = false;
			}
			return updateIcon;
		}

	}

}
