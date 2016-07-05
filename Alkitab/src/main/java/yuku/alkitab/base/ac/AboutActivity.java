package yuku.alkitab.base.ac;

import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.Intent;
import android.content.Loader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.V;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.util.Announce;
import yuku.alkitab.debug.R;

import java.util.concurrent.atomic.AtomicBoolean;

import static yuku.alkitab.base.util.Literals.Array;

public class AboutActivity extends BaseActivity {
	public static final String TAG = AboutActivity.class.getSimpleName();

	public static final int LOADER_announce = 1;

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
	View bAnnouncements;
	TextView tAnnouncements;
	ContentLoadingProgressBar progressAnnouncements;

	enum AnnouncementState {
		init,
		loading,
		has_none,
		has_few,
		error,
	}

	AnnouncementState announcementState;
	long[] announcementIds;
	final AtomicBoolean manualAnnouncementReload = new AtomicBoolean();

	final LoaderManager.LoaderCallbacks<long[]> announcementLoaderCallbacks = new LoaderManager.LoaderCallbacks<long[]>() {
		@Override
		public Loader<long[]> onCreateLoader(final int id, final Bundle args) {
			setAnnouncementState(AnnouncementState.loading);

			return new AsyncTaskLoader<long[]>(AboutActivity.this) {
				@Override
				public long[] loadInBackground() {
					return Announce.getAnnouncementIds();
				}
			};
		}

		@Override
		public void onLoadFinished(final Loader<long[]> loader, final long[] data) {
			if (data == null) {
				setAnnouncementState(AnnouncementState.error);

				if (manualAnnouncementReload.get()) {
					if (!isFinishing()) {
						new AlertDialogWrapper.Builder(AboutActivity.this)
							.setMessage(R.string.about_announcement_load_failed)
							.setPositiveButton(R.string.ok, null)
							.show();
					}
				}
			} else {
				announcementIds = data;
				if (data.length == 0) {
					setAnnouncementState(AnnouncementState.has_none);
				} else {
					setAnnouncementState(AnnouncementState.has_few);
				}
			}
		}

		@Override
		public void onLoaderReset(final Loader<long[]> loader) {
			setAnnouncementState(AnnouncementState.init);
		}
	};

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);

		final Toolbar toolbar = V.get(this, R.id.toolbar);
		setSupportActionBar(toolbar); // must be done first before below lines
		toolbar.setTitle(null);
		toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material);
		toolbar.setNavigationOnClickListener(v -> navigateUp());

		root = V.get(this, R.id.root);
		tVersion = V.get(this, R.id.tVersion);
		tBuild = V.get(this, R.id.tBuild);
		imgLogo = V.get(this, R.id.imgLogo);
		tAboutTextDesc = V.get(this, R.id.tAboutTextDesc);

		bHelp = V.get(this, R.id.bHelp);
		bHelp.setOnClickListener(v -> {
			App.trackEvent("help_button_announcement");
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.bibleforandroid.com/guide?utm_source=app&utm_medium=button&utm_campaign=help")));
		});

		bMaterialSources = V.get(this, R.id.bMaterialSources);
		bMaterialSources.setOnClickListener(v -> {
			App.trackEvent("help_button_material_sources");
			startActivity(HelpActivity.createIntent("help/material_sources.html", getString(R.string.about_material_sources)));
		});

		bCredits = V.get(this, R.id.bCredits);
		bCredits.setOnClickListener(v -> {
			App.trackEvent("help_button_credits");
			startActivity(HelpActivity.createIntent("help/credits.html", getString(R.string.about_credits)));
		});

		bFeedback = V.get(this, R.id.bFeedback);
		bFeedback.setOnClickListener(v -> {
			App.trackEvent("help_button_feedback");
			startActivity(new Intent(App.context, com.example.android.wizardpager.MainActivity.class));
		});

		bEnableBeta = V.get(this, R.id.bEnableBeta);
		bEnableBeta.setOnClickListener(v -> {
				App.trackEvent("help_button_enable_beta");
				new MaterialDialog.Builder(this)
					.content(R.string.about_enable_beta_confirmation)
					.positiveText(R.string.ok)
					.negativeText(R.string.cancel)
					.callback(new MaterialDialog.ButtonCallback() {
						@Override
						public void onPositive(final MaterialDialog dialog) {
							try {
								startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/apps/testing/" + getPackageName())));
							} catch (Exception ignored) {
								// just ignore, this is not important if fails.
							}
						}
					})
					.show();
			}
		);

		// already in beta?
		if (App.getVersionName().contains("beta")) {
			bEnableBeta.setVisibility(View.GONE);
		}

		bAnnouncements = V.get(this, R.id.bAnnouncements);
		bAnnouncements.setOnClickListener(v -> bAnnouncements_click());

		tAnnouncements = V.get(this, R.id.tAnnouncements);
		progressAnnouncements = V.get(this, R.id.progressAnnouncements);

		setAnnouncementState(AnnouncementState.init);

		manualAnnouncementReload.set(false);
		getLoaderManager().initLoader(LOADER_announce, null, announcementLoaderCallbacks).forceLoad();

		imgLogo.setImageDrawable(ResourcesCompat.getDrawableForDensity(getResources(), R.drawable.ic_launcher, DisplayMetrics.DENSITY_XXXHIGH, null));

		imgLogo.setOnTouchListener((v,event) -> {
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
		tBuild.setText(String.format("%s %s", App.getVersionCode(), getString(R.string.last_commit_hash)));

		root.setOnTouchListener(root_touch);
	}

	void bAnnouncements_click() {
		switch (announcementState) {
			case loading:
				// do nothing
				break;
			case has_none:
			case has_few:
				startActivity(HelpActivity.createViewAnnouncementIntent(announcementIds));
				break;
			case error:
				manualAnnouncementReload.set(true);
				getLoaderManager().getLoader(LOADER_announce).forceLoad();
				break;
		}
	}

	void setAnnouncementState(final AnnouncementState state) {
		this.announcementState = state;

		switch (state) {
			case init:
				tAnnouncements.setText(R.string.about_announcements);
				progressAnnouncements.hide();
				break;
			case loading:
				tAnnouncements.setText(R.string.about_announcements);
				progressAnnouncements.show();
				break;
			case has_none:
				tAnnouncements.setText(R.string.about_announcements_none);
				progressAnnouncements.hide();
				break;
			case has_few:
				tAnnouncements.setText(getString(R.string.about_announcements_number, announcementIds.length));
				progressAnnouncements.hide();
				break;
			case error:
				tAnnouncements.setText(R.string.about_announcements);
				progressAnnouncements.hide();
				break;
		}
	}

	View.OnTouchListener root_touch = (v, event) -> {
		if (event.getPointerCount() == 4) {
			getWindow().setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.BR_TL, new int[] {0xffaaffaa, 0xffaaffff, 0xffaaaaff, 0xffffaaff, 0xffffaaaa, 0xffffffaa}));
		} else if (event.getPointerCount() == 5 && event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
			showSecretDialog();
		}

		return false;
	};

	private void showSecretDialog() {
		new AlertDialogWrapper.Builder(this)
			.setItems(Array("Secret settings", "Crash me"), (dialog, which) -> {
				switch (which) {
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
