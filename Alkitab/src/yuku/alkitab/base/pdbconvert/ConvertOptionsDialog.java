package yuku.alkitab.base.pdbconvert;

import android.app.*;
import android.content.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;

import java.nio.charset.*;
import java.util.*;

import yuku.alkitab.base.pdbconvert.ConvertPdbToYes.ConvertParams;

import com.compactbyte.android.bible.*;
import com.compactbyte.bibleplus.reader.*;

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
		void onOk(ConvertParams params);
		void onPdbReadError(Exception e);
	}
	
	public ConvertOptionsDialog(Context context, String filenamepdb, ConvertOptionsCallback callback) {
		this.context = context;
		this.callback = callback;
		
		try {
			pdb = new BiblePlusPDB(new PDBFileStream(filenamepdb), Tabs.hebrewTab, Tabs.greekTab);
			pdb.loadVersionInfo();
			pdb.loadWordIndex();
			pdb.getBookCount();
			
			bookInfo = pdb.getBook(0);
			bookInfo.openBook();
			bookInfo.getVerse(1, 1);
		} catch (Exception e) {
			callback.onPdbReadError(e);
			if (pdb != null) pdb.close();
			return;
		}

		View dialogLayout = LayoutInflater.from(context).inflate(R.layout.dialog_pdbconvert_options, null);
		
		this.alert = new AlertDialog.Builder(context)
		.setView(dialogLayout)
		.setTitle("PDB file options")
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
		
		// FIXME if hebrew/greek, disable aja.
		List<String> charsets = new ArrayList<String>();
		{
			for (Map.Entry<String, Charset> charset: Charset.availableCharsets().entrySet()) {
				String key = charset.getKey();
				Log.d(TAG, "available charset: " + key);
				charsets.add(key);
			}
			
			Collections.sort(charsets, new Comparator<String>() {
				@Override public int compare(String a, String b) {
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
				}
			});
		}
		
		encodingAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, charsets);
		encodingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		cbEncoding.setAdapter(encodingAdapter);
		
		showSample("utf-8"); // default!
		
		cbEncoding.setOnItemSelectedListener(cbEncoding_itemSelected);
	}

	private void showSample(String encoding) {
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
		pdb.close();
		ConvertParams params = new ConvertParams();
		params.inputEncoding = encodingAdapter.getItem(cbEncoding.getSelectedItemPosition());
		params.includeAddlTitle = cAddlTitle.isChecked();
		
		callback.onOk(params);
	}
}
