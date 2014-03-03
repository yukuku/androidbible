package yuku.alkitab.base.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.EditText;
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

	public interface Listener {
		void onDone();
	}

	/**
	 * Open the note edit dialog, editing existing note.
	 * @param context Activity context to create dialogs
	 */
	public TypeNoteDialog(Context context, long _id, Listener listener) {
		this(context, S.getDb().getMarkerById(_id), null, listener);
	}

	/**
	 * Open the note edit dialog for an existing note by ari and ordering (starting from 0).
	 */
	public TypeNoteDialog(Context context, int ari, int ordering, Listener listener) {
		this(context, S.getDb().getMarker(ari, Marker.Kind.note, ordering), null, listener);
	}

	/**
	 * Open the note edit dialog for a new note by ari.
	 */
	public TypeNoteDialog(Context context, int ari, Listener listener) {
		this(context, null, S.activeVersion.reference(ari), listener);
		this.ariForNewNote = ari;
	}

	private TypeNoteDialog(Context context, Marker marker, String reference, Listener listener) {
		this.context = context;
		this.marker = marker;
		this.listener = listener;

		final View dialogLayout = LayoutInflater.from(context).inflate(R.layout.dialog_edit_note, null);

		this.dialog = new AlertDialog.Builder(context)
		.setView(dialogLayout)
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
		})
		.create();

		if (reference == null) {
			reference = S.activeVersion.reference(marker.ari); // TODO multi verse
		}

		this.dialog.setTitle(context.getString(R.string.catatan_alamat, reference));

		tCaption = (EditText) dialogLayout.findViewById(R.id.tCaption);

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
				marker = S.getDb().insertMarker(ariForNewNote, Marker.Kind.note, caption, 1, now, now);
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
						dialog = new TypeNoteDialog(context, ariForNewNote, listener);
					} else { // we're in process of editing an existing note
						dialog = new TypeNoteDialog(context, marker._id, listener);
					}
					dialog.setCaption(tCaption.getText());
					dialog.show();
				}
			})
			.show();
		}
	}
}
