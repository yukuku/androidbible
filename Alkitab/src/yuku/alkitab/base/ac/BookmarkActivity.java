
package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NavUtils;
import android.support.v4.app.ShareCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.util.Xml;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import yuku.afw.D;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.ChooseBackupFileDialog;
import yuku.alkitab.base.dialog.ExportBookmarkDialog;
import yuku.alkitab.base.dialog.LabelEditorDialog;
import yuku.alkitab.base.dialog.LabelEditorDialog.OkListener;
import yuku.alkitab.base.model.Bookmark2;
import yuku.alkitab.base.model.Label;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.BackupManager;
import yuku.alkitab.base.util.Sqlitil;
import yuku.ambilwarna.AmbilWarnaDialog;
import yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class BookmarkActivity extends BaseActivity implements ExportBookmarkDialog.Listener {
	public static final String TAG = BookmarkActivity.class.getSimpleName();
	
	private static final int REQCODE_bukmakList = 1;
	private static final int REQCODE_share = 2;

	DragSortListView lv;
	
	BookmarkFilterAdapter adapter;
	private TextView tLastBackup;

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_bookmark);
		setTitle(R.string.judul_bukmak_activity);
		
		adapter = new BookmarkFilterAdapter();
		adapter.reload();

		tLastBackup = V.get(this, R.id.tLastBackup);
		updateLastBackup();

		lv = V.get(this, android.R.id.list);
		lv.setDropListener(adapter);
		lv.setOnItemClickListener(lv_click);
		lv.setAdapter(adapter);

        BookmarkFilterController c = new BookmarkFilterController(lv, adapter);
        lv.setFloatViewManager(c);
        lv.setOnTouchListener(c);

		registerForContextMenu(lv);
		
		Intent intent = getIntent();
		if (U.equals(intent.getAction(), Intent.ACTION_VIEW)) {
			Uri data = intent.getData();
			if (data != null && (U.equals(data.getScheme(), "content") || U.equals(data.getScheme(), "file"))) { //$NON-NLS-1$ //$NON-NLS-2$
				try {
					final InputStream inputStream = getContentResolver().openInputStream(data);
					
					final AlertDialog[] dialog = {null};
					dialog[0] = new AlertDialog.Builder(BookmarkActivity.this)
					.setMessage(R.string.apakah_anda_mau_menumpuk_pembatas_buku_dan_catatan_tanya)
					.setNegativeButton(R.string.cancel, null)
					.setNeutralButton(R.string.no, new DialogInterface.OnClickListener() {
						@Override public void onClick(DialogInterface dialog_, int which) {
							dialog[0].setOnDismissListener(null);
							importBookmarks(inputStream, false, true);
						}
					})
					.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
						@Override public void onClick(DialogInterface dialog_, int which) {
							dialog[0].setOnDismissListener(null);
							importBookmarks(inputStream, true, true);
						}
					})
					.show();
					dialog[0].setOnDismissListener(finishActivityListener);

				} catch (FileNotFoundException e) {
					msgbox(getString(R.string.bl_file_not_found_filename, data.toString()))
					.setOnDismissListener(finishActivityListener);
				}
			}
		}
	}

	private void updateLastBackup() {
		long lastBackup = Preferences.getLong(Prefkey.lastBackupDate, 0L);
		if (lastBackup != 0) {
			tLastBackup.setText(getString(R.string.backup_last_time,  Sqlitil.toLocalDateTimeSimple(new Date(lastBackup))));
		} else {
			tLastBackup.setText("");
		}
	}

	private void bikinMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.activity_bookmark, menu);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		bikinMenu(menu);
		
		return true;
	}
	
	@Override public boolean onPrepareOptionsMenu(Menu menu) {
		if (menu != null) {
			bikinMenu(menu);
		}
		
		return true;
	}
	
	AlertDialog msgbox(String message) {
		return new AlertDialog.Builder(this)
		.setMessage(message)
		.setPositiveButton(R.string.ok, null)
		.show();
	}
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.menuImport) {
			List<File> backupFiles = BackupManager.listBackupFiles();
			if (backupFiles.size() == 0) {
				msgbox(getString(R.string.file_tidak_bisa_dibaca_file, BackupManager.getFileDir().getAbsolutePath()));
				return true;
			}
			final FragmentManager fm = getSupportFragmentManager();
			final ChooseBackupFileDialog dialog = ChooseBackupFileDialog.getInstance(backupFiles);
			dialog.setChooseBackupFileListener(new ChooseBackupFileDialog.ChooseBackupFileListener() {
				@Override
				public void onSelect(final File file) {
					dialog.dismiss();
					if (!file.exists() || !file.canRead()) {
						msgbox(getString(R.string.file_tidak_bisa_dibaca_file, file.getAbsolutePath()));
						return;
					}

					try {
						final InputStream inputStream = new FileInputStream(file);
						new AlertDialog.Builder(BookmarkActivity.this)
						.setMessage(R.string.apakah_anda_mau_menumpuk_pembatas_buku_dan_catatan_tanya)
						.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								importBookmarks(inputStream, false, false);
							}
						})
						.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								importBookmarks(inputStream, true, false);
							}
						})
						.show();
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}

				}
			});
			dialog.show(fm, "choose file dialog");

			return true;
		} else if (itemId == R.id.menuExport) {
			if (S.getDb().countAllBookmarks() == 0) {
				msgbox(getString(R.string.no_bookmarks_for_backup));
				return true;
			}
			
			new AlertDialog.Builder(this)
			.setMessage(R.string.ekspor_pembatas_buku_dan_catatan_tanya)
			.setNegativeButton(R.string.no, null)
			.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					exportBookmarks(false);
				}
			})
			.show();
			
			return true;
		} else if (itemId == R.id.menuSendBackup) {
			if (S.getDb().countAllBookmarks() == 0) {
				msgbox(getString(R.string.no_bookmarks_for_backup));
				return true;
			}
			
			new AlertDialog.Builder(this)
			.setMessage(R.string.bl_send_backup_confirmation)
			.setNegativeButton(R.string.no, null)
			.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					exportBookmarks(true);
				}
			})
			.show();
			
			return true;
		} else if (itemId == android.R.id.home) {
			Intent upIntent = new Intent(this, IsiActivity.class);
            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                // This activity is not part of the application's task, so create a new task
                // with a synthesized back stack.
                TaskStackBuilder.create(this).addNextIntent(upIntent).startActivities();
                finish();
            } else {
                // This activity is part of the application's task, so simply
                // navigate up to the hierarchical parent activity.
                // sample code uses this: NavUtils.navigateUpTo(this, upIntent);
            	finish();
            }
			return true;
		} else if (itemId == R.id.menuExportBookmarks) {
			FragmentManager fm = getSupportFragmentManager();
			ExportBookmarkDialog dialog = new ExportBookmarkDialog();
			dialog.show(fm, "export_dialog");
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	public void importBookmarks(final InputStream inputStream, boolean overwriteExisting, final boolean finishActivityAfterwards) {
		new AsyncTask<Boolean, Integer, Object>() {
			ProgressDialog pd;
			int count_bookmark = 0;
			int count_label = 0;
			
			@Override
			protected void onPreExecute() {
				pd = new ProgressDialog(BookmarkActivity.this);
				pd.setMessage(getString(R.string.mengimpor_titiktiga));
				pd.setIndeterminate(true);
				pd.setCancelable(false);
				pd.show();
			}
			
			@Override protected Object doInBackground(Boolean... params) {
				final boolean tumpuk = params[0];
				
				final List<Bookmark2> bookmarks = new ArrayList<Bookmark2>();
				final TObjectIntHashMap<Bookmark2> bookmarkToRelIdMap = new TObjectIntHashMap<Bookmark2>();
				final List<Label> labels = new ArrayList<Label>();
				final TObjectIntHashMap<Label> labelToRelIdMap = new TObjectIntHashMap<Label>();
				final TIntLongHashMap labelRelIdToAbsIdMap = new TIntLongHashMap();
				final TIntObjectHashMap<TIntList> bookmark2RelIdToLabelRelIdsMap = new TIntObjectHashMap<TIntList>();
				
				try {
					InputStream fis;
					
					if (inputStream == null) {
						File in = BackupManager.getFileBackup(false);
						fis = new FileInputStream(in);
					} else {
						fis = inputStream;
					}

					Xml.parse(fis, Xml.Encoding.UTF_8, new DefaultHandler2() {
						@Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
							if (localName.equals(Bookmark2.XMLTAG_Bukmak2)) {
								Bookmark2 bookmark = Bookmark2.fromAttributes(attributes);
								int bookmark2_relId = Bookmark2.getRelId(attributes);
								bookmarks.add(bookmark);
								bookmarkToRelIdMap.put(bookmark, bookmark2_relId);
								count_bookmark++;
							} else if (localName.equals(Label.XMLTAG_Label)) {
								Label label = Label.fromAttributes(attributes);
								int label_relId = Label.getRelId(attributes);
								labels.add(label);
								labelToRelIdMap.put(label, label_relId);
								count_label++;
							} else if (localName.equals(Bookmark2_Label.XMLTAG_Bookmark2_Label)) {
								int bookmark2_relId = Integer.parseInt(attributes.getValue("", Bookmark2_Label.XMLATTR_bookmark2_relId)); //$NON-NLS-1$
								int label_relId = Integer.parseInt(attributes.getValue("", Bookmark2_Label.XMLATTR_label_relId)); //$NON-NLS-1$
								
								TIntList labelRelIds = bookmark2RelIdToLabelRelIdsMap.get(bookmark2_relId);
								if (labelRelIds == null) {
									labelRelIds = new TIntArrayList();
									bookmark2RelIdToLabelRelIdsMap.put(bookmark2_relId, labelRelIds);
								}
								labelRelIds.add(label_relId);
							}
						}
					});
					fis.close();
				} catch (Exception e) {
					return e;
				}
				
				{ // bikin label-label yang diperlukan, juga map relId dengan id dari label.
					HashMap<String, Label> judulMap = new HashMap<String, Label>();
					List<Label> xlabelLama = S.getDb().listAllLabels();
					
					for (Label labelLama: xlabelLama) {
						judulMap.put(labelLama.title, labelLama);
					}
					
					for (Label label: labels) {
						// cari apakah label yang judulnya persis sama udah ada
						Label labelLama = judulMap.get(label.title);
						if (labelLama != null) {
							// update warna label lama
							if (tumpuk && label.backgroundColor != null && label.backgroundColor.length() > 0) {
								labelLama.backgroundColor = label.backgroundColor;
								S.getDb().updateLabel(labelLama);
							}
							labelRelIdToAbsIdMap.put(labelToRelIdMap.get(label), labelLama._id);
							Log.d(TAG, "label (lama) r->a : " + labelToRelIdMap.get(label) + "->" + labelLama._id); //$NON-NLS-1$ //$NON-NLS-2$
						} else { // belum ada, harus bikin baru
							Label labelBaru = S.getDb().insertLabel(label.title, label.backgroundColor);
							labelRelIdToAbsIdMap.put(labelToRelIdMap.get(label), labelBaru._id);
							Log.d(TAG, "label (baru) r->a : " + labelToRelIdMap.get(label) + "->" + labelBaru._id); //$NON-NLS-1$ //$NON-NLS-2$
						}
					}
				}
				
				S.getDb().importBookmarks(bookmarks, tumpuk, bookmarkToRelIdMap, labelRelIdToAbsIdMap, bookmark2RelIdToLabelRelIdsMap);
			
				return null;
			}

			@Override protected void onPostExecute(Object result) {
				pd.dismiss();
				
				AlertDialog dialog;
				if (result instanceof Exception) {
					dialog = msgbox(getString(R.string.terjadi_kesalahan_ketika_mengimpor_pesan, ((Exception) result).getMessage()));
				} else {
					dialog = msgbox(getString(R.string.impor_berhasil_angka_diproses, count_bookmark, count_label));
				}
				
				if (finishActivityAfterwards) {
					dialog.setOnDismissListener(finishActivityListener);
				}
				
				adapter.reload();
			}
		}.execute((Boolean)overwriteExisting);
	}
	
	public void exportBookmarks(final boolean sendBackup) {
		BackupManager.backupBookmarks(false, new BackupManager.BackupListener() {
			ProgressDialog pd;

			@Override
			public void onBackupPreExecute() {
				pd = new ProgressDialog(BookmarkActivity.this);
				pd.setMessage(getString(R.string.mengekspor_titiktiga));
				pd.setIndeterminate(true);
				pd.setCancelable(false);
				pd.show();
			}

			@Override
			public void onBackupPostExecute(final Object result) {
				pd.dismiss();

				if (result instanceof String) {
					updateLastBackup();
					if (!sendBackup) {
						msgbox(getString(R.string.ekspor_berhasil_file_yang_dihasilkan_file, result));
					} else {
						Uri uri = Uri.fromFile(new File((String) result));

						Intent intent = ShareCompat.IntentBuilder.from(BookmarkActivity.this)
						.setStream(uri)
						.setType("text/xml") //$NON-NLS-1$
						.createChooserIntent();

						startActivity(intent);
					}
				} else if (result instanceof Exception) {
					msgbox(getString(R.string.terjadi_kesalahan_ketika_mengekspor_pesan, ((Exception) result).getMessage()));
				}
			}
		});
	}

	@Override
	public void onOk(final Uri uri) {
		Log.d(TAG, "Uri for sharing: " + uri);

		final Intent intent = ShareCompat.IntentBuilder.from(this)
		.setStream(uri)
		.setType("text/html")
		.getIntent();

		startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.me_export_markers)), REQCODE_share);
	}


	// constants
	public static class Bookmark2_Label { // DO NOT CHANGE CONSTANT VALUES!
		public static final String XMLTAG_Bookmark2_Label = "Bukmak2_Label"; //$NON-NLS-1$
		public static final String XMLATTR_bookmark2_relId = "bukmak2_relId"; //$NON-NLS-1$
		public static final String XMLATTR_label_relId = "label_relId"; //$NON-NLS-1$
	}
	
	private OnItemClickListener lv_click = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			Intent intent;
			if (position == 0) {
				intent = BookmarkListActivity.createIntent(getApplicationContext(), Db.Bookmark2.kind_bookmark, 0);
			} else if (position == 1) {
				intent = BookmarkListActivity.createIntent(getApplicationContext(), Db.Bookmark2.kind_note, 0);
			} else if (position == 2) {
				intent = BookmarkListActivity.createIntent(getApplicationContext(), Db.Bookmark2.kind_highlight, 0);
			} else if (position == 3) {
				intent = BookmarkListActivity.createIntent(getApplicationContext(), Db.Bookmark2.kind_bookmark, BookmarkListActivity.LABELID_noLabel);
			} else {
				Label label = adapter.getItem(position);
				if (label != null) {
					intent = BookmarkListActivity.createIntent(getApplicationContext(), Db.Bookmark2.kind_bookmark, label._id);
				} else {
					return;
				}
			}
			startActivityForResult(intent, REQCODE_bukmakList);
		}
	};

	DialogInterface.OnDismissListener finishActivityListener = new DialogInterface.OnDismissListener() {
		@Override public void onDismiss(DialogInterface dialog) {
			finish();
		}
	};
	
	@Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {

		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		if (info.position >= 4) {
			getMenuInflater().inflate(R.menu.context_bookmark, menu);
		}
	}

	@Override public boolean onContextItemSelected(android.view.MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

		int itemId = item.getItemId();
		if (itemId == R.id.menuRenameLabel) {
			final Label label = adapter.getItem(info.position);
			if (label == null) {
				return true;
			}
			
			LabelEditorDialog.show(this, label.title, getString(R.string.rename_label_title), new OkListener() {
				@Override public void onOk(String judul) {
					label.title = judul;
					S.getDb().updateLabel(label);
					adapter.notifyDataSetChanged();
				}
			});

			return true;
		} else if (itemId == R.id.menuDeleteLabel) {
			final Label label = adapter.getItem(info.position);
			if (label == null) {
				return true;
			}
			
			int nbukmak = S.getDb().countBookmarksWithLabel(label);

			if (nbukmak == 0) {
				// tiada, langsung hapus aja!
				S.getDb().deleteLabelById(label._id);
				adapter.reload();
			} else {
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.are_you_sure_you_want_to_delete_the_label_label, label.title, nbukmak))
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						S.getDb().deleteLabelById(label._id);
						adapter.reload();
					}
				})
				.show();
			}
			
			return true;
		} else if (itemId == R.id.menuChangeLabelColor) {
			final Label label = adapter.getItem(info.position);
			if (label == null) {
				return true;
			}
			
			int warnaLatarRgb = U.decodeLabelBackgroundColor(label.backgroundColor);
			new AmbilWarnaDialog(BookmarkActivity.this, 0xff000000 | warnaLatarRgb, new OnAmbilWarnaListener() {
				@Override public void onOk(AmbilWarnaDialog dialog, int color) {
					if (color == -1) {
						label.backgroundColor = null;
					} else {
						label.backgroundColor = U.encodeLabelBackgroundColor(0x00ffffff & color);
					}
					S.getDb().updateLabel(label);
					adapter.notifyDataSetChanged();
				}
				
				@Override public void onCancel(AmbilWarnaDialog dialog) {
					// nop
				}
			}).show();
			
			return true;
		}

		return false;
	}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_bukmakList) {
			adapter.reload();
		} else if (requestCode == REQCODE_share && resultCode == RESULT_OK) {
			final ShareActivity.Result result = ShareActivity.obtainResult(data);
			startActivity(result.chosenIntent);
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	private class BookmarkFilterController extends DragSortController {
		int mDivPos;
		int mDraggedPos;
		final DragSortListView lv;

		public BookmarkFilterController(DragSortListView lv, BookmarkFilterAdapter adapter) {
			super(lv, R.id.drag_handle, DragSortController.ON_DOWN, 0);

			this.lv = lv;

			mDivPos = adapter.getDivPosition();
			setRemoveEnabled(false);
		}

		@Override public int startDragPosition(MotionEvent ev) {
			int res = super.dragHandleHitPosition(ev);
			if (res < mDivPos) {
				return DragSortController.MISS;
			}

			return res;
		}

		@Override public View onCreateFloatView(int position) {
			mDraggedPos = position;
			View res = adapter.getView(position, null, lv);
			res.setBackgroundColor(0x22ffffff);
			return res;
		}
		
		@Override public void onDestroyFloatView(View floatView) {
			// Do not call super and do not remove this override.
			floatView.setBackgroundColor(0);
		}

		@Override public void onDragFloatView(View floatView, Point floatPoint, Point touchPoint) {
			super.onDragFloatView(floatView, floatPoint, touchPoint);

			final int first = lv.getFirstVisiblePosition();
			final int lvDivHeight = lv.getDividerHeight();

			View div = lv.getChildAt(mDivPos - first - 1);

			if (div != null) {
				if (mDraggedPos >= mDivPos) {
					// don't allow floating View to go above section divider
					final int limit = div.getBottom() + lvDivHeight;
					if (floatPoint.y < limit) {
						floatPoint.y = limit;
					}
				}
			}
		}
	}

	private class BookmarkFilterAdapter extends BaseAdapter implements DragSortListView.DropListener {
		// 0. [icon] All bookmarks
		// 1. [icon] Notes
		// 2. [icon] Highlights
		// 3. Unlabeled bookmarks
		// 4. dst label2

		List<Label> labels;
		
		private String[] presetCaptions = {
			getString(R.string.bmcat_all_bookmarks),
			getString(R.string.bmcat_notes),
			getString(R.string.bmcat_highlights),
			getString(R.string.bmcat_unlabeled_bookmarks),
		};
		
		@Override public Label getItem(int position) {
			if (position < 4) return null;
			return labels.get(position - 4);
		}

		@Override public long getItemId(int position) {
			return position;
		}

		@Override public void drop(int from, int to) {
			if (from != to) {
				Label fromLabel = getItem(from);
				Label toLabel = getItem(to);
				
				if (fromLabel != null && toLabel != null) {
					S.getDb().reorderLabels(fromLabel, toLabel);
					adapter.reload();
				}
			}
		}

		private boolean hasLabels() {
			return labels != null && labels.size() > 0;
		}

		@Override public int getCount() {
			return 3 + (hasLabels() ? 1 + labels.size() : 0);
		}

		public int getDivPosition() {
			return 4;
		}

		@Override public View getView(int position, View convertView, ViewGroup parent) {
			View res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_bookmark_filter, null);
			
			ImageView imgFilterIcon = V.get(res, R.id.imgFilterIcon);
			if (position < 3) {
				imgFilterIcon.setVisibility(View.VISIBLE);
				imgFilterIcon.setImageResource(position == 0? R.drawable.ic_attr_bookmark: position == 1? R.drawable.ic_attr_note: position == 2? R.drawable.ic_attr_highlight: 0);
			} else {
				imgFilterIcon.setVisibility(View.GONE);
			}
			
			TextView lFilterCaption = V.get(res, R.id.lFilterCaption);
			if (position < 4) {
				lFilterCaption.setVisibility(View.VISIBLE);
				lFilterCaption.setText(presetCaptions[position]);
			} else {
				lFilterCaption.setVisibility(View.GONE);
			}
			
			TextView lFilterLabel = V.get(res, R.id.lFilterLabel);
			if (position < 4) {
				lFilterLabel.setVisibility(View.GONE);
			} else {
				Label label = getItem(position);
				lFilterLabel.setVisibility(View.VISIBLE);
				lFilterLabel.setText(label.title);
				
				U.applyLabelColor(label, lFilterLabel);
			}
			
			View drag_handle = V.get(res, R.id.drag_handle);
			if (position < 4) {
				drag_handle.setVisibility(View.GONE);
			} else {
				drag_handle.setVisibility(View.VISIBLE);
			}
			
			return res;
		}
		
		void reload() {
			labels = S.getDb().listAllLabels();
			
			if (D.EBUG) {
				Log.d(TAG, "_id  title                ordering backgroundColor");
				for (Label label: labels) {
					Log.d(TAG, String.format("%4d %20s %8d %s", label._id, label.title, label.ordering, label.backgroundColor));
				}
			}
			
			notifyDataSetChanged();
		}
	}
}
