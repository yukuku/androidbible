package yuku.alkitab.base.ac;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;

import java.io.IOException;
import java.io.InputStream;

public class AboutActivity extends BaseActivity {
	public static final String TAG = AboutActivity.class.getSimpleName();

	View root;
	TextView lAbout;
	TextView lTranslators;

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
		setTitle(String.format("%s %s", getString(R.string.app_name), App.getVersionName()));
		getSupportActionBar().setSubtitle(String.format("%s %s", App.getVersionCode(), getString(R.string.last_commit_hash)));

		root = V.get(this, R.id.root);
		lAbout = V.get(this, R.id.lAbout);
		lTranslators = V.get(this, R.id.lTranslators);

		try {
			final InputStream input = getAssets().open("help/about.html");
			final byte[] buf = new byte[input.available()];
			input.read(buf);
			lAbout.setText(Html.fromHtml(new String(buf, "utf-8")));
			lAbout.setMovementMethod(LinkMovementMethod.getInstance());
		} catch (IOException e) {
			Log.e(TAG, "reading about text", e);
		}

		String[] translators = getResources().getStringArray(R.array.translators_list);
		SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append(getString(R.string.about_translators)).append('\n');
		sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), 0);

		for (String translator: translators) {
			sb.append("\u2022 "); //$NON-NLS-1$
			int open = translator.indexOf('[');
			int close = translator.indexOf(']');
			if (open != -1 && close > open) {
				sb.append(translator.substring(0, open));
				int sb_len = sb.length();
				sb.append(translator.substring(close + 1));
				sb.setSpan(new URLSpan(translator.substring(open + 1, close)), sb_len, sb.length(), 0);
			} else {
				sb.append(translator);
			}
			sb.append('\n');
		}
		lTranslators.setText(sb);
		lTranslators.setMovementMethod(LinkMovementMethod.getInstance());

		root.setOnTouchListener(root_touch);
	}

	View.OnTouchListener root_touch = new View.OnTouchListener() {
		@Override
		public boolean onTouch(final View v, final MotionEvent event) {
			if (event.getPointerCount() >= 4) {
				getWindow().setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.BR_TL, new int[] {0xffaaffaa, 0xffaaffff, 0xffaaaaff, 0xffffaaff, 0xffffaaaa, 0xffffffaa}));
			}

			return false;
		}
	};
}
