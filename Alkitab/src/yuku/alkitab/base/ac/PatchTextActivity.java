package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
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
	public static final int REQCODE_send = 1;

	EditText tBody;
	View bReference;
	View bSend;

	CharSequence baseBody;
	String extraInfo;

	public static Intent createIntent(final CharSequence baseBody, final String extraInfo) {
		final Intent res = new Intent(App.context, PatchTextActivity.class);
		res.putExtra(EXTRA_baseBody, baseBody);
		res.putExtra(EXTRA_extraInfo, extraInfo);
		return res;
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_patch_text);

		tBody = V.get(this, R.id.tBody);
		tBody.setTextColor(S.applied.fontColor);
		tBody.setBackgroundColor(S.applied.backgroundColor);
		tBody.setTypeface(S.applied.fontFace, S.applied.fontBold);
		tBody.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp);
		tBody.setLineSpacing(0, S.applied.lineSpacingMult);

		final ActionBar actionBar = getSupportActionBar();
		final Context actionBarContext = actionBar.getThemedContext();
		final View customView = LayoutInflater.from(actionBarContext).inflate(R.layout.activity_patch_text_actionbar_custom, null, false);
		actionBar.setDisplayShowCustomEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(false);
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setCustomView(customView, new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		bReference = V.get(customView, R.id.bReference);
		bSend = V.get(customView, R.id.bSend);

		bReference.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				bReference_click();
			}
		});
		bSend.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				bSend_click();
			}
		});

		baseBody = getIntent().getCharSequenceExtra(EXTRA_baseBody);
		tBody.setText(baseBody, TextView.BufferType.EDITABLE);

		extraInfo = getIntent().getStringExtra(EXTRA_extraInfo);

		new AlertDialog.Builder(this)
			.setMessage(R.string.patch_text_intro)
			.setPositiveButton(R.string.ok, null)
			.show();
	}

	void bReference_click() {

	}

	void bSend_click() {
		final String baseText = baseBody.toString();
		final String currentText = tBody.getText().toString();

		final diff_match_patch dmp = new diff_match_patch();
		final LinkedList<diff_match_patch.Diff> diffs = dmp.diff_main(baseText, currentText);
		dmp.Diff_EditCost = 10;
		dmp.diff_cleanupEfficiency(diffs);
		dmp.diff_cleanupSemanticLossless(diffs);

		final StringBuilder sb = new StringBuilder();
		for (diff_match_patch.Diff diff : diffs) {
			switch (diff.operation) {
				case EQUAL:
					sb.append(diff.text);
					break;
				case DELETE:
					sb.append("<del>").append(diff.text).append("</del>");
					break;
				case INSERT:
					sb.append("<ins>").append(diff.text).append("</ins>");
					break;
			}
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
