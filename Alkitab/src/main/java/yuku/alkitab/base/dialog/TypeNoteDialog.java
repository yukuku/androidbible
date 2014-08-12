package yuku.alkitab.base.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.EditText;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Marker;

import java.util.Date;

public class TypeNoteDialog {
	final Context context;
	final AlertDialog dialog;
	final Listener listener;

	EditText tCaption;
	
	Marker marker;
	int ariForNewNote;
	int verseCountForNewNote;

	public interface Listener {
		void onDone();
	}

	/**
	 * Open the note edit dialog, editing existing note.
	 * @param context Activity context to create dialogs
	 */
	public static TypeNoteDialog EditExisting(Context context, long _id, Listener listener) {
		return new TypeNoteDialog(context, S.getDb().getMarkerById(_id), null, listener);
	}

	/**
	 * Open the note edit dialog for an existing note by ari and ordering (starting from 0).
	 */
	public static TypeNoteDialog EditExistingWithOrdering(Context context, int ari, int ordering, Listener listener) {
		return new TypeNoteDialog(context, S.getDb().getMarker(ari, Marker.Kind.note, ordering), null, listener);
	}

	/**
	 * Open the note edit dialog for a new note by ari.
	 */
	public static TypeNoteDialog NewNote(Context context, int ari, int verseCount, Listener listener) {
		final TypeNoteDialog res = new TypeNoteDialog(context, null, S.activeVersion.referenceWithVerseCount(ari, verseCount), listener);
		res.ariForNewNote = ari;
		res.verseCountForNewNote = verseCount;
		return res;
	}

	private TypeNoteDialog(Context context, Marker marker, String reference, Listener listener) {
		this.context = context;
		this.marker = marker;
		this.listener = listener;

		final AlertDialog.Builder builder = new AlertDialog.Builder(context);

		final Context contextForLayout = builder.getContext();
		final View dialogLayout = LayoutInflater.from(contextForLayout).inflate(R.layout.dialog_edit_note, null);

		builder.setView(dialogLayout)
		.setIcon(R.drawable.ic_attr_note)
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				bOk_click();
			}
		})
		.setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				bDelete_click();
			}
		});

		this.dialog = builder.create();

		if (reference == null) {
			reference = S.activeVersion.referenceWithVerseCount(marker.ari, marker.verseCount);
		}

		this.dialog.setTitle(context.getString(R.string.catatan_alamat, reference));

		tCaption = V.get(dialogLayout, R.id.tCaption);

		tCaption.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					if (tCaption.length() == 0) {
						dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
					}
				}
			}
		});
	}

	void setCaption(CharSequence catatan) {
		tCaption.setText(catatan);
	}

	public void show() {
		if (marker != null) {
			tCaption.setText(marker.caption);
		}
		
		dialog.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		dialog.show();
	}

	void bOk_click() {
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
		
		if (listener != null) listener.onDone();
	}

	protected void bDelete_click() {
		// if it's indeed not exist, check if we have some text, if we do, prompt first
		if (marker != null || tCaption.length() > 0) {
			new AlertDialog.Builder(context)
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

					if (listener != null) listener.onDone();
				}
			})
			.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface _unused_, int which) {
					TypeNoteDialog dialog;
					if (marker == null) { // we're in process of creating a new note
						dialog = TypeNoteDialog.NewNote(context, ariForNewNote, verseCountForNewNote, listener);
					} else { // we're in process of editing an existing note
						dialog = TypeNoteDialog.EditExisting(context, marker._id, listener);
					}
					dialog.setCaption(tCaption.getText());
					dialog.show();
				}
			})
			.show();
		}
	}
}
