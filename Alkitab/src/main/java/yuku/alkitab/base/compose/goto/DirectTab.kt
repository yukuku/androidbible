package yuku.alkitab.base.compose.goto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import yuku.alkitab.base.S
import yuku.alkitab.base.util.Jumper
import yuku.alkitab.debug.R
import java.util.regex.Pattern

private val NOBOOK_PATTERN: Pattern = Pattern.compile("(\\d+)(?:[ :.]+(\\d+))?")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectTab(
    initialBookId: Int,
    initialChapter_1: Int,
    initialVerse_1: Int,
    autoFocus: Boolean,
    onGotoFinished: OnGotoFinished,
) {
    val books = remember { S.activeVersion().consecutiveBooks }
    var query by rememberSaveable { mutableStateOf("") }
    val candidates by remember(books) {
        derivedStateOf { computeCandidates(query, books) }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var errorRef by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val kbd = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (autoFocus) {
            focusRequester.requestFocus()
            kbd?.show()
        }
    }

    val sample = remember(initialBookId, initialChapter_1, initialVerse_1) {
        S.activeVersion().reference(initialBookId, initialChapter_1, initialVerse_1)
    }

    fun submit() {
        val ref = query.trim()
        if (ref.isEmpty()) return

        val m = NOBOOK_PATTERN.matcher(ref)
        if (m.matches()) {
            try {
                val ch = (m.group(1) ?: return).toInt()
                val v = m.group(2)?.toInt() ?: 0
                onGotoFinished(GotoTab.DIRECT, initialBookId, ch, v)
                return
            } catch (_: NumberFormatException) {}
        }
        val jumper = Jumper(ref)
        if (!jumper.parseSucceeded) {
            errorRef = ref
            return
        }
        onGotoFinished(GotoTab.DIRECT, jumper.getBookId(books), jumper.chapter, jumper.verse)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = stringResource(R.string.jump_to_prompt, sample))
        Spacer(Modifier.size(16.dp))

        ExposedDropdownMenuBox(
            expanded = menuOpen && candidates.isNotEmpty(),
            onExpandedChange = { menuOpen = it },
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; menuOpen = true },
                singleLine = true,
                modifier = Modifier.menuAnchor().fillMaxWidth().focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )
            DropdownMenu(
                expanded = menuOpen && candidates.isNotEmpty(),
                onDismissRequest = { menuOpen = false },
            ) {
                candidates.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c.title) },
                        onClick = {
                            if (c.bookOnly) {
                                query = c.title + " "
                                menuOpen = false
                            } else {
                                query = c.title
                                menuOpen = false
                                submit()
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.size(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = ::submit) { Text(stringResource(R.string.ok)) }
        }
    }

    errorRef?.let { ref ->
        AlertDialog(
            onDismissRequest = { errorRef = null },
            confirmButton = { TextButton(onClick = { errorRef = null }) { Text(stringResource(R.string.ok)) } },
            text = { Text(stringResource(R.string.alamat_tidak_sah_alamat, ref)) },
        )
    }
}
