package yuku.alkitab.base.storage;

public enum Prefkey {
	song_last_bookName,
	song_last_code,

	goto_last_tab,

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
	 * Sync simple token, used to access user specific data.
	 */
	sync_simpleToken,

	/**
	 * The unix time the access token is obtained
	 */
	sync_token_obtained_time,

	/**
	 * The last known GCM registration id
	 */
	gcm_registration_id,

	/**
	 * The app versionCode when the GCM registration id is obtained.
	 * If the current app versionCode is not equal to this, try to get GCM registration id
	 * again, since the existing registration id is not guaranteed to work with the new app version.
	 */
	gcm_last_app_version_code,

	/**
	 * This installation id is used to differentiate app installations,
	 * so we do not send GCM messages to self.
	 */
	installation_id,

	/** Stores information about last syncs */
	sync_last_infos,

	/**
	 * Last version, book, chapter, and verse.
	 * These were moved from instant_preferences. Now I don't think we need 2 separate preference files.
	 */
	lastBookId,
	lastChapter,
	lastVerse,
	lastVersionId,
	lastSplitVersionId,
	lastSplitOrientation, // string "horizontal" or "vertical"

	/**
	 * The whole history (with many entries)
	 * This was moved from instant_preferences.
	 */
	history,

	/**
	 * (int) Do not offer importing yuku.alkitab or yuku.alkitab.kjv backup files any more.
	 * 1: user suppressed it
	 * 2: imported already
	 */
	stop_import_yuku_alkitab_backups,

	/** Announce: last annoucement check (auto only). Unix time. */
	announce_last_check,

	/** Announce: read announcement ids. long[] in json. */
	announce_read_ids,

	/** Current reading vars */
	current_reading_ari_start,
	current_reading_ari_end,
}
