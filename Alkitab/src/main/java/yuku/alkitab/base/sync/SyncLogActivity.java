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
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.model.SyncLog;
import yuku.alkitab.debug.R;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

public class SyncLogActivity extends BaseActivity {
	ListView lsLog;
	LogAdapter adapter;

	static final ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
		@Override
		protected SimpleDateFormat initialValue() {
			return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		}
	};

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sync_log);

		lsLog = V.get(this, R.id.lsLog);
		lsLog.setAdapter(adapter = new LogAdapter());
	}

	@Override
	protected void onStart() {
		super.onStart();
		adapter.load();
	}

	public static Intent createIntent() {
		return new Intent(App.context, SyncLogActivity.class);
	}

	class LogAdapter extends EasyAdapter {
		List<SyncLog> logs;
		final float density = getResources().getDisplayMetrics().density;

		void load() {
			logs = S.getDb().listLatestSyncLog(500);
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
					sb.append(kind.toString());
					sb.setSpan(new BackgroundColorSpan(kind.backgroundColor), sb_len, sb.length(), 0);
				}

				if (log.syncSetName != null) {
					sb.append(" ");
					sb.append(log.syncSetName);
				}
			}

			if (log.params != null) {
				final int sb_len0 = sb.length();
				for (final Map.Entry<String, Object> entry : log.params.entrySet()) {
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
