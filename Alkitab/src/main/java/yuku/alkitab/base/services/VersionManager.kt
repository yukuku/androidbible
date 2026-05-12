package yuku.alkitab.base.services

import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.model.Version

/**
 * Manages the process-global active Bible version and the list of installed versions.
 * Extracted from [yuku.alkitab.base.S] so callers can depend on a narrow interface
 * instead of the whole service locator.
 */
interface VersionManager {
    fun activeVersion(): Version
    fun activeMVersion(): MVersion
    fun activeVersionId(): String
    fun setActiveVersion(mv: MVersion)

    /**
     * @return null when the specified versionId is unknown OR refers to the internal version.
     */
    fun getVersionFromVersionId(versionId: String?): MVersion?

    /**
     * Returns the list of versions that are:
     * 1. internal, or
     * 2. database versions that have a data file and are marked active.
     */
    fun getAvailableVersions(): List<MVersion>

    fun getMVersionInternal(): MVersionInternal
}
