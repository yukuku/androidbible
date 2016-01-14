package yuku.alkitab.base.br;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.support.v4.util.AtomicFile;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.AlertDialogActivity;
import yuku.alkitab.base.ac.VersionsActivity;
import yuku.alkitab.base.model.MVersionDb;
import yuku.alkitab.base.storage.YesReaderFactory;
import yuku.alkitab.base.util.AddonManager;
import yuku.alkitab.base.util.DownloadMapper;
import yuku.alkitab.debug.R;
import yuku.alkitab.io.BibleReader;
import yuku.alkitab.io.OptionalGzipInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

public class VersionDownloadCompleteReceiver extends BroadcastReceiver {
	private static final String TAG = VersionDownloadCompleteReceiver.class.getSimpleName();

	public VersionDownloadCompleteReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
		if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;

		final long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
		if (id == -1) return;

		final int status = DownloadMapper.instance.getStatus(id);
		if (status != DownloadManager.STATUS_SUCCESSFUL) {
			Log.w(TAG, "ACTION_DOWNLOAD_COMPLETE reported for " + id + " but actual status is " + status);
			return; // lost download
		}

		final Map<String, String> attrs = DownloadMapper.instance.getAttrs(id);
		if (attrs == null) {
			Log.w(TAG, "No download attrs");
			return;
		}

		if (!attrs.containsKey("download_type")) {
			Log.w(TAG, "download_type attr not found for " + id);
			return;
		}

		final String download_type = attrs.get("download_type");

		String preset_name = null;
		int modifyTime = (int) (System.currentTimeMillis() / 1000L);

		final String destPath;
		if ("preset".equals(download_type)) {
			if (!attrs.containsKey("preset_name")) {
				Log.w(TAG, "preset_name attr not found for " + id);
				return;
			}

			preset_name = attrs.get("preset_name");

			if (!attrs.containsKey("modifyTime")) {
				Log.w(TAG, "modifyTime attr not found for " + id);
				return;
			}

			modifyTime = Integer.parseInt(attrs.get("modifyTime"));
			AddonManager.mkYesDir(); // ensure that the directories exist first.
			destPath = AddonManager.getVersionPath(preset_name + ".yes");
		} else if ("url".equals(download_type)) {
			if (!attrs.containsKey("filename_last_segment")) {
				Log.w(TAG, "filename_last_segment attr not found for " + id);
				return;
			}

			final String filename_last_segment = attrs.get("filename_last_segment");
			destPath = AddonManager.getVersionPath(filename_last_segment);
		} else {
			Log.w(TAG, "unknown download_type for " + id + ": " + download_type);
			return;
		}

		// transfer from dm to the actual file
		final DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

		try {
			final ParcelFileDescriptor pfd = dm.openDownloadedFile(id);
			final FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
			final OptionalGzipInputStream ogis = new OptionalGzipInputStream(fis);
			final AtomicFile af = new AtomicFile(new File(destPath));
			final FileOutputStream fos = af.startWrite();
			final byte[] buf = new byte[4096];
			while (true) {
				final int read = ogis.read(buf);
				if (read < 0) break;
				fos.write(buf, 0, read);
			}
			af.finishWrite(fos);
			pfd.close();
		} catch (IOException e) {
			Log.e(TAG, "I/O error when saving downloaded version", e);
			context.startActivity(
				AlertDialogActivity.createOkIntent(null, context.getString(R.string.version_download_saving_io_error))
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			);
			App.getLbm().sendBroadcast(new Intent(VersionsActivity.VersionListFragment.ACTION_RELOAD));
			return;
		} finally {
			DownloadMapper.instance.remove(id);
		}

		final BibleReader reader = YesReaderFactory.createYesReader(destPath);
		if (reader == null) {
			new File(destPath).delete();

			context.startActivity(
				AlertDialogActivity.createOkIntent(null, context.getString(R.string.version_download_corrupted_file))
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			);
			App.getLbm().sendBroadcast(new Intent(VersionsActivity.VersionListFragment.ACTION_RELOAD));
			return;
		}

		// success!

		int maxOrdering = S.getDb().getVersionMaxOrdering();
		if (maxOrdering == 0) maxOrdering = MVersionDb.DEFAULT_ORDERING_START;

		final MVersionDb mvDb = new MVersionDb();
		mvDb.locale = reader.getLocale();
		mvDb.shortName = reader.getShortName();
		mvDb.longName = reader.getLongName();
		mvDb.description = reader.getDescription();
		mvDb.filename = destPath;
		if ("preset".equals(download_type)) {
			mvDb.preset_name = preset_name;
		}
		mvDb.modifyTime = modifyTime;
		mvDb.ordering = maxOrdering + 1;

		S.getDb().insertOrUpdateVersionWithActive(mvDb, true);
		MVersionDb.clearVersionImplCache();

		Toast.makeText(App.context, TextUtils.expandTemplate(context.getText(R.string.version_download_complete), mvDb.longName), Toast.LENGTH_LONG).show();

		if ("ta".equals(mvDb.locale) || "te".equals(mvDb.locale) || "my".equals(mvDb.locale) || "el".equals(mvDb.locale)) {
			context.startActivity(
				AlertDialogActivity.createOkIntent(null, context.getString(R.string.version_download_need_fonts))
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			);
		}

		App.getLbm().sendBroadcast(new Intent(VersionsActivity.VersionListFragment.ACTION_RELOAD));
	}
}
