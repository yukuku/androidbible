package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.BookmarkImporter;
import yuku.alkitab.debug.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YukuAlkitabImportOfferActivity extends BaseActivity {

	TextView tImportDesc;
	Button bCancel;
	Button bNoMore;
	ListView lsBackupFiles;
	BackupFilesAdapter adapter;

	public static Intent createIntent() {
		return new Intent(App.context, YukuAlkitabImportOfferActivity.class);
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreateWithNonToolbarUpButton(savedInstanceState);
		setContentView(R.layout.activity_yuku_alkitab_import_offer);

		tImportDesc = V.get(this, R.id.tImportDesc);
		bCancel = V.get(this, R.id.bCancel);
		bNoMore = V.get(this, R.id.bNoMore);
		lsBackupFiles = V.get(this, R.id.lsBackupFiles);

		lsBackupFiles.setAdapter(adapter = new BackupFilesAdapter());
		lsBackupFiles.setOnItemClickListener((parent, view, position, id) -> {
			final FileInputStream fis;
			try {
				fis = new FileInputStream(adapter.getFile(position));
				BookmarkImporter.importBookmarks(YukuAlkitabImportOfferActivity.this, fis, true, null);
				Preferences.setInt(Prefkey.stop_import_yuku_alkitab_backups, 2);
			} catch (FileNotFoundException e) {
				new AlertDialog.Builder(YukuAlkitabImportOfferActivity.this)
					.setMessage(R.string.marker_migrate_error_opening_backup_file)
					.setPositiveButton(R.string.ok, null)
					.show();
			}
		});

		bCancel.setOnClickListener(v -> finish());

		bNoMore.setOnClickListener(v -> new AlertDialog.Builder(this)
			.setMessage(R.string.marker_migrate_no_more_confirmation)
			.setPositiveButton(R.string.ok, (dialog, which) -> {
				Preferences.setInt(Prefkey.stop_import_yuku_alkitab_backups, 1);
				finish();
			})
			.setNegativeButton(R.string.cancel, null)
			.show());
	}

	class BackupFilesAdapter extends EasyAdapter {
		final List<File> files = new ArrayList<>();
		final java.text.DateFormat sdf = android.text.format.DateFormat.getDateFormat(YukuAlkitabImportOfferActivity.this);

		BackupFilesAdapter() {
			final File dir = new File(Environment.getExternalStorageDirectory(), "bible");
			if (!dir.exists()) return;

			final File[] files = dir.listFiles(new FilenameFilter() {
				final Matcher m = Pattern.compile("yuku.alkitab(\\.kjv)-(backup|autobackup-[0-9-]+)\\.xml").matcher("");

				@Override
				public boolean accept(final File dir, final String filename) {
					m.reset(filename);
					return m.matches();
				}
			});

			if (files == null || files.length == 0) {
				return;
			}

			// sort from newest to oldest
			Arrays.sort(files, (lhs, rhs) -> {
				final long cmp = rhs.lastModified() - lhs.lastModified();
				if (cmp > 0) return 1;
				if (cmp < 0) return -1;
				return 0;
			});

			Collections.addAll(this.files, files);
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(R.layout.item_yuku_alkitab_import_offer, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			final TextView text1 = V.get(view, android.R.id.text1);
			final TextView text2 = V.get(view, android.R.id.text2);

			final File file = files.get(position);

			final TypedValue tv = new TypedValue();
			getTheme().resolveAttribute(android.R.attr.textAppearanceMedium, tv, true);
			text1.setTextAppearance(text1.getContext(), tv.data);

			if (position == 0) {
				text1.setText(R.string.marker_migrate_import_from_newest);
			} else {
				text1.setText(R.string.marker_migrate_import_from_other);
			}
			text2.setText(getString(R.string.marker_migrate_filename_dated, file.getName(), sdf.format(new Date(file.lastModified()))));
		}

		@Override
		public int getCount() {
			return files.size();
		}

		File getFile(int position) {
			return files.get(position);
		}
	}

	public static Matcher getBackupFilenameMatcher() {
		return Pattern.compile("yuku.alkitab(\\.kjv)-(backup|autobackup-[0-9-]+)\\.xml").matcher("");
	}
}
