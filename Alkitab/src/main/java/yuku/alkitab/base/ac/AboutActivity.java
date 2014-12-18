package yuku.alkitab.base.ac;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.debug.R;

public class AboutActivity extends BaseActivity {
	public static final String TAG = AboutActivity.class.getSimpleName();

	View root;
	TextView tVersion;
	TextView tBuild;
	TextView tTranslators;
	ImageView imgLogo;
	TextView tAboutTextDesc;

	View bHelp;
	View bDonation;
	View bBetaFeedback;

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);

		final Toolbar toolbar = V.get(this, R.id.toolbar);
		setSupportActionBar(toolbar); // must be done first before below lines
		toolbar.setTitle(null);
		toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
		toolbar.setNavigationOnClickListener(v -> navigateUp());

		root = V.get(this, R.id.root);
		tVersion = V.get(this, R.id.tVersion);
		tBuild = V.get(this, R.id.tBuild);
		tTranslators = V.get(this, R.id.tTranslators);
		imgLogo = V.get(this, R.id.imgLogo);
		tAboutTextDesc = V.get(this, R.id.tAboutTextDesc);
		bHelp = V.get(this, R.id.bHelp);
		bDonation = V.get(this, R.id.bDonation);
		bBetaFeedback = V.get(this, R.id.bBetaFeedback);

		final Drawable logoDrawable;
		if (Build.VERSION.SDK_INT >= 15) {
			logoDrawable = getResources().getDrawableForDensity(R.drawable.ic_launcher, DisplayMetrics.DENSITY_XXXHIGH);
		} else {
			logoDrawable = getResources().getDrawable(R.drawable.ic_launcher);
		}
		imgLogo.setImageDrawable(logoDrawable);

		tAboutTextDesc.setMovementMethod(LinkMovementMethod.getInstance());

		tVersion.setText(getString(R.string.about_version_name, App.getVersionName()));
		tBuild.setText(String.format("%s %s", App.getVersionCode(), getString(R.string.last_commit_hash)));

		bHelp.setOnClickListener(bHelp_click);
		bDonation.setOnClickListener(bDonation_click);
		bBetaFeedback.setOnClickListener(bBetaFeedback_click);

		String[] translators = getResources().getStringArray(R.array.translators_list);
		SpannableStringBuilder sb = new SpannableStringBuilder();
		for (String translator: translators) {
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
		tTranslators.setText(sb);
		tTranslators.setMovementMethod(LinkMovementMethod.getInstance());

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

	View.OnClickListener bHelp_click = new View.OnClickListener() {
		@Override
		public void onClick(final View v) {
			String page;
			if (U.equals("in", getResources().getConfiguration().locale.getLanguage())) {
				page = "help/html-in/index.html";
			} else {
				page = "help/html-en/index.html";
			}

			startActivity(HelpActivity.createIntent(page, false, null, null));
		}
	};

	View.OnClickListener bDonation_click = new View.OnClickListener() {
		@Override
		public void onClick(final View v) {
			String donation_url = getString(R.string.donation_url);
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(donation_url));
			startActivity(HelpActivity.createIntent("help/donation.html", true, getString(R.string.send_donation_confirmation), intent));
		}
	};

	View.OnClickListener bBetaFeedback_click = new View.OnClickListener() {
		@Override
		public void onClick(final View v) {
			startActivity(new Intent(App.context, com.example.android.wizardpager.MainActivity.class));
		}
	};

	public static Intent createIntent() {
		return new Intent(App.context, AboutActivity.class);
	}
}
