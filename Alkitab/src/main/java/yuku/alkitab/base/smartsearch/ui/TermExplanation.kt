package yuku.alkitab.base.smartsearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import yuku.alkitab.base.smartsearch.LexiconOrigin
import yuku.alkitab.base.smartsearch.PeelStep
import yuku.alkitab.base.smartsearch.PlannedTerm
import yuku.alkitab.base.smartsearch.Resolution
import yuku.alkitab.base.smartsearch.TermKind
import yuku.alkitab.base.smartsearch.VersionVocabulary
import yuku.alkitab.debug.R

private const val COLLAPSED_FORM_COUNT = 18

/** Colors of the pill naming how a term was resolved; each resolution keeps one color everywhere. */
@Composable
private fun resolutionColors(r: Resolution): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (r) {
        Resolution.LEXICON_DIRECT -> cs.primaryContainer to cs.onPrimaryContainer
        Resolution.LEXICON_PEELED -> cs.tertiaryContainer to cs.onTertiaryContainer
        Resolution.OWN_FAMILY -> cs.secondaryContainer to cs.onSecondaryContainer
        Resolution.EXPLICIT_EXACT, Resolution.EXPLICIT_PHRASE -> cs.surfaceVariant to cs.onSurfaceVariant
        Resolution.FALLBACK_LETTERS, Resolution.NOT_A_WORD, Resolution.SMART_OFF -> cs.errorContainer to cs.onErrorContainer
    }
}

@Composable
fun resolutionLabel(r: Resolution): String = stringResource(
    when (r) {
        Resolution.LEXICON_DIRECT -> R.string.smart_search_how_direct
        Resolution.LEXICON_PEELED -> R.string.smart_search_how_peeled
        Resolution.OWN_FAMILY -> R.string.smart_search_how_own_family
        Resolution.EXPLICIT_EXACT -> R.string.smart_search_how_exact
        Resolution.EXPLICIT_PHRASE -> R.string.smart_search_how_phrase
        Resolution.FALLBACK_LETTERS, Resolution.NOT_A_WORD, Resolution.SMART_OFF -> R.string.smart_search_how_letters
    }
)

fun peelTrail(path: List<PeelStep>): String = buildString {
    if (path.isEmpty()) return@buildString
    append(path.first().from)
    for (step in path) {
        append(" (").append(step.label).append(") → ").append(step.to)
    }
}

@Composable
fun explanation(term: PlannedTerm): String = when (term.resolution) {
    Resolution.LEXICON_DIRECT -> if (term.root == term.typed) {
        stringResource(R.string.smart_search_explain_direct_root, term.typed)
    } else {
        stringResource(R.string.smart_search_explain_direct, term.typed, term.root.orEmpty())
    }
    Resolution.LEXICON_PEELED -> stringResource(R.string.smart_search_explain_peeled, term.typed, peelTrail(term.peelPath), term.root.orEmpty())
    Resolution.OWN_FAMILY -> stringResource(R.string.smart_search_explain_own_family, term.typed, term.occurrencesInText.coerceAtLeast(0))
    Resolution.FALLBACK_LETTERS -> stringResource(R.string.smart_search_explain_fallback, term.typed)
    Resolution.NOT_A_WORD -> stringResource(R.string.smart_search_explain_not_a_word, term.typed)
    Resolution.EXPLICIT_EXACT -> stringResource(R.string.smart_search_explain_exact, term.typed)
    Resolution.EXPLICIT_PHRASE -> stringResource(R.string.smart_search_explain_phrase)
    Resolution.SMART_OFF -> stringResource(R.string.smart_search_explain_off, term.typed)
}

@Composable
fun originLabel(origin: LexiconOrigin): String = stringResource(
    when (origin) {
        LexiconOrigin.VERSION -> R.string.smart_search_origin_version
        LexiconOrigin.RULES -> R.string.smart_search_origin_rules
    }
)

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, mono: Boolean = false, outline: Color? = null) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        color = content,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = if (mono) FontFamily.Monospace else null,
        modifier = modifier
            .background(container, shape)
            .let { if (outline != null) it.border(1.dp, outline, shape) else it }
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * One query term: what was typed, what it was resolved to and why, and every form it searches.
 *
 * @param verses how many verses the term matched on its own, or null when not searched yet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TermExplanation(
    term: PlannedTerm,
    vocabulary: VersionVocabulary?,
    verses: Int?,
    millis: Long?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var showAllForms by remember(term) { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = term.typed,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            val root = term.root
            if (term.kind == TermKind.FAMILY && root != null && root != term.typed) {
                Text("  →  ", color = cs.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                Text(root, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, color = cs.primary)
            }
            Spacer(Modifier.weight(1f))
            val (bg, fg) = resolutionColors(term.resolution)
            Pill(resolutionLabel(term.resolution), bg, fg)
        }

        if (term.peelPath.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(term.peelPath.first().from, cs.surfaceVariant, cs.onSurfaceVariant, mono = true)
                for (step in term.peelPath) {
                    Pill("✂ " + step.label, cs.tertiaryContainer, cs.onTertiaryContainer, mono = true)
                    Pill(step.to, cs.surfaceVariant, cs.onSurfaceVariant, mono = true)
                }
            }
        }

        Text(explanation(term), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)

        if (term.kind == TermKind.FAMILY && term.forms.isNotEmpty()) {
            val unreachable = remember(term) { term.formsUnreachableByLetters.toHashSet() }
            val shown = if (showAllForms) term.forms else term.forms.take(COLLAPSED_FORM_COUNT)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (form in shown) {
                    val count = vocabulary?.countOf(form) ?: -1
                    val label = if (count > 0) stringResource(R.string.search_lab_form_count, form, count) else form
                    if (count == 0) {
                        // Listed in the word list but not used by this translation: searched, yet never found.
                        Pill(label, Color.Transparent, cs.onSurfaceVariant.copy(alpha = 0.7f), mono = true, outline = cs.outlineVariant)
                    } else if (form in unreachable) {
                        Pill(label, cs.tertiary, cs.onTertiary, mono = true)
                    } else {
                        Pill(label, cs.surfaceContainerHighest, cs.onSurface, mono = true)
                    }
                }
                if (term.forms.size > COLLAPSED_FORM_COUNT) {
                    val toggle = if (showAllForms) {
                        stringResource(R.string.smart_search_fewer_forms)
                    } else {
                        stringResource(R.string.smart_search_more_forms, term.forms.size - COLLAPSED_FORM_COUNT)
                    }
                    Text(
                        toggle,
                        color = cs.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clickable { showAllForms = !showAllForms }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (vocabulary != null && term.forms.any { vocabulary.countOf(it) == 0 }) {
                Row(verticalAlignment = Alignment.Top) {
                    Spacer(
                        Modifier
                            .padding(top = 3.dp)
                            .size(10.dp)
                            .border(1.dp, cs.outlineVariant, RoundedCornerShape(2.dp))
                    )
                    Text(
                        stringResource(R.string.smart_search_forms_absent_legend),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            if (unreachable.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Top) {
                    Spacer(
                        Modifier
                            .padding(top = 3.dp)
                            .size(10.dp)
                            .background(cs.tertiary, RoundedCornerShape(2.dp))
                    )
                    Text(
                        stringResource(R.string.smart_search_forms_legend, term.typed),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }

        val stats = buildList {
            if (verses != null && millis != null) add(stringResource(R.string.smart_search_term_stats, verses, millis.toInt()))
            if (term.occurrencesInText >= 0 && term.kind != TermKind.PHRASE) add(stringResource(R.string.smart_search_term_occurrences, term.occurrencesInText))
        }
        if (stats.isNotEmpty()) {
            Text(stats.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}
