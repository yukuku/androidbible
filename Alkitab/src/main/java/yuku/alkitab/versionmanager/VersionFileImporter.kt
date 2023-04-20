package yuku.alkitab.versionmanager

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID
import yuku.alkitab.base.storage.YesReaderFactory
import yuku.alkitab.base.util.AddonManager
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.R
import yuku.alkitab.io.OptionalGzipInputStream
import yuku.alkitab.tracking.Tracker

private const val TAG = "VersionFileImporter"
private const val MAX_FILE_SIZE = 100 * 1024 * 1024

class VersionFileImporter(val context: Context) {
    sealed class Result {
        data class YesFileIsAvailableLocally(val localYesFile: File) : Result()
        data class ShouldImportPdb(val cacheFile: File, val yesName: String) : Result()
    }

    /**
     * The entrypoint. Must catch exceptions when calling this method.
     */
    fun importFromUri(uri: Uri): Result {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?: throw IOException("Could not read from $uri: null cursor")

        val cacheFile: File
        val ext: String
        val baseName: String

        cursor.use { c ->
            if (!c.moveToNext()) {
                throw IOException("Could not read first row of cursor for $uri")
            }

            val displayName = c.getString(0)
            val size = c.getLong(1)

            // We will transparently decompress .yes.gz and .pdb.gz files
            // `ext` is the extension of the file without ".gz" if any, in lowercase
            ext = when {
                displayName.lowercase().endsWith(".gz") -> displayName.dropLast(3).substringAfterLast('.', "").lowercase()
                else -> displayName.substringAfterLast('.', "").lowercase()
            }

            if (ext != "yes" && ext != "pdb") {
                throw IOException("Unknown file selected. File name must end with .yes, .pdb, .yes.gz, or .pdb.gz.")
            }

            if (size > MAX_FILE_SIZE) {
                throw IOException("File size is $size, larger than the max of $MAX_FILE_SIZE.")
            }

            // `baseName` is the file name without \.(pdb|yes)(\.gz)?
            baseName = displayName.lowercase().removeSuffix(".gz").removeSuffix(".$ext")

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open input for $uri.")

            cacheFile = File(context.cacheDir, "${UUID.randomUUID()}.$ext")

            inputStream.use { input ->
                cacheFile.outputStream().use { output ->
                    AppLog.d(TAG, "Writing to cache file $cacheFile")
                    OptionalGzipInputStream(input).use { it.copyTo(output) }
                }
            }
        }

        return readCachedFile(ext, baseName, cacheFile)
    }

    private fun readCachedFile(ext: String, baseName: String, cacheFile: File): Result {
        return when (ext) {
            "yes" -> {
                Tracker.trackEvent("versions_open_yes")
                openCachedYesFile(baseName, cacheFile)
            }

            "pdb" -> {
                Tracker.trackEvent("versions_open_pdb")
                openCachedPdbFile(baseName, cacheFile)
            }

            else -> error("should not happen")
        }
    }

    /**
     * @param baseName the file name without \.(pdb|yes)(\.gz)?
     */
    private fun openCachedYesFile(baseName: String, cacheFile: File): Result {
        YesReaderFactory.createYesReader(cacheFile.absolutePath)
            ?: throw IOException("Cached file $cacheFile is not a valid YES file.")

        // Copy the cached file to the persistent files dir
        val yesFile = AddonManager.getWritableVersionFile("$baseName.yes")
        try {
            cacheFile.copyTo(yesFile, overwrite = false)
        } catch (e: FileAlreadyExistsException) {
            throw IOException(context.getString(R.string.ed_file_file_sudah_ada_dalam_daftar_versi, "$baseName.yes"))
        }

        return Result.YesFileIsAvailableLocally(yesFile)
    }

    /**
     * @param baseName the file name without \.(pdb|yes)(\.gz)?
     */
    private fun openCachedPdbFile(baseName: String, cacheFile: File): Result {
        val yesName = yesNameForPdb(baseName)

        // check if it exists previously
        if (AddonManager.getReadableVersionFile(yesName) != null) {
            throw IOException(context.getString(R.string.ed_this_file_is_already_on_the_list))
        }

        return Result.ShouldImportPdb(cacheFile, yesName)
    }

    /**
     * @return a filename for yes that is based on the name of the pdb file.
     * If the pdb file is "Abc Def.pdb" ([baseName] would be "abc def"),
     * this function returns "pdb-abcdef.yes".
     */
    private fun yesNameForPdb(baseName: String): String {
        return "pdb-${baseName.lowercase().replace("[^0-9a-z_.-]".toRegex(), "")}.yes"
    }
}
