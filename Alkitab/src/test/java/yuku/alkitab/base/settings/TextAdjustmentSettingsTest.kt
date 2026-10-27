package yuku.alkitab.base.settings

import android.app.Application
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.debug.R

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class TextAdjustmentSettingsTest {
    @Test
    fun `display settings do not contain the appearance panel contrast control`() {
        yuku.afw.App.initWithAppContext(RuntimeEnvironment.getApplication())
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = controller.get()
        activity.setTheme(R.style.Theme_Alkitab)
        val frame = FrameLayout(activity).apply { id = android.view.View.generateViewId() }
        activity.setContentView(frame)
        try {
            val fragment = DisplayFragment()
            activity.supportFragmentManager.beginTransaction().replace(frame.id, fragment).commitNow()
            assertNull(fragment.findPreference<androidx.preference.Preference>(Prefkey.textColorAdjustment.name))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
