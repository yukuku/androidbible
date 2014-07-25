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
	
	// Moved from prefkey.xml, since we're not using it via PreferenceActivity any more.
	/** Bold */
	boldHuruf,
	/** Typeface */
	jenisHuruf,
	/** Size of text in dp */
	ukuranHuruf2,
	/** Line spacing multiplier in proportional (float) */
	lineSpacingMult,

	/** Last backup date */
	lastBackupDate,

	/** default reading plan */
	active_reading_plan_id,

	/** Night mode activated (boolean) */
	is_night_mode,

	/** marker (bookmark) list selected sorting option */
	marker_list_sort_column, marker_list_sort_ascending,

	/** Last devotion kind */
	devotion_last_kind_name,

	/** Search history (JSON: {@link yuku.alkitab.base.ac.SearchActivity.SearchHistory}) */
	searchHistory,
}
