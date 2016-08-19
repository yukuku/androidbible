package yuku.alkitab.base.sync;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.debug.R;

public class SyncLoginActivity extends BaseActivity {

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
	View panelRegister;
	EditText tChurch;
	EditText tCity;
	Spinner cbReligion;
	TextView tPrivacy;
	Button bRegister;
	Button bLogin;
	Button bChangePassword;

	ReligionAdapter religionAdapter;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		enableNonToolbarUpButton();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sync_login);

		tIntro = V.get(this, R.id.tIntro);
		tEmail = V.get(this, R.id.tEmail);
		tPassword = V.get(this, R.id.tPassword);
		tPasswordNew = V.get(this, R.id.tPasswordNew);
		bForgot = V.get(this, R.id.bForgot);
		panelRegister = V.get(this, R.id.panelRegister);
		tChurch = V.get(this, R.id.tChurch);
		tCity = V.get(this, R.id.tCity);
		cbReligion = V.get(this, R.id.cbReligion);
		tPrivacy = V.get(this, R.id.tPrivacy);
		bRegister = V.get(this, R.id.bRegister);
		bLogin = V.get(this, R.id.bLogin);
		bChangePassword = V.get(this, R.id.bChangePassword);

		cbReligion.setAdapter(religionAdapter = new ReligionAdapter());

		panelRegister.setVisibility(View.GONE);
		panelRegister.setTag(/* opened: */ false);

		bRegister.setOnClickListener(v -> {
			if (Boolean.FALSE.equals(panelRegister.getTag())) {
				panelRegister.setVisibility(View.VISIBLE);
				panelRegister.setTag(/* opened: */ true);
				bLogin.setVisibility(View.GONE);
				return;
			}

			final String email = tEmail.getText().toString().trim();

			if (email.length() == 0) {
				tEmail.setError(getString(R.string.sync_login_form_error_required));
				return;
			} else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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
				final String religion = (String) cbReligion.getSelectedItem();
				final String city = tCity.length() == 0 ? null : tCity.getText().toString().trim();
				final String church = tChurch.length() == 0 ? null : tChurch.getText().toString().trim();

				final Sync.RegisterForm form = new Sync.RegisterForm();
				form.email = email;
				form.password = password;
				form.city = city;
				form.church = church;
				form.religion = religion;

				startThreadWithProgressDialog(getString(R.string.sync_progress_register), () -> {
					try {
						Log.d(TAG, "Sending form to server for creating new account...");
						SyncRecorder.log(SyncRecorder.EventKind.register_attempt, null, "serverPrefix", Sync.getEffectiveServerPrefix(), "email", email);

						final Sync.LoginResponseJson response = Sync.register(form);

						gotSimpleToken(email, response.simpleToken, true);
					} catch (Sync.NotOkException e) {
						Log.d(TAG, "Register failed: " + e.getMessage());
						SyncRecorder.log(SyncRecorder.EventKind.register_failed, null, "email", email, "message", e.getMessage());

						runOnUiThread(() -> new MaterialDialog.Builder(this)
							.content(getString(R.string.sync_register_failed_with_reason, e.getMessage()))
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
			} else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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
					Log.d(TAG, "Sending form to server for login...");
					SyncRecorder.log(SyncRecorder.EventKind.login_attempt, null, "serverPrefix", Sync.getEffectiveServerPrefix(), "email", email);

					final Sync.LoginResponseJson response = Sync.login(email, password);

					gotSimpleToken(email, response.simpleToken, false);
				} catch (Sync.NotOkException e) {
					Log.d(TAG, "Login failed: " + e.getMessage());
					SyncRecorder.log(SyncRecorder.EventKind.login_failed, null, "email", email, "message", e.getMessage());

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
					Log.d(TAG, "Sending form to server for forgot password...");

					Sync.forgotPassword(email);

					runOnUiThread(() -> new MaterialDialog.Builder(this)
						.content(R.string.sync_login_form_forgot_password_success)
						.positiveText(R.string.ok)
						.show()
					);
				} catch (Sync.NotOkException e) {
					Log.d(TAG, "Forgot password failed: " + e.getMessage());

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
					Log.d(TAG, "Sending form to server for changing password...");

					Sync.changePassword(email, password, passwordNew);

					runOnUiThread(() -> new MaterialDialog.Builder(this)
						.content(R.string.sync_login_form_change_password_success)
						.positiveText(R.string.ok)
						.show()
						.setOnDismissListener(dialog -> finish())
					);
				} catch (Sync.NotOkException e) {
					Log.d(TAG, "Change password failed: " + e.getMessage());

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

		new Thread(() -> {
			try {
				task.run();
			} finally {
				pd.dismiss();
			}
		}).start();
	}

	void confirmPassword(final String correctPassword, final Runnable whenCorrect) {
		new MaterialDialog.Builder(this)
			.customView(R.layout.dialog_sync_confirm_password, false)
			.positiveText(R.string.ok)
			.onPositive((dialog, which) -> {
				final EditText tPassword2 = V.get(dialog.getCustomView(), R.id.tPassword2);

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
				panelRegister.setVisibility(View.GONE);
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
		// send GCM registration id, if we already have it.
		final String registration_id = Gcm.renewGcmRegistrationIdIfNeeded(Sync::notifyNewGcmRegistrationId);
		if (registration_id != null) {
			final boolean ok = Sync.sendGcmRegistrationId(simpleToken, registration_id);
			if (!ok) {
				SyncRecorder.log(SyncRecorder.EventKind.login_gcm_sending_failed, null, "accountName", accountName);

				if (isRegister) {
					runOnUiThread(() -> new MaterialDialog.Builder(this)
						.content(R.string.sync_registered_but_no_gcm)
						.positiveText(R.string.ok)
						.show());
				} else {
					runOnUiThread(() -> new MaterialDialog.Builder(this)
						.content(getString(R.string.sync_login_failed_with_reason, "Could not send GCM registration id. Please try again."))
						.positiveText(R.string.ok)
						.show());
				}
				return;
			}
		} else {
			// if not, ignore. Later eventually we will have it.
			SyncRecorder.log(SyncRecorder.EventKind.login_gcm_not_possessed_yet, null, "accountName", accountName);
		}

		runOnUiThread(() -> {
			final Intent data = new Intent();
			data.putExtra("accountName", accountName);
			data.putExtra("simpleToken", simpleToken);
			setResult(RESULT_OK, data);
			finish();
		});
	}

	class ReligionAdapter extends EasyAdapter {
		final Object[][] choices = {
			{null, R.string.sync_login_survey_religion_select_one},
			{"christianity", R.string.sync_login_survey_religion_christianity},
			{"christianity.protestant", R.string.sync_login_survey_religion_christianity_protestant},
			{"christianity.lutheran", R.string.sync_login_survey_religion_christianity_lutheran},
			{"christianity.methodist", R.string.sync_login_survey_religion_christianity_methodist},
			{"christianity.anglican", R.string.sync_login_survey_religion_christianity_anglican},
			{"christianity.adventist", R.string.sync_login_survey_religion_christianity_adventist},
			{"christianity.pentecostal", R.string.sync_login_survey_religion_christianity_pentecostal},
			{"christianity.roman", R.string.sync_login_survey_religion_christianity_roman},
			{"christianity.other", R.string.sync_login_survey_religion_christianity_other},
			{"islam", R.string.sync_login_survey_religion_islam},
			{"buddha", R.string.sync_login_survey_religion_buddha},
			{"hindu", R.string.sync_login_survey_religion_hindu},
			{"other", R.string.sync_login_survey_religion_other},
			{"atheist", R.string.sync_login_survey_religion_atheist},
			{"agnostic", R.string.sync_login_survey_religion_agnostic},
		};

		@Override
		public String getItem(final int position) {
			// used by getSelectedItem
			return (String) choices[position][0];
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_spinner_item, parent, false);
		}

		@Override
		public View newDropDownView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			final TextView text = (TextView) view;
			text.setText(getString((int) choices[position][1]));
		}

		@Override
		public int getCount() {
			return choices.length;
		}
	}
}
