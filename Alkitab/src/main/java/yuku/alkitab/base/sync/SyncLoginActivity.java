package yuku.alkitab.base.sync;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.PatternsCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import java.util.Locale;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.compose.sync.SyncLoginCallbacks;
import yuku.alkitab.base.compose.sync.SyncLoginComposeHost;
import yuku.alkitab.base.settings.ExperimentalFlags;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Background;
import yuku.alkitab.base.widget.MaterialDialogJavaHelper;
import yuku.alkitab.debug.R;

public class SyncLoginActivity extends BaseActivity implements SyncLoginCallbacks {
    static final String TAG = SyncLoginActivity.class.getSimpleName();

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

    boolean useCompose;

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

        useCompose = ExperimentalFlags.INSTANCE.useComposeSyncLogin();
        if (useCompose) {
            SyncLoginComposeHost.setContent(this, this);
            return;
        }

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
            // don't allow uppercase when registering, but we still allow it for login (because some user accounts were already created using uppercase)
            final String email = tEmail.getText().toString().trim().toLowerCase(Locale.US);
            tEmail.setText(email);

            if (email.isEmpty()) {
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

            confirmPassword(this, password, () -> doRegister(email, password));
        });

        bLogin.setOnClickListener(v -> {
            final String email = tEmail.getText().toString().trim();

            if (email.isEmpty()) {
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

            doLogin(email, password);
        });

        tPassword.setOnFocusChangeListener((v, hasFocus) -> bForgot.setVisibility(tPassword.length() > 0 || hasFocus ? View.GONE : View.VISIBLE));

        bForgot.setOnClickListener(v -> {
            final String email = tEmail.getText().toString().trim();

            if (email.isEmpty()) {
                tEmail.setError(getString(R.string.sync_login_form_error_required));
                return;
            } else {
                tEmail.setError(null);
            }

            doForgotPassword(email);
        });

        bChangePassword.setOnClickListener(v -> {
            final String email = tEmail.getText().toString().trim();

            if (email.isEmpty()) {
                tEmail.setError(getString(R.string.sync_login_form_error_required));
                return;
            } else {
                tEmail.setError(null);
            }

            final String password = tPassword.getText().toString();
            if (password.isEmpty()) {
                tPassword.setError(getString(R.string.sync_login_form_error_required));
                return;
            } else {
                tPassword.setError(null);
            }

            final String passwordNew = tPasswordNew.getText().toString();
            if (passwordNew.isEmpty()) {
                tPasswordNew.setError(getString(R.string.sync_login_form_error_required));
                return;
            } else {
                tPasswordNew.setError(null);
            }

            confirmPassword(this, passwordNew, () -> doChangePassword(email, password, passwordNew));
        });

        tIntro.setMovementMethod(LinkMovementMethod.getInstance());
        tPrivacy.setMovementMethod(LinkMovementMethod.getInstance());
    }

    static void confirmPassword(Context context, String correctPassword, Runnable whenCorrect) {
        final View view = LayoutInflater.from(context).inflate(R.layout.dialog_sync_confirm_password, null, false);
        new MaterialAlertDialogBuilder(context)
            .setView(view)
            .setPositiveButton(R.string.ok, (d, w) -> {
                final EditText tPassword2 = view.findViewById(R.id.tPassword2);
                final String password2 = tPassword2.getText().toString();
                if (!password2.equals(correctPassword)) {
                    new MaterialAlertDialogBuilder(context)
                        .setMessage(R.string.sync_login_form_passwords_do_not_match)
                        .setPositiveButton(R.string.ok, null)
                        .show();
                    return;
                }
                whenCorrect.run();
            })
            .show();
    }

    //region Compose UI callbacks (SyncLoginCallbacks). The Compose form does its own validation and
    // inline password confirmation, so these just kick off the same network operations as the legacy UI.

    @Override
    public void onUp() {
        finish();
    }

    @Override
    public void onRegister(@NonNull final String email, @NonNull final String password) {
        doRegister(email, password);
    }

    @Override
    public void onLogin(@NonNull final String email, @NonNull final String password) {
        doLogin(email, password);
    }

    @Override
    public void onForgotPassword(@NonNull final String email) {
        doForgotPassword(email);
    }

    @Override
    public void onChangePassword(@NonNull final String email, @NonNull final String oldPassword, @NonNull final String newPassword) {
        doChangePassword(email, oldPassword, newPassword);
    }

    @Override
    public void onOpenSyncLog() {
        startActivity(SyncLogActivity.createIntent());
    }

    //endregion

    void doRegister(final String email, final String password) {
        final Sync.RegisterForm form = new Sync.RegisterForm();
        form.email = email;
        form.password = password;

        startThreadWithProgressDialog(getString(R.string.sync_progress_register), () -> {
            try {
                AppLog.d(TAG, "Sending form to server for creating new account...");
                SyncRecorder.log(SyncRecorder.EventKind.register_attempt, null, "serverPrefix", Sync.getEffectiveServerPrefix(), "email", email);

                final Sync.LoginResponseJson response = Sync.register(form);

                FirebaseCrashlytics.getInstance().setUserId(form.email);

                gotSimpleToken(email, response.simpleToken, true);
            } catch (Sync.NotOkException e) {
                AppLog.d(TAG, "Register failed", e);
                SyncRecorder.log(SyncRecorder.EventKind.register_failed, null, "email", email, "message", e.getMessage());

                runOnUiThread(() -> MaterialDialogJavaHelper.showOkDialog(this, getString(R.string.sync_register_failed_with_reason, e.getMessage())));
            }
        });
    }

    void doLogin(final String email, final String password) {
        startThreadWithProgressDialog(getString(R.string.sync_progress_login), () -> {
            try {
                AppLog.d(TAG, "Sending form to server for login...");
                SyncRecorder.log(SyncRecorder.EventKind.login_attempt, null, "serverPrefix", Sync.getEffectiveServerPrefix(), "email", email);

                final Sync.LoginResponseJson response = Sync.login(email, password);

                FirebaseCrashlytics.getInstance().setUserId(email);

                gotSimpleToken(email, response.simpleToken, false);
            } catch (Sync.NotOkException e) {
                AppLog.d(TAG, "Login failed", e);
                SyncRecorder.log(SyncRecorder.EventKind.login_failed, null, "email", email, "message", e.getMessage());

                runOnUiThread(() -> MaterialDialogJavaHelper.showOkDialog(
                    this,
                    getString(R.string.sync_login_failed_with_reason, e.getMessage())
                ));
            }
        });
    }

    void doForgotPassword(final String email) {
        startThreadWithProgressDialog(getString(R.string.sync_progress_processing), () -> {
            try {
                AppLog.d(TAG, "Sending form to server for forgot password...");

                Sync.forgotPassword(email);

                runOnUiThread(() -> MaterialDialogJavaHelper.showOkDialog(this, getString(R.string.sync_login_form_forgot_password_success)));
            } catch (Sync.NotOkException e) {
                AppLog.d(TAG, "Forgot password failed", e);

                runOnUiThread(() -> MaterialDialogJavaHelper.showOkDialog(this, e.getMessage()));
            }
        });
    }

    void doChangePassword(final String email, final String password, final String passwordNew) {
        startThreadWithProgressDialog(getString(R.string.sync_progress_processing), () -> {
            try {
                AppLog.d(TAG, "Sending form to server for changing password...");

                Sync.changePassword(email, password, passwordNew);

                runOnUiThread(() ->
                    MaterialDialogJavaHelper.showOkDialog(this, getString(R.string.sync_login_form_change_password_success))
                        .setOnDismissListener(dialog -> finish())
                );
            } catch (Sync.NotOkException e) {
                AppLog.d(TAG, "Change password failed", e);

                runOnUiThread(() -> MaterialDialogJavaHelper.showOkDialog(this, e.getMessage()));
            }
        });
    }

    void startThreadWithProgressDialog(final String message, final Runnable task) {
        final AlertDialog pd = new MaterialAlertDialogBuilder(this)
            .setMessage(message)
            .setCancelable(false)
            .show();

        Background.run(() -> {
            try {
                task.run();
            } finally {
                pd.dismiss();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        if (useCompose) return super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.activity_sync_login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.menuChangePassword) {
            bLogin.setVisibility(View.GONE);
            bRegister.setVisibility(View.GONE);
            bChangePassword.setVisibility(View.VISIBLE);

            tPasswordNew.setVisibility(View.VISIBLE);
            return true;
        } else if (itemId == R.id.menuSyncLog) {
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

                runOnUiThread(() -> {
                    if (isRegister) {
                        MaterialDialogJavaHelper.showOkDialog(this, getString(R.string.sync_registered_but_no_gcm));
                    } else {
                        MaterialDialogJavaHelper.showOkDialog(this, getString(R.string.sync_login_failed_with_reason, "Could not send FCM registration id. Please try again."));
                    }
                });
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
