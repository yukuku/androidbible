
package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
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
import yuku.afw.D;
import yuku.afw.V;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.LabelEditorDialog;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.ambilwarna.AmbilWarnaDialog;
import yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener;

import java.util.List;

public class MarkersActivity extends BaseActivity {
	public static final String TAG = MarkersActivity.class.getSimpleName();
	
	private static final int REQCODE_markerList = 1;
	private static final int REQCODE_share = 2;

	/** Action to broadcast when label list needs to be reloaded due to some background changes */
	public static final String ACTION_RELOAD = MarkersActivity.class.getName() + ".action.RELOAD";

	DragSortListView lv;
	
	BookmarkFilterAdapter adapter;

	public static Intent createIntent() {
		return new Intent(App.context, MarkersActivity.class);
	}

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_markers);
		setTitle(R.string.activity_title_markers);
		
		adapter = new BookmarkFilterAdapter();
		adapter.reload();

		lv = V.get(this, android.R.id.list);
		lv.setDropListener(adapter);
		lv.setOnItemClickListener(lv_click);
		lv.setAdapter(adapter);

        BookmarkFilterController c = new BookmarkFilterController(lv, adapter);
        lv.setFloatViewManager(c);
        lv.setOnTouchListener(c);

		registerForContextMenu(lv);

		App.getLbm().registerReceiver(br, new IntentFilter(ACTION_RELOAD));
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
				S.getDb().deleteLabelById(label._id);
				adapter.reload();
			} else {
				new AlertDialog.Builder(this)
				.setMessage(getString(R.string.are_you_sure_you_want_to_delete_the_label_label, label.title, marker_count))
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok, (dialog, which) -> {
					S.getDb().deleteLabelById(label._id);
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
			
			int warnaLatarRgb = U.decodeLabelBackgroundColor(label.backgroundColor);
			new AmbilWarnaDialog(MarkersActivity.this, 0xff000000 | warnaLatarRgb, new OnAmbilWarnaListener() {
				@Override public void onOk(AmbilWarnaDialog dialog, int color) {
					if (color == -1) {
						label.backgroundColor = null;
					} else {
						label.backgroundColor = U.encodeLabelBackgroundColor(0x00ffffff & color);
					}
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
			final View res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_marker_filter, parent, false);
			
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
