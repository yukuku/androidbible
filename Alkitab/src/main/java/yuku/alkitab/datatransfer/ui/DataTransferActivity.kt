package yuku.alkitab.datatransfer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AnyThread
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
    private lateinit var tLog: TextView
    private lateinit var bSend: Button
    private lateinit var bStart: Button
    private lateinit var bClose: Button

    private val openImportFileRequest = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (mode != Mode.import) {
            return@registerForActivityResult
        }

        if (uri == null) {
            finish()
        } else {
            val jsonString = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    input.reader().readText()
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_transfer)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mode = Mode.values()[intent.getIntExtra("mode", 0)]
        tLog = findViewById(R.id.tLog)
        bSend = findViewById<Button>(R.id.bSend).apply {
            isEnabled = false
        }
        bStart = findViewById<Button>(R.id.bStart).apply {
            isEnabled = false
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
            Mode.import -> openImportFileRequest.launch(arrayOf("application/octet-stream"))
        }
    }

    private fun startExport() {
        val storage = ReadonlyStorageImpl()
        val log = LogInterface { line -> log(line) }
        val process = ExportProcess(storage, log, InstallationUtil.getInstallationId())

        thread {
            try {
                val result = process.export()
                runOnUiThread {
                    successfulExport(result)
                }
            } catch (e: Throwable) {
                log.log("Error occurred: $e")
            }
        }
    }

    private fun successfulExport(result: String) {
        bSend.isEnabled = true
        bSend.setOnClickListener {
            tLog.append(result) // TODO
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
            try {
                process.import(jsonString, options)
                runOnUiThread {
                    successfulImport(jsonString, options)
                }
            } catch (e: Throwable) {
                log.log("Error occurred, all changes have been rolled back: $e")
            }
        }
    }

    private fun successfulImport(jsonString: String, options: ImportProcess.Options) {
        if (!options.actualRun) {
            log("Press Start to do the actual import or Close to cancel")
            bStart.isEnabled = true
            bStart.setOnClickListener {
                bStart.isEnabled = false
                startImport(jsonString, true)
            }
        }
    }

    @AnyThread
    private fun log(line: String) {
        runOnUiThread {
            tLog.append(line.trim())
            tLog.append("\n")
        }
    }

    companion object {
        fun createIntent(context: Context, mode: Mode): Intent {
            return Intent(context, DataTransferActivity::class.java)
                .putExtra("mode", mode.ordinal)
        }
    }
}
