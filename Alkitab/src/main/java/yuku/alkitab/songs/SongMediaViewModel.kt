package yuku.alkitab.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.debug.BuildConfig

class SongMediaViewModel internal constructor(
    private val loadMedia: suspend (String, String) -> SongMedia,
) : ViewModel() {
    constructor() : this(SongMediaRepository::load)

    data class UiState(
        val bookName: String?,
        val code: String?,
        val media: SongMedia?,
        val isLoading: Boolean,
        val isError: Boolean,
    )

    private val mutableState = MutableStateFlow(UiState(null, null, null, false, false))
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun load(bookName: String, code: String, isRetry: Boolean) {
        val previous = mutableState.value
        val isSameSong = previous.bookName == bookName && previous.code == code
        if (isSameSong && !isRetry && !previous.isError && previous.media?.isIncomplete != true) return
        job?.cancel()
        mutableState.value = UiState(bookName, code, previous.media.takeIf { isSameSong }, true, false)
        job = viewModelScope.launch {
            try {
                val media = loadMedia(bookName, code)
                mutableState.value = UiState(bookName, code, media, false, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                mutableState.value = mutableState.value.copy(isLoading = false, isError = true)
            } catch (e: RuntimeException) {
                mutableState.value = mutableState.value.copy(isLoading = false, isError = true)
            }
        }
    }
}

internal object SongMediaRepository {
    suspend fun load(bookName: String, code: String): SongMedia = withContext(Dispatchers.IO) {
        val url = (BuildConfig.SERVER_HOST + "/songs/media").toHttpUrl().newBuilder()
            .addQueryParameter("bookName", bookName)
            .addQueryParameter("code", code)
            .build()
        SongMedia.parse(Connections.downloadString(url.toString()))
    }
}
