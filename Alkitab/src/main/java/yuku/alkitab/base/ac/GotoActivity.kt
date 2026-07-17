package yuku.alkitab.base.ac

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import yuku.afw.App
import yuku.afw.storage.Preferences
import yuku.alkitab.base.ac.base.BaseActivity
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.compose.goto.GotoScreen
import yuku.alkitab.base.fr.GotoDialerFragment
import yuku.alkitab.base.fr.GotoDirectFragment
import yuku.alkitab.base.fr.GotoGridFragment
import yuku.alkitab.base.fr.base.BaseGotoFragment
import yuku.alkitab.base.settings.ExperimentalFlags
import yuku.alkitab.base.storage.GOTO_ASK_FOR_VERSE_DEFAULT
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.debug.R

class GotoActivity : BaseActivity(), BaseGotoFragment.GotoFinishListener {

    class Result {
        @JvmField var bookId: Int = -1
        @JvmField var chapter_1: Int = 0
        @JvmField var verse_1: Int = 0
    }

    companion object {
        private const val EXTRA_bookId = "bookId"
        private const val EXTRA_chapter = "chapter"
        private const val EXTRA_verse = "verse"

        private const val INSTANCE_STATE_tab = "tab"

        @JvmStatic
        fun createIntent(bookId: Int, chapter_1: Int, verse_1: Int): Intent {
            return Intent(App.context, GotoActivity::class.java).apply {
                putExtra(EXTRA_bookId, bookId)
                putExtra(EXTRA_chapter, chapter_1)
                putExtra(EXTRA_verse, verse_1)
            }
        }

        @JvmStatic
        fun obtainResult(data: Intent): Result {
            return Result().apply {
                bookId = data.getIntExtra(EXTRA_bookId, -1)
                chapter_1 = data.getIntExtra(EXTRA_chapter, 0)
                verse_1 = data.getIntExtra(EXTRA_verse, 0)
            }
        }
    }

    private var useCompose = false

    // Legacy mode state. Only valid when [useCompose] is false.
    private var bookId: Int = -1
    private var chapter_1: Int = 0
    private var verse_1: Int = 0
    private var viewPager: ViewPager? = null
    private var okToHideKeyboard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bookId = intent.getIntExtra(EXTRA_bookId, -1)
        chapter_1 = intent.getIntExtra(EXTRA_chapter, 0)
        verse_1 = intent.getIntExtra(EXTRA_verse, 0)

        useCompose = ExperimentalFlags.useComposeGoto()

        if (useCompose) {
            setContent {
                BibleAppTheme {
                    GotoScreen(
                        initialBookId = bookId,
                        initialChapter_1 = chapter_1,
                        initialVerse_1 = verse_1,
                        onUp = { finish() },
                        onGotoFinished = { tab, bookId, chapter, verse ->
                            Preferences.setInt(Prefkey.goto_last_tab, tab.ordinal1Based)
                            val data = Intent().apply {
                                putExtra(EXTRA_bookId, bookId)
                                putExtra(EXTRA_chapter, chapter)
                                putExtra(EXTRA_verse, verse)
                            }
                            setResult(RESULT_OK, data)
                            finish()
                        },
                    )
                }
            }
        } else {
            setupLegacyUi(savedInstanceState)
        }
    }

    private fun setupLegacyUi(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_goto)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        setupEdgeToEdgeDisplay(toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { navigateUp() }

        val viewPager = findViewById<ViewPager>(R.id.viewPager)
        this.viewPager = viewPager
        viewPager.adapter = GotoPagerAdapter(supportFragmentManager)
        viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                if (okToHideKeyboard && position != 1) {
                    val editText = findViewById<View?>(R.id.tDirectReference)
                    if (editText != null) {
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(editText.windowToken, InputMethodManager.HIDE_IMPLICIT_ONLY)
                        imm.hideSoftInputFromWindow(editText.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
                    }
                }
            }
        })

        val tablayout = findViewById<TabLayout>(R.id.tablayout)
        tablayout.tabMode = TabLayout.MODE_SCROLLABLE
        tablayout.setupWithViewPager(viewPager)

        if (savedInstanceState == null) {
            val tabUsed = Preferences.getInt(Prefkey.goto_last_tab, 0)
            if (tabUsed in 1..3) {
                viewPager.setCurrentItem(tabUsed - 1, false)
            }

            if (tabUsed == 2) {
                viewPager.postDelayed({
                    val editText = findViewById<View?>(R.id.tDirectReference)
                    if (editText != null) {
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                    }
                    okToHideKeyboard = true
                }, 100)
            } else {
                okToHideKeyboard = true
            }
        } else {
            viewPager.setCurrentItem(savedInstanceState.getInt(INSTANCE_STATE_tab, 0), false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (useCompose) return super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.activity_goto, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (!useCompose) {
            val menuAskForVerse = menu.findItem(R.id.menuAskForVerse)
            val v = Preferences.getBoolean(Prefkey.gotoAskForVerse, GOTO_ASK_FOR_VERSE_DEFAULT)
            menuAskForVerse.isChecked = v
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!useCompose && item.itemId == R.id.menuAskForVerse) {
            val v = Preferences.getBoolean(Prefkey.gotoAskForVerse, GOTO_ASK_FOR_VERSE_DEFAULT)
            Preferences.setBoolean(Prefkey.gotoAskForVerse, !v)
            invalidateOptionsMenu()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!useCompose) {
            viewPager?.let { outState.putInt(INSTANCE_STATE_tab, it.currentItem) }
        }
    }

    private inner class GotoPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {
        private val pageTitleResIds = intArrayOf(
            R.string.goto_tab_dialer_label,
            R.string.goto_tab_direct_label,
            R.string.goto_tab_grid_label,
        )

        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> Fragment.instantiate(this@GotoActivity, GotoDialerFragment::class.java.name, GotoDialerFragment.createArgs(bookId, chapter_1, verse_1))
                1 -> Fragment.instantiate(this@GotoActivity, GotoDirectFragment::class.java.name, GotoDirectFragment.createArgs(bookId, chapter_1, verse_1))
                else -> Fragment.instantiate(this@GotoActivity, GotoGridFragment::class.java.name, GotoGridFragment.createArgs(bookId, chapter_1, verse_1))
            }
        }

        override fun getCount(): Int = pageTitleResIds.size

        override fun getPageTitle(position: Int): CharSequence = getString(pageTitleResIds[position])
    }

    override fun onGotoFinished(gotoTabUsed: Int, bookId: Int, chapter_1: Int, verse_1: Int) {
        Preferences.setInt(Prefkey.goto_last_tab, gotoTabUsed)

        val data = Intent().apply {
            putExtra(EXTRA_bookId, bookId)
            putExtra(EXTRA_chapter, chapter_1)
            putExtra(EXTRA_verse, verse_1)
        }
        setResult(RESULT_OK, data)
        finish()
    }
}
