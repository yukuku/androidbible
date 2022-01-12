package yuku.alkitab.base.ac;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import com.afollestad.materialdialogs.MaterialDialog;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import static yuku.alkitab.base.util.Literals.List;
import yuku.alkitab.debug.R;
import yuku.alkitab.tracking.Tracker;

public class AboutActivity extends BaseActivity {
	View root;
	TextView tVersion;
	TextView tBuild;
	ImageView imgLogo;
	TextView tAboutTextDesc;

	View bHelp;
	View bMaterialSources;
	View bCredits;
	View bFeedback;
	View bEnableBeta;

	final AtomicBoolean backgroundAnimationStarted = new AtomicBoolean(false);
	int baseHue = 0;
	final float[] hsl = new float[3];
	final int[] colors = new int[6];

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);

		root = findViewById(R.id.root);
		tVersion = findViewById(R.id.tVersion);
		tBuild = findViewById(R.id.tBuild);
		imgLogo = findViewById(R.id.imgLogo);
		tAboutTextDesc = findViewById(R.id.tAboutTextDesc);

		bHelp = findViewById(R.id.bHelp);
		bHelp.setOnClickListener(v -> {
			Tracker.trackEvent("help_button_guide");
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://alkitab.app/guide?utm_source=app&utm_medium=button&utm_campaign=help")));
		});

		bMaterialSources = findViewById(R.id.bMaterialSources);
		bMaterialSources.setOnClickListener(v -> {
			Tracker.trackEvent("help_button_material_sources");
			startActivity(HelpActivity.createIntent("help/material_sources.html", getString(R.string.about_material_sources)));
		});

		bCredits = findViewById(R.id.bCredits);
		bCredits.setOnClickListener(v -> {
			Tracker.trackEvent("help_button_credits");
			startActivity(HelpActivity.createIntent("help/credits.html", getString(R.string.about_credits)));
		});

		bFeedback = findViewById(R.id.bFeedback);
		bFeedback.setOnClickListener(v -> {
			Tracker.trackEvent("help_button_feedback");
			startActivity(new Intent(App.context, com.example.android.wizardpager.MainActivity.class));
		});

		bEnableBeta = findViewById(R.id.bEnableBeta);
		bEnableBeta.setOnClickListener(v -> {
			Tracker.trackEvent("help_button_enable_beta");
			new MaterialDialog.Builder(this)
					.content(R.string.about_enable_beta_confirmation)
					.positiveText(R.string.ok)
					.negativeText(R.string.cancel)
					.onPositive((dialog, which) -> {
						try {
							startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/apps/testing/" + getPackageName())));
						} catch (Exception ignored) {
						}
					})
					.show();
			}
		);

		// already in beta?
		if (App.getVersionName().contains("beta")) {
			bEnableBeta.setVisibility(View.GONE);
		}

		imgLogo.setImageDrawable(ResourcesCompat.getDrawableForDensity(getResources(), R.mipmap.ic_launcher, DisplayMetrics.DENSITY_XXXHIGH, null));

		imgLogo.setOnTouchListener((v, event) -> {
			if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
				final float x = event.getX();
				final float y = event.getY();
				if (x >= v.getWidth() / 2 - 4 && x <= v.getWidth() / 2 + 4) {
					if (y >= v.getHeight() * 3 / 10 - 4 && y <= v.getHeight() * 3 / 10 + 4) {
						showSecretDialog();
					}
				}
			}
			return false;
		});

		tAboutTextDesc.setMovementMethod(LinkMovementMethod.getInstance());

		tVersion.setText(getString(R.string.about_version_name, App.getVersionName()));
		tBuild.setText(String.format(Locale.US, "%s %s", App.getVersionCode(), getString(R.string.last_commit_hash)));

		root.setOnTouchListener(root_touch);
	}

	final View.OnTouchListener root_touch = (v, event) -> {
		if (event.getPointerCount() == 4) {
			startBackgroundAnimation();
		} else if (event.getPointerCount() == 5 && event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
			showSecretDialog();
		}

		return false;
	};

	@SuppressLint("HandlerLeak")
	private void startBackgroundAnimation() {
		if (!backgroundAnimationStarted.compareAndSet(false, true)) {
			return;
		}

		new Handler() {
			@Override
			public void handleMessage(final Message msg) {
				if (isFinishing()) return; // don't leak

				final int baseColor = 0xff99ff99;
				ColorUtils.colorToHSL(baseColor, hsl);
				for (int i = 0; i < colors.length; i++) {
					hsl[0] = (baseHue + i * 60) % 360;
					colors[i] = ColorUtils.HSLToColor(hsl);
				}

				getWindow().setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.BR_TL, colors));

				baseHue += 2;
				sendEmptyMessageDelayed(0, 16);
			}
		}.sendEmptyMessage(0);
	}

	private void showSecretDialog() {
		new MaterialDialog.Builder(this)
			.items(List("Secret settings", "Crash me"))
			.itemsCallback((dialog, itemView, position, text) -> {
				switch (position) {
					case 0:
						startActivity(SecretSettingsActivity.createIntent());
						return;
					case 1:
						throw new RuntimeException("Dummy exception from secret dialog.");
				}
			})
			.show();
	}

	public static Intent createIntent() {
		return new Intent(App.context, AboutActivity.class);
	}
}
