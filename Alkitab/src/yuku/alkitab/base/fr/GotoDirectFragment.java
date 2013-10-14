package yuku.alkitab.base.fr;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import yuku.afw.V;
import yuku.alkitab.debug.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.fr.base.BaseGotoFragment;
import yuku.alkitab.base.util.Jumper;

public class GotoDirectFragment extends BaseGotoFragment {
	public static final String TAG = GotoDirectFragment.class.getSimpleName();
	
	private static final String EXTRA_verse = "verse"; //$NON-NLS-1$
	private static final String EXTRA_chapter = "chapter"; //$NON-NLS-1$
	private static final String EXTRA_bookId = "bookId"; //$NON-NLS-1$

	TextView lDirectSample;
	EditText tDirectReference;
	View bOk;

	int bookId;
	int chapter_1;
	int verse_1;
	private Activity activity;


	public static Bundle createArgs(int bookId, int chapter_1, int verse_1) {
		Bundle args = new Bundle();
		args.putInt(EXTRA_bookId, bookId);
		args.putInt(EXTRA_chapter, chapter_1);
		args.putInt(EXTRA_verse, verse_1);
		return args;
	}

	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		this.activity = activity;
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
		lDirectSample = V.get(res, R.id.lDirectSample);
		tDirectReference = V.get(res, R.id.tDirectReference);
		bOk = V.get(res, R.id.bOk);

		bOk.setOnClickListener(bOk_click);
		
		tDirectReference.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				bOk_click.onClick(bOk);
				return true;
			}
		});
		return res;
	}
	
	@Override public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		{
			String example = S.activeVersion.reference(bookId, chapter_1, verse_1);
			String text = getString(R.string.loncat_ke_alamat_titikdua);
			int pos = text.indexOf("%s"); //$NON-NLS-1$
			if (pos >= 0) {
				SpannableStringBuilder sb = new SpannableStringBuilder();
				sb.append(text.substring(0, pos));
				sb.append(example);
				sb.append(text.substring(pos + 2));
				sb.setSpan(new StyleSpan(Typeface.BOLD), pos, pos + example.length(), 0);
				lDirectSample.setText(sb, BufferType.SPANNABLE);
			}
		}
	}
	
	View.OnClickListener bOk_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			String reference = tDirectReference.getText().toString();
			
			if (reference.trim().length() == 0) {
				return; // do nothing
			}
			
			Jumper jumper = new Jumper(reference);
			if (! jumper.getParseSucceeded()) {
				new AlertDialog.Builder(getActivity())
				.setMessage(getString(R.string.alamat_tidak_sah_alamat, reference))
				.setPositiveButton(R.string.ok, null)
				.show();
				return;
			}
			
			final int bookId = jumper.getBookId(S.activeVersion.getConsecutiveBooks());
			final int chapter = jumper.getChapter();
			final int verse = jumper.getVerse();

			((GotoFinishListener) activity).onGotoFinished(GotoFinishListener.GOTO_TAB_direct, bookId, chapter, verse);
		}
	};
}
