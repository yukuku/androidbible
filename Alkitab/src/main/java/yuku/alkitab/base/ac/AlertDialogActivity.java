package yuku.alkitab.base.ac;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.debug.R;

/**
 * Use this class to show an alert dialog if you don't have an existing activity to
 * show the dialog on.
 *
 * This starts a transparent activity and then shows an alert dialog on top
 * of the transparent activity.
 */
public class AlertDialogActivity extends BaseActivity {

	/** Inputs */
	private static final String EXTRA_TITLE = "title";
	private static final String EXTRA_MESSAGE = "message";
	private static final String EXTRA_NEGATIVE = "negative";
	private static final String EXTRA_POSITIVE = "positive";
	private static final String EXTRA_INPUT_TYPE = "input_type";
	private static final String EXTRA_INPUT_HINT = "input_hint";
	private static final String EXTRA_LAUNCH = "launch";

	/** Output */
	public static final String EXTRA_INPUT = "input";

	public static Intent createOkIntent(final CharSequence title, final CharSequence message) {
		return new Intent(App.context, AlertDialogActivity.class)
			.putExtra(EXTRA_TITLE, title)
			.putExtra(EXTRA_MESSAGE, message);
	}

	public static Intent createInputIntent(final CharSequence title, final CharSequence message, final CharSequence negativeButtonText, final CharSequence positiveButtonText, final int inputType, final CharSequence inputHint) {
		return new Intent(App.context, AlertDialogActivity.class)
			.putExtra(EXTRA_TITLE, title)
			.putExtra(EXTRA_MESSAGE, message)
			.putExtra(EXTRA_NEGATIVE, negativeButtonText)
			.putExtra(EXTRA_POSITIVE, positiveButtonText)
			.putExtra(EXTRA_INPUT_TYPE, inputType)
			.putExtra(EXTRA_INPUT_HINT, inputHint);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Intent intent = getIntent();
		final CharSequence title = intent.getCharSequenceExtra(EXTRA_TITLE);
		final CharSequence message = intent.getCharSequenceExtra(EXTRA_MESSAGE);
		final CharSequence negative = intent.getCharSequenceExtra(EXTRA_NEGATIVE);
		final CharSequence positive = intent.getExtras().getCharSequence(EXTRA_POSITIVE, getText(android.R.string.ok));
		final int inputType = intent.getIntExtra(EXTRA_INPUT_TYPE, 0);
		final CharSequence inputHint = intent.getCharSequenceExtra(EXTRA_INPUT_HINT);
		final Intent launch = intent.getParcelableExtra(EXTRA_LAUNCH);

		final MaterialDialog.Builder builder = new MaterialDialog.Builder(this);

		if (title != null) {
			builder.title(title);
		}

		if (message != null) {
			builder.content(message);
		}

		if (inputHint != null) {
			if (inputType != 0) {
				builder.inputType(inputType);
			}

			builder.input(inputHint, null, false, (dialog, input) -> {
				final Intent returnIntent = new Intent();
				returnIntent.putExtra(EXTRA_INPUT, input.toString());
				setResult(RESULT_OK, returnIntent);
				finish();
			});
		}

		builder.positiveText(positive);

		builder.onPositive((dialog, which) -> {
			if (inputHint == null) {
				final Intent returnIntent = new Intent();
				setResult(RESULT_OK, returnIntent);
				if (launch != null) {
					try {
						startActivity(launch);
					} catch (ActivityNotFoundException e) {
						new MaterialDialog.Builder(AlertDialogActivity.this)
							message(text = "Actvity was not found for intent: " + launch.toString())
							positiveButton(R.string.ok)
							.show();
					}
				}
				finish();
			}
		});

		builder.onNegative((dialog, which) -> finish());

		if (negative != null) {
			builder.negativeText(negative);
		}

		builder.dismissListener(dialog -> finish());

		builder.show();
	}
}
