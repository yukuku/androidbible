package yuku.alkitab.base.pdbconvert;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import com.compactbyte.android.bible.PDBFileStream;
import com.compactbyte.bibleplus.reader.BiblePlusPDB;
import com.compactbyte.bibleplus.reader.BookInfo;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.widget.Localized;
import yuku.alkitab.debug.R;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConvertOptionsDialog {
	static final String TAG = ConvertOptionsDialog.class.getSimpleName();
	
	Context context;
	MaterialDialog alert;
	ConvertOptionsCallback callback;
	
	Spinner cbEncoding;
	TextView lSample;
	CheckBox cAddlTitle;
	
	BiblePlusPDB pdb;
	BookInfo bookInfo;

	ArrayAdapter<String> encodingAdapter;

	public interface ConvertOptionsCallback {
		void onOkYes2(ConvertPdbToYes2.ConvertParams params);
		void onPdbReadError(Throwable e);
	}
	
	public static class PdbKnownErrorException extends Exception {
		public PdbKnownErrorException(BiblePlusPDB pdb) {
			super(constructExceptionString(pdb));
		}

		static String constructExceptionString(BiblePlusPDB pdb) {
			int reason = pdb.getFailReason();
			if (reason == BiblePlusPDB.ERR_NOT_BIBLE_PLUS_FILE) {
				String type = pdb.getHeader().getType();
				String creator = pdb.getHeader().getCreator();
				return Localized.string(R.string.pdb_error_not_palmbible, type, creator);
			} else if (reason == BiblePlusPDB.ERR_FILE_CORRUPTED) {
				return Localized.string(R.string.pdb_error_corrupted);
			} else if (reason == BiblePlusPDB.ERR_NOT_PDB_FILE) {
				return Localized.string(R.string.pdb_error_not_pdb_file);
			}
			return null;
		}
	}
	
	public ConvertOptionsDialog(Context context, String filenamepdb, ConvertOptionsCallback callback) {
		this.context = context;
		this.callback = callback;
		
		try {
			pdb = new BiblePlusPDB(new PDBFileStream(filenamepdb), Tabs.hebrewTab, Tabs.greekTab);
			boolean versionInfoOk = pdb.loadVersionInfo();
			if (!versionInfoOk) {
				throw new PdbKnownErrorException(pdb);
			}
			pdb.loadWordIndex();
			pdb.getBookCount();
			
			bookInfo = pdb.getBook(0);
			bookInfo.openBook();
			bookInfo.getVerse(1, 1);
		} catch (Throwable e) {
			callback.onPdbReadError(e);
			if (pdb != null) {
				try {
					pdb.close();
				} catch (IOException e1) {
					AppLog.e(TAG, "IO exception when closing", e1);
				}
			}
			return;
		}

		this.alert = new MaterialDialog.Builder(context)
			.customView(R.layout.dialog_pdbconvert_options, false)
			.title(R.string.pdb_file_options)
			.positiveButton(R.string.ok)
			.negativeText(R.string.cancel)
			.onPositive((dialog, which) -> bOk_click())
			.build();

		final View dialogLayout = this.alert.getCustomView();

		cbEncoding = dialogLayout.findViewById(R.id.cbEncoding);
		lSample = dialogLayout.findViewById(R.id.lSample);
		cAddlTitle = dialogLayout.findViewById(R.id.cAddlTitle);
		
		String tabEncoding = null;
		if (pdb.isGreek()) {
			tabEncoding = context.getString(R.string.greek_charset);
		} else if (pdb.isHebrew()) {
			tabEncoding = context.getString(R.string.hebrew_charset);
		}
		
		List<String> charsets = new ArrayList<>();
		if (tabEncoding == null) {
			for (Map.Entry<String, Charset> charset: Charset.availableCharsets().entrySet()) {
				String key = charset.getKey();
				AppLog.d(TAG, "available charset: " + key);
				charsets.add(key);
			}
			
			Collections.sort(charsets, (a, b) -> {
				int va = 0;
				int vb = 0;
				if (a.equalsIgnoreCase("utf-8")) va = -2;
				if (a.equalsIgnoreCase("iso-8859-1")) va = -1;
				if (b.equalsIgnoreCase("utf-8")) vb = -2;
				if (b.equalsIgnoreCase("iso-8859-1")) vb = -1;

				if (va == 0 && vb == 0) {
					return a.compareToIgnoreCase(b);
				} else {
					return va - vb;
				}
			});
		} else {
			charsets.add(tabEncoding);
			cbEncoding.setEnabled(false);
		}
		
		encodingAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, charsets);
		encodingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		cbEncoding.setAdapter(encodingAdapter);
		
		showSample("utf-8"); // default! if greek or hebrew, this won't be cared!
		
		cbEncoding.setOnItemSelectedListener(cbEncoding_itemSelected);
	}

	void showSample(String encoding) {
		pdb.setEncoding(encoding);
		
		String bookName = bookInfo.getFullName();
		String verse = bookInfo.getVerse(1, 1);
		if (verse.length() > 90) {
			verse = verse.substring(0, 88) + "...";
		}
		lSample.setText(bookName + " 1:1  " + verse);
	}
	
	private OnItemSelectedListener cbEncoding_itemSelected = new OnItemSelectedListener() {
		@Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
			String encoding = encodingAdapter.getItem(position);
			showSample(encoding);
		}

		@Override public void onNothingSelected(AdapterView<?> parent) {
		}
	};


	public void show() {
		if (alert != null) alert.show();
	}
	
	protected void bOk_click() {
		try {
			pdb.close();
		} catch (IOException e1) {
			AppLog.e(TAG, "IO exception when closing", e1);
		}

		ConvertPdbToYes2.ConvertParams params = new ConvertPdbToYes2.ConvertParams();
		params.inputEncoding = encodingAdapter.getItem(cbEncoding.getSelectedItemPosition());
		params.includeAddlTitle = cAddlTitle.isChecked();
		callback.onOkYes2(params);
	}
}
