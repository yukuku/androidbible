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

	/** Version config updater: the modify time we have currently. Unix time. */
	version_config_current_modify_time,

	/** Version config updater: last update check (auto only). Unix time. */
	version_config_last_update_check,

	/**
	 * The ordering of internal version.
	 * For {@link yuku.alkitab.base.model.MVersionDb}, the ordering is stored in a table on the database.
	 * However for internal version, the ordering is stored in the preferences.
	 * Default is {@link yuku.alkitab.base.model.MVersionInternal#DEFAULT_ORDERING}.
	 */
	internal_version_ordering,

	/**
	 * Sync server prefix.
	 * Example: http://10.0.3.2:9080
	 * Should not end with slash.
	 */
	sync_server_prefix,

	/**
	 * Sync user email.
	 */
	sync_user_email,

	/**
	 * Sync simple token, used to access user specific data.
	 */
	sync_simpleToken,

	/**
	 * The unix time the access token is obtained
	 */
	sync_token_obtained_time,
}
