package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import yuku.afw.V;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.debug.R;

public class SecretSyncDebugActivity extends BaseActivity {
	public static final String TAG = SecretSyncDebugActivity.class.getSimpleName();

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_secret_sync_debug);

		V.get(this, R.id.bMabelClientState).setOnClickListener(bMabelClientState_click);
	}

	View.OnClickListener bMabelClientState_click = v -> {
		final StringBuilder sb = new StringBuilder();
		final Pair<Integer, Sync.Delta<Sync.MabelContent>> clientState = Sync.getMabelClientState();

		sb.append("Base revno: ").append(clientState.first).append('\n');
		sb.append("Delta operations: \n");

		for (final Sync.Operation<Sync.MabelContent> operation : clientState.second.operations) {
			sb.append("\u2022 ").append(operation).append('\n');
		}

		new AlertDialog.Builder(this)
			.setMessage(sb)
			.setPositiveButton(R.string.ok, null)
			.show();
	};
}
