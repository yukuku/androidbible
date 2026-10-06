package yuku.alkitab.imagesharer

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.IOException

class ShareVerseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val aris = intent.getIntArrayExtra("aris")
        val verseTexts = intent.getStringArrayExtra("verseTexts")
        val verseText = verseTexts?.singleOrNull()
        if (aris == null || aris.size != 1 || aris[0] <= 0 || verseText.isNullOrEmpty()) {
            finish()
            return
        }

        val book = (aris[0] ushr 16) and 0xff
        val chapter = (aris[0] ushr 8) and 0xff
        val verse = aris[0] and 0xff
        val reference = getString(R.string.book_reference, book + 1, chapter, verse)

        val text = TextView(this).apply {
            this.text = getString(R.string.image_text, reference, verseText)
            textSize = 20f
            setTextColor(Color.BLUE)
            setBackgroundColor(Color.WHITE)
            setPadding(40, 40, 40, 40)
            measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            layout(0, 0, measuredWidth, measuredHeight)
        }

        val image = createBitmap(text.measuredWidth, text.measuredHeight,
            Bitmap.Config.ARGB_8888)
        text.draw(Canvas(image))

        try {
            val directory = File(cacheDir, "shared")
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Cannot create shared image directory")
            }
            val file = File.createTempFile("verse-", ".png", directory)
            file.outputStream().use { stream ->
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IOException("Cannot encode image")
                }
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(getString(R.string.verse_image), uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, getString(R.string.share_verse_image)))
        } catch (_: IOException) {
            Toast.makeText(this, R.string.unable_to_share, Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.unable_to_share, Toast.LENGTH_LONG).show()
        } finally {
            image.recycle()
        }
        finish()
    }
}
