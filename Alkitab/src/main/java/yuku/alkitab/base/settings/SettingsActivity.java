package yuku.alkitab.base.settings;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.sync.SyncSettingsActivity;
import yuku.alkitab.debug.R;

public class SettingsActivity extends BaseActivity {
    private static final String EXTRA_subClassName = "subClassName";

    static class Header {
        int titleResId;
        Class<? extends PreferenceFragmentCompat> clazz;
        Intent clickIntent;

        public Header(final int titleResId, final Class<? extends PreferenceFragmentCompat> clazz, final Intent clickIntent) {
            this.titleResId = titleResId;
            this.clazz = clazz;
            this.clickIntent = clickIntent;
        }
    }

    static Header[] headers = {
        new Header(R.string.pref_sync_title, null, new Intent(App.context, SyncSettingsActivity.class)),
        new Header(R.string.pref_penampilan_layar, DisplayFragment.class, null),
        new Header(R.string.pref_penggunaan, UsageFragment.class, null),
        new Header(R.string.pref_copy_share, CopyShareFragment.class, null),
        new Header(R.string.pref_data_transfer, DataTransferFragment.class, null),
    };

    RecyclerView lsHeaders;
    HeadersAdapter headersAdapter;

    public static Intent createIntent() {
        return new Intent(App.context, SettingsActivity.class);
    }

    static Intent createSubIntent(Class<? extends PreferenceFragmentCompat> subClass) {
        return new Intent(App.context, SettingsActivity.class)
            .putExtra(EXTRA_subClassName, subClass.getName());
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar ab = getSupportActionBar();
        assert ab != null;
        ab.setDisplayHomeAsUpEnabled(true);

        final String subClassName = getIntent().getStringExtra(EXTRA_subClassName);
        if (subClassName == null) {
            lsHeaders = findViewById(R.id.lsHeaders);
            lsHeaders.setLayoutManager(new LinearLayoutManager(this));
            lsHeaders.setAdapter(headersAdapter = new HeadersAdapter());
        } else {
            final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.fragment_container, Fragment.instantiate(this, subClassName), subClassName);
            ft.commit();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;

        public VH(final View itemView) {
            super(itemView);
            title = itemView.findViewById(android.R.id.title);
        }
    }

    class HeadersAdapter extends RecyclerView.Adapter<VH> {
        final TypedValue tv = new TypedValue();

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            final View v = getLayoutInflater().inflate(R.layout.preference_header_item_material, parent, false);
            getTheme().resolveAttribute(R.attr.selectableItemBackground, tv, true);
            v.setBackgroundResource(tv.resourceId);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(final VH holder, final int position) {
            final Header header = headers[position];
            holder.title.setText(header.titleResId);
            holder.itemView.setOnClickListener(v -> {
                if (header.clickIntent != null) {
                    startActivity(header.clickIntent);
                } else if (header.clazz != null) {
                    startActivity(createSubIntent(header.clazz));
                }
            });
        }

        @Override
        public int getItemCount() {
            return headers.length;
        }
    }

    public static void autoDisplayListPreference(final ListPreference pref) {
        final CharSequence label = pref.getEntry();
        if (label != null) {
            pref.setSummary(label);
        }

        final Preference.OnPreferenceChangeListener originalChangeListener = pref.getOnPreferenceChangeListener();
        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            final boolean changed;

            if (originalChangeListener != null) {
                changed = originalChangeListener.onPreferenceChange(preference, newValue);
            } else {
                changed = true;
            }

            if (changed) {
                final int index = pref.findIndexOfValue((String) newValue);
                if (index >= 0) {
                    pref.setSummary(pref.getEntries()[index]);
                }
            }

            return changed;
        });
    }

    @Deprecated
    public static void setPaddingBasedOnPreferences(final View view) {
        final Resources r = App.context.getResources();
        if (Preferences.getBoolean(r.getString(R.string.pref_textPadding_key), r.getBoolean(R.bool.pref_textPadding_default))) {
            final int top = r.getDimensionPixelOffset(R.dimen.text_top_padding);
            final int bottom = r.getDimensionPixelOffset(R.dimen.text_bottom_padding);
            final int side = r.getDimensionPixelOffset(R.dimen.text_side_padding);
            view.setPadding(side, top, side, bottom);
        } else {
            final int no = r.getDimensionPixelOffset(R.dimen.text_nopadding);
            view.setPadding(no, no, no, no);
        }
    }

    public static Rect getPaddingBasedOnPreferences() {
        final Resources r = App.context.getResources();
        if (Preferences.getBoolean(r.getString(R.string.pref_textPadding_key), r.getBoolean(R.bool.pref_textPadding_default))) {
            final int top = r.getDimensionPixelOffset(R.dimen.text_top_padding);
            final int bottom = r.getDimensionPixelOffset(R.dimen.text_bottom_padding);
            final int side = r.getDimensionPixelOffset(R.dimen.text_side_padding);
            return new Rect(side, top, side, bottom);
        } else {
            final int no = r.getDimensionPixelOffset(R.dimen.text_nopadding);
            final int bottom = r.getDimensionPixelOffset(R.dimen.text_bottom_padding);
            return new Rect(no, no, no, bottom);
        }
    }
}
