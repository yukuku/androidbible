package yuku.alkitab.base.smartsearch

import yuku.alkitab.base.util.QueryTokenizer

/** How one query term is matched against verse text. */
enum class TermKind {
    /** Any word containing the letters: the app's classic behavior. */
    SUBSTRING,

    /** Exactly this word. */
    EXACT_WORD,

    /** These words in this order. */
    PHRASE,

    /** Any word of the family the term belongs to. */
    FAMILY,
}

/** Why a term ended up as its [TermKind]; this is what the diagnostics explain to the tester. */
enum class Resolution {
    /** The typed word is a form (or root) listed in the lexicon. */
    LEXICON_DIRECT,

    /** The typed word is not in the lexicon, but peeling affixes off it reached a form that is. */
    LEXICON_PEELED,

    /** The typed word occurs in the text yet has no relatives there, so it is its own family. */
    OWN_FAMILY,

    /** Neither the lexicon nor peeling knows the word, so the classic letter match is used. */
    FALLBACK_LETTERS,

    /** The term holds something other than letters (digits, punctuation), so letters are matched. */
    NOT_A_WORD,

    /** The reader asked for this exact word with `+` or quotes. */
    EXPLICIT_EXACT,

    /** The reader asked for a phrase with quotes. */
    EXPLICIT_PHRASE,

    /** Smart search is off or has no lexicon for this translation. */
    SMART_OFF,
}

/**
 * One term of a query, with everything needed to search for it and to explain the search.
 *
 * @property typed the term as typed, lowercase, without `+` or quotes.
 * @property legacyToken the term in [QueryTokenizer]'s encoding (`+` prefix for whole-word), used
 * for every kind except [TermKind.FAMILY].
 * @property forms for [TermKind.FAMILY], every word that counts as a match, most frequent first.
 * @property peelPath for [Resolution.LEXICON_PEELED], the affixes that were taken off.
 */
class PlannedTerm(
    val typed: String,
    val kind: TermKind,
    val resolution: Resolution,
    val legacyToken: String,
    val root: String? = null,
    val forms: List<String> = emptyList(),
    val peelPath: List<PeelStep> = emptyList(),
    val matcher: FamilyMatcher? = null,
    val occurrencesInText: Int = -1,
) {
    /**
     * The forms a letter search for [typed] could never reach: they do not contain the typed
     * letters. These are the verses the classic search was silently missing.
     */
    val formsUnreachableByLetters: List<String>
        get() = forms.filter { !it.contains(typed) }
}

/**
 * Turns a query string into [PlannedTerm]s, deciding for each term how it will be matched.
 *
 * With no lexicon every term keeps the classic behavior, so the plan is always usable and the
 * diagnostics can explain why nothing was widened.
 */
class SmartSearchPlanner(
    private val lexicon: SearchLexicon?,
    private val vocabulary: VersionVocabulary?,
) {
    fun plan(query: String?): List<PlannedTerm> {
        val tokens = QueryTokenizer.tokenize(query).distinct()
        return tokens.map { planToken(it) }
    }

    private fun planToken(token: String): PlannedTerm {
        if (QueryTokenizer.isPlussedToken(token)) {
            val bare = QueryTokenizer.tokenWithoutPlus(token)
            val isPhrase = QueryTokenizer.tokenizeMultiwordToken(bare) != null
            return PlannedTerm(
                typed = bare,
                kind = if (isPhrase) TermKind.PHRASE else TermKind.EXACT_WORD,
                resolution = if (isPhrase) Resolution.EXPLICIT_PHRASE else Resolution.EXPLICIT_EXACT,
                legacyToken = token,
                occurrencesInText = if (isPhrase) -1 else vocabulary?.countOf(bare) ?: -1,
            )
        }

        val lex = lexicon ?: return PlannedTerm(token, TermKind.SUBSTRING, Resolution.SMART_OFF, token)

        if (!token.all { Character.isLetter(it) || it == '-' }) {
            return PlannedTerm(token, TermKind.SUBSTRING, Resolution.NOT_A_WORD, token)
        }

        if (lex.isKnown(token)) {
            return family(token, lex.rootOf.getValue(token), Resolution.LEXICON_DIRECT, emptyList(), lex)
        }

        val vocab = vocabulary
        if (vocab != null && token in vocab) {
            return PlannedTerm(
                typed = token,
                kind = TermKind.EXACT_WORD,
                resolution = Resolution.OWN_FAMILY,
                legacyToken = "+$token",
                root = token,
                forms = listOf(token),
                occurrencesInText = vocab.countOf(token),
            )
        }

        val path = AffixPeeler.peel(token) { lex.isKnown(it) }
        if (path != null && path.isNotEmpty()) {
            return family(token, lex.rootOf.getValue(path.last().to), Resolution.LEXICON_PEELED, path, lex)
        }

        return PlannedTerm(token, TermKind.SUBSTRING, Resolution.FALLBACK_LETTERS, token, occurrencesInText = vocab?.countOf(token) ?: -1)
    }

    private fun family(typed: String, root: String, resolution: Resolution, path: List<PeelStep>, lex: SearchLexicon): PlannedTerm {
        val forms = lex.formsOf(root).let { if (typed in it) it else it + typed }
        return PlannedTerm(
            typed = typed,
            kind = TermKind.FAMILY,
            resolution = resolution,
            legacyToken = typed,
            root = root,
            forms = forms,
            peelPath = path,
            matcher = FamilyMatcher(forms.toHashSet(), lex.rootOf.keys),
            occurrencesInText = vocabulary?.let { v -> forms.sumOf { v.countOf(it) } } ?: -1,
        )
    }
}
