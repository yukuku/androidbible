package yuku.alkitab.base

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.widget.TextView
import androidx.annotation.Keep
import androidx.core.graphics.ColorUtils
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.NoBackupSharedPreferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.model.Label
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.UUID

private const val TAG = "U"

object U {
    /**
     * If text is null, this returns null.
     * If verse doesn't start with @: don't do anything.
     * Otherwise, remove all @'s and one character after that and text between @< and @>.
     */
    @JvmStatic
    @JvmOverloads
    fun removeSpecialCodes(text: String?, force: Boolean = false): String? {
        if (text == null) return null
        if (text.isEmpty()) return text
        if (!force && text[0] != '@') return text

        val sb = StringBuilder(text.length)
        var pos = 0

        while (true) {
            val p = text.indexOf('@', pos)
            if (p == -1) {
                break
            }

            sb.append(text, pos, p)
            pos = p + 2

            if (p + 1 < text.length) {
                val skipped = text[p + 1]
                when (skipped) {
                    // did we skip "@<"?
                    '<' -> {
                        // look for matching "@>"
                        val q = text.indexOf("@>", pos)
                        if (q != -1) {
                            pos = q + 2
                        }
                    }
                    // did we skip a paragraph marker, new paragraph, or newline?
                    // if so, add a space if needed
                    '0', '1', '2', '3', '4', '^', '8' -> {
                        // only add if the last character output is not already a whitespace
                        if (sb.isNotEmpty() && !Character.isWhitespace(sb[sb.length - 1])) {
                            sb.append(' ')
                        }
                        // otherwise we do not need to put extra space
                    }
                }
            }
        }

        sb.append(text, pos, text.length)
        return sb.toString()
    }

    @JvmStatic
    fun encodeLabelBackgroundColor(colorRgb_background: Int): String {
        val sb = StringBuilder(10)
        sb.append('b') // 'b': background color
        val h = Integer.toHexString(colorRgb_background)
        for (x in h.length..5) {
            sb.append('0')
        }
        sb.append(h)
        return sb.toString()
    }

    /**
     * @return colorRgb (without alpha) or -1 if can't decode
     */
    @JvmStatic
    fun decodeLabelBackgroundColor(backgroundColor: String?): Int {
        if (backgroundColor == null || backgroundColor.length == 0) return -1
        return if (backgroundColor.length >= 7 && backgroundColor[0] == 'b') { // 'b': background color
            Integer.parseInt(backgroundColor.substring(1, 7), 16)
        } else {
            -1
        }
    }

    @JvmStatic
    fun getLabelForegroundColorBasedOnBackgroundColor(colorRgb: Int): Int {
        val hsl = floatArrayOf(0f, 0f, 0f)
        rgbToHsl(colorRgb, hsl)

        if (hsl[2] > 0.5f)
            hsl[2] -= 0.44f
        else
            hsl[2] += 0.44f

        return hslToRgb(hsl)
    }

    @JvmStatic
    fun rgbToHsl(rgb: Int, hsl: FloatArray) {
        val r = (0x00ff0000 and rgb shr 16) / 255f
        val g = (0x0000ff00 and rgb shr 8) / 255f
        val b = (0x000000ff and rgb) / 255f
        val max = Math.max(Math.max(r, g), b)
        val min = Math.min(Math.min(r, g), b)
        val c = max - min

        var h_ = 0f
        if (c == 0f) {
            h_ = 0f
        } else if (max == r) {
            h_ = (g - b) / c
            if (h_ < 0) h_ += 6f
        } else if (max == g) {
            h_ = (b - r) / c + 2f
        } else if (max == b) {
            h_ = (r - g) / c + 4f
        }
        val h = 60f * h_

        val l = (max + min) * 0.5f

        val s: Float
        if (c == 0f) {
            s = 0f
        } else {
            s = c / (1 - Math.abs(2f * l - 1f))
        }

        hsl[0] = h
        hsl[1] = s
        hsl[2] = l
    }

    @JvmStatic
    fun hslToRgb(hsl: FloatArray): Int {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]

        val c = (1 - Math.abs(2f * l - 1f)) * s
        val h_ = h / 60f
        var h_mod2 = h_
        if (h_mod2 >= 4f)
            h_mod2 -= 4f
        else if (h_mod2 >= 2f) h_mod2 -= 2f

        val x = c * (1 - Math.abs(h_mod2 - 1))
        val r_: Float
        val g_: Float
        val b_: Float
        if (h_ < 1) {
            r_ = c
            g_ = x
            b_ = 0f
        } else if (h_ < 2) {
            r_ = x
            g_ = c
            b_ = 0f
        } else if (h_ < 3) {
            r_ = 0f
            g_ = c
            b_ = x
        } else if (h_ < 4) {
            r_ = 0f
            g_ = x
            b_ = c
        } else if (h_ < 5) {
            r_ = x
            g_ = 0f
            b_ = c
        } else {
            r_ = c
            g_ = 0f
            b_ = x
        }

        val m = l - 0.5f * c
        val r = ((r_ + m) * 255f + 0.5f).toInt()
        val g = ((g_ + m) * 255f + 0.5f).toInt()
        val b = ((b_ + m) * 255f + 0.5f).toInt()
        return r shl 16 or (g shl 8) or b
    }

    @JvmStatic
    fun copyToClipboard(text: CharSequence) {
        val clipboardManager = App.context.getSystemService(Context.CLIPBOARD_SERVICE) as android.text.ClipboardManager
        clipboardManager.text = text
    }

    @JvmStatic
    fun getForegroundColorOnDarkBackgroundByBookId(bookId: Int):Int {
        return when (bookId) {
            in 0..38 -> // OT
                0xff_ef5350.toInt() // Pink
            in 39..65 -> // NT
                0xff_42a5f5.toInt() // Blue 400
            else -> // others
                0xff_eeeeee.toInt() // Grey 200
        }
	}

    @JvmStatic
    fun getBackgroundColorByBookId(bookId: Int): Int {
        return when (bookId) {
            in 0..38 -> // OT
                0xff_e53935.toInt() // Red 600
            in 39..65 -> // NT
                0xff_1e88e5.toInt() // Blue 600
            else -> // others
                0xff_212121.toInt() // Grey 900
        }
	}

    @JvmStatic
    fun getSearchKeywordTextColorByBrightness(brightness: Float): Int {
        return if (brightness < 0.5f) {
            0xff69f0ae.toInt() // Green A200
        } else {
            0xff00c853.toInt() // Green A700
        }
    }

    @JvmStatic
    fun equals(a: Any?, b: Any): Boolean {
        if (a === b) return true
        return if (a == null) false else a == b
    }

    @JvmStatic
    fun applyLabelColor(label: Label, view: TextView): Int {
        var bgColorRgb = decodeLabelBackgroundColor(label.backgroundColor)
        if (bgColorRgb == -1) {
            bgColorRgb = 0x212121 // default color Grey 900
        }

        var grad: GradientDrawable? = null

        val bg = view.background
        if (bg is GradientDrawable) {
            grad = bg
        } else if (bg is StateListDrawable) {
            val current = bg.current
            if (current is GradientDrawable) {
                grad = current
            }
        }
        if (grad != null) {
            grad.setColor(0xff000000.toInt() or bgColorRgb)
            val labelColor = 0xff000000.toInt() or getLabelForegroundColorBasedOnBackgroundColor(bgColorRgb)
            view.setTextColor(labelColor)
            return labelColor
        }
        return 0
    }

    @JvmStatic
    @Throws(IOException::class)
    fun inputStreamUtf8ToString(input: InputStream): String {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (true) {
            val read = input.read(buf)
            if (read < 0) break
            baos.write(buf, 0, read)
        }
        return String(baos.toByteArray(), Charset.forName("utf-8"))
    }

    /**
     * The reason we use an installation id instead of just the simpleToken for sync
     * to identify originating device, is so that the FCM messages does not contain
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

    @JvmStatic
    fun getTextColorForSelectedVerse(selectedVerseBgColor: Int): Int {
        return if (ColorUtils.calculateLuminance(selectedVerseBgColor) > 0.4) {
            0xff000000.toInt()
        } else {
            0xffffffff.toInt()
        }
    }

    @JvmStatic
    fun dumpIntent(intent: Intent, via: String) {
        AppLog.d(TAG, "Got intent via $via")
        AppLog.d(TAG, "  action: ${intent.action}")
        AppLog.d(TAG, "  data uri: ${intent.data}")
        AppLog.d(TAG, "  component: ${intent.component}")
        AppLog.d(TAG, "  flags: 0x${Integer.toHexString(intent.flags)}")
        AppLog.d(TAG, "  mime: ${intent.type}")
        val extras = intent.extras
        AppLog.d(TAG, "  extras: ${extras?.size() ?: "null"}")
        if (extras != null) {
            for (key in extras.keySet()) {
                AppLog.d(TAG, "    $key = ${extras.get(key)}")
            }
        }
    }

    @Keep
    class InstallationInfoJson {
        var installation_id: String? = null
        var app_packageName: String? = null
        var app_versionCode: Int = 0
        var app_debug: Boolean = false
        var build_manufacturer: String? = null
        var build_model: String? = null
        var build_device: String? = null
        var build_product: String? = null
        var os_sdk_int: Int = 0
        var os_release: String? = null
        var locale: String? = null
        var last_commit_hash: String? = null
    }

    /**
     * Return a JSON string that contains information about the app installation on this particular device.
     */
    @JvmStatic
    fun getInstallationInfoJson(): String {
        val obj = InstallationInfoJson()
        obj.installation_id = getInstallationId()
        obj.app_packageName = App.context.packageName
        obj.app_versionCode = App.getVersionCode()
        obj.app_debug = BuildConfig.DEBUG
        obj.build_manufacturer = Build.MANUFACTURER
        obj.build_model = Build.MODEL
        obj.build_device = Build.DEVICE
        obj.build_product = Build.PRODUCT
        obj.os_sdk_int = Build.VERSION.SDK_INT
        obj.os_release = Build.VERSION.RELEASE
        val locale = App.context.resources.configuration.locale
        obj.locale = locale?.toString()
        obj.last_commit_hash = App.context.getString(R.string.last_commit_hash)
        return App.getDefaultGson().toJson(obj)
    }

    interface ThrowEverythingRunnable {
        @Throws(Exception::class)
        fun run()
    }

    @JvmStatic
    fun wontThrow(r: ThrowEverythingRunnable) {
        try {
            r.run()
        } catch (e: Exception) {
            throw RuntimeException("ThrowEverythingRunnable is passed but caused exception: $r", e)
        }
    }
}
