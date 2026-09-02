package yuku.alkitab.base.actionmode

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.core.app.ApplicationProvider
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import yuku.afw.storage.Preferences
import yuku.alkitab.base.config.AppConfig
import yuku.alkitab.base.util.AlkitabGptIntegration
import yuku.alkitab.base.util.ExtensionManager
import yuku.alkitab.base.util.ShareUrl
import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

/**
 * Robolectric is required here (not pure JUnit, see CLAUDE.md "Unit Testing"):
 *
 * - `onCreateActionMode` calls `host.activity.menuInflater.inflate(R.menu.context_isi, menu)`,
 *   which needs real Resources to parse the menu XML.
 * - `onPrepareActionMode` reads `host.activity.resources.configuration.smallestScreenWidthDp`
 *   and `host.activity.getString(R.string.pref_autoDictionaryAnalyze_key)`.
 * - `Preferences.getBoolean(...)` reaches into `App.context.sharedPreferences`.
 *
 * A test-scope shadow file ("Method not mocked" workaround) is not enough — we need a
 * working Resources/Activity stack for menu inflation to do anything meaningful.
 *
 * The copy/share branches are exercised through [yuku.alkitab.base.util.VerseTextFormatterTest]
 * (pure JUnit) rather than here — they fan out into `ShareUrl` async callbacks and clipboard
 * side-effects that are impractical to mock at this level.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = yuku.afw.App::class, sdk = [34])
class VerseActionModeControllerTest {

    private lateinit var activity: AppCompatActivity
    private lateinit var host: VerseActionModeHost
    private lateinit var actions: VerseActionModeActions
    private lateinit var controller: VerseActionModeController
    private lateinit var mode: ActionMode

    @Before
    fun setUp() {
        // AppLog's static initializer calls FirebaseCrashlytics.getInstance(); that's handled
        // by the test-scope shadow at src/test/java/com/google/firebase/crashlytics/FirebaseCrashlytics.java.

        // Make sure static App.context is populated so yuku.alkitab.base.util/config
        // lookups during tests don't NPE.
        yuku.afw.App.initWithAppContext(ApplicationProvider.getApplicationContext())

        activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()

        host = mockk(relaxed = true)
        actions = mockk(relaxed = true)

        every { host.activity } returns activity
        every { host.root } returns android.widget.FrameLayout(activity)
        every { host.chapter_1 } returns 1
        every { host.hasEsvsbAsal } returns false
        every { host.alkitabGptLaunchIntent } returns null
        every { host.activeSplit0Book } returns makeBook("Gen")
        every { host.activeSplit0Version } returns mockk(relaxed = true) {
            every { shortName } returns "KJV"
        }
        every { host.activeSplit0VersionId } returns "internal"
        every { host.activeSplit0MVersion } returns mockk(relaxed = true)
        every { host.activeSplit1Version } returns null
        every { host.activeSplit1VersionId } returns null
        every { host.activeSplit1MVersion } returns null
        every { host.activeSplit1BookById(any()) } returns null
        every { host.selectedVersesSplit0_1 } returns ints(1)
        every { host.selectedVersesSplit1_1 } returns ints()
        every { host.dataSplit0 } returns makeData("In the beginning...")
        every { host.dataSplit1 } returns VersesDataModel.EMPTY

        every { actions.checkRibkaEligibility() } returns RibkaEligibility.None

        // Static / object collaborators.
        mockkStatic(AppConfig::class)
        mockkObject(ExtensionManager)
        mockkStatic(Preferences::class)

        every { AppConfig.get() } returns newAppConfig(menuGuide = true, menuCommentary = true, menuDictionary = true)
        every { ExtensionManager.getExtensions() } returns emptyList()
        every { Preferences.getBoolean(any<String>(), any<Boolean>()) } returns false
        every { Preferences.getBoolean(any<Int>(), any<Int>()) } returns false

        controller = VerseActionModeController(host, actions)

        mode = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    // ----- onPrepareActionMode: menu visibility -----

    @Test
    fun `onPrepareActionMode with a single verse and no split shows the unified copy and share items, and hides the per-split variants`() {
        every { host.selectedVersesSplit0_1 } returns ints(5)

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        assertTrue(menu.findItem(R.id.menuCopy).isVisible)
        assertFalse(menu.findItem(R.id.menuCopySplit0).isVisible)
        assertFalse(menu.findItem(R.id.menuCopySplit1).isVisible)
        assertFalse(menu.findItem(R.id.menuCopyBothSplits).isVisible)
        assertTrue(menu.findItem(R.id.menuShare).isVisible)
        assertFalse(menu.findItem(R.id.menuShareSplit0).isVisible)
        assertTrue(menu.findItem(R.id.menuCompare).isVisible)
        assertTrue(menu.findItem(R.id.menuAddBookmark).isVisible)
        assertTrue(menu.findItem(R.id.menuAddNote).isVisible)
    }

    @Test
    fun `onPrepareActionMode with multiple contiguous verses keeps Add Bookmark and Add Note visible but hides Compare`() {
        every { host.selectedVersesSplit0_1 } returns ints(3, 4, 5)

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        assertTrue(menu.findItem(R.id.menuAddBookmark).isVisible)
        assertTrue(menu.findItem(R.id.menuAddNote).isVisible)
        assertFalse(menu.findItem(R.id.menuCompare).isVisible)
    }

    @Test
    fun `onPrepareActionMode with multiple non-contiguous verses hides Add Bookmark, Add Note, and Compare`() {
        every { host.selectedVersesSplit0_1 } returns ints(1, 3, 5)

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        assertFalse(menu.findItem(R.id.menuAddBookmark).isVisible)
        assertFalse(menu.findItem(R.id.menuAddNote).isVisible)
        assertFalse(menu.findItem(R.id.menuCompare).isVisible)
    }

    @Test
    fun `onPrepareActionMode with a split version active hides the unified copy item and shows the per-split copy variants`() {
        every { host.selectedVersesSplit0_1 } returns ints(2)
        every { host.activeSplit1Version } returns mockk(relaxed = true)

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        assertFalse(menu.findItem(R.id.menuCopy).isVisible)
        assertTrue(menu.findItem(R.id.menuCopySplit0).isVisible)
        assertTrue(menu.findItem(R.id.menuCopySplit1).isVisible)
        assertTrue(menu.findItem(R.id.menuCopyBothSplits).isVisible)
        assertFalse(menu.findItem(R.id.menuShare).isVisible)
        assertTrue(menu.findItem(R.id.menuShareBothSplits).isVisible)
    }

    @Test
    fun `onPrepareActionMode with zero selected verses finishes the action mode (defensive guard against the cc11d3466c89 crash)`() {
        every { host.selectedVersesSplit0_1 } returns ints()
        every { mode.finish() } just Runs

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        verify { mode.finish() }
    }

    @Test
    fun `onPrepareActionMode shows the Ribka Report item when the active version is Ribka-eligible and a single verse is selected`() {
        every { host.selectedVersesSplit0_1 } returns ints(3)
        every { actions.checkRibkaEligibility() } returns RibkaEligibility.Main

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        assertTrue(menu.findItem(R.id.menuRibkaReport).isVisible)
    }

    @Test
    fun `onPrepareActionMode hides the Ribka Report item when multiple verses are selected, even if the version is eligible`() {
        every { host.selectedVersesSplit0_1 } returns ints(3, 4)
        every { actions.checkRibkaEligibility() } returns RibkaEligibility.Main

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        assertFalse(menu.findItem(R.id.menuRibkaReport).isVisible)
    }

    @Test
    fun `onCreateActionMode reveals the ESV Study Bible menu item when the companion app is installed`() {
        every { host.hasEsvsbAsal } returns true
        every { host.selectedVersesSplit0_1 } returns ints(1)

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)

        assertTrue(menu.findItem(R.id.menuEsvsb).isVisible)
    }

    @Test
    fun `onPrepareActionMode hides the Alkitab GPT item when that app is not installed`() {
        every { host.selectedVersesSplit0_1 } returns ints(1)
        every { host.alkitabGptLaunchIntent } returns null

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        assertFalse(menu.findItem(R.id.menuAlkitabGpt).isVisible)
    }

    @Test
    fun `onPrepareActionMode reveals the Alkitab GPT item once the asynchronous lookup has found the app installed`() {
        every { host.selectedVersesSplit0_1 } returns ints(1)

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)

        // The lookup is still pending when the action mode is created, so the item starts hidden.
        controller.onPrepareActionMode(mode, menu)
        assertFalse(menu.findItem(R.id.menuAlkitabGpt).isVisible)

        // The result lands and the action mode is invalidated, which re-runs onPrepareActionMode.
        every { host.alkitabGptLaunchIntent } returns Intent(Intent.ACTION_VIEW).setPackage(AlkitabGptIntegration.PACKAGE_NAME)
        controller.onPrepareActionMode(mode, menu)
        assertTrue(menu.findItem(R.id.menuAlkitabGpt).isVisible)
    }

    @Test
    fun `onPrepareActionMode hides Guide, Commentary, and Dictionary when AppConfig disables them`() {
        every { host.selectedVersesSplit0_1 } returns ints(1)
        every { AppConfig.get() } returns newAppConfig(menuGuide = false, menuCommentary = false, menuDictionary = false)

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        assertFalse(menu.findItem(R.id.menuGuide).isVisible)
        assertFalse(menu.findItem(R.id.menuCommentary).isVisible)
        assertFalse(menu.findItem(R.id.menuDictionary).isVisible)
    }

    @Test
    fun `onPrepareActionMode hides the Dictionary item when the auto-dictionary preference is on (auto-lookup makes the manual item redundant)`() {
        every { host.selectedVersesSplit0_1 } returns ints(1)
        every {
            Preferences.getBoolean(
                eq(activity.getString(R.string.pref_autoDictionaryAnalyze_key)),
                any<Boolean>(),
            )
        } returns true

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        assertFalse(menu.findItem(R.id.menuDictionary).isVisible)
    }

    @Test
    fun `onPrepareActionMode omits an extension that does not support multiple verses when the user has selected more than one`() {
        val singleExt = mockk<ExtensionManager.Info>(relaxed = true) {
            every { supportsMultipleVerses } returns false
            every { label } returns "SingleOnly"
        }
        val multiExt = mockk<ExtensionManager.Info>(relaxed = true) {
            every { supportsMultipleVerses } returns true
            every { label } returns "Multi"
        }
        every { ExtensionManager.getExtensions() } returns listOf(singleExt, multiExt)
        every { host.selectedVersesSplit0_1 } returns ints(1, 2)

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        val labels = (0 until menu.size()).map { menu.getItem(it).title?.toString() }
        assertFalse("SingleOnly extension must be hidden on multi-select", labels.contains("SingleOnly"))
        assertTrue("Multi-capable extension must still be shown", labels.contains("Multi"))
    }

    // ----- onActionItemClicked: routing -----

    @Test
    fun `clicking the Dictionary menu item calls actions startDictionaryMode with the encoded ARI of every selected verse`() {
        every { host.selectedVersesSplit0_1 } returns ints(2, 3)
        every { host.activeSplit0Book } returns makeBook("Gen").apply { bookId = 0 }

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        val arisSlot = slot<Set<Int>>()
        every { actions.startDictionaryMode(capture(arisSlot)) } just Runs

        controller.onActionItemClicked(mode, menu.findItem(R.id.menuDictionary))

        // Ari.encode(bookId=0, chapter=1, verse=2) = (0<<16) | (1<<8) | 2 = 258
        // Ari.encode(bookId=0, chapter=1, verse=3) = 259
        assertEquals(setOf(258, 259), arisSlot.captured)
    }

    @Test
    fun `clicking the Alkitab GPT menu item starts the resolved intent with the ARI, reference, and plain verse text of the selection`() {
        val template = Intent("org.sabda.gpt.action.VIEW").setPackage(AlkitabGptIntegration.PACKAGE_NAME)

        every { host.activeSplit0Book } returns makeBook("Gen").apply { bookId = 0 }
        every { host.selectedVersesSplit0_1 } returns ints(1, 2)
        every { host.dataSplit0 } returns makeData("@@In the @9beginning@7...", "And the earth...")
        every { host.alkitabGptLaunchIntent } returns template

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)
        controller.onActionItemClicked(mode, menu.findItem(R.id.menuAlkitabGpt))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals("org.sabda.gpt.action.VIEW", started.action)
        assertEquals(AlkitabGptIntegration.PACKAGE_NAME, started.getPackage())
        // Ari.encode(bookId=0, chapter=1, verse=1) = (1<<8) | 1 = 257
        assertEquals(257, started.getIntExtra(AlkitabGptIntegration.EXTRA_ARI, 0))
        assertEquals("Gen 1:1-2", started.getStringExtra(AlkitabGptIntegration.EXTRA_REFERENCE))
        assertEquals("In the beginning...\nAnd the earth...", started.getStringExtra(AlkitabGptIntegration.EXTRA_VERSE_TEXT))

        // The template is shared across taps, so it must not have picked up the extras itself.
        assertFalse(template.hasExtra(AlkitabGptIntegration.EXTRA_ARI))
    }

    @Test
    fun `clicking the Alkitab GPT menu item starts nothing when the app is not installed`() {
        every { host.selectedVersesSplit0_1 } returns ints(1)
        every { host.alkitabGptLaunchIntent } returns null

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)
        controller.onActionItemClicked(mode, menu.findItem(R.id.menuAlkitabGpt))

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `clicking Add Bookmark with a non-contiguous selection throws because the menu is supposed to be hidden in that state (defensive contract check)`() {
        every { host.selectedVersesSplit0_1 } returns ints(1, 3)

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)

        val item: MenuItem = menu.findItem(R.id.menuAddBookmark)
        var threw = false
        try {
            controller.onActionItemClicked(mode, item)
        } catch (e: RuntimeException) {
            threw = true
            assertTrue(e.message!!.contains("Non contiguous"))
        }
        assertTrue("Add Bookmark must throw when invoked on a non-contiguous selection", threw)
    }

    // ----- onDestroyActionMode -----

    @Test
    fun `onDestroyActionMode unchecks all verses on split 0 when the uncheckVersesWhenActionModeDestroyed flag is true`() {
        every { host.uncheckVersesWhenActionModeDestroyed } returns true

        controller.onDestroyActionMode(mode)

        verify { actions.onActionModeDestroyed() }
        verify { actions.uncheckAllVersesSplit0() }
    }

    @Test
    fun `onDestroyActionMode skips the uncheck-all when the uncheckVersesWhenActionModeDestroyed flag is false (preserves selection across split-view transitions)`() {
        every { host.uncheckVersesWhenActionModeDestroyed } returns false

        controller.onDestroyActionMode(mode)

        verify { actions.onActionModeDestroyed() }
        verify(exactly = 0) { actions.uncheckAllVersesSplit0() }
    }

    // ----- onActionItemClicked: ShareUrl.make metadata routing -----
    //
    // Regression-and-fix coverage for the bug where Copy/Share Split1 would build the text
    // from split1 but pass split0-anchored metadata (version, preset_name, ari_bc) to
    // ShareUrl.make, producing a share URL that pointed back at the wrong version.

    @Test
    fun `clicking Copy Split1 passes split1 version, preset_name, and ari_bc to ShareUrl make (not split0 as before)`() {
        val split0Book = makeBook("Gen").apply { bookId = 0 }
        val split1Book = makeBook("Kej").apply { bookId = 2 }
        val split1Version = mockk<Version>(relaxed = true) { every { shortName } returns "TB" }

        every { host.activeSplit0Book } returns split0Book
        every { host.activeSplit0Version } returns mockk(relaxed = true) { every { shortName } returns "KJV" }
        every { host.activeSplit0VersionId } returns "internal"
        every { host.activeSplit1Version } returns split1Version
        every { host.activeSplit1VersionId } returns "preset/tb"
        every { host.activeSplit1BookById(0) } returns split1Book
        every { host.selectedVersesSplit0_1 } returns ints(3)
        every { host.selectedVersesSplit1_1 } returns ints(3)
        every { host.dataSplit1 } returns makeData("", "", "", "Pada mulanya Allah menciptakan langit dan bumi.")

        stubShareUrlMake()

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)
        controller.onActionItemClicked(mode, menu.findItem(R.id.menuCopySplit1))

        verify(exactly = 1) {
            ShareUrl.make(
                activity = any(),
                immediatelyCancel = any(),
                verseText = any(),
                ari_bc = Ari.encode(2, 1, 0),
                selectedVerses_1 = any(),
                reference = any(),
                version = split1Version,
                preset_name = "tb",
                callback = any(),
            )
        }
    }

    @Test
    fun `clicking Share Split1 passes split1 version, preset_name, and ari_bc to ShareUrl make (not split0 as before)`() {
        val split0Book = makeBook("Gen").apply { bookId = 0 }
        val split1Book = makeBook("Kej").apply { bookId = 2 }
        val split1Version = mockk<Version>(relaxed = true) { every { shortName } returns "TB" }

        every { host.activeSplit0Book } returns split0Book
        every { host.activeSplit0Version } returns mockk(relaxed = true) { every { shortName } returns "KJV" }
        every { host.activeSplit0VersionId } returns "internal"
        every { host.activeSplit1Version } returns split1Version
        every { host.activeSplit1VersionId } returns "preset/tb"
        every { host.activeSplit1BookById(0) } returns split1Book
        every { host.selectedVersesSplit0_1 } returns ints(3)
        every { host.selectedVersesSplit1_1 } returns ints(3)
        every { host.dataSplit1 } returns makeData("", "", "", "Pada mulanya Allah menciptakan langit dan bumi.")

        stubShareUrlMake()

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)
        controller.onActionItemClicked(mode, menu.findItem(R.id.menuShareSplit1))

        verify(exactly = 1) {
            ShareUrl.make(
                activity = any(),
                immediatelyCancel = any(),
                verseText = any(),
                ari_bc = Ari.encode(2, 1, 0),
                selectedVerses_1 = any(),
                reference = any(),
                version = split1Version,
                preset_name = "tb",
                callback = any(),
            )
        }
    }

    @Test
    fun `clicking Share Split0 with a split view active still uses split0 metadata for ShareUrl make (regression guard so the easy case keeps working)`() {
        val split0Book = makeBook("Gen").apply { bookId = 0 }
        val split1Book = makeBook("Kej").apply { bookId = 2 }
        val split0Version = mockk<Version>(relaxed = true) { every { shortName } returns "KJV" }
        val split1Version = mockk<Version>(relaxed = true) { every { shortName } returns "TB" }

        every { host.activeSplit0Book } returns split0Book
        every { host.activeSplit0Version } returns split0Version
        every { host.activeSplit0VersionId } returns "preset/kjv"
        every { host.activeSplit1Version } returns split1Version
        every { host.activeSplit1VersionId } returns "preset/tb"
        every { host.activeSplit1BookById(0) } returns split1Book
        every { host.selectedVersesSplit0_1 } returns ints(3)
        every { host.selectedVersesSplit1_1 } returns ints(3)

        stubShareUrlMake()

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)
        controller.onActionItemClicked(mode, menu.findItem(R.id.menuShareSplit0))

        verify(exactly = 1) {
            ShareUrl.make(
                activity = any(),
                immediatelyCancel = any(),
                verseText = any(),
                ari_bc = Ari.encode(0, 1, 0),
                selectedVerses_1 = any(),
                reference = any(),
                version = split0Version,
                preset_name = "kjv",
                callback = any(),
            )
        }
    }

    @Test
    fun `clicking Share with no split active uses split0 metadata for ShareUrl make (regression guard for the non-split code path)`() {
        val split0Book = makeBook("Gen").apply { bookId = 0 }
        val split0Version = mockk<Version>(relaxed = true) { every { shortName } returns "KJV" }

        every { host.activeSplit0Book } returns split0Book
        every { host.activeSplit0Version } returns split0Version
        every { host.activeSplit0VersionId } returns "internal"
        every { host.activeSplit1Version } returns null
        every { host.activeSplit1VersionId } returns null
        every { host.selectedVersesSplit0_1 } returns ints(3)

        stubShareUrlMake()

        val menu = inflateMenu()
        controller.onCreateActionMode(mode, menu)
        controller.onPrepareActionMode(mode, menu)
        controller.onActionItemClicked(mode, menu.findItem(R.id.menuShare))

        // preset_name is null because versionId = "internal" is not a preset.
        verify(exactly = 1) {
            ShareUrl.make(
                activity = any(),
                immediatelyCancel = any(),
                verseText = any(),
                ari_bc = Ari.encode(0, 1, 0),
                selectedVerses_1 = any(),
                reference = any(),
                version = split0Version,
                preset_name = null,
                callback = any(),
            )
        }
    }

    /**
     * Stubs `ShareUrl.make` to a no-op so tests can verify the arguments without triggering the
     * real network/dialog flow. `mockkObject` is undone by `unmockkAll()` in @After.
     */
    private fun stubShareUrlMake() {
        mockkObject(ShareUrl)
        every {
            ShareUrl.make(
                activity = any(),
                immediatelyCancel = any(),
                verseText = any(),
                ari_bc = any(),
                selectedVerses_1 = any(),
                reference = any(),
                version = any(),
                preset_name = any(),
                callback = any(),
            )
        } just Runs
    }

    // ----- helpers -----

    private fun inflateMenu(): Menu {
        @Suppress("RestrictedApi")
        return MenuBuilder(activity)
    }

    private fun ints(vararg values: Int): IntArrayList {
        val list = IntArrayList(values.size)
        for (v in values) list.add(v)
        return list
    }

    private fun makeBook(shortName: String): Book {
        val book = Book()
        book.bookId = 0
        book.shortName = shortName
        book.chapter_count = 50
        book.verse_counts = IntArray(50) { 31 }
        book.abbreviation = shortName
        return book
    }

    private fun makeData(vararg verses: String): VersesDataModel {
        val scv = object : SingleChapterVerses {
            override val verseCount: Int get() = verses.size
            override fun getVerse(verse_0: Int): String = verses[verse_0]
        }
        return VersesDataModel(ari_bc_ = 0, verses_ = scv)
    }

    /** Instantiates [AppConfig] reflectively (private ctor) and sets the fields we care about. */
    private fun newAppConfig(
        menuGuide: Boolean = false,
        menuCommentary: Boolean = false,
        menuDictionary: Boolean = false,
    ): AppConfig {
        val ctor = AppConfig::class.java.getDeclaredConstructor()
        ctor.isAccessible = true
        val cfg = ctor.newInstance()
        cfg.menuGuide = menuGuide
        cfg.menuCommentary = menuCommentary
        cfg.menuDictionary = menuDictionary
        return cfg
    }
}
