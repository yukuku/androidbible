package yuku.alkitab.base.gesture

/**
 * Write-side surface that [ReaderGestureHandler] invokes to trigger reader
 * operations in response to gestures. Implemented by the hosting Activity.
 */
interface ReaderGestureActions {
    fun bLeft_click()
    fun bRight_click()
    fun applyPreferences()

    /**
     * Toggles fullscreen mode; the implementer is expected to also propagate
     * the state to the left-drawer handle so its own chrome stays in sync.
     */
    fun onGestureFullScreenToggle(fullScreen: Boolean)

    fun jumpToAri(ari: Int)
}
