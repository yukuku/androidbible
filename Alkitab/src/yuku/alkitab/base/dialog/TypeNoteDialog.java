package yuku.alkitab.base.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.EditText;
import yuku.alkitab.base.S;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.Bookmark2;
import yuku.alkitab.util.Ari;

import java.util.Date;

public class TypeNoteDialog {
	final Context context;
	final AlertDialog dialog;
	final Listener listener;
	final Book book;
	final int chapter_1;
	final int verse_1;
	
	EditText tCaption;
	
	int ari;
	String reference;
	Bookmark2 bookmark;

	public interface Listener {
		void onDone();
	}
	
	public TypeNoteDialog(Context context, Book book, int chapter_1, int verse_1, Listener listener) {
		this.book = book;
		this.chapter_1 = chapter_1;
		this.verse_1 = verse_1;
		this.ari = Ari.encode(book.bookId, chapter_1, verse_1);
		this.reference = book.reference(chapter_1, verse_1);
		this.context = context;
		this.listener = listener;
		
		final AlertDialog.Builder builder = new AlertDialog.Builder(context);

		final Context contextForLayout = Build.VERSION.SDK_INT >= 11? builder.getContext(): context;
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

		tCaption = (EditText) dialogLayout.findViewById(R.id.tCaption);
		
		tCaption.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override public void onFocusChange(View v, boolean hasFocus) {
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
		this.dialog.setTitle(context.getString(R.string.catatan_alamat, reference));
		
		this.bookmark = S.getDb().getBookmarkByAri(ari, Db.Bookmark2.kind_note);
		if (bookmark != null) {
			tCaption.setText(bookmark.caption);
		}
		
		dialog.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		dialog.show();
	}

	protected void bOk_click() {
		CharSequence caption = tCaption.getText();
		Date now = new Date();
		if (bookmark != null) {
			if (caption.length() == 0) {
				S.getDb().deleteBookmarkByAri(ari, Db.Bookmark2.kind_note);
			} else {
				bookmark.caption = caption.toString();
				bookmark.modifyTime = now;
				S.getDb().updateBookmark(bookmark);
			}
		} else { // bookmark == null; not existing, so only insert when there is some text
			if (caption.length() > 0) {
				bookmark = S.getDb().insertBookmark(ari, Db.Bookmark2.kind_note, caption.toString(), now, now);
			}
		}
		
		if (listener != null) listener.onDone();
	}

	protected void bDelete_click() {
		// if it's indeed not exist, check if we have some text, if we do, prompt first
		if (bookmark != null || (bookmark == null && tCaption.length() > 0)) {
			new AlertDialog.Builder(context)
			.setMessage(R.string.anda_yakin_mau_menghapus_catatan_ini)
			.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					if (bookmark != null) {
						// really delete from db
						S.getDb().deleteBookmarkByAri(ari, Db.Bookmark2.kind_note);
					} else {
						// do nothing, because it's indeed not in the db, only in editor buffer
					}
					
					if (listener != null) listener.onDone();
				}
			})
			.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface _unused_, int which) {
					TypeNoteDialog dialog = new TypeNoteDialog(context, book, chapter_1, verse_1, listener);
					dialog.setCaption(tCaption.getText());
					dialog.show();
				}
			})
			.show();
		}
	}
}
