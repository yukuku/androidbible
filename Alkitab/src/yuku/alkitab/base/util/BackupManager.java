package yuku.alkitab.base.util;

import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Xml;
import gnu.trove.list.TLongList;
import gnu.trove.map.hash.TLongIntHashMap;
import org.xmlpull.v1.XmlSerializer;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.BookmarkActivity;
import yuku.alkitab.base.model.Bookmark2;
import yuku.alkitab.base.model.Label;
import yuku.alkitab.base.storage.Prefkey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BackupManager {
	static final int AUTOBACKUP_AFTER_DAYS = 3;
	static final String autobackupBaseName = App.context.getPackageName() + "-autobackup";

	public interface BackupListener {
		public void onBackupPreExecute();

		public void onBackupPostExecute(Object result);
	}


	public static void backupBookmarks(final boolean autoBackup, final BackupListener backupListener) {
		new AsyncTask<Void, Integer, Object>() {

			@Override
			protected void onPreExecute() {
				if (backupListener != null) {
					backupListener.onBackupPreExecute();
				}
			}

			@Override
			protected Object doInBackground(Void... params) {
				File out = getFileBackup(autoBackup);
				try {
					FileOutputStream fos = new FileOutputStream(out);

					XmlSerializer xml = Xml.newSerializer();
					xml.setOutput(fos, "utf-8"); //$NON-NLS-1$
					xml.startDocument("utf-8", null); //$NON-NLS-1$
					xml.startTag(null, "backup"); //$NON-NLS-1$

					List<Bookmark2> bookmarks = new ArrayList<Bookmark2>();
					{ // write bookmarks
						Cursor cursor = S.getDb().listAllBookmarks();
						try {
							while (cursor.moveToNext()) {
								Bookmark2 bookmark = Bookmark2.fromCursor(cursor);
								bookmarks.add(bookmark); // register bookmark
								bookmark.writeXml(xml, bookmarks.size() /* 1-based relId */);
							}
						} finally {
							cursor.close();
						}
					}

					TLongIntHashMap labelAbsIdToRelIdMap = new TLongIntHashMap();
					List<Label> labels = S.getDb().listAllLabels();
					{ // write labels
						for (int i = 0; i < labels.size(); i++) {
							Label label = labels.get(i);
							label.writeXml(xml, i + 1 /* 1-based relId */);
							labelAbsIdToRelIdMap.put(label._id, i + 1 /* 1-based relId */);
						}
					}

					{ // write mapping from bookmark to label
						for (int bookmark2_relId_0 = 0; bookmark2_relId_0 < bookmarks.size(); bookmark2_relId_0++) {
							Bookmark2 bookmark = bookmarks.get(bookmark2_relId_0);
							TLongList labelIds = S.getDb().listLabelIdsByBookmarkId(bookmark._id);
							if (labelIds != null && labelIds.size() > 0) {
								for (int i = 0; i < labelIds.size(); i++) {
									long labelId = labelIds.get(i);

									// we now need 2 relids, bookmark relid and label relid
									int bookmark2_relId = bookmark2_relId_0 + 1; // 1-based
									int label_relId = labelAbsIdToRelIdMap.get(labelId);

									if (label_relId != labelAbsIdToRelIdMap.getNoEntryValue()) { // just in case
										writeBookmark2_LabelXml(xml, bookmark2_relId, label_relId);
									}
								}
							}
						}
					}

					xml.endTag(null, "backup"); //$NON-NLS-1$
					xml.endDocument();
					fos.close();

					return out.getAbsolutePath();
				} catch (Exception e) {
					return e;
				}
			}

			@Override protected void onPostExecute(Object result) {
				if (!(result instanceof Exception)) {
					Preferences.setLong(Prefkey.lastBackupDate, System.currentTimeMillis());
				}
				if (backupListener != null) {
					backupListener.onBackupPostExecute(result);
				}
			}

			void writeBookmark2_LabelXml(XmlSerializer xml, int bookmark2_relId, int label_relId) throws IOException {
				xml.startTag(null, BookmarkActivity.Bookmark2_Label.XMLTAG_Bookmark2_Label);
				xml.attribute(null, BookmarkActivity.Bookmark2_Label.XMLATTR_bookmark2_relId, String.valueOf(bookmark2_relId));
				xml.attribute(null, BookmarkActivity.Bookmark2_Label.XMLATTR_label_relId, String.valueOf(label_relId));
				xml.endTag(null, BookmarkActivity.Bookmark2_Label.XMLTAG_Bookmark2_Label);
			}

		}.execute();
	}

	public static void startAutoBackup() {
		long backupDate = Preferences.getLong(Prefkey.lastBackupDate, 0L);
		long elapsedDays = (System.currentTimeMillis() - backupDate) / (1000 * 60 * 60 * 24);
		if (elapsedDays >= AUTOBACKUP_AFTER_DAYS) {
			backupBookmarks(true, null);
		}
	}

	public static File getFileBackup(boolean autoBackup) {
		File dir = getFileDirectory();
		if (!dir.exists()) {
			dir.mkdir();
		}
		if (autoBackup) {
			List<File> backupFiles = listBackupFiles();
			if (backupFiles.size() == 0) {
				int count = 0;
				File oldestFile = null;
				for (File file : backupFiles) {
					if (file.getName().startsWith(autobackupBaseName)) {
						count++;
						if (oldestFile == null || file.lastModified() < oldestFile.lastModified()) {
							oldestFile = file;
						}
					}
				}
				if (count > 9 && oldestFile != null) {
					oldestFile.delete();
				}
			}
			String time = new SimpleDateFormat("yyyyMMdd-hhmmss", Locale.US).format(new Date());
			return new File(dir, autobackupBaseName + "-" + time + ".xml");
		} else {
			return new File(dir, App.context.getPackageName() + "-backup.xml");
		}
	}

	public static List<File> listBackupFiles() {
		File dir = getFileDirectory();
		List<File> backupFiles = new ArrayList<File>();
		File manualBackupFile = new File(dir, App.context.getPackageName() + "-backup.xml");
		if (manualBackupFile.exists()) {
			backupFiles.add(manualBackupFile);
		}

		final File[] files = dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(final File dir, final String filename) {
				return filename.startsWith(autobackupBaseName) && filename.endsWith(".xml");
			}
		});
		if (files != null) {
			Collections.addAll(backupFiles, files);
			Collections.sort(backupFiles, Collections.reverseOrder());
		}
		return backupFiles;
	}

	public static File getFileDirectory() {
		return new File(Environment.getExternalStorageDirectory(), "bible");
	}

}