package yuku.alkitab.base.gesture

import androidx.appcompat.app.AppCompatActivity
import yuku.alkitab.base.widget.Floater
import yuku.alkitab.base.widget.TextAppearancePanel
import yuku.alkitab.model.Book
import yuku.alkitab.model.Version

/**
 * Read-only state surface that [ReaderGestureHandler] reads from to drive
 * one- and two-finger gestures on the reader. Implemented by the hosting Activity.
 */
interface ReaderGestureHost {
    val activity: AppCompatActivity
    val chapter_1: Int
    val activeSplit0Book: Book
    val activeSplit0Version: Version
    val floater: Floater
    val textAppearancePanel: TextAppearancePanel?
}
