package yuku.alkitab.imagesharer

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

open class MultipleVersesPlainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val aris = intent.getIntArrayExtra("aris")
        val verseTexts = intent.getStringArrayExtra("verseTexts")
        if (aris == null || aris.isEmpty() || aris.any { it <= 0 }
            || verseTexts == null || verseTexts.size != aris.size) {
            finish()
            return
        }

        setContentView(R.layout.activity_multiple_verses)
        findViewById<TextView>(R.id.tData).text = buildString {
            append(getString(R.string.received_aris, aris.contentToString()))
            append("\n\n")
            append(getString(R.string.received_texts))
            for (verseText in verseTexts) {
                append("\n\n")
                append(verseText?.takeIf { it.isNotEmpty() }
                    ?: getString(R.string.unavailable_verse))
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
