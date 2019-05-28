package yuku.alkitab.base.sync;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.util.PatternsCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.LoginEvent;
import com.crashlytics.android.answers.SignUpEvent;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Background;
import yuku.alkitab.debug.R;

public class SyncLoginActivity extends BaseActivity {

	public static final String ANSWERS_METHOD = "sync";

	public static class Result {
		public String accountName;
		public String simpleToken;
	}

	public static Intent createIntent() {
		return new Intent(App.context, SyncLoginActivity.class);
	}

	public static Result obtainResult(final Intent data) {
		if (data == null) return null;
		final Result res = new Result();
		res.accountName = data.getStringExtra("accountName");
		res.simpleToken = data.getStringExtra("simpleToken");
		return res;
	}

	TextView tIntro;
	EditText tEmail;
	EditText tPassword;
	EditText tPasswordNew;
	Button bForgot;
	TextView tPrivacy;
	Button bRegister;
	Button bLogin;
	Button bChangePassword;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sync_login);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);

		tIntro = findViewById(R.id.tIntro);
		tEmail = findViewById(R.id.tEmail);
		tPassword = findViewById(R.id.tPassword);
		tPasswordNew = findViewById(R.id.tPasswordNew);
		bForgot = findViewById(R.id.bForgot);
		tPrivacy = findViewById(R.id.tPrivacy);
		bRegister = findViewById(R.id.bRegister);
		bLogin = findViewById(R.id.bLogin);
		bChangePassword = findViewById(R.id.bChangePassword);

		bRegister.setOnClickListener(v -> {
			final String email = tEmail.getText().toString().trim();

			if (email.length() == 0) {
				tEmail.setError(getString(R.string.sync_login_form_error_required));
				return;
			} else if (!PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()) {
				tEmail.setError(getString(R.string.sync_login_form_error_email_pattern));
				return;
			} else {
				tEmail.setError(null);
			}

			if (tPassword.length() == 0) {
				tPassword.setError(getString(R.string.sync_login_form_error_required));
				return;
			} else {
				tPassword.setError(null);
			}

			final String password = tPassword.getText().toString();

			confirmPassword(password, () -> {
				final Sync.RegisterForm form = new Sync.RegisterForm();
				form.email = email;
				form.password = password;

				startThreadWithProgressDialog(getString(R.string.sync_progress_register), () -> {
					try {
						AppLog.d(TAG, "Sending form to server for creating new account...");
						SyncRecorder.log(SyncRecorder.EventKind.register_attempt, null, "serverPrefix", Sync.getEffectiveServerPrefix(), "email", email);

						final Sync.LoginResponseJson response = Sync.register(form);

						Answers.getInstance().logSignUp(new SignUpEvent()
							.putMethod(ANSWERS_METHOD)
							.putSuccess(true)
						);

						Crashlytics.setUserEmail(form.email);

						gotSimpleToken(email, response.simpleToken, true);
					} catch (Sync.NotOkException e) {
						AppLog.d(TAG, "Register failed", e);
						SyncRecorder.log(SyncRecorder.EventKind.register_failed, null, "email", email, "message", e.getMessage());

						Answers.getInstance().logSignUp(new SignUpEvent()
							.putMethod(ANSWERS_METHOD)
							.putSuccess(false)
						);

						runOnUiThread(() -> new MaterialDialog.Builder(this)
							.content(R.string.sync_register_failed_with_reason, e.getMessage())
							.positiveText(R.string.ok)
							.show()
						);
					}
				});
			});
		});

		bLogin.setOnClickListener(v -> {
			final String email = tEmail.getText().toString().trim();

			if (email.length() == 0) {
				tEmail.setError(getString(R.string.sync_login_form_error_required));
				return;
			} else if (!PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()) {
				tEmail.setError(getString(R.string.sync_login_form_error_email_pattern));
				return;
			} else {
				tEmail.setError(null);
			}

			if (tPassword.length() == 0) {
				tPassword.setError(getString(R.string.sync_login_form_error_required));
				return;
			} else {
				tPassword.setError(null);
			}

			final String password = tPassword.getText().toString();

			startThreadWithProgressDialog(getString(R.string.sync_progress_login), () -> {
				try {
					AppLog.d(TAG, "Sending form to server for login...");
					SyncRecorder.log(SyncRecorder.EventKind.login_attempt, null, "serverPrefix", Sync.getEffectiveServerPrefix(), "email", email);

					final Sync.LoginResponseJson response = Sync.login(email, password);

					Answers.getInstance().logLogin(new LoginEvent()
						.putMethod(ANSWERS_METHOD)
						.putSuccess(true)
					);

					Crashlytics.setUserEmail(email);

					gotSimpleToken(email, response.simpleToken, false);
				} catch (Sync.NotOkException e) {
					AppLog.d(TAG, "Login failed", e);
					SyncRecorder.log(SyncRecorder.EventKind.login_failed, null, "email", email, "message", e.getMessage());

					Answers.getInstance().logLogin(new LoginEvent()
						.putMethod(ANSWERS_METHOD)
						.putSuccess(false)
					);

					runOnUiThread(() -> new MaterialDialog.Builder(this)
						.content(getString(R.string.sync_login_failed_with_reason, e.getMessage()))
						.positiveText(R.string.ok)
						.show()
					);
				}
			});
		});

		tPassword.setOnFocusChangeListener((v, hasFocus) -> bForgot.setVisibility(tPassword.length() > 0 || hasFocus ? View.GONE : View.VISIBLE));

		bForgot.setOnClickListener(v -> {
			final String email = tEmail.getText().toString().trim();

			if (email.length() == 0) {
				tEmail.setError(getString(R.string.sync_login_form_error_required));
				return;
			} else {
				tEmail.setError(null);
			}

			startThreadWithProgressDialog(getString(R.string.sync_progress_processing), () -> {
				try {
					AppLog.d(TAG, "Sending form to server for forgot password...");

					Sync.forgotPassword(email);

					runOnUiThread(() -> new MaterialDialog.Builder(this)
						.content(R.string.sync_login_form_forgot_password_success)
						.positiveText(R.string.ok)
						.show()
					);
				} catch (Sync.NotOkException e) {
					AppLog.d(TAG, "Forgot password failed", e);

					runOnUiThread(() -> new MaterialDialog.Builder(this)
						.content(e.getMessage())
						.positiveText(R.string.ok)
						.show()
					);
				}
			});
		});

		bChangePassword.setOnClickListener(v -> {
			final String email = tEmail.getText().toString().trim();

			if (email.length() == 0) {
				tEmail.setError(getString(R.string.sync_login_form_error_required));
				return;
			} else {
				tEmail.setError(null);
			}

			final String password = tPassword.getText().toString();
			if (password.length() == 0) {
				tPassword.setError(getString(R.string.sync_login_form_error_required));
				return;
			} else {
				tPassword.setError(null);
			}

			final String passwordNew = tPasswordNew.getText().toString();
			if (passwordNew.length() == 0) {
				tPasswordNew.setError(getString(R.string.sync_login_form_error_required));
				return;
			} else {
				tPasswordNew.setError(null);
			}

			confirmPassword(passwordNew, () -> startThreadWithProgressDialog(getString(R.string.sync_progress_processing), () -> {
				try {
					AppLog.d(TAG, "Sending form to server for changing password...");

					Sync.changePassword(email, password, passwordNew);

					runOnUiThread(() -> new MaterialDialog.Builder(this)
						.content(R.string.sync_login_form_change_password_success)
						.positiveText(R.string.ok)
						.show()
						.setOnDismissListener(dialog -> finish())
					);
				} catch (Sync.NotOkException e) {
					AppLog.d(TAG, "Change password failed", e);

					runOnUiThread(() -> new MaterialDialog.Builder(this)
						.content(e.getMessage())
						.positiveText(R.string.ok)
						.show()
					);
				}
			}));

		});

		tIntro.setMovementMethod(LinkMovementMethod.getInstance());
		tPrivacy.setMovementMethod(LinkMovementMethod.getInstance());
	}

	void startThreadWithProgressDialog(final String message, final Runnable task) {
		final MaterialDialog pd = new MaterialDialog.Builder(this)
			.content(message)
			.cancelable(false)
			.progress(true, 0)
			.show();

		Background.run(() -> {
			try {
				task.run();
			} finally {
				pd.dismiss();
			}
		});
	}

	void confirmPassword(final String correctPassword, final Runnable whenCorrect) {
		new MaterialDialog.Builder(this)
			.customView(R.layout.dialog_sync_confirm_password, false)
			.positiveText(R.string.ok)
			.onPositive((dialog, which) -> {
				final EditText tPassword2 = dialog.getCustomView().findViewById(R.id.tPassword2);

				final String password2 = tPassword2.getText().toString();

				if (!U.equals(correctPassword, password2)) {
					new MaterialDialog.Builder(dialog.getContext())
						.content(R.string.sync_login_form_passwords_do_not_match)
						.positiveText(R.string.ok)
						.show();
					return;
				}

				whenCorrect.run();
			})
			.show();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_sync_login, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		final int itemId = item.getItemId();
		switch (itemId) {
			case R.id.menuChangePassword:
				bLogin.setVisibility(View.GONE);
				bRegister.setVisibility(View.GONE);
				bChangePassword.setVisibility(View.VISIBLE);

				tPasswordNew.setVisibility(View.VISIBLE);
				return true;

			case R.id.menuSyncLog:
				startActivity(SyncLogActivity.createIntent());
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * We created account or logged in successfully. Call this method from a background thread!
	 * Close this activity and report success.
	 */
	void gotSimpleToken(final String accountName, final String simpleToken, final boolean isRegister) {
		// send FCM registration id, if we already have it.
		final String registration_id = Fcm.renewFcmRegistrationIdIfNeeded(Sync::notifyNewFcmRegistrationId);
		if (registration_id != null) {
			final boolean ok = Sync.sendFcmRegistrationId(simpleToken, registration_id);
			if (!ok) {
				SyncRecorder.log(SyncRecorder.EventKind.login_fcm_sending_failed, null, "accountName", accountName);

				if (isRegister) {
					runOnUiThread(() -> new MaterialDialog.Builder(this)
						.content(R.string.sync_registered_but_no_gcm)
						.positiveText(R.string.ok)
						.show());
				} else {
					runOnUiThread(() -> new MaterialDialog.Builder(this)
						.content(getString(R.string.sync_login_failed_with_reason, "Could not send FCM registration id. Please try again."))
						.positiveText(R.string.ok)
						.show());
				}
				return;
			}
		} else {
			// if not, ignore. Later eventually we will have it.
			SyncRecorder.log(SyncRecorder.EventKind.login_fcm_not_possessed_yet, null, "accountName", accountName);
		}

		runOnUiThread(() -> {
			final Intent data = new Intent();
			data.putExtra("accountName", accountName);
			data.putExtra("simpleToken", simpleToken);
			setResult(RESULT_OK, data);
			finish();
		});
	}
}
