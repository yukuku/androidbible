package yuku.alkitab.base.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import yuku.alkitab.debug.R

@Composable
fun NumericEntryValue(
    text: String,
    isActive: Boolean,
    isReplaceOnNextInput: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val border = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF89969D)
    val description = stringResource(if (isReplaceOnNextInput) R.string.keypad_value_replace else R.string.keypad_value_append)
    val fieldModifier = modifier
        .semantics { stateDescription = description }
        .drawWithContent {
            drawContent()
            val width = if (isActive) 2.dp.toPx() else 1.dp.toPx()
            drawRoundRect(
                color = border,
                topLeft = Offset(width / 2, width / 2),
                size = Size(size.width - width, size.height - width),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width, pathEffect = if (isActive) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
        }
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = text.ifEmpty { "–" },
                style = MaterialTheme.typography.headlineMedium,
                color = if (isReplaceOnNextInput) Color(0xFFB2BDC4) else Color(0xFFF4F7F9),
            )
        }
    }
    if (onClick == null) {
        Surface(color = Color(0xFF293C45), shape = RoundedCornerShape(8.dp), modifier = fieldModifier, content = content)
    } else {
        Surface(onClick = onClick, color = Color(0xFF293C45), shape = RoundedCornerShape(8.dp), modifier = fieldModifier, content = content)
    }
}
