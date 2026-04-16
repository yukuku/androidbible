package yuku.alkitab.base.ac

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.method.LinkMovementMethod
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import yuku.alkitab.base.App
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R

class AboutActivity : BaseActivity() {
    private lateinit var root: View
    private lateinit var tVersion: TextView
    private lateinit var tBuild: TextView
    private lateinit var imgLogo: ImageView
    private lateinit var tAboutTextDesc: TextView
    private lateinit var bHelp: View
    private lateinit var bMaterialSources: View
    private lateinit var bCredits: View

    private val backgroundAnimationStarted = AtomicBoolean(false)
    private var baseHue = 0
    private val hsl = FloatArray(3)
    private val colors = IntArray(6)

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        root = findViewById(R.id.root)
        tVersion = findViewById(R.id.tVersion)
        tBuild = findViewById(R.id.tBuild)
        imgLogo = findViewById(R.id.imgLogo)
        tAboutTextDesc = findViewById(R.id.tAboutTextDesc)

        bHelp = findViewById(R.id.bHelp)
        bHelp.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, "https://alkitab.app/guide?utm_source=app&utm_medium=button&utm_campaign=help".toUri()))
        }

        bMaterialSources = findViewById(R.id.bMaterialSources)
        bMaterialSources.setOnClickListener {
            startActivity(HelpActivity.createIntent("help/material_sources.html", getString(R.string.about_material_sources)))
        }

        bCredits = findViewById(R.id.bCredits)
        bCredits.setOnClickListener {
            startActivity(HelpActivity.createIntent("help/credits.html", getString(R.string.about_credits)))
        }

        imgLogo.setImageDrawable(ResourcesCompat.getDrawableForDensity(resources, R.mipmap.ic_launcher, DisplayMetrics.DENSITY_XXXHIGH, null))
        imgLogo.setOnTouchListener { v: View, event: MotionEvent ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val x = event.x
                val y = event.y
                if (x >= v.width / 2 - 4 && x <= v.width / 2 + 4) {
                    if (y >= v.height * 3 / 10 - 4 && y <= v.height * 3 / 10 + 4) {
                        showSecretDialog()
                    }
                }
            }
            false
        }

        tAboutTextDesc.movementMethod = LinkMovementMethod.getInstance()
        tVersion.text = getString(R.string.about_version_name, App.getVersionName())
        tBuild.text = String.format(Locale.US, "%s %s", App.getVersionCode(), BuildConfig.LAST_COMMIT_HASH)
        root.setOnTouchListener { _, event ->
            if (event.pointerCount == 4) {
                startBackgroundAnimation()
            } else if (event.pointerCount == 5 && event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                showSecretDialog()
            }
            false
        }
    }

    private fun startBackgroundAnimation() {
        if (!backgroundAnimationStarted.compareAndSet(false, true)) {
            return
        }

        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (isFinishing) return  // don't leak
                val baseColor = -0x660067
                ColorUtils.colorToHSL(baseColor, hsl)
                for (i in colors.indices) {
                    hsl[0] = ((baseHue + i * 60) % 360).toFloat()
                    colors[i] = ColorUtils.HSLToColor(hsl)
                }
                window.setBackgroundDrawable(GradientDrawable(GradientDrawable.Orientation.BR_TL, colors))
                baseHue += 2
                sendEmptyMessageDelayed(0, 16)
            }
        }.sendEmptyMessage(0)
    }

    private fun showSecretDialog() {
        MaterialAlertDialogBuilder(this)
            .setItems(arrayOf("Secret settings", "Crash me")) { _, index ->
                when (index) {
                    0 -> startActivity(SecretSettingsActivity.createIntent())
                    1 -> throw RuntimeException("Dummy exception from secret dialog.")
                }
            }
            .show()
    }

    companion object {
        @JvmStatic
        fun createIntent(): Intent {
            return Intent(App.context, AboutActivity::class.java)
        }
    }
}