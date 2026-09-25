package yuku.alkitab.base.smartsearch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import yuku.alkitab.base.smartsearch.LexiconMode
import yuku.alkitab.base.smartsearch.PlannedTerm
import yuku.alkitab.base.smartsearch.VersionVocabulary
import yuku.alkitab.debug.R

/** What the Search Lab screen can ask for; implemented by [SearchLabViewModel]. */
interface SearchLabActions {
    fun setSmartEnabled(on: Boolean)
    fun setDiagnostics(on: Boolean)
    fun setMode(mode: LexiconMode)
    fun setQuery(q: String)
    fun runBenchmark()
}

@Composable
fun SearchLabScreen(viewModel: SearchLabViewModel, onUp: () -> Unit, onChangeVersion: () -> Unit) {
    val state by viewModel.state.collectAsState()
    SearchLabContent(state, viewModel, onUp, onChangeVersion)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchLabContent(
    state: SearchLabState,
    actions: SearchLabActions,
    onUp: () -> Unit,
    onChangeVersion: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                title = { Text(stringResource(R.string.search_lab_title)) },
                navigationIcon = {
                    IconButton(onClick = onUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SettingsCard(state, actions) }
            item { TranslationCard(state, onChangeVersion) }
            item { WordCard(state, actions) }
            item { BenchmarkCard(state, actions) }
        }
    }
}

@Composable
private fun LabCard(title: String, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCard(state: SearchLabState, viewModel: SearchLabActions) {
    LabCard(stringResource(R.string.search_lab_settings_title)) {
        SwitchRow(stringResource(R.string.pref_smartSearch_title), stringResource(R.string.pref_smartSearch_summary), state.smartEnabled) { viewModel.setSmartEnabled(it) }
        SwitchRow(
            stringResource(R.string.pref_smartSearchDiagnostics_title),
            stringResource(R.string.pref_smartSearchDiagnostics_summary),
            state.diagnostics,
            enabled = state.smartEnabled,
        ) { viewModel.setDiagnostics(it) }

        Text(stringResource(R.string.search_lab_mode_title), style = MaterialTheme.typography.bodyLarge)
        val modes = listOf(
            LexiconMode.AUTO to R.string.search_lab_mode_auto,
            LexiconMode.BUILT_IN to R.string.search_lab_mode_builtin,
            LexiconMode.RULES to R.string.search_lab_mode_rules,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { viewModel.setMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    label = { Text(stringResource(label), maxLines = 1) },
                )
            }
        }
        Text(
            stringResource(
                when (state.mode) {
                    LexiconMode.AUTO -> R.string.search_lab_mode_auto_desc
                    LexiconMode.BUILT_IN -> R.string.search_lab_mode_builtin_desc
                    LexiconMode.RULES -> R.string.search_lab_mode_rules_desc
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranslationCard(state: SearchLabState, onChangeVersion: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    LabCard(stringResource(R.string.search_lab_translation_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.versionName, style = MaterialTheme.typography.bodyLarge)
                Text("${state.versionId} · ${state.versionLocale.orEmpty()}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant)
            }
            OutlinedButton(onClick = onChangeVersion) { Text(stringResource(R.string.search_lab_change_version)) }
        }

        val selection = state.selection
        if (state.preparing || selection == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.search_lab_preparing), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            return@LabCard
        }

        Text(lexiconLine(selection), style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
        val lex = selection.lexicon
        if (lex == null) {
            Text(stringResource(R.string.search_lab_selection_none), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        } else {
            Text(
                stringResource(
                    R.string.search_lab_lexicon_line,
                    lex.id,
                    originLabel(lex.origin),
                    lex.families.size,
                    lex.formCount,
                    lex.loadMillis.toInt(),
                ),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurfaceVariant,
            )
        }
        selection.vocabulary?.let { v ->
            Text(
                stringResource(R.string.search_lab_vocabulary, v.counts.size, v.tokenCount, v.buildMillis.toInt()),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WordCard(state: SearchLabState, viewModel: SearchLabActions) {
    val cs = MaterialTheme.colorScheme
    LabCard(stringResource(R.string.search_lab_word_title)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.setQuery(it) },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_lab_word_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.wordBusy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        val word = state.word
        if (word == null) {
            if (!state.wordBusy) Text(stringResource(R.string.search_lab_word_empty), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            return@LabCard
        }

        Surface(color = cs.surfaceContainerHighest, shape = RoundedCornerShape(10.dp)) {
            Text(
                stringResource(R.string.search_lab_counts, word.letterCount, word.smartCount, word.gained, word.dropped),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().padding(10.dp),
            )
        }
        TermList(word.primary, state.selection?.vocabulary)

        val alternative = word.alternative
        if (alternative != null) {
            HorizontalDivider(color = cs.outlineVariant)
            SectionTitle(stringResource(if (word.alternativeIsRules) R.string.search_lab_compare_rules else R.string.search_lab_compare_curated))
            TermList(alternative, state.selection?.vocabulary)
        }
    }
}

@Composable
private fun TermList(terms: List<PlannedTerm>, vocabulary: VersionVocabulary?) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (term in terms) {
            TermExplanation(term = term, vocabulary = vocabulary, verses = null, millis = null)
        }
    }
}

@Composable
private fun BenchmarkCard(state: SearchLabState, viewModel: SearchLabActions) {
    val cs = MaterialTheme.colorScheme
    LabCard(stringResource(R.string.search_lab_benchmark_title)) {
        Text(stringResource(R.string.search_lab_benchmark_desc), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        FilledTonalButton(onClick = { viewModel.runBenchmark() }, enabled = !state.benchmarkRunning && state.selection != null) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (state.benchmarkRunning) R.string.search_lab_benchmark_running else R.string.search_lab_benchmark_run))
        }
        val rows = state.benchmark ?: return@LabCard
        if (rows.isEmpty()) return@LabCard

        @Composable
        fun Cells(q: String, a: String, b: String, c: String, d: String, e: String, header: Boolean, gainedHot: Boolean = false, droppedHot: Boolean = false) {
            val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
            val color = if (header) cs.onSurfaceVariant else cs.onSurface
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(q, style = style, color = color, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(2.1f), maxLines = 1)
                Text(a, style = style, color = color, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1.3f), maxLines = 1)
                Text(b, style = style, color = color, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1.1f))
                Text(c, style = style, color = if (gainedHot) cs.tertiary else color, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text(d, style = style, color = if (droppedHot) cs.error else color, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1.4f), maxLines = 1)
                Text(e, style = style, color = color, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.7f), maxLines = 1)
            }
        }

        Column {
            Cells(
                stringResource(R.string.search_lab_benchmark_col_query),
                stringResource(R.string.search_lab_benchmark_col_letters),
                stringResource(R.string.search_lab_benchmark_col_smart),
                stringResource(R.string.search_lab_benchmark_col_new),
                stringResource(R.string.search_lab_benchmark_col_dropped),
                stringResource(R.string.search_lab_benchmark_col_ms),
                header = true,
            )
            HorizontalDivider(color = cs.outlineVariant)
            for (r in rows) {
                Cells(r.query, "${r.letters}", "${r.smart}", "+${r.gained}", "−${r.dropped}", "${r.smartMillis}", header = false, gainedHot = r.gained > 0, droppedHot = r.dropped > 0)
            }
        }
    }
}
