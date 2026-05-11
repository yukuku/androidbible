package yuku.alkitab.base.widget

/**
 * Write-side surface that [ReaderGestureHandler] invokes to trigger Activity
 * operations in response to gestures.
 */
interface ReaderGestureActions {
    /** Called when the floater (goto-button drag) drops on a book/chapter; jump there. */
    fun onFloaterAriSelected(ari: Int)

    /** Move to the previous chapter (corresponds to the activity's `bLeft_click`). */
    fun goToPreviousChapter()

    /** Move to the next chapter (corresponds to the activity's `bRight_click`). */
    fun goToNextChapter()

    /** Re-apply preferences after the font-size preference is changed via pinch zoom. */
    fun applyPreferences()

    /**
     * Toggle fullscreen reading mode AND flip the left-drawer handle's
     * fullscreen state. The original gesture code called both setters together
     * (the system fullscreen on the activity plus a UI flag on the drawer
     * handle), so they stay paired here under a single action.
     */
    fun setFullScreenWithDrawerHandle(yes: Boolean)
}
