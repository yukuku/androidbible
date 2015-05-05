package yuku.alkitab.base.ac;

import android.app.DownloadManager;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.app.NavUtils;
import android.support.v4.app.ShareCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.samples.apps.iosched.ui.widget.SlidingTabLayout;
import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.config.VersionConfig;
import yuku.alkitab.base.model.MVersion;
import yuku.alkitab.base.model.MVersionDb;
import yuku.alkitab.base.model.MVersionInternal;
import yuku.alkitab.base.model.MVersionPreset;
import yuku.alkitab.base.pdbconvert.ConvertOptionsDialog;
import yuku.alkitab.base.pdbconvert.ConvertPdbToYes2;
import yuku.alkitab.base.storage.YesReaderFactory;
import yuku.alkitab.base.sv.VersionConfigUpdaterService;
import yuku.alkitab.base.util.AddonManager;
import yuku.alkitab.base.util.DownloadMapper;
import yuku.alkitab.base.util.QueryTokenizer;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.io.BibleReader;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.zip.GZIPInputStream;


public class VersionsActivity extends BaseActivity {
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
	SlidingTabLayout slidingTabs;
	String query_text;

	public static Intent createIntent() {
		return new Intent(App.context, VersionsActivity.class);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_versions);

		setTitle(R.string.kelola_versi);

		final Toolbar toolbar = V.get(this, R.id.toolbar);
		setSupportActionBar(toolbar); // must be done first before below lines
		toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
		toolbar.setNavigationOnClickListener(v -> navigateUp());

		// Create the adapter that will return a fragment for each of the three
		// primary sections of the activity.
		mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager());

		// Set up the ViewPager with the sections adapter.
		mViewPager = V.get(this, R.id.viewPager);
		mViewPager.setAdapter(mSectionsPagerAdapter);

		slidingTabs = V.get(this, R.id.sliding_tabs);
		slidingTabs.setCustomTabColorizer(position -> getResources().getColor(R.color.accent));
		slidingTabs.setViewPager(mViewPager);

		processIntent(getIntent(), "onCreate");

		// try to auto-update version list
		VersionConfigUpdaterService.checkUpdate(true);
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

		// we are trying to open a file, so let's go to the DOWNLOADED tab, as it is more relevant.
		mViewPager.setCurrentItem(1);

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
				new AlertDialogWrapper.Builder(this)
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
				new AlertDialogWrapper.Builder(this)
					.setMessage(getString(R.string.tidak_bisa_membuat_folder, AddonManager.getYesPath()))
					.setPositiveButton(R.string.ok, null)
					.show();
				return;
			}

			File localFile = new File(AddonManager.getYesPath(), filelastname);
			if (localFile.exists()) {
				new AlertDialogWrapper.Builder(this)
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
			new AlertDialogWrapper.Builder(this)
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

	// taken from FragmentPagerAdapter
	private static String makeFragmentName(int viewId, int index) {
		return "android:switcher:" + viewId + ":" + index;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_versions, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		final MenuItem menuSearch = menu.findItem(R.id.menuSearch);
		MenuItemCompat.setOnActionExpandListener(menuSearch, new MenuItemCompat.OnActionExpandListener() {
			@Override
			public boolean onMenuItemActionExpand(final MenuItem item) {
				setOthersVisible(item, false);
				return true;
			}

			@Override
			public boolean onMenuItemActionCollapse(final MenuItem item) {
				setOthersVisible(item, true);
				return true;
			}

			private void setOthersVisible(final MenuItem except, final boolean visible) {
				for (int i = 0, len = menu.size(); i < len; i++) {
					final MenuItem other = menu.getItem(i);
					if (except != other) {
						other.setVisible(visible);
					}
				}
			}
		});

		final SearchView searchView = (SearchView) menuSearch.getActionView();
		searchView.setOnQueryTextListener(searchView_change);

		return true;
	}

	final SearchView.OnQueryTextListener searchView_change = new SearchView.OnQueryTextListener() {
		@Override
		public boolean onQueryTextSubmit(final String query) {
			query_text = query;
			startSearch();
			return false;
		}

		@Override
		public boolean onQueryTextChange(final String newText) {
			query_text = newText;
			startSearch();
			return false;
		}
	};

	private void startSearch() {
		// broadcast to all fragments that we have a new query_text
		for (final String tag : new String[]{makeFragmentName(R.id.viewPager, 0), makeFragmentName(R.id.viewPager, 1)}) {
			final Fragment f = getFragmentManager().findFragmentByTag(tag);
			if (f instanceof QueryTextReceiver) {
				((QueryTextReceiver) f).setQueryText(query_text);
			}
		}
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menuAddFromLocal:
				clickOnOpenFile();
				return true;
			case R.id.menuAddFromUrl:
				openUrlInputDialog();
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
			new AlertDialogWrapper.Builder(this)
				.setMessage(R.string.ed_no_external_storage)
				.setPositiveButton(R.string.ok, null)
				.show();
		}
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
			return VersionListFragment.newInstance(position == 1, query_text);
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
			new AlertDialogWrapper.Builder(this)
				.setMessage(R.string.ed_this_file_is_already_on_the_list)
				.setPositiveButton(R.string.ok, null)
				.show();
			return;
		}

		if (!AddonManager.mkYesDir()) {
			new AlertDialogWrapper.Builder(this)
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

				new AlertDialogWrapper.Builder(VersionsActivity.this)
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

						new AlertDialogWrapper.Builder(VersionsActivity.this)
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

				final MaterialDialog pd = new MaterialDialog.Builder(VersionsActivity.this)
					.content(R.string.ed_reading_pdb_file)
					.cancelable(false)
					.progress(true, 0)
					.show();

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
							pd.setContent(getString(R.string.ed_finished));
						} else {
							int at = (Integer) values[0];
							String message = (String) values[1];
							pd.setContent("(" + at + ") " + message + "...");
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
				new AlertDialogWrapper.Builder(this)
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
			if (maxOrdering == 0) maxOrdering = MVersionDb.DEFAULT_ORDERING_START;

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
			new AlertDialogWrapper.Builder(this)
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

	void openUrlInputDialog() {
		new MaterialDialog.Builder(this)
			.customView(R.layout.dialog_version_add_from_url, false)
			.positiveText(R.string.ok)
			.callback(new MaterialDialog.ButtonCallback() {
				@Override
				public void onPositive(final MaterialDialog dialog) {
					final EditText tUrl = V.get(dialog.getCustomView(), R.id.tUrl);

					final String url = tUrl.getText().toString().trim();
					if (url.length() == 0) {
						return;
					}

					final Uri uri = Uri.parse(url);
					final String scheme = uri.getScheme();
					if (!U.equals(scheme, "http") && !U.equals(scheme, "https")) {
						new AlertDialogWrapper.Builder(VersionsActivity.this)
							.setMessage(R.string.version_download_invalid_url)
							.setPositiveButton(R.string.ok, null)
							.show();
						return;
					}

					// guess destination filename
					String last = uri.getLastPathSegment();
					if (TextUtils.isEmpty(last) || !last.toLowerCase(Locale.US).endsWith(".yes")) {
						new AlertDialogWrapper.Builder(VersionsActivity.this)
							.setMessage(R.string.version_download_not_yes)
							.setPositiveButton(R.string.ok, null)
							.show();
						return;
					}

					{
						final String downloadKey = "version:url:" + url;

						final int status = DownloadMapper.instance.getStatus(downloadKey);
						if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING) {
							// it's downloading!
							return;
						}

						final DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url))
							.setTitle(last)
							.setVisibleInDownloadsUi(false)
							.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

						final Map<String, String> attrs = new LinkedHashMap<>();
						attrs.put("download_type", "url");
						attrs.put("filename_last_segment", last);

						DownloadMapper.instance.enqueue(downloadKey, req, attrs);
					}
				}
			})
			.show();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_openFile) {
			final FileChooserResult result = FileChooserActivity.obtainResult(data);
			if (result == null) {
				return;
			}

			// we are trying to open a file, so let's go to the DOWNLOADED tab, as it is more relevant.
			mViewPager.setCurrentItem(1);

			final String filename = result.firstFilename;

			if (filename.toLowerCase(Locale.US).endsWith(".yes.gz")) { //$NON-NLS-1$
				// decompress or see if the same filename without .gz exists
				final File maybeDecompressed = new File(filename.substring(0, filename.length() - 3));
				if (maybeDecompressed.exists() && !maybeDecompressed.isDirectory() && maybeDecompressed.canRead()) {
					handleFileOpenYes(maybeDecompressed.getAbsolutePath());
				} else {
					final MaterialDialog pd = new MaterialDialog.Builder(this)
						.content(R.string.sedang_mendekompres_harap_tunggu)
						.cancelable(false)
						.progress(true, 0)
						.show();

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
	public static class VersionListFragment extends Fragment implements QueryTextReceiver {
		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		private static final String ARG_DOWNLOADED_ONLY = "downloaded_only";
		private static final String ARG_INITIAL_QUERY_TEXT = "initial_query_text";

		public static final String ACTION_RELOAD = VersionListFragment.class.getName() + ".action.RELOAD";
		public static final String ACTION_UPDATE_REFRESHING_STATUS = VersionListFragment.class.getName() + ".action.UPDATE_REFRESHING_STATUS";
		public static final String EXTRA_refreshing = "refreshing";

		private static final int REQCODE_share = 2;
		private LayoutInflater inflater;

		SwipeRefreshLayout swiper;
		DragSortListView lsVersions;
		VersionAdapter adapter;
		private boolean downloadedOnly;
		private String query_text;

		/**
		 * Returns a new instance of this fragment for the given section
		 * number.
		 */
		public static VersionListFragment newInstance(final boolean downloadedOnly, final String initial_query_text) {
			final VersionListFragment res = new VersionListFragment();
			final Bundle args = new Bundle();
			args.putBoolean(ARG_DOWNLOADED_ONLY, downloadedOnly);
			args.putString(ARG_INITIAL_QUERY_TEXT, initial_query_text);
			res.setArguments(args);
			return res;
		}

		public VersionListFragment() {
		}

		final BroadcastReceiver br = new BroadcastReceiver() {
			@Override
			public void onReceive(final Context context, final Intent intent) {
				final String action = intent.getAction();
				if (ACTION_RELOAD.equals(action)) {
					if (adapter != null) adapter.reload();
				} else if (ACTION_UPDATE_REFRESHING_STATUS.equals(action)) {
					final boolean refreshing = intent.getBooleanExtra(EXTRA_refreshing, false);
					if (swiper != null) {
						swiper.setRefreshing(refreshing);
					}
				}
			}
		};

		@Override
		public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			App.getLbm().registerReceiver(br, new IntentFilter(ACTION_RELOAD));
			App.getLbm().registerReceiver(br, new IntentFilter(ACTION_UPDATE_REFRESHING_STATUS));

			downloadedOnly = getArguments().getBoolean(ARG_DOWNLOADED_ONLY);
			query_text = getArguments().getString(ARG_INITIAL_QUERY_TEXT);
		}

		@Override
		public void onDestroy() {
			super.onDestroy();

			App.getLbm().unregisterReceiver(br);
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			this.inflater = inflater;
			final View rootView = inflater.inflate(downloadedOnly? R.layout.fragment_versions_downloaded: R.layout.fragment_versions_all, container, false);

			adapter = new VersionAdapter();

			lsVersions = V.get(rootView, R.id.lsVersions);
			lsVersions.setAdapter(adapter);
			if (downloadedOnly) {
				final VersionOrderingController c = new VersionOrderingController(lsVersions);
				lsVersions.setFloatViewManager(c);
				lsVersions.setOnTouchListener(c);
			}

			swiper = V.get(rootView, R.id.swiper);
			if (swiper != null) { // Can be null, if the layout used is fragment_versions_downloaded.
				swiper.setColorSchemeColors(getResources().getColor(R.color.accent), 0xffcbcbcb, getResources().getColor(R.color.accent), 0xffcbcbcb);
				swiper.setOnRefreshListener(swiper_refresh);
			}

			return rootView;
		}

		final SwipeRefreshLayout.OnRefreshListener swiper_refresh = () -> VersionConfigUpdaterService.checkUpdate(false);

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

		@Override
		public void setQueryText(final String query_text) {
			this.query_text = query_text;
			adapter.reload(); // do not broadcast, since the query only changes this fragment
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

			final AlertDialogWrapper.Builder b = new AlertDialogWrapper.Builder(getActivity());

			int button_count = 0;

			// can we update?
			if (hasUpdateAvailable(mv)) {
				button_count++;
				//noinspection ConstantConditions
				b.setPositiveButton(R.string.ed_update_button, (dialog, which) -> startDownload(VersionConfig.get().getPreset(((MVersionDb) mv).preset_name)));

				details.append("\n");
				final int details_len = details.length();
				details.append("  ");
				details.setSpan(new ImageSpan(App.context, R.drawable.ic_version_update, DynamicDrawableSpan.ALIGN_BASELINE), details_len, details_len + 1, 0);
				details.append(getString(R.string.ed_details_update_available));
			}

			// can we share?
			if (mv instanceof MVersionDb && mv.hasDataFile()) {
				button_count++;
				b.setNegativeButton(R.string.version_menu_share, (dialog, which) -> {
					final MVersionDb mvDb = (MVersionDb) mv;

					final Intent intent = ShareCompat.IntentBuilder.from(getActivity())
						.setType("application/octet-stream")
						.addStream(Uri.fromFile(new File(mvDb.filename)))
						.getIntent();

					startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.version_share_title)), REQCODE_share);
				});
			}

			// can we delete?
			if (mv instanceof MVersionDb) {
				button_count++;
				b.setNeutralButton(R.string.buang_dari_daftar, (dialog, which) -> {
					final MVersionDb mvDb = (MVersionDb) mv;
					new AlertDialogWrapper.Builder(getActivity())
						.setMessage(getString(R.string.juga_hapus_file_datanya_file, mvDb.filename))
						.setPositiveButton(R.string.delete, (dialog1, which1) -> {
							S.getDb().deleteVersion(mvDb);
							App.getLbm().sendBroadcast(new Intent(ACTION_RELOAD));
							new File(mvDb.filename).delete();
						})
						.setNegativeButton(R.string.no, (dialog1, which1) -> {
							S.getDb().deleteVersion(mvDb);
							App.getLbm().sendBroadcast(new Intent(ACTION_RELOAD));
						})
						.setNeutralButton(R.string.cancel, null)
						.show();
				});
			}

			// can we download?
			if (mv instanceof MVersionPreset) {
				button_count++;
				b.setPositiveButton(R.string.ed_download_button, (dialog, which) -> startDownload((MVersionPreset) mv));
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
			{
				int enabled = -1;
				try {
					enabled = App.context.getPackageManager().getApplicationEnabledSetting("com.android.providers.downloads");
				} catch (Exception e) {
					Log.d(TAG, "getting app enabled setting", e);
				}

				if (enabled == -1
					|| enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
					|| enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
					new MaterialDialog.Builder(getActivity())
						.content(R.string.ed_download_manager_not_enabled_prompt)
						.positiveText(R.string.ok)
						.negativeText(R.string.cancel)
						.callback(new MaterialDialog.ButtonCallback() {
							@Override
							public void onPositive(final MaterialDialog dialog) {
								try {
									startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.android.providers.downloads")));
								} catch (ActivityNotFoundException e) {
									Log.e(TAG, "opening apps setting", e);
								}
							}
						})
						.show();

					return;
				}
			}

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
			attrs.put("download_type", "preset");
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
					new AlertDialogWrapper.Builder(getActivity())
						.setMessage(getString(R.string.the_file_for_this_version_is_no_longer_available_file, mv.filename))
						.setPositiveButton(R.string.delete, (dialog, which) -> {
							S.getDb().deleteVersion(mv);
							App.getLbm().sendBroadcast(new Intent(ACTION_RELOAD));
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

		public class VersionAdapter extends EasyAdapter implements DragSortListView.DropListener {
			final List<Item> items = new ArrayList<>();

			VersionAdapter() {
				reload();
			}

			/**
			 * The list of versions are loaded as follows:
			 * - Internal version {@link yuku.alkitab.base.model.MVersionInternal}, is always there
			 * - Versions stored in database {@link yuku.alkitab.base.model.MVersionDb} is all loaded
			 * - For each non-hidden {@link yuku.alkitab.base.model.MVersionPreset} defined in {@link yuku.alkitab.base.config.VersionConfig},
			 *   check if the {@link yuku.alkitab.base.model.MVersionPreset#preset_name} corresponds to one of the
			 *   database version above. If it does, do not add to the resulting list. Otherwise, add it so user can download it.
			 *
			 * Note: Downloaded preset version will become database version after added.
			 */
			void reload() {
				items.clear();

				{ // internal
					items.add(new Item(S.getMVersionInternal()));
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
					final boolean showHidden = Preferences.getBoolean(getString(R.string.pref_showHiddenVersion_key), false);
					for (MVersionPreset preset : VersionConfig.get().presets) {
						if (!showHidden && preset.hidden) continue;
						if (presetNamesInDb.contains(preset.preset_name)) continue;

						items.add(new Item(preset));
					}
				}

				if (!TextUtils.isEmpty(query_text)) { // filter items based on query_text
					final Matcher[] matchers = QueryTokenizer.matcherizeTokens(QueryTokenizer.tokenize(query_text));
					for (int i = items.size() - 1; i >= 0; i--) {
						final Item item = items.get(i);
						if (!matchMatchers(item.mv, matchers)) {
							items.remove(i);
						}
					}
				}

				// Sort items. For "all" tab, sort is based on display language. For "downloaded" tab, sort is based on ordering.
				if (!downloadedOnly) {
					Collections.sort(items, (a, b) -> {
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
					});
				} else {
					Collections.sort(items, (a, b) -> a.mv.ordering - b.mv.ordering);

					if (BuildConfig.DEBUG) {
						Log.d(TAG, "ordering   type                   versionId");
						Log.d(TAG, "========   ===================    =================");
						for (final Item item : items) {
							Log.d(TAG, String.format("%8d   %-20s   %s", item.mv.ordering, item.mv.getClass().getSimpleName(), item.mv.getVersionId()));
						}
					}
				}

				// mark first item in each group
				String lastLocale = "<sentinel>";
				for (Item item : items) {
					item.firstInGroup = !U.equals(item.mv.locale, lastLocale);
					lastLocale = item.mv.locale;
				}

				notifyDataSetChanged();
			}

			private boolean matchMatchers(final MVersion mv, final Matcher[] matchers) {
				// have to match all tokens
				for (final Matcher m : matchers) {
					if (m.reset(mv.longName).find()) {
						continue;
					}

					if (mv.shortName != null && m.reset(mv.shortName).find()) {
						continue;
					}

					if (mv.description != null && m.reset(mv.description).find()) {
						continue;
					}

					if (mv.locale != null && m.reset(getDisplayLanguage(mv.locale)).find()) {
						continue;
					}

					return false;
				}
				return true;
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
				final View drag_handle = V.get(view, R.id.drag_handle);

				final Item item = getItem(position);
				final MVersion mv = item.mv;

				bLongName.setOnClickListener(v -> itemNameClick(item));

				panelRight.setOnClickListener(v -> itemCheckboxClick(item, view));

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

				if (downloadedOnly) {
					drag_handle.setVisibility(View.VISIBLE);
				} else {
					drag_handle.setVisibility(View.GONE);
				}
			}

			@Override
			public void drop(final int from, final int to) {
				if (from == to) return;

				final Item fromItem = getItem(from);
				final Item toItem = getItem(to);

				S.getDb().reorderVersions(fromItem.mv, toItem.mv);
				App.getLbm().sendBroadcast(new Intent(ACTION_RELOAD));
			}
		}

		private boolean hasUpdateAvailable(final MVersion mv) {
			if (!(mv instanceof MVersionDb)) {
				return false;
			}

			final MVersionDb mvDb = (MVersionDb) mv;
			if (mvDb.preset_name == null || mvDb.modifyTime == 0) {
				return false;
			}

			final int available = VersionConfig.get().getModifyTime(mvDb.preset_name);
			return !(available == 0 || available <= mvDb.modifyTime);
		}

		private class VersionOrderingController extends DragSortController {
			final DragSortListView lv;
			final int dragHandleId;

			public VersionOrderingController(DragSortListView lv) {
				super(lv, R.id.drag_handle, DragSortController.ON_DOWN, 0);
				this.dragHandleId = R.id.drag_handle;
				this.lv = lv;

				setRemoveEnabled(false);
			}

			@Override public int startDragPosition(MotionEvent ev) {
				return super.dragHandleHitPosition(ev);
			}

			@Override public View onCreateFloatView(int position) {
				final View res = adapter.getView(position, null, lv);
				res.setBackgroundColor(0x22ffffff);
				return res;
			}

			@Override public void onDestroyFloatView(View floatView) {
				// Do not call super and do not remove this override.
				floatView.setBackgroundColor(0);
			}
		}
	}
}

interface QueryTextReceiver {
	void setQueryText(String query_text);
}

