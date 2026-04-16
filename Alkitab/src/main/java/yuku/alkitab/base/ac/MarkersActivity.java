
package yuku.alkitab.base.ac;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kotlin.Unit;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.LabelEditorDialog;
import yuku.alkitab.base.sync.SyncSettingsActivity;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.LabelColorUtil;
import yuku.alkitab.base.widget.MaterialDialogJavaHelper;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.ambilwarna.AmbilWarnaDialog;

public class MarkersActivity extends BaseActivity {
    static final String TAG = MarkersActivity.class.getSimpleName();

    private static final int REQCODE_markerList = 1;

    /** Number of fixed preset filters shown before user labels. */
    private static final int PRESET_COUNT = 4;

    /**
     * Action to broadcast when label list needs to be reloaded due to some background changes
     */
    public static final String ACTION_RELOAD = MarkersActivity.class.getName() + ".action.RELOAD";

    RecyclerView lv;
    View bGotoSync;

    MarkerFilterAdapter adapter;
    ItemTouchHelper itemTouchHelper;

    public static Intent createIntent() {
        return new Intent(App.context, MarkersActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        lv.setLayoutManager(new LinearLayoutManager(this));
        lv.setAdapter(adapter);

        itemTouchHelper = new ItemTouchHelper(new LabelReorderCallback());
        itemTouchHelper.attachToRecyclerView(lv);

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
        int itemId = item.getItemId();
        if (itemId == R.id.menuLabelSort) {
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

    private void onItemClick(int position) {
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
            if (label == null) {
                return;
            }
            intent = MarkerListActivity.createIntent(getApplicationContext(), Marker.Kind.bookmark, label._id);
        }
        startActivityForResult(intent, REQCODE_markerList);
    }

    private void showLabelPopupMenu(View anchor, int position) {
        if (position < PRESET_COUNT) return;
        final Label label = adapter.getItem(position);
        if (label == null) return;

        final PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.context_markers, popup.getMenu());
        popup.setOnMenuItemClickListener(menuItem -> onLabelMenuItemSelected(menuItem, label));
        popup.show();
    }

    private boolean onLabelMenuItemSelected(MenuItem item, Label label) {
        int itemId = item.getItemId();
        if (itemId == R.id.menuRenameLabel) {
            LabelEditorDialog.show(this, label.title, getString(R.string.rename_label_title), title -> {
                label.title = title;
                S.getDb().insertOrUpdateLabel(label);
                adapter.notifyDataSetChanged();
            });
            return true;
        } else if (itemId == R.id.menuDeleteLabel) {
            final int marker_count = S.getDb().countMarkersWithLabel(label);

            if (marker_count == 0) {
                // no markers, just delete straight away
                S.getDb().deleteLabelAndMarker_LabelsByLabelId(label._id);
                adapter.reload();
            } else {
                MaterialDialogJavaHelper.showOkDialog(
                    this,
                    getString(R.string.are_you_sure_you_want_to_delete_the_label_label, label.title, "" + marker_count),
                    getString(R.string.delete),
                    () -> {
                        S.getDb().deleteLabelAndMarker_LabelsByLabelId(label._id);
                        adapter.reload();
                        return Unit.INSTANCE;
                    },
                    getString(R.string.cancel)
                );
            }

            return true;
        } else if (itemId == R.id.menuChangeLabelColor) {
            int colorRgb = LabelColorUtil.decodeBackground(label.backgroundColor);
            new AmbilWarnaDialog(MarkersActivity.this, 0xff000000 | colorRgb, new AmbilWarnaDialog.OnAmbilWarnaListener() {
                @Override
                public void onOk(AmbilWarnaDialog dialog, int color) {
                    label.backgroundColor = LabelColorUtil.encodeBackground(0x00ffffff & color);

                    S.getDb().insertOrUpdateLabel(label);
                    adapter.notifyDataSetChanged();
                }

                @Override
                public void onCancel(AmbilWarnaDialog dialog) {
                    // nop
                }
            }).show();

            return true;
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQCODE_markerList) {
            adapter.reload();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private class LabelReorderCallback extends ItemTouchHelper.Callback {
        /** Snapshot of `labels` captured at drag start, used to resolve the destination label on drop. */
        private List<Label> dragStartSnapshot;
        /** Starting adapter position of the dragged viewHolder. */
        private int dragStartPos = RecyclerView.NO_POSITION;

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            // Only user labels (position >= PRESET_COUNT) can be dragged.
            if (viewHolder.getBindingAdapterPosition() < PRESET_COUNT) {
                return 0;
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false; // drag is initiated by touching the drag handle
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public boolean canDropOver(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder current, @NonNull RecyclerView.ViewHolder target) {
            // Prevent dropping a user label into the preset-filter section.
            return target.getBindingAdapterPosition() >= PRESET_COUNT;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
            final int from = viewHolder.getBindingAdapterPosition();
            final int to = target.getBindingAdapterPosition();
            if (from < PRESET_COUNT || to < PRESET_COUNT) return false;
            adapter.moveItemLocally(from, to);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            // unused
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                viewHolder.itemView.setBackgroundColor(0x22ffffff);
                dragStartPos = viewHolder.getBindingAdapterPosition();
                dragStartSnapshot = new ArrayList<>(adapter.labels);
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setBackgroundColor(0);

            final int endPos = viewHolder.getBindingAdapterPosition();
            final List<Label> snapshot = dragStartSnapshot;
            final int startPos = dragStartPos;
            dragStartSnapshot = null;
            dragStartPos = RecyclerView.NO_POSITION;

            if (snapshot == null || startPos == RecyclerView.NO_POSITION || endPos == RecyclerView.NO_POSITION) return;
            if (startPos == endPos) return;

            final int fromIdx = startPos - PRESET_COUNT;
            final int toIdx = endPos - PRESET_COUNT;
            if (fromIdx < 0 || fromIdx >= snapshot.size() || toIdx < 0 || toIdx >= snapshot.size()) return;

            final Label fromLabel = snapshot.get(fromIdx);
            final Label toLabel = snapshot.get(toIdx);
            S.getDb().reorderLabels(fromLabel, toLabel);
            adapter.reload();
        }
    }

    class LabelViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgFilterIcon;
        final TextView lFilterCaption;
        final TextView lFilterLabel;
        final View drag_handle;

        LabelViewHolder(@NonNull View itemView) {
            super(itemView);
            imgFilterIcon = itemView.findViewById(R.id.imgFilterIcon);
            lFilterCaption = itemView.findViewById(R.id.lFilterCaption);
            lFilterLabel = itemView.findViewById(R.id.lFilterLabel);
            drag_handle = itemView.findViewById(R.id.drag_handle);

            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(pos);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos >= PRESET_COUNT) {
                    showLabelPopupMenu(v, pos);
                    return true;
                }
                return false;
            });

            drag_handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(this);
                }
                return false;
            });
        }
    }

    private class MarkerFilterAdapter extends RecyclerView.Adapter<LabelViewHolder> {
        // 0. [icon] All bookmarks
        // 1. [icon] Notes
        // 2. [icon] Highlights
        // 3. Unlabeled bookmarks
        // 4 and so on. labels

        List<Label> labels = new ArrayList<>();

        private final String[] presetCaptions = {
            getString(R.string.bmcat_all_bookmarks),
            getString(R.string.bmcat_notes),
            getString(R.string.bmcat_highlights),
            getString(R.string.bmcat_unlabeled_bookmarks),
        };

        MarkerFilterAdapter() {
        }

        public Label getItem(int position) {
            if (position < PRESET_COUNT) return null;
            int idx = position - PRESET_COUNT;
            if (idx < 0 || idx >= labels.size()) return null;
            return labels.get(idx);
        }

        private boolean hasLabels() {
            return !labels.isEmpty();
        }

        @Override
        public int getItemCount() {
            return 3 + (hasLabels() ? 1 + labels.size() : 0);
        }

        @NonNull
        @Override
        public LabelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_marker_filter, parent, false);
            return new LabelViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LabelViewHolder h, int position) {
            if (position < 3) {
                h.imgFilterIcon.setVisibility(View.VISIBLE);
                h.imgFilterIcon.setImageResource(position == 0 ? R.drawable.ic_attr_bookmark : position == 1 ? R.drawable.ic_attr_note : R.drawable.ic_attr_highlight);
            } else {
                h.imgFilterIcon.setVisibility(View.GONE);
            }

            if (position < PRESET_COUNT) {
                h.lFilterCaption.setVisibility(View.VISIBLE);
                h.lFilterCaption.setText(presetCaptions[position]);
            } else {
                h.lFilterCaption.setVisibility(View.GONE);
            }

            if (position < PRESET_COUNT) {
                h.lFilterLabel.setVisibility(View.GONE);
            } else {
                Label label = getItem(position);
                h.lFilterLabel.setVisibility(View.VISIBLE);
                h.lFilterLabel.setText(label.title);

                LabelColorUtil.apply(label, h.lFilterLabel);
            }

            h.drag_handle.setVisibility(position < PRESET_COUNT ? View.GONE : View.VISIBLE);
        }

        /** Reorder within the local list during a drag. Persistence happens once on drop. */
        void moveItemLocally(int from, int to) {
            int fromIdx = from - PRESET_COUNT;
            int toIdx = to - PRESET_COUNT;
            if (fromIdx < 0 || fromIdx >= labels.size() || toIdx < 0 || toIdx >= labels.size()) return;

            Label moved = labels.remove(fromIdx);
            labels.add(toIdx, moved);
            notifyItemMoved(from, to);
        }

        void reload() {
            labels = S.getDb().listAllLabels();

            if (BuildConfig.DEBUG) {
                AppLog.d(TAG, "_id  title                ordering backgroundColor");
                for (Label label : labels) {
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
