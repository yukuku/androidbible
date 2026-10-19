package yuku.alkitab.base.sync

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.compose.toolbar.ToolbarScaffold
import yuku.alkitab.base.model.SyncLog
import yuku.alkitab.base.util.ClipboardUtil
import yuku.alkitab.debug.R

class SyncLogActivity : BaseActivity() {
    private var logs by mutableStateOf<List<SyncLog>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdgeComposeToolbar()
        SyncKotlin.observeWork(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    logs = loadLogs()
                    delay(2000)
                }
            }
        }
        setContent {
            BibleAppTheme {
                SyncLogScreen(
                    logs = logs,
                    isToolbarAtBottom = isToolbarAtBottom,
                    toolbarColor = Color(toolbarBackgroundColor),
                    onUp = { finish() },
                    onCopy = {
                        lifecycleScope.launch {
                            logs = loadLogs()
                            ClipboardUtil.copyToClipboard(formatSyncLogExport(logs))
                        }
                    },
                )
            }
        }
    }

    private suspend fun loadLogs(): List<SyncLog> = withContext(Dispatchers.IO) {
        val stored = try {
            App.services.storage.db.listLatestSyncLog(SyncDiagnostics.MAX_ROWS)
        } catch (error: RuntimeException) {
            SyncRecorder.error("gather", null, error)
            emptyList()
        }
        (stored + SyncRecorder.fallbackLogs()).sortedByDescending { it.createTime }
    }

    companion object {
        @JvmStatic
        fun createIntent(): Intent = Intent(App.context, SyncLogActivity::class.java)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncLogScreen(
    logs: List<SyncLog>,
    isToolbarAtBottom: Boolean,
    toolbarColor: Color,
    onUp: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolbarScaffold(
        isToolbarAtBottom = isToolbarAtBottom,
        toolbarColor = toolbarColor,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {},
        modifier = modifier,
        toolbar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_log_activity_title)) },
                navigationIcon = {
                    IconButton(onClick = onUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(androidx.appcompat.R.string.abc_action_bar_up_description))
                    }
                },
                actions = {
                    TextButton(onClick = onCopy) { Text(stringResource(R.string.bl_selection_copy), color = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = toolbarColor, titleContentColor = Color.White, navigationIconContentColor = Color.White),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(logs) { log ->
                SyncLogRow(log)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SyncLogRow(log: SyncLog, modifier: Modifier = Modifier) {
    val kind = SyncRecorder.EventKind.fromCode(log.kind_code)
    Column(modifier.fillMaxWidth().padding(8.dp)) {
        Text(formatSyncLogTime(log), style = MaterialTheme.typography.bodySmall)
        Text(
            syncLogEvent(log),
            modifier = Modifier.background(Color(kind?.backgroundColor ?: 0xff222222.toInt())).padding(4.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!log.params.isNullOrEmpty()) {
            Text(
                buildAnnotatedString {
                    log.params.entries.forEachIndexed { index, (key, value) ->
                        if (index > 0) append('\n')
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(key) }
                        append(" ${formatSyncLogValue(value)}")
                    }
                },
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatSyncLogTime(log: SyncLog): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(log.createTime)

private fun syncLogEvent(log: SyncLog): String = buildString {
    append("${log.kind_code} ${SyncRecorder.EventKind.fromCode(log.kind_code)?.name ?: "unknown"}")
    log.syncSetName?.let { append(" $it") }
}

private fun formatSyncLogValue(value: Any?): String = when {
    value is Double && value.isFinite() && value == value.toLong().toDouble() -> value.toLong().toString()
    else -> value.toString()
}

internal fun formatSyncLogExport(logs: List<SyncLog>): String = buildString {
    appendLine("Sync diagnostics (newest first; pruned from 600 to 500 stored events)")
    appendLine("App ${App.getVersionName()} (${App.getVersionCode()}); Android ${Build.VERSION.SDK_INT}")
    if (SyncRecorder.fallbackLogs().isNotEmpty()) appendLine("Diagnostic storage unavailable; some events are memory-only.")
    for (log in logs) {
        appendLine("${formatSyncLogTime(log)} ${syncLogEvent(log)}")
        log.params?.forEach { (key, value) -> appendLine("$key ${formatSyncLogValue(value)}") }
        appendLine()
    }
}
