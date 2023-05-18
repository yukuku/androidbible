package yuku.alkitab.base.util

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import yuku.alkitab.base.App
import yuku.alkitab.debug.BuildConfig

object ExtensionManager {
    const val TAG = "ExtensionManager"
    const val ACTION_SHOW_VERSE_INFO = "yuku.alkitab.extensions.action.SHOW_VERSE_INFO"

    private val openExtension = Intent(ACTION_SHOW_VERSE_INFO)

    private var extensions: List<Info>? = null

    private fun Bundle?.getBooleanFromMetadata(key: String): Boolean {
        return this?.getBoolean(key, false) ?: false
    }

    private val receiver = InvalidateExtensionsReceiver()

    /**
     * Clear extensions cache when other apps are installed/updated/removed
     */
    @JvmStatic
    fun registerReceivers(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        context.registerReceiver(receiver, filter)
    }

    @Synchronized
    fun getExtensions(): List<Info> {
        val extensions = this.extensions
        if (extensions != null) return extensions.toList()

        val newExtensions = queryExtensions()
        this.extensions = newExtensions
        return newExtensions.toList()
    }

    private fun queryExtensions(): List<Info> {
        val pm = App.context.packageManager
        val resolveInfos = pm.queryIntentActivities(openExtension, 0)
        val ret = mutableListOf<Info>()

        for (ri in resolveInfos) {
            try {
                val ai = pm.getActivityInfo(ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), PackageManager.GET_META_DATA)
                val supportsMultipleVerses = ai.metaData.getBooleanFromMetadata("supportsMultipleVerses")
                val includeVerseText = ai.metaData.getBooleanFromMetadata("includeVerseText")
                val includeVerseTextFormatting = ai.metaData.getBooleanFromMetadata("includeVerseTextFormatting")
                ret += Info(ai, ai.loadLabel(pm), supportsMultipleVerses, includeVerseText, includeVerseTextFormatting)
            } catch (e: PackageManager.NameNotFoundException) {
                AppLog.e(TAG, "PackageManager should not emit this", e)
            }
        }

        if (BuildConfig.DEBUG) {
            AppLog.d(TAG, "Found " + ret.size + " extensions:")
            for (info in ret) {
                AppLog.d(TAG, "- " + info.activityInfo.packageName + "/" + info.activityInfo.name)
            }
        }

        return ret
    }

    @Synchronized
    fun invalidate() {
        extensions = null
    }

    class Info(val activityInfo: ActivityInfo, val label: CharSequence, val supportsMultipleVerses: Boolean, val includeVerseText: Boolean, val includeVerseTextFormatting: Boolean)

    private class InvalidateExtensionsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppLog.d(TAG, "invalidating extensions because of ${intent.action}")
            invalidate()
        }
    }
}
