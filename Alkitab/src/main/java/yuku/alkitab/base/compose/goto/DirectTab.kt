package yuku.alkitab.base.compose.goto

import android.graphics.Typeface
import android.text.Spanned
import android.text.TextUtils
import android.text.style.StyleSpan
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import yuku.alkitab.base.S
import yuku.alkitab.base.util.Jumper
import yuku.alkitab.debug.R
import java.util.regex.Pattern

private val NOBOOK_PATTERN: Pattern = Pattern.compile("(\\d+)(?:[ :.]+(\\d+))?")

@Composable
fun DirectTab(
    initialBookId: Int,
    initialChapter_1: Int,
    initialVerse_1: Int,
    isActive: Boolean,
    onGotoFinished: OnGotoFinished,
) {
    val context = LocalContext.current
    val books = remember { S.activeVersion().consecutiveBooks }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val candidates by remember(books) {
        derivedStateOf { computeCandidates(query.text, books) }
    }
    var errorRef by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val kbd = LocalSoftwareKeyboardController.current

    LaunchedEffect(isActive) {
        if (isActive) {
            focusRequester.requestFocus()
            kbd?.show()
        }
    }

    val sample = remember(initialBookId, initialChapter_1, initialVerse_1) {
        S.activeVersion().reference(initialBookId, initialChapter_1, initialVerse_1)
    }
    val prompt = TextUtils.expandTemplate(context.getText(R.string.jump_to_prompt), sample)
        .toAnnotatedString()

    fun submit() {
        val ref = query.text.trim()
        if (ref.isEmpty()) return

        val m = NOBOOK_PATTERN.matcher(ref)
        if (m.matches()) {
            val ch = m.group(1)?.toIntOrNull()
            if (ch != null) {
                val v = m.group(2)?.toIntOrNull() ?: 0
                onGotoFinished(GotoTab.DIRECT, initialBookId, ch, v)
                return
            }
        }
        val jumper = Jumper(ref)
        if (!jumper.parseSucceeded) {
            errorRef = ref
            return
        }
        onGotoFinished(GotoTab.DIRECT, jumper.getBookId(books), jumper.chapter, jumper.verse)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).imePadding()) {
        Text(text = prompt)
        Spacer(Modifier.size(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            trailingIcon = {
                IconButton(onClick = ::submit) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.ok),
                    )
                }
            },
        )

        if (candidates.isNotEmpty()) {
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(candidates, key = { it.title }) { c ->
                    ListItem(
                        headlineContent = { Text(c.title) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newText = if (c.bookOnly) c.title + " " else c.title
                                query = TextFieldValue(
                                    text = newText,
                                    selection = TextRange(newText.length),
                                )
                                if (c.hasVerse) submit()
                            },
                    )
                }
            }
        }
    }

    errorRef?.let { ref ->
        AlertDialog(
            onDismissRequest = { errorRef = null },
            confirmButton = {
                TextButton(onClick = { errorRef = null }) { Text(stringResource(R.string.ok)) }
            },
            text = { Text(stringResource(R.string.alamat_tidak_sah_alamat, ref)) },
        )
    }
}

private fun CharSequence.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    append(this@toAnnotatedString.toString())
    if (this@toAnnotatedString is Spanned) {
        for (span in getSpans(0, length, StyleSpan::class.java)) {
            if (span.style == Typeface.BOLD) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), getSpanStart(span), getSpanEnd(span))
            }
        }
    }
}
