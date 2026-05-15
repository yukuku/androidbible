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
    history_button_understood,  // Moved from prefkey.xml, since we're not using it via PreferenceActivity any more.

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

    /**
     * Sync simple token, used to access user specific data.
     */
    sync_simpleToken,

    /**
     * The unix time the access token is obtained
     */
    sync_token_obtained_time,

    /**
     * The last known FCM registration id.
     * Note: In the previous versions of this app, it was named "gcm_registration_id".
     * It is intentionally changed so that FCM is differentiated from GCM.
     */
    fcm_registration_id,

    /**
     * The app versionCode when the FCM registration id is obtained.
     * If the current app versionCode is not equal to this, try to get FCM registration id
     * again, since the existing registration id is not guaranteed to work with the new app version.
     * Note: In the previous versions of this app, it was named "gcm_last_app_version_code".
     * It is intentionally changed so that FCM is differentiated from GCM.
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

    /**
     * Last version, book, chapter, and verse.
     * These were moved from instant_preferences. Now I don't think we need 2 separate preference files.
     */
    lastBookId, lastChapter, lastVerse, lastVersionId, lastSplitVersionId, lastSplitOrientation,  // string "horizontal" or "vertical"
    lastSplitProp,  // float proportion of the top or left split window

    /**
     * The whole history (with many entries)
     * This was moved from instant_preferences.
     */
    history,

    /** Current reading vars  */
    current_reading_ari_start, current_reading_ari_end,

    /** Option to ask for verse number in goto screen  */
    gotoAskForVerse,

    /**
     * Audio bible: ETag of the most recently fetched `/audio/catalog` response.
     * Sent as `If-None-Match` on subsequent fetches for 304 short-circuit.
     */
    audioCatalog_etag,

    /**
     * Audio bible: playback speed (float, 0.5–2.0). Default 1.0.
     */
    audioPlaybackSpeed,

    /**
     * One-shot completion flag for the REM-10 `Marker` / `Label` / `Marker_Label`
     * copy from the legacy `AlkitabDb` tables into Room. Set to true exactly
     * once after the migration has either successfully copied all legacy rows
     * or determined there is nothing to copy. Used in place of a count-based
     * "are the Room tables empty?" check, which would resurrect deleted user
     * data: if the user (or sync) deletes every marker/label after migration,
     * the Room tables drop back to zero rows and a count check would re-copy
     * the legacy rows on next launch. See GitHub issue #195.
     */
    marker_data_migration_v1_done,

    /**
     * One-shot completion flag for the REM-11 `Version` copy from the legacy
     * `AlkitabDb` table into Room. Same rationale as
     * [marker_data_migration_v1_done] — see GitHub issue #195.
     */
    version_data_migration_v1_done,

    /**
     * One-shot completion flag for the `Devotion` copy from the legacy
     * `AlkitabDb` table into Room. Same rationale as
     * [marker_data_migration_v1_done] — see GitHub issue #195. The Devotion
     * table is a transient cache (entries expire on `touchTime`), so
     * resurrecting deleted rows is less damaging than for markers, but the
     * flag-based gate is cheap and keeps the pattern uniform across tables.
     */
    devotion_data_migration_v1_done,

    /**
     * One-shot completion flag for the `PerVersion` copy from the legacy
     * `AlkitabDb` table into Room. Same rationale as
     * [marker_data_migration_v1_done] — see GitHub issue #195. PerVersion
     * rows are user-mutable (settings can be reset / cleared), so a
     * count-based gate would resurrect cleared rows on the next launch.
     */
    per_version_data_migration_v1_done,

    /**
     * One-shot completion flag for the REM-29 `ProgressMark` /
     * `ProgressMarkHistory` copy from the legacy `AlkitabDb` tables into
     * Room. Same rationale as [marker_data_migration_v1_done] — see GitHub
     * issue #195. The progress-mark table is user-mutable (pins can be
     * overwritten with the empty placeholder), so a count-based gate would
     * resurrect cleared rows on the next launch.
     */
    progress_mark_data_migration_v1_done,

    /**
     * One-shot completion flag for the REM-30 `ReadingPlan` /
     * `ReadingPlanProgress` copy from the legacy `AlkitabDb` tables into
     * Room. Same rationale as [marker_data_migration_v1_done] — see GitHub
     * issue #195. Reading-plan progress is user-mutable (a day can be
     * un-checked, an entire plan can be deleted), so a count-based gate
     * would resurrect cleared rows on the next launch.
     */
    reading_plan_data_migration_v1_done,

    /**
     * One-shot completion flag for the REM-31 `SyncShadow` / `SyncLog`
     * copy from the legacy `AlkitabDb` tables into Room. Same rationale as
     * [marker_data_migration_v1_done] — see GitHub issue #195. A sync
     * shadow can be cleared by the user from the sync-settings screen and
     * a sync log entry can be wiped by the same path; a count-based gate
     * would resurrect cleared rows on the next launch.
     */
    sync_shadow_data_migration_v1_done,
}