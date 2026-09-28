package yuku.alkitab.base.smartsearch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import yuku.alkitab.base.smartsearch.LexiconSelection
import yuku.alkitab.base.smartsearch.SelectionReason
import yuku.alkitab.base.smartsearch.SmartSearchReport
import yuku.alkitab.debug.R

/** Which slice of the results the search screen lists. */
enum class ResultFilter { ALL, NEW, DROPPED }

/** Observable state of the panel, owned by the search screen so the result list can follow the filter. */
class SmartSearchPanelState {
    var report by mutableStateOf<SmartSearchReport?>(null)
    var expanded by mutableStateOf(false)
    var filter by mutableStateOf(ResultFilter.ALL)
}

/**
 * Sits above the search results and explains the search that produced them: a one-line summary
 * that is always visible, filter chips to list only the verses smart search gained or dropped,
 * and, expanded, how every word was resolved and what the engine did.
 */
@Composable
fun SmartSearchPanel(
    state: SmartSearchPanelState,
    onFilterChanged: (ResultFilter) -> Unit,
    onOpenLab: () -> Unit,
) {
    val report = state.report ?: return
    val cs = MaterialTheme.colorScheme

    Surface(color = cs.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { state.expanded = !state.expanded }
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_search_panel_title), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    Text(summary(report), style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                }
                Icon(
                    if (state.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(if (state.expanded) R.string.smart_search_panel_collapse else R.string.smart_search_panel_expand),
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }

            if (report.classic != null && report.widened) {
                FilterRow(report, state.filter, onFilterChanged)
            }

            AnimatedVisibility(visible = state.expanded) {
                val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                ) {
                    Text(lexiconLine(report.selection), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    if (!report.widened) {
                        Text(stringResource(R.string.smart_search_not_widened), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }

                    SectionTitle(stringResource(R.string.smart_search_section_terms))
                    for (outcome in report.terms) {
                        TermExplanation(
                            term = outcome.term,
                            vocabulary = report.selection.vocabulary,
                            verses = outcome.aris.size(),
                            millis = outcome.millis,
                        )
                        HorizontalDivider(color = cs.outlineVariant)
                    }

                    SectionTitle(stringResource(R.string.smart_search_section_engine))
                    EngineDetails(report)
                    TextButton(onClick = onOpenLab) {
                        Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.smart_search_open_lab))
                    }
                }
            }
            HorizontalDivider(color = cs.outlineVariant)
        }
    }
}

@Composable
private fun summary(report: SmartSearchReport): String {
    val count = report.result.size()
    return if (report.classic != null) {
        stringResource(R.string.smart_search_summary_compare, count, report.gained.size(), report.dropped.size())
    } else {
        stringResource(R.string.smart_search_summary_plain, count)
    }
}

@Composable
private fun FilterRow(report: SmartSearchReport, filter: ResultFilter, onFilterChanged: (ResultFilter) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == ResultFilter.ALL,
                onClick = { onFilterChanged(ResultFilter.ALL) },
                label = { Text(stringResource(R.string.smart_search_filter_all, report.result.size())) },
            )
            FilterChip(
                selected = filter == ResultFilter.NEW,
                onClick = { onFilterChanged(ResultFilter.NEW) },
                label = { Text(stringResource(R.string.smart_search_filter_new, report.gained.size())) },
                enabled = report.gained.size() > 0,
            )
            FilterChip(
                selected = filter == ResultFilter.DROPPED,
                onClick = { onFilterChanged(ResultFilter.DROPPED) },
                label = { Text(stringResource(R.string.smart_search_filter_dropped, report.dropped.size())) },
                enabled = report.dropped.size() > 0,
            )
        }
        val hint = when (filter) {
            ResultFilter.ALL -> null
            ResultFilter.NEW -> stringResource(R.string.smart_search_filter_hint_new)
            ResultFilter.DROPPED -> stringResource(R.string.smart_search_filter_hint_dropped)
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun lexiconLine(selection: LexiconSelection): String {
    val lex = selection.lexicon
    return when (selection.reason) {
        SelectionReason.BUILT_IN -> stringResource(R.string.smart_search_lexicon_builtin, lex?.id.orEmpty())
        SelectionReason.RULES -> stringResource(R.string.smart_search_lexicon_rules)
        SelectionReason.NO_BUILT_IN_LEXICON -> stringResource(R.string.smart_search_off_no_builtin)
        SelectionReason.UNSUPPORTED_LANGUAGE -> stringResource(R.string.smart_search_off_language, "")
        SelectionReason.DISABLED -> stringResource(R.string.smart_search_off_disabled)
    }
}

@Composable
private fun EngineDetails(report: SmartSearchReport) {
    val cs = MaterialTheme.colorScheme
    val lines = buildList {
        report.selection.lexicon?.let { lex ->
            add(stringResource(R.string.smart_search_engine_lexicon, "${lex.id} (${originLabel(lex.origin)})", lex.families.size, lex.formCount, lex.loadMillis.toInt()))
        }
        report.selection.vocabulary?.let { v ->
            add(stringResource(R.string.smart_search_engine_vocabulary, v.counts.size, v.tokenCount))
        }
        if (report.vocabularyMillis >= 0) {
            add(stringResource(R.string.smart_search_engine_vocabulary_built, report.vocabularyMillis.toInt()))
        }
        add(
            if (report.classicMillis >= 0) {
                stringResource(R.string.smart_search_engine_timings, report.planMillis.toInt(), report.searchMillis.toInt(), report.classicMillis.toInt())
            } else {
                stringResource(R.string.smart_search_engine_timings_no_classic, report.planMillis.toInt(), report.searchMillis.toInt())
            }
        )
        add("${report.versionName} · ${report.versionId}")
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (line in lines) {
            Text(line, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}
