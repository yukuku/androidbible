package yuku.alkitab.base.ac.base

import android.content.Intent
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import yuku.alkitab.base.util.RequestCodes
import yuku.alkitab.base.widget.TextAppearancePanel

abstract class BaseTextAppearanceActivity : BaseActivity() {
    private var appearancePanel: TextAppearancePanel? = null
    private lateinit var appearanceOverlay: FrameLayout
    private lateinit var applyAppearance: Runnable
    private val appearanceBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = hideTextAppearancePanel()
    }

    protected open fun setupTextAppearancePanel(overlay: FrameLayout, applyAppearance: Runnable) {
        appearanceOverlay = overlay
        this.applyAppearance = applyAppearance
        onBackPressedDispatcher.addCallback(this, appearanceBackCallback)
    }

    protected fun toggleTextAppearancePanel() {
        if (appearancePanel != null) {
            hideTextAppearancePanel()
            return
        }
        appearancePanel = TextAppearancePanel(
            this,
            appearanceOverlay,
            object : TextAppearancePanel.Listener {
                override fun onValueChanged() {
                    applyNightModeColors()
                    applyAppearance.run()
                }

                override fun onCloseButtonClick() = hideTextAppearancePanel()
            },
            RequestCodes.FromActivity.TextAppearanceGetFonts,
            RequestCodes.FromActivity.TextAppearanceCustomColors,
        ).also { it.show() }
        appearanceBackCallback.isEnabled = true
    }

    private fun hideTextAppearancePanel() {
        appearancePanel?.hide()
        appearancePanel = null
        appearanceBackCallback.isEnabled = false
    }

    override fun onStart() {
        super.onStart()
        appearancePanel?.displayValues()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        appearancePanel?.onActivityResult(requestCode)
    }
}
