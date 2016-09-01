package yuku.alkitab.base.ac;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import com.example.android.wizardpager.MainActivity;
import name.fraser.neil.plaintext.diff_match_patch;
import yuku.afw.V;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.debug.R;

import java.util.LinkedList;

public class PatchTextActivity extends BaseActivity {
	public static final String EXTRA_baseBody = "baseBody";
	public static final String EXTRA_extraInfo = "extraInfo";
	public static final String EXTRA_referenceUrl = "referenceUrl";
	public static final int REQCODE_send = 1;

	EditText tBody;

	CharSequence baseBody;
	String extraInfo;
	String referenceUrl;

	public static Intent createIntent(final CharSequence baseBody, final String extraInfo, final String referenceUrl) {
		final Intent res = new Intent(App.context, PatchTextActivity.class);
		res.putExtra(EXTRA_baseBody, baseBody);
		res.putExtra(EXTRA_extraInfo, extraInfo);
		res.putExtra(EXTRA_referenceUrl, referenceUrl);
		return res;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_patch_text);

		final Toolbar toolbar = V.get(this, R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);
		ab.setDisplayShowTitleEnabled(false);

		tBody = V.get(this, R.id.tBody);
		tBody.setTextColor(S.applied.fontColor);
		tBody.setBackgroundColor(S.applied.backgroundColor);
		tBody.setTypeface(S.applied.fontFace, S.applied.fontBold);
		tBody.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp);
		tBody.setLineSpacing(0, S.applied.lineSpacingMult);

		baseBody = getIntent().getCharSequenceExtra(EXTRA_baseBody);
		tBody.setText(baseBody, TextView.BufferType.EDITABLE);

		extraInfo = getIntent().getStringExtra(EXTRA_extraInfo);
		referenceUrl = getIntent().getStringExtra(EXTRA_referenceUrl);

		new MaterialDialog.Builder(this)
			.content(R.string.patch_text_intro)
			.positiveText(R.string.ok)
			.show();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_patch_text, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		final MenuItem menuReference = menu.findItem(R.id.menuReference);
		menuReference.setVisible(referenceUrl != null);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == R.id.menuReference) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(referenceUrl)));
			return true;
		} else if (item.getItemId() == R.id.menuSend) {
			menuSend_click();
		}

		return super.onOptionsItemSelected(item);
	}

	void menuSend_click() {
		final String baseText = baseBody.toString();
		final String currentText = tBody.getText().toString();

		final diff_match_patch dmp = new diff_match_patch();
		final LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(baseText, currentText);
		dmp.Diff_EditCost = 10;
		dmp.diff_cleanupEfficiency(diffs);
		dmp.diff_cleanupSemanticLossless(diffs);

		final StringBuilder sb = new StringBuilder();
		boolean hasEdits = false;
		for (diff_match_patch.Diff diff : diffs) {
			switch (diff.operation) {
				case EQUAL:
					sb.append(diff.text);
					break;
				case DELETE:
					sb.append("<del>").append(diff.text).append("</del>");
					hasEdits = true;
					break;
				case INSERT:
					sb.append("<ins>").append(diff.text).append("</ins>");
					hasEdits = true;
					break;
			}
		}

		if (!hasEdits) {
			new MaterialDialog.Builder(this)
				.content(R.string.patch_text_error_no_edits)
				.positiveText(R.string.ok)
				.show();
			return;
		}

		final String patchTextMessage = "PATCHTEXT\n\n" + extraInfo + "\n\n" + sb.toString();
		startActivityForResult(MainActivity.createIntent(App.context, patchTextMessage), REQCODE_send);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (requestCode == REQCODE_send && resultCode == RESULT_OK) {
			setResult(RESULT_OK);
			finish();
			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}
}
