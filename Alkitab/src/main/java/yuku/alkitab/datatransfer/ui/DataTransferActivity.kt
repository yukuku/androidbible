package yuku.alkitab.datatransfer.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AnyThread
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.util.InstallationUtil
import yuku.alkitab.datatransfer.process.ExportProcess
import yuku.alkitab.datatransfer.process.ImportProcess
import yuku.alkitab.datatransfer.process.LogInterface
import yuku.alkitab.datatransfer.process.ReadWriteStorageImpl
import yuku.alkitab.datatransfer.process.ReadonlyStorageImpl
import yuku.alkitab.debug.R

class DataTransferActivity : BaseActivity() {
    enum class Mode {
        export,
        import,
    }

    private lateinit var mode: Mode
    private lateinit var scroll: ScrollView
    private lateinit var tLog: TextView
    private lateinit var progress: ProgressBar
    private lateinit var bSend: Button
    private lateinit var bStart: Button
    private lateinit var bClose: Button

    private val openImportFileRequest = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (mode != Mode.import) return@registerForActivityResult

        if (uri == null) {
            finish()
            return@registerForActivityResult
        }

        val jsonString = try {
            contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            }
        } catch (e: Exception) {
            null
        }
        if (jsonString == null) {
            log("Could not read contents from $uri")
        } else {
            startImport(jsonString, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_transfer)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mode = Mode.values()[intent.getIntExtra("mode", 0)]
        scroll = findViewById(R.id.scroll)
        tLog = findViewById(R.id.tLog)
        progress = findViewById<ProgressBar>(R.id.progress).apply {
            visibility = View.INVISIBLE
        }
        bSend = findViewById<Button>(R.id.bSend).apply {
            isEnabled = false
            if (mode == Mode.import) visibility = View.GONE
        }
        bStart = findViewById<Button>(R.id.bStart).apply {
            isEnabled = false
            if (mode == Mode.export) visibility = View.GONE
        }
        bClose = findViewById<Button>(R.id.bClose).apply {
            setOnClickListener {
                finish()
            }
        }

        when (mode) {
            Mode.export -> setTitle(R.string.data_transfer_export_title)
            Mode.import -> setTitle(R.string.data_transfer_import_title)
        }

        when (mode) {
            Mode.export -> startExport()
            Mode.import -> openImportFileRequest.launch(arrayOf("application/json", "application/octet-stream"))
        }
    }

    private fun startExport() {
        val storage = ReadonlyStorageImpl()
        val log = LogInterface { line -> log(line) }
        val process = ExportProcess(storage, log, InstallationUtil.getInstallationId())

        thread {
            showInProgress {
                try {
                    val result = process.export()
                    runOnUiThread {
                        successfulExport(result)
                    }
                } catch (e: Throwable) {
                    logBold("Error occurred: $e")
                }
            }
        }
    }

    private fun successfulExport(result: String) {
        logBold("Press [Send] to send your exported data to another app of your choice.")
        bSend.isEnabled = true
        bSend.setOnClickListener {
            bSend.isEnabled = false

            try {
                val dir = File(this.cacheDir, "data_transfer")
                dir.mkdir()

                val date = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())
                val file = File(dir, "${getString(R.string.app_name)} data ${date}.export.json")
                file.writeText(result)

                val uri = FileProvider.getUriForFile(this, "$packageName.file_provider", file)

                ShareCompat.IntentBuilder(this)
                    .setType("application/octet-stream")
                    .addStream(uri)
                    .startChooser()

            } catch (e: Exception) {
                logBold("Error while preparing exported data: $e")
            }
        }
    }

    private fun startImport(jsonString: String, actualRun: Boolean) {
        val storage = ReadWriteStorageImpl()
        val log = LogInterface { line -> log(line) }
        val process = ImportProcess(storage, log)
        val options = ImportProcess.Options(
            importHistory = true,
            importMabel = true,
            importPins = true,
            importRpp = true,
            actualRun = actualRun,
        )

        thread {
            showInProgress {
                try {
                    process.import(jsonString, options)
                    runOnUiThread {
                        successfulImport(jsonString, options)
                    }
                } catch (e: Throwable) {
                    logBold("Error occurred, all changes have been rolled back: $e")
                }
            }
        }
    }

    private fun successfulImport(jsonString: String, options: ImportProcess.Options) {
        if (!options.actualRun) {
            logBold("The operations above are only simulation. Press [Start] to start the actual import or [Close] to cancel.")
            bStart.isEnabled = true
            bStart.setOnClickListener {
                bStart.isEnabled = false
                startImport(jsonString, true)
            }
        } else {
            logBold("Done. Press [Close] to end the import operation.")
        }
    }

    @AnyThread
    private fun log(line: String) {
        // artificial delay
        if (Thread.currentThread() != Looper.getMainLooper().thread) SystemClock.sleep(200)

        runOnUiThread {
            tLog.append(line.trim())
            tLog.append("\n")
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    @AnyThread
    private fun logBold(line: String) = runOnUiThread {
        tLog.append(buildSpannedString {
            bold { color(Color.YELLOW) { append(line.trim()) } }
            appendLine()
        })
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    @AnyThread
    private fun showInProgress(action: () -> Unit) {
        runOnUiThread { progress.visibility = View.VISIBLE }
        action()
        runOnUiThread { progress.visibility = View.INVISIBLE }
    }

    companion object {
        fun createIntent(context: Context, mode: Mode): Intent {
            return Intent(context, DataTransferActivity::class.java)
                .putExtra("mode", mode.ordinal)
        }
    }
}
