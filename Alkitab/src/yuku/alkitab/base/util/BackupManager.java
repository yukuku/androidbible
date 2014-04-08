package yuku.alkitab.base.util;

import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Xml;
import gnu.trove.list.TLongList;
import gnu.trove.map.hash.TLongIntHashMap;
import org.xml.sax.Attributes;
import org.xmlpull.v1.XmlSerializer;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.BookmarkActivity;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.storage.InternalDb;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.model.Bookmark2;
import yuku.alkitab.model.Label;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
								Bookmark2 bookmark = InternalDb.bookmark2FromCursor(cursor);
								bookmarks.add(bookmark); // register bookmark
								writeXmlForBookmark2(bookmark, xml, bookmarks.size() /* 1-based relId */);
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
							writeXmlForLabel(label, xml, i + 1 /* 1-based relId */);
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

	// conversions from Bookmark2 to tags
	public static final String XMLTAG_Bukmak2 = "Bukmak2"; //$NON-NLS-1$
	private static final String XMLATTR_ari = "ari"; //$NON-NLS-1$
	private static final String XMLATTR_kind = "jenis"; //$NON-NLS-1$
	private static final String XMLATTR_caption = "tulisan"; //$NON-NLS-1$
	private static final String XMLATTR_addTime = "waktuTambah"; //$NON-NLS-1$
	private static final String XMLATTR_modifyTime = "waktuUbah"; //$NON-NLS-1$
	private static final String XMLATTR_relId = "relId"; //$NON-NLS-1$
	private static final String XMLVAL_bookmark = "bukmak"; //$NON-NLS-1$
	private static final String XMLVAL_note = "catatan"; //$NON-NLS-1$
	private static final String XMLVAL_highlight = "stabilo"; //$NON-NLS-1$
	public static final String XMLTAG_Label = "Label"; //$NON-NLS-1$
	private static final String XMLATTR_title = "judul"; //$NON-NLS-1$
	private static final String XMLATTR_bgColor = "warnaLatar"; //$NON-NLS-1$


	private static void writeXmlForBookmark2(Bookmark2 bookmark2, XmlSerializer xml, int relId) throws IOException {
		xml.startTag(null, XMLTAG_Bukmak2);
		xml.attribute(null, XMLATTR_relId, String.valueOf(relId));
		xml.attribute(null, XMLATTR_ari, String.valueOf(bookmark2.ari));
		xml.attribute(null, XMLATTR_kind, bookmark2.kind == Db.Bookmark2.kind_bookmark? XMLVAL_bookmark: bookmark2.kind == Db.Bookmark2.kind_note? XMLVAL_note: bookmark2.kind == Db.Bookmark2.kind_highlight? XMLVAL_highlight: String.valueOf(bookmark2.kind));
		if (bookmark2.caption != null) {
			xml.attribute(null, XMLATTR_caption, escapeHighUnicode(bookmark2.caption));
		}
		if (bookmark2.addTime != null) {
			xml.attribute(null, XMLATTR_addTime, String.valueOf(Sqlitil.toInt(bookmark2.addTime)));
		}
		if (bookmark2.modifyTime != null) {
			xml.attribute(null, XMLATTR_modifyTime, String.valueOf(Sqlitil.toInt(bookmark2.modifyTime)));
		}
		xml.endTag(null, XMLTAG_Bukmak2);
	}

	public static Bookmark2 bookmark2FromAttributes(Attributes attributes) {
		int ari = Integer.parseInt(attributes.getValue("", XMLATTR_ari)); //$NON-NLS-1$
		String kind_s = attributes.getValue("", XMLATTR_kind); //$NON-NLS-1$
		int kind = kind_s.equals(XMLVAL_bookmark)? Db.Bookmark2.kind_bookmark: kind_s.equals(XMLVAL_note)? Db.Bookmark2.kind_note: kind_s.equals(XMLVAL_highlight)? Db.Bookmark2.kind_highlight: Integer.parseInt(kind_s);
		String caption = unescapeHighUnicode(attributes.getValue("", XMLATTR_caption)); //$NON-NLS-1$
		Date addTime = Sqlitil.toDate(Integer.parseInt(attributes.getValue("", XMLATTR_addTime))); //$NON-NLS-1$
		Date modifyTime = Sqlitil.toDate(Integer.parseInt(attributes.getValue("", XMLATTR_modifyTime))); //$NON-NLS-1$

		return new Bookmark2(ari, kind, caption, addTime, modifyTime);
	}

	public static int getRelId(Attributes attributes) {
		String s = attributes.getValue("", XMLATTR_relId); //$NON-NLS-1$
		return s == null? 0: Integer.parseInt(s);
	}


	public static void writeXmlForLabel(Label label, XmlSerializer xml, int relId) throws IOException {
		// ordering is not backed up
		xml.startTag(null, XMLTAG_Label);
		xml.attribute(null, XMLATTR_relId, String.valueOf(relId));
		xml.attribute(null, XMLATTR_title, escapeHighUnicode(label.title));
		if (label.backgroundColor != null) xml.attribute(null, XMLATTR_bgColor, label.backgroundColor);
		xml.endTag(null, XMLTAG_Label);
	}

	public static Label labelFromAttributes(Attributes attributes) {
		String title = unescapeHighUnicode(attributes.getValue("", XMLATTR_title)); //$NON-NLS-1$
		String bgColor = attributes.getValue("", XMLATTR_bgColor); //$NON-NLS-1$

		return new Label(-1, title, 0, bgColor);
	}

	// use [[~Uxxxxxx~]] for escaping where the 6x are hexadecimal digits
	public static String escapeHighUnicode(String input) {
		if (input == null) return null;

		boolean hasHigh = false;
		final int cplen = input.codePointCount(0, input.length());
		for (int i = 0, cpi = 0; i < cplen; i++) {
			final int cp = input.codePointAt(cpi);
			cpi += Character.charCount(cp);
			if (cp > 0xffff) {
				hasHigh = true;
				break;
			}
		}

		if (!hasHigh) return input;

		StringBuilder sb = new StringBuilder();
		for (int i = 0, cpi = 0; i < cplen; i++) {
			final int cp = input.codePointAt(cpi);
			cpi += Character.charCount(cp);
			if (cp > 0xffff) {
				sb.append("[[~U");
				final String s = Integer.toHexString(cp);
				for (int pad = 0; pad < (6-s.length()); pad++) {
					sb.append('0');
				}
				sb.append(s);
				sb.append("~]]");
			} else {
				sb.append((char) cp);
			}
		}

		return sb.toString();
	}

	static ThreadLocal<Matcher> highUnicodeMatcher = new ThreadLocal<Matcher>() {
		@Override
		protected Matcher initialValue() {
			return Pattern.compile("\\[\\[~U([0-9A-Fa-f]{6})~\\]\\]").matcher("");
		}
	};

	public static String unescapeHighUnicode(String input) {
		if (input == null) return null;

		final Matcher m = highUnicodeMatcher.get();

		m.reset(input);

		StringBuffer res = new StringBuffer();
		while (m.find()) {
			String s = m.group(1);
			final int cp = Integer.parseInt(s, 16);
			m.appendReplacement(res, new String(new int[] {cp}, 0, 1));
		}
		m.appendTail(res);

		return res.toString();
	}
}