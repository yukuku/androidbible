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
import java.util.UUID
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

        val inputStream = contentResolver.openInputStream(uri)

        if (inputStream == null) {
            log("Could not read contents from $uri")
        } else {
            // Do not try to load to memory. Copy to cache file.
            val cacheFile = File(this.cacheDir, "data-import-tmp-${UUID.randomUUID()}.json")

            try {
                inputStream.use { input ->
                    cacheFile.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }

                startImport(cacheFile, false)
            } catch (e: Exception) {
                logBold("Error occurred: ${e.stackTraceToString()}")
            }
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
                    val dir = File(this.cacheDir, "data_transfer")
                    dir.mkdir()

                    val date = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date())

                    val cacheFile = File(dir, "${getString(R.string.app_name)} data ${date}.export.json")

                    process.export(cacheFile)

                    runOnUiThread {
                        successfulExport(cacheFile)
                    }
                } catch (e: Throwable) {
                    logBold("Error occurred: ${e.stackTraceToString()}")
                }
            }
        }
    }

    private fun successfulExport(cacheFile: File) {
        logBold("Press [Send] to send your exported data to another app of your choice.")
        bSend.isEnabled = true
        bSend.setOnClickListener {
            bSend.isEnabled = false

            try {
                val uri = FileProvider.getUriForFile(this, "$packageName.file_provider", cacheFile)

                ShareCompat.IntentBuilder(this)
                    .setType("application/octet-stream")
                    .addStream(uri)
                    .startChooser()

            } catch (e: Exception) {
                logBold("Error while preparing exported data: $e")
            }
        }
    }

    private fun startImport(cacheFile: File, actualRun: Boolean) {
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
                    process.import(cacheFile, options)
                    runOnUiThread {
                        successfulImport(cacheFile, options)
                    }
                } catch (e: Throwable) {
                    logBold("Error occurred, all changes have been rolled back: ${e.stackTraceToString()}")
                }
            }
        }
    }

    private fun successfulImport(cacheFile: File, options: ImportProcess.Options) {
        if (!options.actualRun) {
            logBold("The operations above are only simulation. Press [Start] to start the actual import or [Close] to cancel.")
            bStart.isEnabled = true
            bStart.setOnClickListener {
                bStart.isEnabled = false
                startImport(cacheFile, true)
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
