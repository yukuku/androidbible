package yuku.alkitab.base.sync;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.model.SyncLog;
import yuku.alkitab.debug.R;

public class SyncLogActivity extends BaseActivity {
	ListView lsLog;
	LogAdapter adapter;
    private final android.os.Handler refreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            adapter.load();
            refreshHandler.postDelayed(this, 2000);
        }
    };

	static final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US));

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sync_log);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		setupEdgeToEdgeDisplay(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);

		lsLog = findViewById(R.id.lsLog);
		lsLog.setAdapter(adapter = new LogAdapter());
        SyncKotlin.observeWork(this);
	}

	@Override
	protected void onStart() {
		super.onStart();
        refresh.run();
	}

    @Override
    protected void onStop() {
        refreshHandler.removeCallbacks(refresh);
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(final android.view.Menu menu) {
        menu.add(0, 1, 0, R.string.bl_selection_copy);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final android.view.MenuItem item) {
        if (item.getItemId() == 1) {
            adapter.load();
            final StringBuilder text = new StringBuilder("Sync diagnostics (newest first; up to 500 stored events)\n");
            text.append("App ").append(App.getVersionName()).append(" (").append(App.getVersionCode())
                .append("); Android ").append(android.os.Build.VERSION.SDK_INT).append("\n");
            if (!SyncRecorder.fallbackLogs().isEmpty()) text.append("Diagnostic storage unavailable; some events are memory-only.\n");
            for (final SyncLog log : adapter.logs) {
                text.append(dateFormat.get().format(log.createTime)).append(" ");
                final SyncRecorder.EventKind kind = SyncRecorder.EventKind.fromCode(log.kind_code);
                text.append(log.kind_code).append(" ").append(kind == null ? "unknown" : kind.name())
                    .append(" ").append(log.syncSetName == null ? "" : SyncDiagnostics.safeSet(log.syncSetName))
                    .append("\n").append(SyncDiagnostics.safeParams(log.params)).append("\n\n");
            }
            yuku.alkitab.base.util.ClipboardUtil.copyToClipboard(text);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

	public static Intent createIntent() {
		return new Intent(App.context, SyncLogActivity.class);
	}

	class LogAdapter extends EasyAdapter {
		List<SyncLog> logs;
		final float density = getResources().getDisplayMetrics().density;

		void load() {
			try {
                logs = new java.util.ArrayList<>(App.services.storage.getDb().listLatestSyncLog(SyncDiagnostics.MAX_ROWS));
            } catch (RuntimeException error) {
                SyncRecorder.error("gather", null, error);
                logs = new java.util.ArrayList<>();
            }
            logs.addAll(SyncRecorder.fallbackLogs());
            logs.sort((left, right) -> right.createTime.compareTo(left.createTime));
			notifyDataSetChanged();
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			final SyncLog log = logs.get(position);

			final SpannableStringBuilder sb = new SpannableStringBuilder();
			sb.append(dateFormat.get().format(log.createTime));
			sb.append("\n");

			{
				final SyncRecorder.EventKind kind = SyncRecorder.EventKind.fromCode(log.kind_code);
				final int sb_len = sb.length();
				if (kind == null) {
					sb.append(String.valueOf(log.kind_code));
					sb.setSpan(new BackgroundColorSpan(0xff222222), sb_len, sb.length(), 0);
				} else {
					sb.append(String.valueOf(kind.code)).append(" ").append(kind.toString());
					sb.setSpan(new BackgroundColorSpan(kind.backgroundColor), sb_len, sb.length(), 0);
				}

				if (log.syncSetName != null) {
					sb.append(" ");
					sb.append(SyncDiagnostics.safeSet(log.syncSetName));
				}
			}

			if (log.params != null) {
				final int sb_len0 = sb.length();
				for (final Map.Entry<String, Object> entry : SyncDiagnostics.safeParams(log.params).entrySet()) {
					sb.append("\n");
					final int sb_len = sb.length();
					sb.append(entry.getKey());
					sb.setSpan(new StyleSpan(Typeface.BOLD), sb_len, sb.length(), 0);
					sb.append(" ");
					final Object value = entry.getValue();
					if (value instanceof Number) {
						final double doubleValue = ((Number) value).doubleValue();
						final double floored = Math.floor(doubleValue);
						if (floored == doubleValue) {
							sb.append(String.valueOf((long) floored));
						} else {
							sb.append(String.valueOf(doubleValue));
						}
					} else {
						sb.append(String.valueOf(value));
					}
				}
				sb.setSpan(new RelativeSizeSpan(0.8f), sb_len0, sb.length(), 0);
				sb.setSpan(new LeadingMarginSpan.Standard(((int) (8 * density))), sb_len0, sb.length(), 0);
			}

			final TextView text = (TextView) view;
			text.setTextSize(14.f);
			text.setText(sb);
			text.setPadding((int) (4 * density), (int) (4 * density), (int) (4 * density), (int) (4 * density));
		}

		@Override
		public int getCount() {
			return logs == null ? 0 : logs.size();
		}
	}
}
