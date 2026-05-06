package yuku.alkitab.base.compose.goto

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.GOTO_ASK_FOR_VERSE_DEFAULT
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.model.Book

sealed class GridStage {
    object Books : GridStage()
    data class Chapters(val book: Book) : GridStage()
    data class Verses(val book: Book, val chapter_1: Int) : GridStage()

    val depth: Int
        get() = when (this) {
            Books -> 0
            is Chapters -> 1
            is Verses -> 2
        }
}

class GotoViewModel : ViewModel() {

    val askForVerse: StateFlow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Prefkey.gotoAskForVerse.name) {
                trySend(Preferences.getBoolean(Prefkey.gotoAskForVerse, GOTO_ASK_FOR_VERSE_DEFAULT))
            }
        }
        Preferences.registerObserver(listener)
        trySend(Preferences.getBoolean(Prefkey.gotoAskForVerse, GOTO_ASK_FOR_VERSE_DEFAULT))
        awaitClose { Preferences.unregisterObserver(listener) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Preferences.getBoolean(Prefkey.gotoAskForVerse, GOTO_ASK_FOR_VERSE_DEFAULT),
    )

    var gridStage: GridStage by mutableStateOf(GridStage.Books)
}
