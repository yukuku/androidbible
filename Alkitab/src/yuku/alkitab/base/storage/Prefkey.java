package yuku.alkitab.base.storage;

public enum Prefkey {
	song_last_bookName,
	song_last_code,
	
	goto_last_tab,
	
	patch_devotionSlippedHtmlTags,
	
	/**
	 * When this is true, the user has understood that the middle button can be used
	 * to open history. So let us remove the hint.
	 */
	history_button_understood,

	/**
	 * Authenticated user account email
	 */
	auth_google_account_name,

	/**
	 * Authenticated user token
	 */
	auth_google_token,

	// Moved from prefkey.xml, since we're not using it via PreferenceActivity any more.
	/** Bold */
	boldHuruf,
	/** Typeface */
	jenisHuruf,
	/** Size of text in dp */
	ukuranHuruf2,
	/** Line spacing multiplier in proportional (float) */
	lineSpacingMult,
	
}
