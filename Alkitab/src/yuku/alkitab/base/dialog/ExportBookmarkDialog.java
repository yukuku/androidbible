package yuku.alkitab.base.dialog;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListView;
import yuku.alkitab.R;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.BookmarkListActivity;
import yuku.alkitab.base.model.Bookmark2;
import yuku.alkitab.base.model.Label;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.util.Sqlitil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExportBookmarkDialog extends DialogFragment {
	public static final String TAG = ExportBookmarkDialog.class.getSimpleName();
	LayoutInflater inflater;

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {

		this.inflater = inflater;
		View view = inflater.inflate(R.layout.dialog_export, container, false);
		getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		ListView lsBookmark = (ListView) view.findViewById(R.id.lsBookmark);
		final BookmarkAdapter adapter = new BookmarkAdapter();
		adapter.load();
		lsBookmark.setAdapter(adapter);
		lsBookmark.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				CheckedTextView checkedTextView = (CheckedTextView) view.findViewById(android.R.id.text1);
				boolean isChecked = checkedTextView.isChecked();
				List<ItemAdapter> items = adapter.items;
				items.get(position).checks = !isChecked;
				checkedTextView.setChecked(!isChecked);
				if (items.get(position).type == ItemType.allBookmarks) {
					for (ItemAdapter itemAdapter : items) {
						if (itemAdapter.type == ItemType.label || itemAdapter.type == ItemType.unlabel) {
							itemAdapter.checks = !isChecked;
						}
					}
				}
				if ((items.get(position).type == ItemType.label || items.get(position).type == ItemType.unlabel) && isChecked == true) {
					for (ItemAdapter itemAdapter : items) {
						if (itemAdapter.type == ItemType.allBookmarks) {
							itemAdapter.checks = false;
							break;
						}
					}
				}
				if ((items.get(position).type == ItemType.label || items.get(position).type == ItemType.unlabel) && isChecked == false) {
					boolean allChecked = true;
					for (ItemAdapter itemAdapter : items) {
						if (itemAdapter.type == ItemType.label || itemAdapter.type == ItemType.unlabel) {
							if (itemAdapter.checks == true) {
								continue;
							} else {
								allChecked = false;
							}
						}
					}
					if (allChecked) {
						for (ItemAdapter itemAdapter : items) {
							if (itemAdapter.type == ItemType.allBookmarks) {
								itemAdapter.checks = true;
								break;
							}
						}
					}
				}
				adapter.notifyDataSetChanged();
			}
		});
		Button bCancel = (Button) view.findViewById(R.id.bCancel);
		bCancel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				ExportBookmarkDialog.this.dismiss();
			}
		});
		Button bOk = (Button) view.findViewById(R.id.bOk);
		bOk.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				boolean showNoLabelBookmarks = false;
				List<Label> exportedLabels = new ArrayList<Label>();
				boolean showNotes = false;
				boolean showHighlights = false;

				for (ItemAdapter itemAdapter : adapter.items) {
					if (itemAdapter.type == ItemType.notes && itemAdapter.checks) {
						showNotes = true;
					} else if (itemAdapter.type == ItemType.highlight && itemAdapter.checks) {
						showHighlights = true;
					} else if (itemAdapter.type == ItemType.unlabel && itemAdapter.checks) {
						showNoLabelBookmarks = true;
					} else if (itemAdapter.type == ItemType.label && itemAdapter.checks) {
						exportedLabels.add(itemAdapter.label);
					}
				}
				ExportData data = getAllData(showNoLabelBookmarks, exportedLabels, showNotes, showHighlights);
				exportFile(writeHtml(data));
				ExportBookmarkDialog.this.dismiss();
			}
		});
		return view;
	}

	private ExportData getAllData(boolean showNoLabelBookmarks, List<Label> exportedlabels, boolean showNotes, boolean showHighlights) {

		List<Bookmark2> noLabelBookmarks = new ArrayList<Bookmark2>();
		List<Bookmark2> notes = new ArrayList<Bookmark2>();
		List<Bookmark2> highlights = new ArrayList<Bookmark2>();

		//no label
		if (showNoLabelBookmarks) {
			Cursor cursor = S.getDb().listBookmarks(Db.Bookmark2.kind_bookmark, BookmarkListActivity.LABELID_noLabel, Db.Bookmark2.addTime, false);
			while (cursor.moveToNext()) {
				Bookmark2 bookmark = Bookmark2.fromCursor(cursor);
				noLabelBookmarks.add(bookmark);
			}
		}

		//notes
		if (showNotes) {
			Cursor cursor = S.getDb().listBookmarks(Db.Bookmark2.kind_note, 0, Db.Bookmark2.addTime, false);
			while (cursor.moveToNext()) {
				Bookmark2 bookmark = Bookmark2.fromCursor(cursor);
				notes.add(bookmark);
			}
			cursor.close();
		}

		//highlight
		if (showHighlights) {
			Cursor cursor = S.getDb().listBookmarks(Db.Bookmark2.kind_highlight, 0, Db.Bookmark2.addTime, false);
			while (cursor.moveToNext()) {
				Bookmark2 bookmark2 = Bookmark2.fromCursor(cursor);
				highlights.add(bookmark2);
			}
			cursor.close();
		}

		ExportData exportData = new ExportData();
		exportData.noLabelBookmarks = noLabelBookmarks;
		exportData.labels = exportedlabels;
		exportData.notes = notes;
		exportData.highlights = highlights;

		return exportData;
	}

	class ExportData {
		List<Bookmark2> noLabelBookmarks;
		List<Bookmark2> notes;
		List<Bookmark2> highlights;
		List<Label> labels;
	}

	private File writeHtml(ExportData data) {
		List<Bookmark2> noLabelBookmarks = data.noLabelBookmarks;
		List<Label> labels = data.labels;
		List<Bookmark2> notes = data.notes;
		List<Bookmark2> highlights = data.highlights;

		File file = new File(App.context.getCacheDir(), "exported-markers-" + UUID.randomUUID().toString() + ".html");
		try {
			OutputStreamWriter osw = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(file)), "utf-8");
			PrintWriter pw = new PrintWriter(osw);

			//write classes
			String str = "<html>\n" +
							"<head>\n" +
							"\t<style type=\"text/css\">\n" +
							"\t\tbody {\n" +
							"\t\t\tfont-family: sans-serif;\n" +
							"\t\t\tfont-size: 11pt;\n" +
							"\t\t}\n" +
							"\t\th1 {\n" +
							"\t\t\tfont-size: 140%;\n" +
							"\t\t}\n" +
							"\t\tdl {\n" +
							"\t\t\tmargin-bottom: 3em;\n" +
							"\t\t}\n" +
							"\t\tdt {\n" +
							"\t\t\tmargin-bottom: 1em;\n" +
							"\t\t\tmargin-left: 1em;\n" +
							"\t\t}\n" +
							"\t\t.label {\n" +
							"\t\t\tfont-size: 120%;\n" +
							"\t\t\tfont-weight: bold;\n" +
							"\t\t}\n" +
							"\t\t#bookmark_list .title, #note_list .reference, #highlight_list .reference {\n" +
							"\t\t\tfont-size: 120%;\n" +
							"\t\t}\n" +
							"\t\t.times {\n" +
							"\t\t\tfloat: right;\n" +
							"\t\t\tfont-size: 80%;\n" +
							"\t\t}\n" +
							"\t\t#bookmark_list .reference {\n" +
							"\t\t\ttext-decoration: underline;\n" +
							"\t\t}\n" +
							"\t</style>\n" +
							"</head>";
			pw.print(str);

			pw.print("<body>");

			if (noLabelBookmarks.size() + labels.size() > 0) {
				pw.print("<h1>" + getString(R.string.pembatas_buku) + "</h1>\n<dl id='bookmark_list'>\n");

				//write no label bookmarks
				if (noLabelBookmarks.size() > 0) {
					pw.print("<span class='label'>Tanpa label</span>\n");
					for (Bookmark2 noLabelBookmark : noLabelBookmarks) {
						String verseText = S.activeVersion.loadVerseText(noLabelBookmark.ari);
						verseText = U.removeSpecialCodes(verseText);
						pw.print("<dt>\n");
						pw.print("<span class='title'>" + noLabelBookmark.caption + "</span>\n<span class='times'>\n");
						pw.print("<span class='createTime' data-unixtime='" + noLabelBookmark.addTime.getTime() + "'>" + Sqlitil.toLocaleDateMedium(noLabelBookmark.addTime) + "</span>\n");
						pw.print("<span class='modifyTime' data-unixtime='" + noLabelBookmark.modifyTime.getTime() + "'>(diedit " + Sqlitil.toLocaleDateMedium(noLabelBookmark.modifyTime) + ")</span>\n");
						pw.print("</span><br/>\n");
						pw.print("<span class='reference' data-ari='" + noLabelBookmark.ari + "'>" + noLabelBookmark.caption + "</span> <span class='snippet'>" + verseText + "</span>\n");
						pw.print("</dt>\n");
					}
				}
				//write labeled bookmarks
				if (labels.size() > 0) {
					int color;
					String labelName;
					for (Label label : labels) {
						color = U.decodeLabelBackgroundColor(label.backgroundColor);
						labelName = label.title;
						pw.print("<span class='label' data-bgcolor='" + Integer.toHexString(color) + "' style='background: #" + Integer.toHexString(color) + "'>" + labelName + "</span>\n");
						Cursor cursor = S.getDb().listBookmarks(Db.Bookmark2.kind_bookmark, label._id, Db.Bookmark2.addTime, false);
						while (cursor.moveToNext()) {
							Bookmark2 bookmark = Bookmark2.fromCursor(cursor);
							String verseText = S.activeVersion.loadVerseText(bookmark.ari);
							verseText = U.removeSpecialCodes(verseText);
							pw.print("<dt>\n");
							pw.print("<span class='title'>" + bookmark.caption + "</span>\n");
							pw.print("<span class='times'>\n");
							pw.print("<span class='createTime' data-unixtime='" + bookmark.ari + "'>" + Sqlitil.toLocaleDateMedium(bookmark.addTime) + "</span>\n");
							pw.print("<span class='modifyTime' data-unixtime='" + bookmark.modifyTime.getTime() + "'>(diedit " + Sqlitil.toLocaleDateMedium(bookmark.modifyTime) + ")</span>\n");
							pw.print("</span><br/>\n");
							pw.print("<span class='reference' data-ari='" + bookmark.ari + "'>" + bookmark.caption + "</span> <span class='snippet'>" + verseText + "</span>\n");
							pw.print("</dt>\n");
						}
					}

				}

				pw.print("</dl>");
			}

			//write notes
			if (notes.size() > 0) {
				pw.print("<h1>" + getString(R.string.bmcat_notes) + "</h1>\n<dl id='note_list'>\n");
				for (Bookmark2 note : notes) {
					String reference = S.activeVersion.reference(note.ari);
					pw.print("<dt>\n");
					pw.print("<span class='reference' data-ari='" + note.ari + "'>" + reference + "</span>\n");
					pw.print("<span class='times'>\n");
					pw.print("<span class='createTime' data-unixtime='" + note.addTime.getTime() + "'>" + Sqlitil.toLocaleDateMedium(note.addTime) + "</span>\n");
					pw.print("<span class='modifyTime' data-unixtime='" + note.modifyTime.getTime() + "'>(diedit " + Sqlitil.toLocaleDateMedium(note.modifyTime) + ")</span><br/>\n");
					pw.print("</span><br/>\n");
					pw.print("<span class='text'>" + note.caption + "</span>\n");
					pw.print("</dt>\n");
				}
				pw.print("</dl>\n");
			}

			//write highlights
			if (highlights.size() > 0) {
				pw.print("<h1>" + getString(R.string.bmcat_highlights) + "</h1>\n");
				pw.print("<dl id='highlight_list'>\n");
				for (Bookmark2 highlight : highlights) {
					String verseText = S.activeVersion.loadVerseText(highlight.ari);
					verseText = U.removeSpecialCodes(verseText);
					Log.d(TAG, "warna highlight: " + highlight.caption);
					int color = U.decodeHighlight(highlight.caption);
					String reference = S.activeVersion.reference(highlight.ari);
					pw.print("<dt>\n");
					pw.print("<span class='reference' data-ari='" + highlight.ari + "'>" + reference + "</span>\n");
					pw.print("<span class='times'>\n");
					pw.print("<span class='createTime' data-unixtime='" + highlight.addTime.getTime() + "'>" + Sqlitil.toLocaleDateMedium(highlight.addTime) + "</span>\n");
					pw.print("<span class='modifyTime'  data-unixtime='" + highlight.modifyTime.getTime() + "'>(diedit " + Sqlitil.toLocaleDateMedium(highlight.modifyTime) + ")</span><br/>\n");
					pw.print("</span><br/>\n");
					pw.print("<span class='snippet' data-bgcolor='" + Integer.toHexString(color) + "' style='background-color: #" + Integer.toHexString(color) + "'>" + verseText + "</span>\n");
					pw.print("</dt>\n");
				}
				pw.print("</dl>\n");
			}

			pw.print("</body>\n</html>\n");
			pw.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return file;
	}

	private void exportFile(File file) {
		Uri uri = FileProvider.getUriForFile(App.context, getString(R.string.file_provider_markers_export_authority), file);

		Log.d(TAG, "uri buat share: " + uri);
		final Intent intent = ShareCompat.IntentBuilder.from(getActivity())
		.setStream(uri)
		.setType("text/html")
		.getIntent()
		.setData(uri)
		.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

		startActivity(Intent.createChooser(intent, getString(R.string.me_export_markers)));

	}

	public enum ItemType {notes, highlight, allBookmarks, label, unlabel}

	class ItemAdapter {
		ItemType type;
		boolean checks;
		Label label;
	}

	class BookmarkAdapter extends BaseAdapter {

		List<Bookmark2> bookmarks = new ArrayList<Bookmark2>();
		List<Label> labels;
		List<ItemAdapter> items;

		void load() {
			labels = S.getDb().listAllLabels();
			Cursor cursor = S.getDb().listAllBookmarks();
			try {
				while (cursor.moveToNext()) {
					Bookmark2 bookmark = Bookmark2.fromCursor(cursor);
					bookmarks.add(bookmark);
				}
			} finally {
				cursor.close();
			}
			items = new ArrayList<ItemAdapter>(labels.size() + 4);
			for (int i = 0; i < labels.size() + 4; i++) {
				ItemAdapter itemAdapter = new ItemAdapter();
				itemAdapter.checks = true;
				if (i == 0) {
					itemAdapter.type = ItemType.notes;
				} else if (i == 1) {
					itemAdapter.type = ItemType.highlight;
				} else if (i == 2) {
					itemAdapter.type = ItemType.allBookmarks;
				} else if (i == 3) {
					itemAdapter.type = ItemType.unlabel;
				} else {
					itemAdapter.type = ItemType.label;
					itemAdapter.label = labels.get(i - 4);
				}
				items.add(itemAdapter);
			}
		}

		@Override
		public int getCount() {
			int count = 4 + labels.size();
			return count;
		}

		@Override
		public Object getItem(final int position) {
			return null;
		}

		@Override
		public long getItemId(final int position) {
			return 0;
		}

		@Override
		public View getView(final int position, final View convertView, final ViewGroup parent) {

			View rowView;
			if (convertView == null) {
				rowView = inflater.inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
			} else {
				rowView = convertView;
			}

			CheckedTextView textView = (CheckedTextView) rowView.findViewById(android.R.id.text1);
			if (position == 0) {
				textView.setText(R.string.me_all_notes);
			} else if (position == 1) {
				textView.setText(R.string.me_all_highlights);
			} else if (position == 2) {
				textView.setText(R.string.me_all_bookmarks);
			} else if (position == 3) {
				textView.setText("- " + getString(R.string.me_no_labels));
			} else {
				textView.setText("- " + labels.get(position - 4).title);
			}
			textView.setChecked(items.get(position).checks);
			return rowView;
		}

	}
}
