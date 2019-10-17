package yuku.alkitab.base.ac;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewFlipper;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.VersesDialog;
import yuku.alkitab.base.widget.CallbackSpan;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Marker;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabconverter.util.DesktopVerseFinder;
import yuku.alkitabconverter.util.DesktopVerseParser;
import yuku.alkitabintegration.display.Launcher;

import java.util.Date;

public class NoteActivity extends BaseActivity {
	//region static constructors
	private static final String EXTRA_marker_id = "marker_id";
	private static final String EXTRA_reference = "reference";
	private static final String EXTRA_ariForNewNote = "ariForNewNote";
	private static final String EXTRA_verseCountForNewNote = "verseCountForNewNote";

	private static Intent createIntent(final long marker_id, final String reference, final int ariForNewNote, final int verseCountForNewNote) {
		final Intent res = new Intent(App.context, NoteActivity.class);
		res.putExtra(EXTRA_marker_id, marker_id);
		res.putExtra(EXTRA_reference, reference);
		res.putExtra(EXTRA_ariForNewNote, ariForNewNote);
		res.putExtra(EXTRA_verseCountForNewNote, verseCountForNewNote);
		return res;
	}

	/**
	 * Open the note edit dialog, editing existing note.
	 */
	public static Intent createEditExistingIntent(long _id) {
		return createIntent(_id, null, 0, 0);
	}

	/**
	 * Open the note edit dialog for a new note by ari.
	 */
	public static Intent createNewNoteIntent(String reference, int ari, int verseCount) {
		return createIntent(0, reference, ari, verseCount);
	}
	//endregion

	Marker marker;
	int ariForNewNote;
	int verseCountForNewNote;

	boolean editingMode;
	boolean justClickedLink;

	ViewFlipper viewFlipper;
	TextView tCaptionReadOnly;
	EditText tCaption;

	int clickedTextOffset = -1;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_note);

		final long _id = getIntent().getLongExtra(EXTRA_marker_id, 0L);
		if (_id != 0L) {
			marker = S.getDb().getMarkerById(_id);
		}

		String reference = getIntent().getStringExtra(EXTRA_reference);
		ariForNewNote = getIntent().getIntExtra(EXTRA_ariForNewNote, 0);
		verseCountForNewNote = getIntent().getIntExtra(EXTRA_verseCountForNewNote, 0);

		if (reference == null) {
			reference = S.activeVersion().referenceWithVerseCount(marker.ari, marker.verseCount);
		}

		setTitle(reference);

		viewFlipper = findViewById(R.id.viewFlipper);
		tCaptionReadOnly = findViewById(R.id.tCaptionReadOnly);
		tCaption = findViewById(R.id.tCaption);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);

		if (marker != null) {
			tCaptionReadOnly.setText(marker.caption);
			tCaption.setText(marker.caption);

			// editing existing note. Make the note read-only at first.
			setEditingMode(false);
		} else {
			// a new note
			setEditingMode(true);
		}
	}

	@Override protected void onStart() {
		super.onStart();

		final S.CalculatedDimensions applied = S.applied();

		{ // apply background color, by overriding window background
			getWindow().setBackgroundDrawable(new ColorDrawable(applied.backgroundColor));
		}

		// text formats
		for (final TextView tv: new TextView[]{tCaption, tCaptionReadOnly}) {
			tv.setTextColor(applied.fontColor);
			tv.setTypeface(applied.fontFace, applied.fontBold);
			tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp);
			tv.setLineSpacing(0, applied.lineSpacingMult);

			SettingsActivity.setPaddingBasedOnPreferences(tv);
		}

		getWindow().getDecorView().setKeepScreenOn(Preferences.getBoolean(getString(R.string.pref_keepScreenOn_key), getResources().getBoolean(R.bool.pref_keepScreenOn_default)));
	}

	static class VerseSpan extends CallbackSpan<String> {
		VerseSpan(final String verse, final OnClickListener<String> verseClickListener) {
			super(verse, verseClickListener);
		}
	}

	final CallbackSpan.OnClickListener<String> verseClickListener = (widget, verse) -> {
		justClickedLink = true;

		final IntArrayList verseRanges = DesktopVerseParser.verseStringToAri(verse);
		if (verseRanges == null || verseRanges.size() == 0) {
			new MaterialDialog.Builder(widget.getContext())
				.content(R.string.note_activity_cannot_parse_verse)
				.positiveText(R.string.ok)
				.show();
			return;
		}

		final VersesDialog versesDialog = VersesDialog.newInstance(verseRanges);
		versesDialog.setListener(new VersesDialog.VersesDialogListener() {
			@Override
			public void onVerseSelected(final VersesDialog dialog, final int ari) {
				startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(ari));
				versesDialog.dismiss();
			}
		});
		versesDialog.show(getSupportFragmentManager(), "dialog_verses");
	};

	void setEditingMode(final boolean editingMode) {
		if (editingMode) {
			viewFlipper.setDisplayedChild(1);

			if (clickedTextOffset != -1) {
				tCaption.setSelection(clickedTextOffset);
			}

		} else {
			viewFlipper.setDisplayedChild(0);

			// put verse links
			final SpannableStringBuilder text = new SpannableStringBuilder(tCaptionReadOnly.getText());
			DesktopVerseFinder.findInText(text, new DesktopVerseFinder.DetectorListener() {
				@Override
				public boolean onVerseDetected(final int start, final int end, final String verse) {
					text.setSpan(new VerseSpan(verse, verseClickListener), start, end, 0);
					return true;
				}

				@Override
				public void onNoMoreDetected() {
				}
			});
			tCaptionReadOnly.setText(text);
			tCaptionReadOnly.setMovementMethod(LinkMovementMethod.getInstance());
			tCaptionReadOnly.setOnTouchListener((v, event) -> {
				// needed to calculate where the click is on the text layout
				if (event.getActionMasked() == MotionEvent.ACTION_UP) {
					clickedTextOffset = -1;
					final Layout layout = tCaptionReadOnly.getLayout();
					if (layout != null) {
						final int line = layout.getLineForVertical((int) event.getY() - tCaptionReadOnly.getTotalPaddingTop());
						clickedTextOffset = layout.getOffsetForHorizontal(line, (int) event.getX() - tCaptionReadOnly.getTotalPaddingLeft());
					}
				}
				return false;
			});
			tCaptionReadOnly.setOnClickListener(v -> {
				if (!Preferences.getBoolean(R.string.pref_tapToEditNote_key, R.bool.pref_tapToEditNote_default)) {
					return;
				}

				if (!justClickedLink) {
					setEditingMode(true);
				} else {
					justClickedLink = false;
				}
			});
		}

		this.editingMode = editingMode;

		supportInvalidateOptionsMenu();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_note, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		final MenuItem menuEdit = menu.findItem(R.id.menuEdit);
		final MenuItem menuDelete = menu.findItem(R.id.menuDelete);
		final MenuItem menuOk = menu.findItem(R.id.menuOk);

		menuEdit.setVisible(!editingMode);
		menuDelete.setVisible(editingMode);
		menuOk.setVisible(editingMode);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menuEdit: {
				setEditingMode(true);
			}
			return true;
			case R.id.menuDelete: {
				// if it's indeed not exist, check if we have some text, if we do, prompt first
				if (marker != null || tCaption.length() > 0) {
					new MaterialDialog.Builder(this)
						.content(R.string.anda_yakin_mau_menghapus_catatan_ini)
						.positiveText(R.string.delete)
						.onPositive((dialog, which) -> {
							if (marker != null) {
								// really delete from db
								S.getDb().deleteNonBookmarkMarkerById(marker._id);
							} else {
								// do nothing, because it's indeed not in the db, only in editor buffer
							}

							setResult(RESULT_OK);
							realFinish();
						})
						.negativeText(R.string.cancel)
						.show();
				} else { // no existing marker and buffer is empty
					realFinish(); // no need to setResult(RESULT_OK), because nothing is to be reloaded
				}

			}
			return true;
			case R.id.menuOk: {
				ok_click();
			}
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	void ok_click() {
		if (editingMode) { // if we are not editing, do not bother checking and saving.
			final String caption = tCaption.getText().toString();
			final Date now = new Date();

			if (marker != null) { // update existing marker
				if (U.equals(marker.caption, caption)) {
					// when there is no change, do nothing
				} else {
					if (caption.length() == 0) { // delete instead of update
						S.getDb().deleteNonBookmarkMarkerById(marker._id);
					} else {
						marker.caption = caption;
						marker.modifyTime = now;
						S.getDb().insertOrUpdateMarker(marker);
					}
				}
			} else { // marker == null; not existing, so only insert when there is some text
				if (caption.length() > 0) {
					marker = S.getDb().insertMarker(ariForNewNote, Marker.Kind.note, caption, verseCountForNewNote, now, now);
				}
			}

			setResult(RESULT_OK);
		}

		realFinish();
	}

	@Override
	public void finish() {
		// make sure we save it before exiting this screen
		ok_click();
		realFinish();
	}

	void realFinish() {
		super.finish();
	}
}
