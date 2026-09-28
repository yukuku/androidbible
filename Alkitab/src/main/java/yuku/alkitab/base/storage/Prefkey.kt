package yuku.alkitab.base.storage

import androidx.annotation.Keep

const val GOTO_ASK_FOR_VERSE_DEFAULT = true

@Keep
enum class Prefkey {
    song_last_bookName, song_last_code, goto_last_tab,

    /**
     * When this is true, the user has understood that the middle button can be used
     * to open history. So let us remove the hint.
     */
    history_button_understood,

    /** Bold  */
    boldHuruf,

    /** Typeface  */
    jenisHuruf,

    /** Size of text in dp  */
    ukuranHuruf2,

    /** Line spacing multiplier in proportional (float)  */
    lineSpacingMult,

    /** Last backup date  */
    lastBackupDate,

    /** default reading plan  */
    active_reading_plan_id,

    /** Night mode activated (boolean)  */
    is_night_mode,

    /** marker (bookmark) list selected sorting option  */
    marker_list_sort_column, marker_list_sort_ascending,

    /** Last devotion kind  */
    devotion_last_kind_name,

    /** Search history (JSON: [yuku.alkitab.base.ac.SearchActivity.SearchHistory])  */
    searchHistory,

    /** Version config updater: the modify time we have currently. Unix time.  */
    version_config_current_modify_time,

    /** Version config updater: last update check (auto only). Unix time.  */
    version_config_last_update_check,

    /**
     * The ordering of internal version.
     * For [yuku.alkitab.base.model.MVersionDb], the ordering is stored in a table on the database.
     * However for internal version, the ordering is stored in the preferences.
     * Default is [yuku.alkitab.base.model.MVersionInternal.DEFAULT_ORDERING].
     */
    internal_version_ordering,

    /**
     * Sync server prefix.
     * Example: http://10.0.3.2:9080
     * Should not end with slash.
     */
    sync_server_prefix,

    /** Sync simple token, used to access user specific data. */
    sync_simpleToken,

    /** The unix time the access token is obtained. */
    sync_token_obtained_time,

    /** The last known FCM registration id. */
    fcm_registration_id,

    /**
     * The app versionCode when the FCM registration id is obtained.
     * If the current app versionCode is not equal to this, try to get FCM registration id
     * again, since the existing registration id is not guaranteed to work with the new app version.
     */
    fcm_last_app_version_code,

    /**
     * True when we have a stored FCM registration id that we failed to send to the sync server.
     * Set by [yuku.alkitab.base.sync.Sync.sendFcmRegistrationId] on failure, cleared on success.
     * Checked at app launch to retry the send.
     */
    fcm_registration_pending,

    /**
     * This installation id is used to differentiate app installations,
     * so we do not send FCM messages to self.
     */
    installation_id,

    /** Stores information about last syncs  */
    sync_last_infos,

    /** Last version, book, chapter, and verse. */
    lastBookId, lastChapter, lastVerse, lastVersionId, lastSplitVersionId, lastSplitOrientation,  // string "horizontal" or "vertical"
    lastSplitProp,  // float proportion of the top or left split window

    /** The whole history (with many entries) */
    history,

    /** Current reading vars  */
    current_reading_ari_start, current_reading_ari_end,

    /** Option to ask for verse number in goto screen  */
    gotoAskForVerse,

    /** Audio bible: playback speed (float, 0.5-2.0). Default 1.0. */
    audioPlaybackSpeed,

    /**
     * Audio bible: which recording plays for each version, as a JSON object
     * mapping versionId to audioId, e.g. `{"preset/in-tb": "davar"}`. One key
     * holds the whole map since per-version enum keys are not expressible.
     * Managed by [yuku.alkitab.base.audio.AudioSetSelections].
     */
    audioSelectedSets,

    /**
     * One-shot completion flag for the legacy `SongDb` to Room copy. Gating on
     * a count of the Room tables would not work: the user can delete song books
     * from the songs screen, and empty tables would then look like a copy that
     * never ran, resurrecting the deleted books on the next launch.
     */
    song_db_data_migration_v1_done,

    /**
     * Smart search: which lexicon to use, one of `auto`, `curated` or `rules`. See
     * [yuku.alkitab.base.smartsearch.LexiconMode].
     */
    smartSearchLexiconMode,
}