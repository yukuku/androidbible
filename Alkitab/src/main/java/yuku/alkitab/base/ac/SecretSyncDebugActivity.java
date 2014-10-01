package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class SecretSyncDebugActivity extends BaseActivity {
	public static final String TAG = SecretSyncDebugActivity.class.getSimpleName();

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_secret_sync_debug);

		V.get(this, R.id.bMabelClientState).setOnClickListener(bMabelClientState_click);
		V.get(this, R.id.bGenerateDummies).setOnClickListener(bGenerateDummies_click);
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

	int rand(int n) {
		return (int) (Math.random() * n);
	}

	View.OnClickListener bGenerateDummies_click = v -> {
		final Label label1 = S.getDb().insertLabel(randomString("L1_", 1, 3, 8), U.encodeLabelBackgroundColor(rand(0xffffff)));
		final Label label2 = S.getDb().insertLabel(randomString("L2_", 1, 3, 8), U.encodeLabelBackgroundColor(rand(0xffffff)));

		for (int i = 0; i < 10; i++) {
			final Marker marker = S.getDb().insertMarker(0x000101 + rand(30), Marker.Kind.values()[rand(3)], randomString("M" + i + "_", rand(2) + 1, 4, 7), rand(2) + 1, new Date(), new Date());
			final Set<Label> labelSet = new HashSet<>();
			if (rand(10) < 5) {
				labelSet.add(label1);
			}
			if (rand(10) < 3) {
				labelSet.add(label2);
			}
			S.getDb().updateLabels(marker, labelSet);
		}

		new AlertDialog.Builder(this)
			.setMessage("10 markers, 2 labels generated.")
			.setPositiveButton(R.string.ok, null)
			.show();
	};

	private String randomString(final String prefix, final int word_count, final int minwordlen, final int maxwordlen) {
		final StringBuilder sb = new StringBuilder(prefix);
		for (int i = 0; i < word_count; i++) {
			for (int j = 0, wordlen = rand(maxwordlen - minwordlen) + minwordlen; j < wordlen; j++) {
				if (j % 2 == 0) {
					sb.append("bcdfghjklmnpqrstvwyz".charAt(rand(20)));
				} else {
					sb.append("aeiou".charAt(rand(5)));
				}
			}
			if (i != word_count - 1) {
				sb.append(' ');
			}
		}
		return sb.toString();
	}
}
