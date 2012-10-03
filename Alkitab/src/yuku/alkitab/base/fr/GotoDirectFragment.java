package yuku.alkitab.base.fr;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.fr.base.BaseGotoFragment;
import yuku.alkitab.base.util.Jumper;

public class GotoDirectFragment extends BaseGotoFragment {
	public static final String TAG = GotoDirectFragment.class.getSimpleName();
	
	private static final String EXTRA_verse = "verse"; //$NON-NLS-1$
	private static final String EXTRA_chapter = "chapter"; //$NON-NLS-1$
	private static final String EXTRA_bookId = "bookId"; //$NON-NLS-1$

	TextView lContohLoncat;
	EditText tAlamatLoncat;
	View bOk;

	int bookId;
	int chapter_1;
	int verse_1;


	
	public static Bundle createArgs(int bookId, int chapter_1, int verse_1) {
		Bundle args = new Bundle();
		args.putInt(EXTRA_bookId, bookId);
		args.putInt(EXTRA_chapter, chapter_1);
		args.putInt(EXTRA_verse, verse_1);
		return args;
	}
	
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Bundle args = getArguments();
		if (args != null) {
			bookId = args.getInt(EXTRA_bookId, -1);
			chapter_1 = args.getInt(EXTRA_chapter);
			verse_1 = args.getInt(EXTRA_verse);
		}
	}

	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.fragment_goto_direct, container, false);
		lContohLoncat = V.get(res, R.id.lContohLoncat);
		tAlamatLoncat = V.get(res, R.id.tAlamatLoncat);
		bOk = V.get(res, R.id.bOk);

		bOk.setOnClickListener(bOk_click);
		
		tAlamatLoncat.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				bOk_click.onClick(bOk);
				return true;
			}
		});
		return res;
	}
	
	@Override public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		{
			String alamatContoh = S.reference(S.activeVersion.getBook(bookId), chapter_1, verse_1);
			String text = getString(R.string.loncat_ke_alamat_titikdua);
			int pos = text.indexOf("%s"); //$NON-NLS-1$
			if (pos >= 0) {
				SpannableStringBuilder sb = new SpannableStringBuilder();
				sb.append(text.substring(0, pos));
				sb.append(alamatContoh);
				sb.append(text.substring(pos + 2));
				sb.setSpan(new StyleSpan(Typeface.BOLD), pos, pos + alamatContoh.length(), 0);
				lContohLoncat.setText(sb, BufferType.SPANNABLE);
			}
		}
		
		tAlamatLoncat.setOnFocusChangeListener(new OnFocusChangeListener() {
			@Override public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					showKeyboard();
				}
			}
		});
		tAlamatLoncat.requestFocus();
	}
	
	OnClickListener bOk_click = new OnClickListener() {
		@Override public void onClick(View v) {
			String reference = tAlamatLoncat.getText().toString();
			
			if (reference.trim().length() == 0) {
				return; // do nothing
			}
			
			Jumper jumper = new Jumper();
			boolean success = jumper.parse(reference);
			if (! success) {
				new AlertDialog.Builder(getActivity())
				.setMessage(getString(R.string.alamat_tidak_sah_alamat, reference))
				.setPositiveButton(R.string.ok, null)
				.show();
				return;
			}
			
			int bookId = jumper.getBookId(S.activeVersion.getConsecutiveBooks());
			int chapter = jumper.getChapter();
			int verse = jumper.getVerse();
			
			((GotoFinishListener) getActivity()).onGotoFinished(GotoFinishListener.GOTO_TAB_direct, bookId, chapter, verse);
		}
	};

	public void onTabSelected() {
		showKeyboard();
	}

	private void showKeyboard() {
		if (getActivity() != null) {
			InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.showSoftInput(tAlamatLoncat, InputMethodManager.SHOW_IMPLICIT);
		}
	}
}
