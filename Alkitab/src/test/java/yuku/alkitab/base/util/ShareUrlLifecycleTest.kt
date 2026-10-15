package yuku.alkitab.base.util

import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import io.mockk.*
import java.io.IOException
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.debug.R
import yuku.alkitab.util.IntArrayList

@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class ShareUrlLifecycleTest {
    private val call = mockk<Call>(relaxed = true)
    private val networkCallback = slot<okhttp3.Callback>()
    private val callback = mockk<ShareUrl.Callback>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Connections::class)
        val client = mockk<OkHttpClient>()
        every { Connections.okHttp } returns client
        every { client.newCall(any()) } returns call
        every { call.enqueue(capture(networkCallback)) } just Runs
    }

    @After
    fun tearDown() {
        unmockkStatic(Connections::class)
    }

    @Test
    fun `destroy dismisses the dialog cancels the request and suppresses late failure`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_Alkitab)
        controller.setup()
        make(controller.get())
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        controller.pause().stop().destroy()
        assertFalse(dialog.isShowing)
        verify { call.cancel() }
        networkCallback.captured.onFailure(call, IOException("cancelled"))
        shadowOf(Looper.getMainLooper()).idle()
        verify { callback wasNot Called }
    }

    @Test
    fun `completion queued before destruction cannot call a destroyed activity`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_Alkitab)
        controller.setup()
        make(controller.get())
        Thread { networkCallback.captured.onFailure(call, IOException("offline")) }.apply { start(); join() }
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(ShadowDialog.getLatestDialog().isShowing)
        verify { callback wasNot Called }
    }

    @Test
    fun `user dismissal still delivers cancellation and finalization exactly once`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_Alkitab)
        controller.setup()
        make(controller.get())
        ShadowDialog.getLatestDialog().dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        networkCallback.captured.onFailure(call, IOException("cancelled"))
        verify(exactly = 1) { callback.onUserCancel() }
        verify(exactly = 1) { callback.onFinally() }
        verify(exactly = 0) { callback.onError(any()) }
        controller.pause().stop().destroy()
    }

    @Test
    fun `successful completion dismisses the dialog and delivers the URL once`() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_Alkitab)
        controller.setup()
        make(controller.get())
        val body = spyk("""{"success":true,"share_url":"https://example.com/verse","message":""}""".toResponseBody())
        val response = Response.Builder().request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        networkCallback.captured.onResponse(call, response)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(ShadowDialog.getLatestDialog().isShowing)
        verify(exactly = 1) { callback.onSuccess("https://example.com/verse") }
        verify(exactly = 1) { callback.onFinally() }
        verify(exactly = 0) { callback.onUserCancel() }
        verify { body.close() }
        controller.pause().stop().destroy()
    }

    private fun make(activity: AppCompatActivity) {
        val verses = IntArrayList(1).apply { add(1) }
        ShareUrl.make(activity, false, "text", 0x100, verses, "Genesis 1:1", mockk(relaxed = true), null, callback)
    }
}
