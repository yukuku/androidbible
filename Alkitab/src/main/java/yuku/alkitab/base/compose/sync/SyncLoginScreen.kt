package yuku.alkitab.base.compose.sync

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.util.PatternsCompat
import java.util.Locale
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.debug.R

/**
 * Callbacks the [SyncLoginScreen] delegates back to the hosting activity. The
 * activity keeps ownership of the network calls, progress dialogs and the
 * activity result, so the composable stays a pure, self-contained form.
 */
interface SyncLoginCallbacks {
    fun onUp()
    fun onRegister(email: String, password: String)
    fun onLogin(email: String, password: String)
    fun onForgotPassword(email: String)
    fun onChangePassword(email: String, oldPassword: String, newPassword: String)
    fun onOpenSyncLog()
}

/** Java-friendly bridge so [SyncLoginActivity] (still Java) can install the Compose UI. */
object SyncLoginComposeHost {
    @JvmStatic
    fun setContent(activity: ComponentActivity, callbacks: SyncLoginCallbacks) {
        activity.setContent {
            BibleAppTheme {
                SyncLoginScreen(
                    onUp = callbacks::onUp,
                    onRegister = callbacks::onRegister,
                    onLogin = callbacks::onLogin,
                    onForgotPassword = callbacks::onForgotPassword,
                    onChangePassword = callbacks::onChangePassword,
                    onOpenSyncLog = callbacks::onOpenSyncLog,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncLoginScreen(
    onUp: () -> Unit,
    onRegister: (email: String, password: String) -> Unit,
    onLogin: (email: String, password: String) -> Unit,
    onForgotPassword: (email: String) -> Unit,
    onChangePassword: (email: String, oldPassword: String, newPassword: String) -> Unit,
    onOpenSyncLog: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    var emailError by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordError by rememberSaveable { mutableStateOf<String?>(null) }
    var newPasswordError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmError by rememberSaveable { mutableStateOf<String?>(null) }

    var changePasswordMode by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val requiredMsg = stringResource(R.string.sync_login_form_error_required)
    val emailPatternMsg = stringResource(R.string.sync_login_form_error_email_pattern)
    val mismatchMsg = stringResource(R.string.sync_login_form_confirm_password_mismatch)

    // Normalizes and validates the email, updating [email] and [emailError] in place.
    fun validateEmail(lowercase: Boolean): Boolean {
        val e = email.trim().let { if (lowercase) it.lowercase(Locale.US) else it }
        email = e
        return when {
            e.isEmpty() -> {
                emailError = requiredMsg
                false
            }

            !PatternsCompat.EMAIL_ADDRESS.matcher(e).matches() -> {
                emailError = emailPatternMsg
                false
            }

            else -> {
                emailError = null
                true
            }
        }
    }

    fun onRegisterClick() {
        // don't allow uppercase when registering, but we still allow it for login (because some
        // user accounts were already created using uppercase)
        var ok = validateEmail(lowercase = true)
        if (password.isEmpty()) {
            passwordError = requiredMsg
            ok = false
        } else {
            passwordError = null
        }
        if (confirmPassword != password) {
            confirmError = mismatchMsg
            ok = false
        } else {
            confirmError = null
        }
        if (ok) onRegister(email, password)
    }

    fun onLoginClick() {
        var ok = validateEmail(lowercase = false)
        if (password.isEmpty()) {
            passwordError = requiredMsg
            ok = false
        } else {
            passwordError = null
        }
        if (ok) onLogin(email, password)
    }

    fun onForgotClick() {
        val e = email.trim()
        email = e
        if (e.isEmpty()) {
            emailError = requiredMsg
            return
        }
        emailError = null
        onForgotPassword(e)
    }

    fun onChangePasswordClick() {
        var ok = validateEmail(lowercase = false)
        if (password.isEmpty()) {
            passwordError = requiredMsg
            ok = false
        } else {
            passwordError = null
        }
        if (newPassword.isEmpty()) {
            newPasswordError = requiredMsg
            ok = false
        } else {
            newPasswordError = null
        }
        if (confirmPassword != newPassword) {
            confirmError = mismatchMsg
            ok = false
        } else {
            confirmError = null
        }
        if (ok) onChangePassword(email, password, newPassword)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_login_activity_title)) },
                navigationIcon = {
                    IconButton(onClick = onUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sync_login_change_password_menu_item)) },
                            onClick = {
                                changePasswordMode = true
                                confirmPassword = ""
                                confirmError = null
                                menuOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sync_menu_item_sync_log)) },
                            onClick = {
                                menuOpen = false
                                onOpenSyncLog()
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.sync_intro),
                contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .align(Alignment.CenterHorizontally),
            )

            LinkText(
                textResId = R.string.sync_login_form_intro_text,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .align(Alignment.CenterHorizontally),
            )

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    emailError = null
                },
                label = { Text(stringResource(R.string.sync_login_form_email_hint)) },
                singleLine = true,
                isError = emailError != null,
                supportingText = emailError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )

            PasswordField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = null
                },
                labelResId = R.string.sync_login_form_password_hint,
                error = passwordError,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            if (changePasswordMode) {
                PasswordField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        newPasswordError = null
                    },
                    labelResId = R.string.sync_login_form_new_password_hint,
                    error = newPasswordError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            } else {
                TextButton(
                    onClick = { onForgotClick() },
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.End),
                ) {
                    Text(stringResource(R.string.sync_login_form_forgot_button))
                }
            }

            PasswordField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    confirmError = null
                },
                labelResId = R.string.sync_login_dialog_confirm_password_hint,
                error = confirmError,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            LinkText(
                textResId = R.string.sync_login_form_privacy_text,
                modifier = Modifier.padding(top = 12.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (changePasswordMode) {
                Button(
                    onClick = { onChangePasswordClick() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sync_login_change_password_button))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onRegisterClick() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.sync_login_form_register_button))
                    }
                    Button(
                        onClick = { onLoginClick() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.sync_login_form_login_button))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    labelResId: Int,
    error: String?,
    modifier: Modifier = Modifier,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelResId)) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            val icon = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
            val desc = stringResource(
                if (visible) R.string.sync_login_form_hide_password else R.string.sync_login_form_show_password
            )
            IconButton(onClick = { visible = !visible }) {
                Icon(icon, contentDescription = desc)
            }
        },
        modifier = modifier,
    )
}

/**
 * Renders a string resource that contains HTML `<a>` links (intro / privacy texts) with the links
 * clickable, using a plain [TextView] because the source strings carry Android markup spans.
 */
@Composable
private fun LinkText(textResId: Int, modifier: Modifier = Modifier) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = modifier.wrapContentWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { tv ->
            tv.text = tv.context.getText(textResId)
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
        },
    )
}
