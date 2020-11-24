package yuku.alkitab.datatransfer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import kotlin.concurrent.thread
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.util.InstallationUtil
import yuku.alkitab.datatransfer.process.ExportProcess
import yuku.alkitab.datatransfer.process.LogInterface
import yuku.alkitab.datatransfer.process.StorageImpl
import yuku.alkitab.debug.R

class DataTransferActivity : BaseActivity() {
    enum class Mode {
        export,
        import,
    }

    private lateinit var mode: Mode
    private lateinit var tLog: TextView
    private lateinit var bSend: Button
    private lateinit var bClose: Button

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
        bClose = findViewById(R.id.bClose)

        when (mode) {
            Mode.export -> setTitle(R.string.data_transfer_export_title)
            Mode.import -> setTitle(R.string.data_transfer_import_title)
        }

        when (mode) {
            Mode.export -> {
                startExport()
            }
        }
    }

    private fun startExport() {
        val storage = StorageImpl()
        val log = LogInterface { line -> log(line) }
        val process = ExportProcess(storage, log, InstallationUtil.getInstallationId())

        thread {
            try {
                val result = process.export()
                runOnUiThread {
                    successfulExport(result)
                }
            } catch (e: Throwable) {
                log.log("Error occured: $e")
            }
        }
    }

    private fun successfulExport(result: String) {
        bSend.isEnabled = true
        bSend.setOnClickListener {

        }
    }

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
