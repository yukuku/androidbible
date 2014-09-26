package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import yuku.afw.V;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Marker;

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
	 * Open the note edit dialog for an existing note by ari and ordering (starting from 0).
	 */
	public static Intent createEditExistingWithOrderingIntent(int ari, int ordering) {
		final Marker marker = S.getDb().getMarker(ari, Marker.Kind.note, ordering);
		return createIntent(marker._id, null, 0, 0);
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

	EditText tCaption;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, true);
		setContentView(R.layout.activity_note);

		final long _id = getIntent().getLongExtra(EXTRA_marker_id, 0L);
		if (_id != 0L) {
			marker = S.getDb().getMarkerById(_id);
		}

		String reference = getIntent().getStringExtra(EXTRA_reference);
		ariForNewNote = getIntent().getIntExtra(EXTRA_ariForNewNote, 0);
		verseCountForNewNote = getIntent().getIntExtra(EXTRA_verseCountForNewNote, 0);

		if (reference == null) {
			reference = S.activeVersion.referenceWithVerseCount(marker.ari, marker.verseCount);
		}

		setTitle(reference);

		tCaption = V.get(this, R.id.tCaption);

		if (marker != null) {
			tCaption.setText(marker.caption);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_note, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menuDelete: {
				// if it's indeed not exist, check if we have some text, if we do, prompt first
				if (marker != null || tCaption.length() > 0) {
					new AlertDialog.Builder(this)
						.setMessage(R.string.anda_yakin_mau_menghapus_catatan_ini)
						.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								if (marker != null) {
									// really delete from db
									S.getDb().deleteNonBookmarkMarkerById(marker._id);
								} else {
									// do nothing, because it's indeed not in the db, only in editor buffer
								}

								setResult(RESULT_OK);
								finish();
							}
						})
						.setNegativeButton(R.string.no, null)
						.show();
				}

			}
			return true;
			case R.id.menuOk: {
				final String caption = tCaption.getText().toString();
				final Date now = new Date();

				if (marker != null) { // update existing marker
					if (caption.length() == 0) { // delete instead of update
						S.getDb().deleteNonBookmarkMarkerById(marker._id);
					} else {
						marker.caption = caption;
						marker.modifyTime = now;
						S.getDb().updateMarker(marker);
					}
				} else { // marker == null; not existing, so only insert when there is some text
					if (caption.length() > 0) {
						marker = S.getDb().insertMarker(ariForNewNote, Marker.Kind.note, caption, verseCountForNewNote, now, now);
					}
				}

				setResult(RESULT_OK);
				finish();
			}
			return true;
		}

		return super.onOptionsItemSelected(item);
	}
}
