package yuku.alkitab.base.ac

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.IsiActivity
import yuku.alkitab.base.S
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.model.SyncShadow
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.sync.Sync
import yuku.alkitab.base.sync.Sync.ApplyAppendDeltaResult
import yuku.alkitab.base.sync.Sync.SyncResponseJson
import yuku.alkitab.base.sync.Sync_History
import yuku.alkitab.base.sync.Sync_Mabel
import yuku.alkitab.base.sync.Sync_Pins
import yuku.alkitab.base.sync.Sync_Rp
import yuku.alkitab.base.util.Background
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.util.InstallationUtil.getInstallationId
import yuku.alkitab.base.util.LabelColorUtil.encodeBackground
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.model.Label
import yuku.alkitab.model.Marker
import yuku.alkitab.model.Marker_Label

class SecretSyncDebugActivity : BaseActivity() {
    private lateinit var tServer: EditText
    private lateinit var tUserEmail: EditText
    private lateinit var cMakeDirtyMarker: CheckBox
    private lateinit var cMakeDirtyLabel: CheckBox
    private lateinit var cMakeDirtyMarker_Label: CheckBox
    private lateinit var cbSyncSetName: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secret_sync_debug)
        tServer = findViewById(R.id.tServer)
        tUserEmail = findViewById(R.id.tUserEmail)
        cMakeDirtyMarker = findViewById(R.id.cMakeDirtyMarker)
        cMakeDirtyLabel = findViewById(R.id.cMakeDirtyLabel)
        cMakeDirtyMarker_Label = findViewById(R.id.cMakeDirtyMarker_Label)

        findViewById<View>(R.id.bServerSave).setOnClickListener {
            MaterialDialog(this).show {
                message(text = "This will reset your synced shadow to revision 0.")
                positiveButton(R.string.ok) {
                    Preferences.setString(Prefkey.sync_server_prefix, tServer.text.toString().trim())

                    // do the same as logging out
                    bLogout_click.onClick(null)
                }
                negativeButton(R.string.cancel)
            }
        }

        findViewById<View>(R.id.bServerReset).setOnClickListener {
            MaterialDialog(this).show {
                message(text = "This will reset your synced shadow to revision 0.")
                positiveButton(R.string.ok) {
                    Preferences.remove(Prefkey.sync_server_prefix)

                    // do the same as logging out
                    bLogout_click.onClick(null)

                    tServer.setText("")
                }
                negativeButton(R.string.cancel)
            }
        }

        findViewById<View>(R.id.bMabelClientState).setOnClickListener(bMabelClientState_click)
        findViewById<View>(R.id.bGenerateDummies).setOnClickListener(bGenerateDummies_click)
        findViewById<View>(R.id.bGenerateDummies2).setOnClickListener(bGenerateDummies2_click)
        findViewById<View>(R.id.bMabelMonkey).setOnClickListener(bMabelMonkey_click)
        findViewById<View>(R.id.bLogout).setOnClickListener(bLogout_click)
        findViewById<View>(R.id.bSync).setOnClickListener(bSync_click)

        cbSyncSetName = findViewById(R.id.cbSyncSetName)
        cbSyncSetName.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, SyncShadow.ALL_SYNC_SET_NAMES)

        findViewById<View>(R.id.bCheckHash).setOnClickListener(bCheckHash_click)
    }

    private var bMabelClientState_click = View.OnClickListener {
        val sb = StringBuilder()
        val pair = Sync_Mabel.getClientStateAndCurrentEntities()
        val clientState = pair.clientState
        sb.append("Base revno: ").append(clientState.base_revno).append('\n')
        sb.append("Delta operations (size ").append(clientState.delta.operations.size).append("):\n")
        for (operation in clientState.delta.operations) {
            sb.append("\u2022 ").append(operation).append('\n')
        }

        MaterialDialog(this).show {
            message(text = sb)
            positiveButton(R.string.ok)
        }
    }

    fun rand(n: Int): Int {
        return (Math.random() * n).toInt()
    }

    private var bGenerateDummies_click = View.OnClickListener {
        val label1 = S.getDb().insertLabel(randomString("L1_", 1, 3, 8), encodeBackground(rand(0xffffff)))
        val label2 = S.getDb().insertLabel(randomString("L2_", 1, 3, 8), encodeBackground(rand(0xffffff)))
        for (i in 0..9) {
            val marker = S.getDb().insertMarker(0x000101 + rand(30), Marker.Kind.values()[rand(3)], randomString("M" + i + "_", rand(2) + 1, 4, 7), rand(2) + 1, Date(), Date())
            val labelSet: MutableSet<Label> = HashSet()
            if (rand(10) < 5) {
                labelSet.add(label1)
            }
            if (rand(10) < 3) {
                labelSet.add(label2)
            }
            S.getDb().updateLabels(marker, labelSet)
        }

        MaterialDialog(this).show {
            message(text = "10 markers, 2 labels generated.")
            positiveButton(R.string.ok)
        }
    }

    private var bGenerateDummies2_click = View.OnClickListener {
        val label1 = S.getDb().insertLabel(randomString("LL1_", 1, 3, 8), encodeBackground(rand(0xffffff)))
        val label2 = S.getDb().insertLabel(randomString("LL2_", 1, 3, 8), encodeBackground(rand(0xffffff)))
        for (i in 0..999) {
            val kind = Marker.Kind.values()[rand(3)]
            val now = Date()
            val marker = S.getDb().insertMarker(0x000101 + rand(30), kind, if (kind == Marker.Kind.highlight) Highlights.encode(rand(0xffffff)) else randomString("MM" + i + "_", if (rand(10) < 5) rand(81) else rand(400) + 4, 5, 15), rand(2) + 1, now, now)
            val labelSet: MutableSet<Label> = HashSet()
            if (rand(10) < 1) {
                labelSet.add(label1)
            }
            if (rand(10) < 4) {
                labelSet.add(label2)
            }
            S.getDb().updateLabels(marker, labelSet)
        }

        MaterialDialog(this).show {
            message(text = "1000 markers, 2 labels generated.")
            positiveButton(R.string.ok)
        }
    }

    var toastHandler = Handler()

    inner class MonkeyThread @SuppressLint("ShowToast") constructor() : Thread() {
        private val stopRequested = AtomicBoolean()
        private val toast = Toast.makeText(this@SecretSyncDebugActivity, "none", Toast.LENGTH_SHORT)

        private fun toast(msg: String?) {
            toastHandler.post {
                toast.setText(msg)
                toast.show()
            }
        }

        override fun run() {
            while (!stopRequested.get()) {
                toast("preparing")
                SystemClock.sleep(5000)

                run {
                    val nlabel = rand(5)
                    toast("creating $nlabel labels")
                    for (i in 0 until nlabel) {
                        S.getDb().insertLabel(randomString("monkey L $i ", 1, 3, 8), encodeBackground(rand(0xffffff)))
                    }
                }

                if (stopRequested.get()) return
                toast("waiting for 10 secs")
                SystemClock.sleep(10000)

                val labels = S.getDb().listAllLabels()

                run {
                    val nmarker = rand(500)
                    toast("creating $nmarker markers")
                    for (i in 0 until nmarker) {
                        val kind = Marker.Kind.values()[rand(3)]
                        val now = Date()
                        val marker = S.getDb().insertMarker(0x000101 + rand(30), kind, if (kind == Marker.Kind.highlight) Highlights.encode(rand(0xffffff)) else randomString("monkey M $i ", rand(8) + 2, 3, 5), rand(2) + 1, now, now)
                        if (rand(10) < 1 && labels.size > 0) {
                            val labelSet: MutableSet<Label> = HashSet()
                            labelSet.add(labels[rand(labels.size)])
                            S.getDb().updateLabels(marker, labelSet)
                        }
                    }
                }

                if (stopRequested.get()) return
                toast("waiting for 10 secs")
                SystemClock.sleep(10000)

                val markers = S.getDb().listAllMarkers()
                if (markers.size > 10) {
                    val nmarker = rand(markers.size / 10)
                    toast("deleting up to 10% of markers: $nmarker markers")
                    for (i in 0 until nmarker) {
                        val marker = markers[rand(markers.size)]
                        markers.remove(marker)
                        val mls = S.getDb().listMarker_LabelsByMarker(marker)
                        for (ml in mls) {
                            S.getDb().deleteMarker_LabelByGid(ml.gid)
                        }
                        S.getDb().deleteMarkerByGid(marker.gid)
                    }
                }

                if (stopRequested.get()) return
                toast("waiting for 10 secs")
                SystemClock.sleep(10000)

                if (labels.size > 10) {
                    val nlabel = rand(labels.size / 10)
                    toast("deleting up to 10% of label: $nlabel labels")
                    for (i in 0 until nlabel) {
                        val label = labels[rand(labels.size)]
                        labels.remove(label)
                        S.getDb().deleteLabelAndMarker_LabelsByLabelId(label._id)
                    }
                }

                if (stopRequested.get()) return
                toast("waiting for 10 secs")
                SystemClock.sleep(10000)

                run {
                    val nmarker = rand(markers.size / 5)
                    toast("editing up to 20% of markers: $nmarker markers")
                    for (i in 0 until nmarker) {
                        val marker = markers[rand(markers.size)]
                        marker.caption = randomString("monkey edit M $i ", rand(8) + 2, 3, 5)
                        S.getDb().insertOrUpdateMarker(marker)
                    }
                }

                if (stopRequested.get()) return
                toast("waiting for 40 secs")
                SystemClock.sleep(40000)
            }
        }

        fun requestStop() {
            stopRequested.set(true)
        }
    }

    private val bMabelMonkey_click = View.OnClickListener {
        if (!BuildConfig.DEBUG) return@OnClickListener

        monkey?.let {
            it.requestStop()
            monkey = null
            MaterialDialog(this).show {
                message(text = "monkey stopped")
            }
            return@OnClickListener
        }
    }

    private fun randomString(prefix: String, word_count: Int, minwordlen: Int, maxwordlen: Int): String {
        val sb = StringBuilder(prefix)
        for (i in 0 until word_count) {
            var j = 0
            val wordlen = rand(maxwordlen - minwordlen) + minwordlen
            while (j < wordlen) {
                if (j % 2 == 0) {
                    sb.append("bcdfghjklmnpqrstvwyz"[rand(20)])
                } else {
                    sb.append("aeiou"[rand(5)])
                }
                j++
            }
            if (i != word_count - 1) {
                sb.append(' ')
            }
        }
        return sb.toString()
    }

    private var bLogout_click = View.OnClickListener {
        Preferences.hold()
        Preferences.remove(getString(R.string.pref_syncAccountName_key))
        Preferences.remove(Prefkey.sync_simpleToken)
        Preferences.remove(Prefkey.sync_token_obtained_time)
        Preferences.unhold()

        for (syncSetName in SyncShadow.ALL_SYNC_SET_NAMES) {
            S.getDb().deleteSyncShadowBySyncSetName(syncSetName)
        }
    }

    private var bSync_click = View.OnClickListener {
        val simpleToken = Preferences.getString(Prefkey.sync_simpleToken)
        if (simpleToken == null) {
            MaterialDialog(this).show {
                message(text = "not logged in")
                positiveButton(R.string.ok)
            }
            return@OnClickListener
        }

        val pair = Sync_Mabel.getClientStateAndCurrentEntities()
        val clientState = pair.clientState
        val entitiesBeforeSync = pair.currentEntities

        val requestBody = FormBody.Builder()
            .add("simpleToken", simpleToken)
            .add("syncSetName", SyncShadow.SYNC_SET_MABEL)
            .add("installation_id", getInstallationId())
            .add("clientState", App.getDefaultGson().toJson(clientState))
            .build()

        val call = Connections.longTimeoutOkHttpClient.newCall(
            Request.Builder()
                .url(Sync.getEffectiveServerPrefix() + "sync/api/sync")
                .post(requestBody)
                .build()
        )

        if (cMakeDirtyMarker.isChecked) {
            S.getDb().insertMarker(0x000101 + rand(30), Marker.Kind.values()[rand(3)], randomString("MMD0_", rand(2) + 1, 4, 7), rand(2) + 1, Date(), Date())
        }

        if (cMakeDirtyLabel.isChecked) {
            S.getDb().insertLabel(randomString("LMD_", 1, 3, 8), encodeBackground(rand(0xffffff)))
        }

        if (cMakeDirtyMarker_Label.isChecked) {
            val labels = S.getDb().listAllLabels()
            val markers = S.getDb().listAllMarkers()
            if (labels.size > 0 && markers.size > 0) {
                val marker_label = Marker_Label.createNewMarker_Label(markers[0].gid, labels[0].gid)
                S.getDb().insertOrUpdateMarker_Label(marker_label)
            } else {
                MaterialDialog(this).show {
                    message(text = "not enough markers and labels to create marker_label")
                    positiveButton(R.string.ok)
                }
                return@OnClickListener
            }
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    MaterialDialog(this@SecretSyncDebugActivity).show {
                        message(text = "Error: " + e.message)
                        positiveButton(R.string.ok)
                    }
                }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val debugSyncResponse = App.getDefaultGson().fromJson<SyncResponseJson<Sync_Mabel.Content>>(response.body!!.charStream(), object : TypeToken<SyncResponseJson<Sync_Mabel.Content?>?>() {}.type)
                runOnUiThread {
                    if (debugSyncResponse.success) {
                        val final_revno = debugSyncResponse.final_revno
                        val append_delta = debugSyncResponse.append_delta
                        val applyResult = S.getDb().applyMabelAppendDelta(final_revno, pair.shadowEntities, clientState, append_delta, entitiesBeforeSync, simpleToken)

                        MaterialDialog(this@SecretSyncDebugActivity).show {
                            message(text = "Final revno: $final_revno\nApply result: $applyResult\nAppend delta: $append_delta")
                            positiveButton(R.string.ok)
                        }

                        if (applyResult == ApplyAppendDeltaResult.ok) {
                            App.getLbm().sendBroadcast(Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED))
                            App.getLbm().sendBroadcast(Intent(MarkersActivity.ACTION_RELOAD))
                            App.getLbm().sendBroadcast(Intent(MarkerListActivity.ACTION_RELOAD))
                        }
                    } else {
                        MaterialDialog(this@SecretSyncDebugActivity).show {
                            message(text = debugSyncResponse.message)
                            positiveButton(R.string.ok)
                        }
                    }
                }
            }
        })
    }

    private var bCheckHash_click = View.OnClickListener {
        val syncSetName = cbSyncSetName.selectedItem as String
        val entities = mutableListOf<Sync.Entity<*>>()

        val pd = MaterialDialog(this).show {
            // TODO .progress(true, 0)
            message(text = "getting entities…")
        }

        Background.run {
            when (syncSetName) {
                SyncShadow.SYNC_SET_MABEL -> entities.addAll(Sync_Mabel.getEntitiesFromCurrent())
                SyncShadow.SYNC_SET_RP -> entities.addAll(Sync_Rp.getEntitiesFromCurrent())
                SyncShadow.SYNC_SET_PINS -> entities.addAll(Sync_Pins.getEntitiesFromCurrent())
                SyncShadow.SYNC_SET_HISTORY -> entities.addAll(Sync_History.getEntitiesFromCurrent())
            }

            entities.sortWith { lhs, rhs -> lhs.gid.compareTo(rhs.gid) }

            var hashCode = 1
            for (entity in entities) {
                val elementHashCode = entity.hashCode()
                hashCode = 31 * hashCode + elementHashCode
            }

            pd.dismiss()

            runOnUiThread {
                MaterialDialog(this).show {
                    message(text = "entities.size=${entities.size} hash=${String.format(Locale.US, "0x%08x", hashCode)}")
                    positiveButton(text = "OK")
                }
            }
        }
    }

    companion object {
        var monkey: MonkeyThread? = null
    }
}
