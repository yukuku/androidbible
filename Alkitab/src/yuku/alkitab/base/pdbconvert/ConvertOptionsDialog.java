package yuku.alkitab.base.pdbconvert;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import yuku.afw.App;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;

import com.compactbyte.android.bible.PDBFileStream;
import com.compactbyte.bibleplus.reader.BiblePlusPDB;
import com.compactbyte.bibleplus.reader.BookInfo;

public class ConvertOptionsDialog {
	public static final String TAG = ConvertOptionsDialog.class.getSimpleName();
	
	Context context;
	AlertDialog alert;
	ConvertOptionsCallback callback;
	
	Spinner cbEncoding;
	TextView lSample;
	CheckBox cAddlTitle;
	
	BiblePlusPDB pdb;
	BookInfo bookInfo;

	ArrayAdapter<String> encodingAdapter;

	public interface ConvertOptionsCallback {
		void onOkYes1(ConvertPdbToYes1.ConvertParams params);
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
				return App.context.getString(R.string.pdb_error_not_palmbible, type, creator); 
			} else if (reason == BiblePlusPDB.ERR_FILE_CORRUPTED) {
				return App.context.getString(R.string.pdb_error_corrupted);
			} else if (reason == BiblePlusPDB.ERR_NOT_PDB_FILE) {
				return App.context.getString(R.string.pdb_error_not_pdb_file);
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
			if (versionInfoOk == false) {
				throw new PdbKnownErrorException(pdb);
			}
			pdb.loadWordIndex();
			pdb.getBookCount();
			
			bookInfo = pdb.getBook(0);
			bookInfo.openBook();
			bookInfo.getVerse(1, 1);
		} catch (Throwable e) {
			callback.onPdbReadError(e);
			if (pdb != null) pdb.close();
			return;
		}

		View dialogLayout = LayoutInflater.from(context).inflate(R.layout.dialog_pdbconvert_options, null);
		
		this.alert = new AlertDialog.Builder(context)
		.setView(dialogLayout)
		.setTitle(R.string.pdb_file_options)
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				bOk_click();
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.create();

		cbEncoding = (Spinner) dialogLayout.findViewById(R.id.cbEncoding);
		lSample = (TextView) dialogLayout.findViewById(R.id.lSample);
		cAddlTitle = (CheckBox) dialogLayout.findViewById(R.id.cAddlTitle);
		
		String tabEncoding = null;
		if (pdb.isGreek()) {
			tabEncoding = context.getString(R.string.greek_charset);
		} else if (pdb.isHebrew()) {
			tabEncoding = context.getString(R.string.hebrew_charset);
		}
		
		List<String> charsets = new ArrayList<String>();
		if (tabEncoding == null) {
			for (Map.Entry<String, Charset> charset: Charset.availableCharsets().entrySet()) {
				String key = charset.getKey();
				Log.d(TAG, "available charset: " + key); //$NON-NLS-1$
				charsets.add(key);
			}
			
			Collections.sort(charsets, new Comparator<String>() {
				@Override public int compare(String a, String b) {
					int va = 0;
					int vb = 0;
					if (a.equalsIgnoreCase("utf-8")) va = -2; //$NON-NLS-1$
					if (a.equalsIgnoreCase("iso-8859-1")) va = -1; //$NON-NLS-1$
					if (b.equalsIgnoreCase("utf-8")) vb = -2; //$NON-NLS-1$
					if (b.equalsIgnoreCase("iso-8859-1")) vb = -1; //$NON-NLS-1$
					
					if (va == 0 && vb == 0) {
						return a.compareToIgnoreCase(b);
					} else {
						return va - vb;
					}
				}
			});
		} else {
			charsets.add(tabEncoding);
			cbEncoding.setEnabled(false);
		}
		
		encodingAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, charsets);
		encodingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		cbEncoding.setAdapter(encodingAdapter);
		
		showSample("utf-8"); // default! if greek or hebrew, this won't be cared! //$NON-NLS-1$
		
		cbEncoding.setOnItemSelectedListener(cbEncoding_itemSelected);
	}

	void showSample(String encoding) {
		pdb.setEncoding(encoding); 
		
		String bookName = bookInfo.getFullName();
		String verse = bookInfo.getVerse(1, 1);
		if (verse.length() > 90) {
			verse = verse.substring(0, 88) + "..."; //$NON-NLS-1$
		}
		lSample.setText(bookName + " 1:1  " + verse); //$NON-NLS-1$
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
		pdb.close();
		
		if (Preferences.getBoolean("outputYesVersion2", false) == false) {
			ConvertPdbToYes1.ConvertParams params = new ConvertPdbToYes1.ConvertParams();
			params.inputEncoding = encodingAdapter.getItem(cbEncoding.getSelectedItemPosition());
			params.includeAddlTitle = cAddlTitle.isChecked();
			callback.onOkYes1(params);
		} else {
			ConvertPdbToYes2.ConvertParams params = new ConvertPdbToYes2.ConvertParams();
			params.inputEncoding = encodingAdapter.getItem(cbEncoding.getSelectedItemPosition());
			params.includeAddlTitle = cAddlTitle.isChecked();
			callback.onOkYes2(params);
		}
	}
}
