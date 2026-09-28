package yuku.alkitab.base.smartsearch

import yuku.afw.storage.Preferences
import yuku.alkitab.base.settings.ExperimentalFlags
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.model.Version

private const val TAG = "LexiconRepository"

/** Which lexicon smart search should use; chosen in the Search Lab. */
enum class LexiconMode(val key: String) {
    /** The version's own lexicon, else the rules-only one. */
    AUTO("auto"),

    /** Only the version's own lexicon; without one, search stays classic. */
    BUILT_IN("builtin"),

    /** Always the rules-only lexicon derived on the device, to compare against the version's own. */
    RULES("rules"),
    ;

    companion object {
        fun current(): LexiconMode {
            val key = Preferences.getString(Prefkey.smartSearchLexiconMode, AUTO.key)
            return entries.firstOrNull { it.key == key } ?: AUTO
        }

        fun set(mode: LexiconMode) = Preferences.setString(Prefkey.smartSearchLexiconMode, mode.key)
    }
}

/** Why smart search is or is not widening terms for a version. */
enum class SelectionReason {
    /** Turned off in the settings. */
    DISABLED,

    /** The version's own lexicon is in use. */
    BUILT_IN,

    /** The rules-only lexicon derived from the version's own words is in use. */
    RULES,

    /** Built-in-only mode was chosen and the version carries no lexicon. */
    NO_BUILT_IN_LEXICON,

    /** The version is not in a language the affix rules know, and carries no lexicon. */
    UNSUPPORTED_LANGUAGE,
}

/** The lexicon picked for one version, with the reason, for the search and its diagnostics. */
class LexiconSelection(
    val lexicon: SearchLexicon?,
    val reason: SelectionReason,
    val vocabulary: VersionVocabulary?,
)

/**
 * Loads and caches the lexicons smart search uses: the one a version carries in its own data, or
 * one derived from the version's words by rules alone.
 */
object LexiconRepository {
    /** Keyed by version id; a null value records that the version carries no lexicon. */
    private val builtIn = HashMap<String, SearchLexicon?>()
    private val rulesByVersion = HashMap<String, SearchLexicon>()

    /** The version's own lexicon, or null when it carries none. Reads it on first use, so call it off the main thread. */
    fun builtInFor(version: Version, versionId: String): SearchLexicon? {
        synchronized(this) {
            if (versionId in builtIn) return builtIn[versionId]
        }
        val t0 = System.nanoTime()
        val families = try {
            version.loadLexicon()
        } catch (e: Exception) {
            AppLog.e(TAG, "loading lexicon of $versionId", e)
            null
        }
        val lex = families?.let {
            SearchLexicon(version.shortName ?: versionId, LexiconOrigin.VERSION, it, (System.nanoTime() - t0) / 1_000_000)
        }
        synchronized(this) { builtIn[versionId] = lex }
        return lex
    }

    fun rulesFor(vocabulary: VersionVocabulary): SearchLexicon = synchronized(this) {
        rulesByVersion[vocabulary.versionKey]
    } ?: RulesLexiconBuilder.build(vocabulary).also { built ->
        synchronized(this) { rulesByVersion[vocabulary.versionKey] = built }
    }

    /**
     * Picks the lexicon for searching [version], building the version's vocabulary if needed.
     * Reads the whole text on first use, so call it off the main thread.
     */
    fun select(version: Version, versionId: String, mode: LexiconMode = LexiconMode.current()): LexiconSelection {
        if (!ExperimentalFlags.smartSearch()) {
            return LexiconSelection(null, SelectionReason.DISABLED, null)
        }
        return selectIgnoringSwitch(version, versionId, mode)
    }

    /** Like [select], but ignores the on/off switch; the Search Lab uses it to preview. */
    fun selectIgnoringSwitch(version: Version, versionId: String, mode: LexiconMode): LexiconSelection {
        val own = if (mode != LexiconMode.RULES) builtInFor(version, versionId) else null
        if (own != null) {
            return LexiconSelection(own, SelectionReason.BUILT_IN, VocabularyCache.get(versionId, version))
        }
        if (mode == LexiconMode.BUILT_IN) {
            return LexiconSelection(null, SelectionReason.NO_BUILT_IN_LEXICON, null)
        }
        if (!SearchLexicon.sameLanguage("id", version.locale)) {
            return LexiconSelection(null, SelectionReason.UNSUPPORTED_LANGUAGE, null)
        }
        val vocab = VocabularyCache.get(versionId, version)
        return LexiconSelection(rulesFor(vocab), SelectionReason.RULES, vocab)
    }
}
