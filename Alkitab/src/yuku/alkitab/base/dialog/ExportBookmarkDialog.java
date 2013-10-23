package yuku.alkitab.base.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckedTextView;
import android.widget.ListView;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.BookmarkListActivity;
import yuku.alkitab.model.Bookmark2;
import yuku.alkitab.model.Label;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.storage.InternalDb;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.debug.R;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ExportBookmarkDialog extends DialogFragment {
	public static final String TAG = ExportBookmarkDialog.class.getSimpleName();

	public static interface Listener {
		void onOk(Uri uri);
	}

	Listener listener;

	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		this.listener = (Listener) activity;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState) {
		final BookmarkAdapter adapter = new BookmarkAdapter();
		adapter.load();

		final AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
		.setNegativeButton(getActivity().getString(R.string.cancel), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				dialog.dismiss();
			}
		})
		.setPositiveButton(getActivity().getString(R.string.ok), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				boolean showNoLabelBookmarks = false;
				List<Label> exportedLabels = new ArrayList<Label>();
				boolean showNotes = false;
				boolean showHighlights = false;
				boolean hasSomethingToExport = false;
				for (ItemAdapter itemAdapter : adapter.items) {
					if (itemAdapter.type == ItemType.notes && itemAdapter.checks) {
						showNotes = true;
						hasSomethingToExport = true;
					} else if (itemAdapter.type == ItemType.highlight && itemAdapter.checks) {
						showHighlights = true;
						hasSomethingToExport = true;
					} else if (itemAdapter.type == ItemType.unlabel && itemAdapter.checks) {
						showNoLabelBookmarks = true;
						hasSomethingToExport = true;
					} else if (itemAdapter.type == ItemType.label && itemAdapter.checks) {
						exportedLabels.add(itemAdapter.label);
						hasSomethingToExport = true;
					}
				}
				if (!hasSomethingToExport) {
					return;
				}

				ExportData data = getAllData(showNoLabelBookmarks, exportedLabels, showNotes, showHighlights);
				final File file = writeHtml(data);

				final Uri uri = new Uri.Builder().scheme("content").authority(getString(R.string.file_provider_authority)).encodedPath("cache/" + file.getName()).build();
				listener.onOk(uri);

				dialog.dismiss();
			}
		})
		.create();

		View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_export, null);

		ListView lsBookmark = V.get(view, R.id.lsBookmark);
		lsBookmark.setAdapter(adapter);
		lsBookmark.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				CheckedTextView checkedTextView = V.get(view, android.R.id.text1);
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

				boolean enabled = false;
				for (ItemAdapter itemAdapter : adapter.items) {
					if (itemAdapter.checks) {
						enabled = true;
						break;
					}
				}
				alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(enabled);
			}
		});
		alertDialog.setView(view);

		return alertDialog;
	}

	private ExportData getAllData(boolean showNoLabelBookmarks, List<Label> exportedlabels, boolean showNotes, boolean showHighlights) {

		List<Bookmark2> noLabelBookmarks = new ArrayList<Bookmark2>();
		List<Pair<Label, List<Bookmark2>>> labeledBookmarks = new ArrayList<Pair<Label, List<Bookmark2>>>();
		List<Bookmark2> notes = new ArrayList<Bookmark2>();
		List<Bookmark2> highlights = new ArrayList<Bookmark2>();

		//no label
		if (showNoLabelBookmarks) {
			Cursor cursor = S.getDb().listBookmarks(Db.Bookmark2.kind_bookmark, BookmarkListActivity.LABELID_noLabel, Db.Bookmark2.addTime, false);
			while (cursor.moveToNext()) {
				Bookmark2 bookmark = InternalDb.bookmark2FromCursor(cursor);
				noLabelBookmarks.add(bookmark);
			}
			cursor.close();
		}

		//with label
		if (exportedlabels.size() > 0) {
			for (Label label : exportedlabels) {
				List<Bookmark2> bookmarks = new ArrayList<Bookmark2>();
				Cursor cursor = S.getDb().listBookmarks(Db.Bookmark2.kind_bookmark, label._id, Db.Bookmark2.addTime, false);
				while (cursor.moveToNext()) {
					Bookmark2 bookmark = InternalDb.bookmark2FromCursor(cursor);
					bookmarks.add(bookmark);
				}
				cursor.close();
				if (bookmarks.size() > 0) {
					Pair<Label, List<Bookmark2>> pair = new Pair<Label, List<Bookmark2>>(label, bookmarks);
					labeledBookmarks.add(pair);
				}
			}
		}

		//notes
		if (showNotes) {
			Cursor cursor = S.getDb().listBookmarks(Db.Bookmark2.kind_note, 0, Db.Bookmark2.addTime, false);
			while (cursor.moveToNext()) {
				Bookmark2 bookmark = InternalDb.bookmark2FromCursor(cursor);
				notes.add(bookmark);
			}
			cursor.close();
		}

		//highlight
		if (showHighlights) {
			Cursor cursor = S.getDb().listBookmarks(Db.Bookmark2.kind_highlight, 0, Db.Bookmark2.addTime, false);
			while (cursor.moveToNext()) {
				Bookmark2 bookmark2 = InternalDb.bookmark2FromCursor(cursor);
				highlights.add(bookmark2);
			}
			cursor.close();
		}

		ExportData exportData = new ExportData();
		exportData.noLabelBookmarks = noLabelBookmarks;
		exportData.labeledBookmarks = labeledBookmarks;
		exportData.notes = notes;
		exportData.highlights = highlights;

		return exportData;
	}

	class ExportData {
		List<Bookmark2> noLabelBookmarks;
		List<Pair<Label, List<Bookmark2>>> labeledBookmarks;
		List<Bookmark2> notes;
		List<Bookmark2> highlights;
	}

	private File writeHtml(ExportData data) {
		List<Bookmark2> noLabelBookmarks = data.noLabelBookmarks;
		List<Pair<Label, List<Bookmark2>>> labeledBookmarks = data.labeledBookmarks;
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
							"</head>\n";
			pw.print(str);

			pw.print("<body>\n");

			if (noLabelBookmarks.size() + labeledBookmarks.size() > 0) {
				pw.print("<h1>" + getString(R.string.me_section_bookmarks) + "</h1>\n<dl id='bookmark_list'>\n");

				//write no label bookmarks
				if (noLabelBookmarks.size() > 0) {
					printBookmarks(pw, null, noLabelBookmarks);
				}

				//write labeled bookmarks
				if (labeledBookmarks.size() > 0) {
					for (Pair<Label, List<Bookmark2>> pair : labeledBookmarks) {
						printBookmarks(pw, pair.first, pair.second);
					}
				}

				pw.print("</dl>\n");
			}

			//write notes
			if (notes.size() > 0) {
				pw.print("<h1>" + getString(R.string.me_section_notes) + "</h1>\n<dl id='note_list'>\n");
				for (Bookmark2 note : notes) {
					String reference = S.activeVersion.reference(note.ari);
					String noteText = TextUtils.htmlEncode(note.caption);
					Log.d(TAG, noteText);
					pw.print("<dt>\n");
					pw.print("<span class='reference' data-ari='" + note.ari + "'>" + reference + "</span>\n");
					printTimes(pw, note);
					pw.print("<span class='text'>" + noteText + "</span>\n");
					pw.print("</dt>\n");
				}
				pw.print("</dl>\n");
			}

			//write highlights
			if (highlights.size() > 0) {
				pw.print("<h1>" + getString(R.string.me_section_highlights) + "</h1>\n");
				pw.print("<dl id='highlight_list'>\n");
				for (Bookmark2 highlight : highlights) {
					String verseText = S.activeVersion.loadVerseText(highlight.ari);
					verseText = U.removeSpecialCodes(verseText);
					String backgroundString = "";
					String colorString = getColorString(U.decodeHighlight(highlight.caption));
					if (colorString != null) {
						backgroundString = " data-bgcolor='" + colorString + "' style='background-color: #" + colorString;
					}
					String reference = S.activeVersion.reference(highlight.ari);

					pw.print("<dt>\n");
					pw.print("<span class='reference' data-ari='" + highlight.ari + "'>" + reference + "</span>\n");
					printTimes(pw, highlight);
					pw.print("<span class='snippet'" + backgroundString + "'>" + verseText + "</span>\n");
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

	private void printBookmarks(final PrintWriter pw, final Label label, final List<Bookmark2> bookmarks) {
		final String labelName;
		final String backgroundString;
		if (label != null) {
			final int backgroundColor = U.decodeLabelBackgroundColor(label.backgroundColor);
			if (backgroundColor != -1) {
				final int foregroundColor = U.getLabelForegroundColorBasedOnBackgroundColor(backgroundColor);
				final String colorString = getColorString(backgroundColor);
				backgroundString = String.format(Locale.US, " data-bgcolor='%s' style='background-color: #%s; color: #%s' ", colorString, colorString, getColorString(foregroundColor));
			} else {
				backgroundString = "";
			}
			labelName = label.title;
		} else {
			labelName = TextUtils.htmlEncode(getString(R.string.me_no_labels));
			backgroundString = "";
		}
		pw.print("<span class='label' " + backgroundString + ">" + labelName + "</span>\n");
		for (Bookmark2 bookmark : bookmarks) {
			String reference = S.activeVersion.reference(bookmark.ari);
			String verseText = S.activeVersion.loadVerseText(bookmark.ari);
			verseText = U.removeSpecialCodes(verseText);
			pw.print("<dt>\n");
			pw.print("<span class='title'>" + TextUtils.htmlEncode(bookmark.caption) + "</span>\n");
			printTimes(pw, bookmark);
			pw.print("<span class='reference' data-ari='" + bookmark.ari + "'>" + reference + "</span> <span class='snippet'>" + verseText + "</span>\n");
			pw.print("</dt>\n");
		}
	}

	private void printTimes(final PrintWriter pw, final Bookmark2 bookmark) {
		pw.print("<span class='times'>\n");
		pw.print("<span class='createTime' data-unixtime='" + Sqlitil.toInt(bookmark.addTime) + "'>" + Sqlitil.toLocaleDateMedium(bookmark.addTime) + "</span>\n");
		pw.print("<span class='modifyTime' data-unixtime='" + Sqlitil.toInt(bookmark.modifyTime) + "'" + (bookmark.addTime.equals(bookmark.modifyTime)? " style='display: none' ": "") + ">" + getString(R.string.me_edited_modify_date, Sqlitil.toLocaleDateMedium(bookmark.modifyTime)) + "</span>\n");
		pw.print("</span><br/>\n");
	}

	private String getColorString(final int color) {
		if (color == -1) {
			return null;
		}
		StringBuilder sb = new StringBuilder(10);
		String h = Integer.toHexString(color);
		for (int x = h.length(); x < 6; x++) {
			sb.append('0');
		}
		sb.append(h);
		return sb.toString();
	}

	public enum ItemType {notes, highlight, allBookmarks, label, unlabel}

	class ItemAdapter {
		ItemType type;
		boolean checks;
		Label label;
	}

	class BookmarkAdapter extends EasyAdapter {

		List<Bookmark2> bookmarks = new ArrayList<Bookmark2>();
		List<Label> labels;
		List<ItemAdapter> items;

		void load() {
			labels = S.getDb().listAllLabels();
			Cursor cursor = S.getDb().listAllBookmarks();
			try {
				while (cursor.moveToNext()) {
					Bookmark2 bookmark = InternalDb.bookmark2FromCursor(cursor);
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
			return 4 + labels.size();
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getActivity().getLayoutInflater().inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			CheckedTextView textView = V.get(view, android.R.id.text1);
			if (position == 0) {
				textView.setText(R.string.me_all_notes);
			} else if (position == 1) {
				textView.setText(R.string.me_all_highlights);
			} else if (position == 2) {
				textView.setText(R.string.me_all_bookmarks);
			} else if (position == 3) {
				textView.setText("– " + getString(R.string.me_no_labels));
			} else {
				textView.setText("– " + labels.get(position - 4).title);
			}
			textView.setChecked(items.get(position).checks);
		}
	}
}
