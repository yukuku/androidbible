package yuku.alkitab.base.util

import android.app.Activity
import androidx.annotation.Keep
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.google.gson.JsonParseException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import yuku.alkitab.base.App
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.widget.MaterialDialogProgressHelper.progress
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

object ShareUrl {
    fun make(
        activity: Activity,
        immediatelyCancel: Boolean,
        verseText: String,
        ari_bc: Int,
        selectedVerses_1: IntArrayList,
        reference: String,
        version: Version,
        preset_name: String?,
        callback: Callback,
    ) {
        if (immediatelyCancel) { // user explicitly ask for not submitting url
            callback.onUserCancel()
            callback.onFinally()
            return
        }

        val aris = (0 until selectedVerses_1.size())
            .map { Ari.encodeWithBc(ari_bc, selectedVerses_1[it]) }
            .joinToString(",")

        val form = FormBody.Builder().apply {
            add("verseText", verseText)
            add("aris", aris)
            add("verseReferences", reference)
            preset_name?.let { add("preset_name", it) }
            version.getLongName()?.let { add("versionLongName", it) }
            version.getShortName()?.let { add("versionShortName", it) }
        }.build()

        val call = Connections.okHttp.newCall(
            Request.Builder()
                .url(BuildConfig.SERVER_HOST + "/v/create")
                .post(form)
                .build()
        )

        // when set to true, do not call any callback
        val done = AtomicBoolean()

        val dialog = MaterialDialog(activity).show {
            message(text = "Getting share URL…")
            progress(true, 0)
            negativeButton(R.string.cancel) { dialog ->
                if (done.getAndSet(true)) return@negativeButton
                done.set(true)
                callback.onUserCancel()
                dialog.dismiss()
                callback.onFinally()
            }
            onDismiss { dialog ->
                if (done.getAndSet(true)) return@onDismiss
                callback.onUserCancel()
                dialog.dismiss()
                callback.onFinally()
            }
        }

        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!done.getAndSet(true)) {
                    onComplete { callback.onError(e) }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (done.getAndSet(true)) return
                val body = response.body
                if (body == null) {
                    onComplete { callback.onError(IOException("empty response body")) }
                } else {
                    try {
                        val obj = App.getDefaultGson().fromJson(body.charStream(), ShareUrlResponseJson::class.java)
                        if (obj.success) {
                            onComplete { callback.onSuccess(obj.share_url) }
                        } else {
                            onComplete { callback.onError(Exception(obj.message)) }
                        }
                    } catch (e: JsonParseException) {
                        onComplete { callback.onError(e) }
                    }
                }
            }

            fun onComplete(todo: Runnable) {
                activity.runOnUiThread {
                    if (activity.isFinishing) return@runOnUiThread
                    todo.run()
                    try {
                        dialog.dismiss()
                    } catch (ignored: Exception) {
                    }
                    callback.onFinally()
                }
            }
        })
    }

    interface Callback {
        fun onSuccess(shareUrl: String)
        fun onUserCancel()
        fun onError(e: Exception)
        fun onFinally()
    }

    @Keep
    data class ShareUrlResponseJson(
        val success: Boolean,
        val message: String,
        val share_url: String,
    )
}
