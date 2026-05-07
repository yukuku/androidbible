package yuku.alkitab.base.ac

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import yuku.afw.App
import yuku.afw.storage.Preferences
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.compose.goto.GotoScreen
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.widget.ConfigurationWrapper

class GotoActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ConfigurationWrapper.wrap(newBase))
    }

    class Result {
        @JvmField var bookId: Int = -1
        @JvmField var chapter_1: Int = 0
        @JvmField var verse_1: Int = 0
    }

    companion object {
        private const val EXTRA_bookId = "bookId"
        private const val EXTRA_chapter = "chapter"
        private const val EXTRA_verse = "verse"

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialBookId = intent.getIntExtra(EXTRA_bookId, -1)
        val initialChapter = intent.getIntExtra(EXTRA_chapter, 0)
        val initialVerse = intent.getIntExtra(EXTRA_verse, 0)

        setContent {
            BibleAppTheme {
                GotoScreen(
                    initialBookId = initialBookId,
                    initialChapter_1 = initialChapter,
                    initialVerse_1 = initialVerse,
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
    }
}
