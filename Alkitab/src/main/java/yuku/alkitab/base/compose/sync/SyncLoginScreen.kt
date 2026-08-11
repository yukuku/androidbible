package yuku.alkitab.base.compose.sync

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.util.PatternsCompat
import java.util.Locale
import kotlinx.coroutines.launch
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.debug.R

/**
 * Reports the outcome of a network action back to the composable so it can drop its loading state
 * and surface a message inline (via a Snackbar) instead of a dialog. Invoked on the UI thread.
 * [message] is the error text on failure, or a success message to show (or `null` when the activity
 * simply finishes, e.g. after a successful sign-in).
 */
fun interface SyncActionResultCallback {
    fun onResult(success: Boolean, message: String?)
}

/**
 * Callbacks the [SyncLoginScreen] delegates back to the hosting activity. The activity keeps
 * ownership of the network calls and the activity result; the composable owns all UI, including
 * validation, the loading indicator and result messages — so the flow needs no dialogs.
 */
interface SyncLoginCallbacks {
    fun onUp()
    fun login(email: String, password: String, callback: SyncActionResultCallback)
    fun register(email: String, password: String, callback: SyncActionResultCallback)
    fun forgotPassword(email: String, callback: SyncActionResultCallback)
    fun changePassword(email: String, oldPassword: String, newPassword: String, callback: SyncActionResultCallback)
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
                    onLogin = { email, password, onResult -> callbacks.login(email, password) { s, m -> onResult(s, m) } },
                    onRegister = { email, password, onResult -> callbacks.register(email, password) { s, m -> onResult(s, m) } },
                    onForgotPassword = { email, onResult -> callbacks.forgotPassword(email) { s, m -> onResult(s, m) } },
                    onChangePassword = { email, oldPassword, newPassword, onResult ->
                        callbacks.changePassword(email, oldPassword, newPassword) { s, m -> onResult(s, m) }
                    },
                    onOpenSyncLog = callbacks::onOpenSyncLog,
                )
            }
        }
    }
}

/** The distinct things a user can do on this screen. Only one is active at a time, so each flow
 *  shows only its own fields and a single primary button. */
private enum class SyncLoginMode { SIGN_IN, CREATE_ACCOUNT, RESET, CHANGE_PASSWORD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncLoginScreen(
    onUp: () -> Unit,
    onLogin: (email: String, password: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onRegister: (email: String, password: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onForgotPassword: (email: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onChangePassword: (email: String, oldPassword: String, newPassword: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onOpenSyncLog: () -> Unit,
) {
    var modeName by rememberSaveable { mutableStateOf(SyncLoginMode.SIGN_IN.name) }
    val mode = SyncLoginMode.valueOf(modeName)

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    var emailError by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordError by rememberSaveable { mutableStateOf<String?>(null) }
    var newPasswordError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmError by rememberSaveable { mutableStateOf<String?>(null) }

    var submitting by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val requiredMsg = stringResource(R.string.sync_login_form_error_required)
    val emailPatternMsg = stringResource(R.string.sync_login_form_error_email_pattern)
    val mismatchMsg = stringResource(R.string.sync_login_form_confirm_password_mismatch)

    fun clearErrors() {
        emailError = null
        passwordError = null
        newPasswordError = null
        confirmError = null
    }

    fun switchMode(target: SyncLoginMode) {
        if (submitting) return
        modeName = target.name
        clearErrors()
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

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

    fun handleResult(success: Boolean, message: String?) {
        submitting = false
        // On a successful sign-in/create the activity finishes and there is nothing to show.
        if (message != null) showMessage(message)
    }

    fun submit() {
        clearErrors()
        when (mode) {
            SyncLoginMode.SIGN_IN -> {
                var ok = validateEmail(lowercase = false)
                if (password.isEmpty()) {
                    passwordError = requiredMsg
                    ok = false
                }
                if (!ok) return
                submitting = true
                onLogin(email, password) { s, m -> handleResult(s, m) }
            }

            SyncLoginMode.CREATE_ACCOUNT -> {
                // don't allow uppercase when registering, but we still allow it for login (because
                // some user accounts were already created using uppercase)
                var ok = validateEmail(lowercase = true)
                if (password.isEmpty()) {
                    passwordError = requiredMsg
                    ok = false
                }
                if (confirmPassword != password) {
                    confirmError = mismatchMsg
                    ok = false
                }
                if (!ok) return
                submitting = true
                onRegister(email, password) { s, m -> handleResult(s, m) }
            }

            SyncLoginMode.RESET -> {
                if (!validateEmail(lowercase = false)) return
                submitting = true
                onForgotPassword(email) { s, m -> handleResult(s, m) }
            }

            SyncLoginMode.CHANGE_PASSWORD -> {
                var ok = validateEmail(lowercase = false)
                if (password.isEmpty()) {
                    passwordError = requiredMsg
                    ok = false
                }
                if (newPassword.isEmpty()) {
                    newPasswordError = requiredMsg
                    ok = false
                }
                if (confirmPassword != newPassword) {
                    confirmError = mismatchMsg
                    ok = false
                }
                if (!ok) return
                submitting = true
                onChangePassword(email, password, newPassword) { s, m -> handleResult(s, m) }
            }
        }
    }

    val titleRes = when (mode) {
        SyncLoginMode.SIGN_IN -> R.string.sync_login_mode_sign_in
        SyncLoginMode.CREATE_ACCOUNT -> R.string.sync_login_mode_create_account
        SyncLoginMode.RESET -> R.string.sync_login_reset_password_title
        SyncLoginMode.CHANGE_PASSWORD -> R.string.sync_login_change_password_button
    }
    val actionRes = when (mode) {
        SyncLoginMode.SIGN_IN -> R.string.sync_login_mode_sign_in
        SyncLoginMode.CREATE_ACCOUNT -> R.string.sync_login_mode_create_account
        SyncLoginMode.RESET -> R.string.sync_login_action_send_reset_email
        SyncLoginMode.CHANGE_PASSWORD -> R.string.sync_login_change_password_button
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // The blue-gray of every View toolbar, so this screen's chrome
                // matches the rest of the app.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                title = { Text(stringResource(titleRes)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // A theme-tinted vector, so it stays visible in both light and dark. (The legacy XML
            // screen keeps its own raster illustration, which was drawn for a dark background.)
            Icon(
                imageVector = Icons.Filled.CloudSync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(72.dp)
                    .align(Alignment.CenterHorizontally),
            )

            LinkText(
                textResId = R.string.sync_login_form_intro_text,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .align(Alignment.CenterHorizontally),
            )

            // The primary-flow picker has just the two account flows. Reset is reached from the
            // "Forgot password?" link, and Change password from the link at the bottom.
            if (mode == SyncLoginMode.SIGN_IN || mode == SyncLoginMode.CREATE_ACCOUNT) {
                val segments = listOf(
                    SyncLoginMode.SIGN_IN to R.string.sync_login_mode_sign_in,
                    SyncLoginMode.CREATE_ACCOUNT to R.string.sync_login_mode_create_account,
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    segments.forEachIndexed { index, (segMode, labelRes) ->
                        SegmentedButton(
                            selected = mode == segMode,
                            onClick = { switchMode(segMode) },
                            enabled = !submitting,
                            shape = SegmentedButtonDefaults.itemShape(index, segments.size),
                            // Drop the check icon: on narrow (~320dp) screens it steals the width the
                            // label needs, forcing an ellipsis. Without it the label can wrap instead.
                            icon = {},
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    emailError = null
                },
                label = { Text(stringResource(R.string.sync_login_form_email_hint)) },
                singleLine = true,
                enabled = !submitting,
                isError = emailError != null,
                supportingText = emailError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )

            if (mode != SyncLoginMode.RESET) {
                PasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = null
                    },
                    labelResId = if (mode == SyncLoginMode.CHANGE_PASSWORD) {
                        R.string.sync_login_current_password_hint
                    } else {
                        R.string.sync_login_form_password_hint
                    },
                    error = passwordError,
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            if (mode == SyncLoginMode.SIGN_IN) {
                TextButton(
                    onClick = { switchMode(SyncLoginMode.RESET) },
                    enabled = !submitting,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.End),
                ) {
                    Text(stringResource(R.string.sync_login_forgot_password_link))
                }
            }

            if (mode == SyncLoginMode.CHANGE_PASSWORD) {
                PasswordField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        newPasswordError = null
                    },
                    labelResId = R.string.sync_login_form_new_password_hint,
                    error = newPasswordError,
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            if (mode == SyncLoginMode.CREATE_ACCOUNT || mode == SyncLoginMode.CHANGE_PASSWORD) {
                PasswordField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        confirmError = null
                    },
                    labelResId = R.string.sync_login_dialog_confirm_password_hint,
                    error = confirmError,
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            if (mode == SyncLoginMode.RESET) {
                Text(
                    text = stringResource(R.string.sync_login_reset_password_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (mode != SyncLoginMode.CHANGE_PASSWORD) {
                LinkText(
                    textResId = R.string.sync_login_form_privacy_text,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { submit() },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                } else {
                    Text(stringResource(actionRes))
                }
            }

            if (mode == SyncLoginMode.SIGN_IN) {
                TextButton(
                    onClick = { switchMode(SyncLoginMode.CHANGE_PASSWORD) },
                    enabled = !submitting,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.sync_login_change_password_button))
                }
            }

            if (mode == SyncLoginMode.RESET || mode == SyncLoginMode.CHANGE_PASSWORD) {
                TextButton(
                    onClick = { switchMode(SyncLoginMode.SIGN_IN) },
                    enabled = !submitting,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.sync_login_back_to_sign_in))
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
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelResId)) },
        singleLine = true,
        enabled = enabled,
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
