package yuku.alkitab.base.ac;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.debug.R;

public class AboutActivity extends BaseActivity {
	public static final String TAG = AboutActivity.class.getSimpleName();

	View root;
	TextView tVersion;
	TextView tBuild;
	ImageView imgLogo;
	TextView tAboutTextDesc;

	View bHelp;
	View bMaterialSources;
	View bCredits;
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
		imgLogo = V.get(this, R.id.imgLogo);
		tAboutTextDesc = V.get(this, R.id.tAboutTextDesc);

		bHelp = V.get(this, R.id.bHelp);
		bHelp.setOnClickListener(v -> startActivity(HelpActivity.createIntent("help/guide.html", false, null, null)));

		bMaterialSources = V.get(this, R.id.bMaterialSources);
		bMaterialSources.setOnClickListener(v -> startActivity(HelpActivity.createIntent("help/material_sources.html", false, null, null)));

		bCredits = V.get(this, R.id.bCredits);
		bCredits.setOnClickListener(v -> startActivity(HelpActivity.createIntent("help/credits.html", false, null, null)));

		bBetaFeedback = V.get(this, R.id.bBetaFeedback);
		bBetaFeedback.setOnClickListener(v -> startActivity(new Intent(App.context, com.example.android.wizardpager.MainActivity.class)));

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

		root.setOnTouchListener(root_touch);
	}

	View.OnTouchListener root_touch = (v, event) -> {
		if (event.getPointerCount() >= 4) {
			getWindow().setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.BR_TL, new int[] {0xffaaffaa, 0xffaaffff, 0xffaaaaff, 0xffffaaff, 0xffffaaaa, 0xffffffaa}));
		}

		return false;
	};

	public static Intent createIntent() {
		return new Intent(App.context, AboutActivity.class);
	}
}
