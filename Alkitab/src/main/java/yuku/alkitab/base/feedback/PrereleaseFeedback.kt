package yuku.alkitab.base.feedback

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.compose.ComposeBottomSheetHost
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R

object PrereleaseFeedback {
    val version: PrereleaseVersion? = PrereleaseVersion.parse(App.getVersionName())

    fun isReaderButtonVisible(): Boolean =
        version != null && Preferences.getString(Prefkey.prerelease_feedback_hidden_commit) != BuildConfig.LAST_COMMIT_HASH

    fun show(activity: Activity, onHidden: (() -> Unit)?) {
        val version = version ?: return
        ComposeBottomSheetHost.show(activity) { dismiss ->
            FeedbackSheetContent(
                label = version.label,
                onSendEmail = {
                    val subject = version.emailSubject(
                        activity.getString(R.string.app_name), App.getVersionName(), BuildConfig.LAST_COMMIT_HASH,
                    )
                    val uri = Uri.parse("mailto:help@alkitab.app?subject=${Uri.encode(subject)}")
                    try {
                        activity.startActivity(Intent(Intent.ACTION_SENDTO, uri))
                        dismiss()
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(activity, R.string.prerelease_feedback_no_email_app, Toast.LENGTH_LONG).show()
                    }
                },
                onHide = onHidden?.let { callback ->
                    {
                        Preferences.setString(Prefkey.prerelease_feedback_hidden_commit, BuildConfig.LAST_COMMIT_HASH)
                        callback()
                        dismiss()
                    }
                },
            )
        }
    }
}

@Composable
private fun FeedbackSheetContent(
    label: String,
    onSendEmail: () -> Unit,
    onHide: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val feedbackButtonText = stringResource(R.string.prerelease_feedback_button, label)
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = feedbackButtonText,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(stringResource(R.string.prerelease_feedback_message, label))
        Button(onClick = onSendEmail, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.prerelease_feedback_send_email))
        }
        if (onHide != null) {
            TextButton(onClick = onHide, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.prerelease_feedback_hide, feedbackButtonText),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
