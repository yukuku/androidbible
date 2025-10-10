package yuku.alkitab.base.util

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.net.toUri

object AppChecker {

    data class Result(
        val isInstalled: Boolean,
        val versionName: String? = null,
        val versionCode: Long? = null,
    )

    /**
     * Mengecek apakah aplikasi dengan [packageName] terinstall dan
     * apakah versi >= [requiredVersionName].
     *
     * @param showRedirectDialog kalau true, tampilkan dialog untuk buka Play Store saat gagal
     */
    fun validateApp(
        context: Context,
        packageName: String,
        requiredVersionName: String,
        showRedirectDialog: Boolean = true,
    ): Boolean {
        val result = checkApp(context, packageName)

        // tidak terpasang
        if (!result.isInstalled) {
            if (showRedirectDialog) {
                showRedirectDialog(
                    context,
                    packageName,
                    "Aplikasi belum terpasang.\nDibutuhkan versi $requiredVersionName"
                )
            }
            return false
        }

        // versi tidak sesuai (kurang dari required)
        val current = parseVersion(result.versionName)
        val required = parseVersion(requiredVersionName)
        if (compareVersion(current, required) < 0) {
            if (showRedirectDialog) {
                showRedirectDialog(
                    context,
                    packageName,
                    "Versi tidak sesuai.\nDibutuhkan $requiredVersionName, terpasang ${result.versionName}"
                )
            }
            return false
        }

        return true
    }

    private fun checkApp(context: Context, packageName: String): Result {
        return try {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageInfo(packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }

            Result(true, pkgInfo.versionName, versionCode)
        } catch (e: PackageManager.NameNotFoundException) {
            Result(false)
        }
    }

    /** Konversi "1.4.1" jadi angka list [1,4,1] supaya bisa dibandingkan */
    private fun parseVersion(version: String?): List<Int> {
        return version?.split(".")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
    }

    /** Menampilkan dialog redirect ke Play Store */
    private fun showRedirectDialog(context: Context, packageName: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle("Aplikasi dibutuhkan")
            .setMessage(message)
            .setPositiveButton("Buka Play Store") { _, _ ->
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
                    )
                } catch (e: Exception) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri())
                    )
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /** Bandingkan 2 list versi, misalnya [1,4,1] vs [1,3,9] */
    private fun compareVersion(v1: List<Int>, v2: List<Int>): Int {
        val maxLen = maxOf(v1.size, v2.size)
        for (i in 0 until maxLen) {
            val part1 = v1.getOrElse(i) { 0 }
            val part2 = v2.getOrElse(i) { 0 }
            if (part1 != part2) return part1 - part2
        }
        return 0
    }

    fun showOpenAppOrRedirectDialog(
        context: Context,
        packageName: String,
        requiredVersionName: String,
        bookName: String,
        chapter: String
    ) {
        val appInstalled = validateApp(context, packageName, requiredVersionName, showRedirectDialog = false)

        if (appInstalled) {
            AlertDialog.Builder(context)
                .setTitle("Buka Alkitab GPT?")
                .setMessage("Supaya chat Anda tersimpan dalam history, silakan buka Alkitab GPT.")
                .setPositiveButton("Buka") { _, _ ->
                    try {
                        val intent = Intent("org.sabda.gpt.action.SHOW_CHAT_POPUP").apply {
                            setPackage(packageName)
                            putExtra("bookName", bookName)
                            putExtra("chapter", chapter)
                            putExtra("source", "Apps Alkitab")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("AppChecker", "Gagal membuka aplikasi GPT: ${e.message}")
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        } else {
            // kalau belum terpasang, arahkan ke Play Store
            showRedirectDialog(
                context,
                packageName,
                "Aplikasi Alkitab GPT dibutuhkan.\nSilakan pasang terlebih dahulu."
            )
        }
    }
}