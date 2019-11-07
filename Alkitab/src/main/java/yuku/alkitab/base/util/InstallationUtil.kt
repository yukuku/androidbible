package yuku.alkitab.base.util

import android.os.Build
import androidx.annotation.Keep
import androidx.core.os.ConfigurationCompat
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.storage.NoBackupSharedPreferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import java.util.UUID

object InstallationUtil {
    /**
     * We use an installation id instead of just the simpleToken for sync
     * to identify originating device, is so that the FCM messages do not contain
     * simpleToken, which is sensitive.
     */
    @JvmStatic
    @Synchronized
    fun getInstallationId(): String {
        val nbsp = NoBackupSharedPreferences.get()

        var res: String? = Preferences.getString(Prefkey.installation_id, null)
        if (res == null) {
            res = nbsp.getString(Prefkey.installation_id.name)
            if (res == null) {
                res = "i1:" + UUID.randomUUID().toString()
                nbsp.setString(Prefkey.installation_id.name, res)
            }
        } else {
            // we need to remove it from the backed up folder and move it to the nonbacked up folder
            Preferences.remove(Prefkey.installation_id)
            nbsp.setString(Prefkey.installation_id.name, res)
        }

        return res
    }

    @Keep
    @Suppress("unused")
    class InstallationInfoJson(
        val installation_id: String,
        val app_packageName: String,
        val app_versionCode: Int,
        val app_debug: Boolean,
        val build_manufacturer: String,
        val build_model: String,
        val build_device: String,
        val build_product: String,
        val os_sdk_int: Int,
        val os_release: String,
        val locale: String,
        val last_commit_hash: String
    )

    /**
     * Return a JSON string that contains information about the app installation on this particular device.
     */
    @JvmStatic
    fun getInfoJson(): String {
        val context = App.context

        val obj = InstallationInfoJson(
            installation_id = getInstallationId(),
            app_packageName = context.packageName,
            app_versionCode = App.getVersionCode(),
            app_debug = BuildConfig.DEBUG,
            build_manufacturer = Build.MANUFACTURER,
            build_model = Build.MODEL,
            build_device = Build.DEVICE,
            build_product = Build.PRODUCT,
            os_sdk_int = Build.VERSION.SDK_INT,
            os_release = Build.VERSION.RELEASE,
            locale = ConfigurationCompat.getLocales(context.resources.configuration)[0].toString(),
            last_commit_hash = context.getString(R.string.last_commit_hash)
        )

        return App.getDefaultGson().toJson(obj)
    }
}
