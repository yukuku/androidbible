
package yuku.alkitab.base.ac;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
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
import com.afollestad.materialdialogs.MaterialDialog;
import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.LabelEditorDialog;
import yuku.alkitab.base.sync.SyncSettingsActivity;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.BookmarkImporter;
import yuku.alkitab.base.util.LabelColorUtil;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.ambilwarna.AmbilWarnaDialog;
import yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener;
import yuku.filechooser.FileChooserActivity;
import yuku.filechooser.FileChooserConfig;
import yuku.filechooser.FileChooserResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MarkersActivity extends BaseActivity {
	public static final String TAG = MarkersActivity.class.getSimpleName();
	
	private static final int REQCODE_markerList = 1;
	private static final int REQCODE_share = 2;
	private static final int REQCODE_migrateFromV3 = 3;

	/** Action to broadcast when label list needs to be reloaded due to some background changes */
	public static final String ACTION_RELOAD = MarkersActivity.class.getName() + ".action.RELOAD";

	DragSortListView lv;
    View bGotoSync;
	
	MarkerFilterAdapter adapter;

	public static Intent createIntent() {
		return new Intent(App.context, MarkersActivity.class);
	}

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_markers);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);

		adapter = new MarkerFilterAdapter();
		adapter.reload();

		lv = findViewById(android.R.id.list);
		lv.setDropListener(adapter);
		lv.setOnItemClickListener(lv_click);
		lv.setAdapter(adapter);

        MarkerFilterController c = new MarkerFilterController(lv, adapter);
        lv.setFloatViewManager(c);
        lv.setOnTouchListener(c);

		registerForContextMenu(lv);

		bGotoSync = findViewById(R.id.bGotoSync);
        bGotoSync.setOnClickListener(v -> startActivity(SyncSettingsActivity.createIntent()));

		App.getLbm().registerReceiver(br, new IntentFilter(ACTION_RELOAD));
	}

	@Override
	protected void onStart() {
		super.onStart();

		// hide sync button if we are already syncing
		final String syncAccountName = Preferences.getString(R.string.pref_syncAccountName_key);
		findViewById(R.id.panelGotoSync).setVisibility(syncAccountName != null ? View.GONE : View.VISIBLE);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_markers, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		final MenuItem menuLabelSort = menu.findItem(R.id.menuLabelSort);

		final int labelCount = adapter.getLabelCount();
		menuLabelSort.setVisible(labelCount > 1);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menuMigrateFromV3: {
				final FileChooserConfig config = new FileChooserConfig();
				config.mode = FileChooserConfig.Mode.Open;
				config.pattern = "(yuku\\.alkitab|yuku\\.alkitab\\.kjv|org\\.sabda\\.alkitab|org\\.sabda\\.online)-(backup|autobackup-[0-9-]+)\\.xml";
				config.title = getString(R.string.marker_migrate_file_chooser_title);
				final Intent intent = FileChooserActivity.createIntent(this, config);
				startActivityForResult(intent, REQCODE_migrateFromV3);
			} return true;
			case R.id.menuLabelSort:
				S.getDb().sortLabelsAlphabetically();
				adapter.reload();
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		App.getLbm().unregisterReceiver(br);
	}

	BroadcastReceiver br = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			if (ACTION_RELOAD.equals(intent.getAction())) {
				adapter.reload();
			}
		}
	};

	private OnItemClickListener lv_click = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			Intent intent;
			if (position == 0) {
				intent = MarkerListActivity.createIntent(App.context, Marker.Kind.bookmark, 0);
			} else if (position == 1) {
				intent = MarkerListActivity.createIntent(App.context, Marker.Kind.note, 0);
			} else if (position == 2) {
				intent = MarkerListActivity.createIntent(App.context, Marker.Kind.highlight, 0);
			} else if (position == 3) {
				intent = MarkerListActivity.createIntent(App.context, Marker.Kind.bookmark, MarkerListActivity.LABELID_noLabel);
			} else {
				Label label = adapter.getItem(position);
				if (label != null) {
					intent = MarkerListActivity.createIntent(getApplicationContext(), Marker.Kind.bookmark, label._id);
				} else {
					return;
				}
			}
			startActivityForResult(intent, REQCODE_markerList);
		}
	};

	@Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {

		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		if (info.position >= 4) {
			getMenuInflater().inflate(R.menu.context_markers, menu);
		}
	}

	@Override public boolean onContextItemSelected(android.view.MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

		int itemId = item.getItemId();
		if (itemId == R.id.menuRenameLabel) {
			final Label label = adapter.getItem(info.position);
			if (label == null) {
				return true;
			}
			
			LabelEditorDialog.show(this, label.title, getString(R.string.rename_label_title), title -> {
				label.title = title;
				S.getDb().insertOrUpdateLabel(label);
				adapter.notifyDataSetChanged();
			});

			return true;
		} else if (itemId == R.id.menuDeleteLabel) {
			final Label label = adapter.getItem(info.position);
			if (label == null) {
				return true;
			}
			
			final int marker_count = S.getDb().countMarkersWithLabel(label);

			if (marker_count == 0) {
				// no markers, just delete straight away
				S.getDb().deleteLabelAndMarker_LabelsByLabelId(label._id);
				adapter.reload();
			} else {
				new MaterialDialog.Builder(this)
					.content(getString(R.string.are_you_sure_you_want_to_delete_the_label_label, label.title, marker_count))
					.negativeText(R.string.cancel)
					.positiveText(R.string.delete)
					.onPositive((dialog, which) -> {
						S.getDb().deleteLabelAndMarker_LabelsByLabelId(label._id);
						adapter.reload();
					})
					.show();
			}
			
			return true;
		} else if (itemId == R.id.menuChangeLabelColor) {
			final Label label = adapter.getItem(info.position);
			if (label == null) {
				return true;
			}
			
			int colorRgb = LabelColorUtil.decodeBackground(label.backgroundColor);
			new AmbilWarnaDialog(MarkersActivity.this, 0xff000000 | colorRgb, new OnAmbilWarnaListener() {
				@Override public void onOk(AmbilWarnaDialog dialog, int color) {
					label.backgroundColor = LabelColorUtil.encodeBackground(0x00ffffff & color);

					S.getDb().insertOrUpdateLabel(label);
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
		if (requestCode == REQCODE_markerList) {
			adapter.reload();
			return;
		} else if (requestCode == REQCODE_share && resultCode == RESULT_OK) {
			final ShareActivity.Result result = ShareActivity.obtainResult(data);
			startActivity(result.chosenIntent);
			return;
		} else if (requestCode == REQCODE_migrateFromV3 && resultCode == RESULT_OK) {
			final FileChooserResult result = FileChooserActivity.obtainResult(data);
			if (result != null) {
				final File file = new File(result.firstFilename);
				try {
					final FileInputStream fis = new FileInputStream(file);
					BookmarkImporter.importBookmarks(this, fis, false, () -> adapter.reload());
				} catch (IOException e) {
					new MaterialDialog.Builder(this)
						.content(R.string.marker_migrate_error_opening_backup_file)
						.positiveText(R.string.ok)
						.show();
				}
			}
			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	private class MarkerFilterController extends DragSortController {
		int mDivPos;
		int mDraggedPos;
		final DragSortListView lv;

		public MarkerFilterController(DragSortListView lv, MarkerFilterAdapter adapter) {
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
			final View res = adapter.getView(position, null, lv);
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

	private class MarkerFilterAdapter extends BaseAdapter implements DragSortListView.DropListener {
		// 0. [icon] All bookmarks
		// 1. [icon] Notes
		// 2. [icon] Highlights
		// 3. Unlabeled bookmarks
		// 4 and so on. labels

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
			final View res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_marker_filter, parent, false);

			ImageView imgFilterIcon = res.findViewById(R.id.imgFilterIcon);
			if (position < 3) {
				imgFilterIcon.setVisibility(View.VISIBLE);
				imgFilterIcon.setImageResource(position == 0? R.drawable.ic_attr_bookmark: position == 1? R.drawable.ic_attr_note: position == 2? R.drawable.ic_attr_highlight: 0);
			} else {
				imgFilterIcon.setVisibility(View.GONE);
			}

			TextView lFilterCaption = res.findViewById(R.id.lFilterCaption);
			if (position < 4) {
				lFilterCaption.setVisibility(View.VISIBLE);
				lFilterCaption.setText(presetCaptions[position]);
			} else {
				lFilterCaption.setVisibility(View.GONE);
			}

			TextView lFilterLabel = res.findViewById(R.id.lFilterLabel);
			if (position < 4) {
				lFilterLabel.setVisibility(View.GONE);
			} else {
				Label label = getItem(position);
				lFilterLabel.setVisibility(View.VISIBLE);
				lFilterLabel.setText(label.title);

				LabelColorUtil.apply(label, lFilterLabel);
			}

			View drag_handle = res.findViewById(R.id.drag_handle);
			if (position < 4) {
				drag_handle.setVisibility(View.GONE);
			} else {
				drag_handle.setVisibility(View.VISIBLE);
			}
			
			return res;
		}
		
		void reload() {
			labels = S.getDb().listAllLabels();
			
			if (BuildConfig.DEBUG) {
				AppLog.d(TAG, "_id  title                ordering backgroundColor");
				for (Label label: labels) {
					AppLog.d(TAG, String.format(Locale.US, "%4d %20s %8d %s", label._id, label.title, label.ordering, label.backgroundColor));
				}
			}
			
			notifyDataSetChanged();
			supportInvalidateOptionsMenu();
		}

		public int getLabelCount() {
			return labels.size();
		}
	}
}
