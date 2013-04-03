package yuku.alkitab.base.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.EditText;

import java.util.Date;

import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.Bookmark2;
import yuku.alkitab.base.storage.Db;

public class TypeNoteDialog {
	final Context context;
	final AlertDialog dialog;
	final RefreshCallback refreshCallback;
	final Book book;
	final int pasal_1;
	final int ayat_1;
	
	EditText tCatatan;
	
	int ari;
	String alamat;
	Bookmark2 bukmak;

	public interface RefreshCallback {
		void onDone();
	}
	
	public TypeNoteDialog(Context context, Book book, int pasal_1, int ayat_1, RefreshCallback refreshCallback) {
		this.book = book;
		this.pasal_1 = pasal_1;
		this.ayat_1 = ayat_1;
		this.ari = Ari.encode(book.bookId, pasal_1, ayat_1);
		this.alamat = S.reference(book, pasal_1, ayat_1);
		this.context = context;
		this.refreshCallback = refreshCallback;
		
		View dialogLayout = LayoutInflater.from(context).inflate(R.layout.dialog_edit_note, null);
		
		this.dialog = new AlertDialog.Builder(context)
		.setView(dialogLayout)
		.setIcon(R.drawable.attribute_type_note)
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				bOk_click();
			}
		})
		.setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				bHapus_click();
			}
		})
		.create();

		tCatatan = (EditText) dialogLayout.findViewById(R.id.tCatatan);
		
		tCatatan.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					if (tCatatan.length() == 0) {
						dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
					}
				}
			}
		});
	}
	
	void setCatatan(CharSequence catatan) {
		tCatatan.setText(catatan);
	}

	public void show() {
		this.dialog.setTitle(context.getString(R.string.catatan_alamat, alamat));
		
		this.bukmak = S.getDb().getBookmarkByAri(ari, Db.Bookmark2.kind_note);
		if (bukmak != null) {
			tCatatan.setText(bukmak.caption);
		}
		
		dialog.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		dialog.show();
	}

	protected void bOk_click() {
		String tulisan = tCatatan.getText().toString();
		Date kini = new Date();
		if (bukmak != null) {
			if (tulisan.length() == 0) {
				S.getDb().deleteBookmarkByAri(ari, Db.Bookmark2.kind_note);
			} else {
				bukmak.caption = tulisan;
				bukmak.modifyTime = kini;
				S.getDb().updateBookmark(bukmak);
			}
		} else { // bukmak == null; belum ada sebelumnya, maka hanya insert kalo ada tulisan.
			if (tulisan.length() > 0) {
				bukmak = S.getDb().insertBookmark(ari, Db.Bookmark2.kind_note, tulisan, kini, kini);
			}
		}
		
		if (refreshCallback != null) refreshCallback.onDone();
	}

	protected void bHapus_click() {
		// kalo emang ga ada, cek apakah udah ada teks, kalau udah ada, tanya dulu
		if (bukmak != null || (bukmak == null && tCatatan.length() > 0)) {
			new AlertDialog.Builder(context)
			.setTitle(R.string.hapus_catatan)
			.setMessage(R.string.anda_yakin_mau_menghapus_catatan_ini)
			.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					if (bukmak != null) {
						// beneran hapus dari db
						S.getDb().deleteBookmarkByAri(ari, Db.Bookmark2.kind_note);
					} else {
						// ga ngapa2in, karena emang ga ada di db, cuma di editor buffer
					}
					
					if (refreshCallback != null) refreshCallback.onDone();
				}
			})
			.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface _unused_, int which) {
					TypeNoteDialog dialog = new TypeNoteDialog(context, book, pasal_1, ayat_1, refreshCallback);
					dialog.setCatatan(tCatatan.getText());
					dialog.show();
				}
			})
			.show();
		}
	}
}
