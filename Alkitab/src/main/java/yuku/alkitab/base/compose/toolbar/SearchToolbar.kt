package yuku.alkitab.base.compose.toolbar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yuku.alkitab.debug.R

@Composable
fun SearchToolbar(
    versionInitials: String,
    onNavigateUp: () -> Unit,
    onVersionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        BarIconButton(
            painter = androidx.appcompat.R.drawable.abc_ic_ab_back_material,
            contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
            contentColor = Color.White,
            onClick = onNavigateUp,
            modifier = Modifier.width(48.dp).fillMaxHeight(),
        )
        Text(
            text = stringResource(R.string.search),
            color = Color.White,
            fontSize = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Text(
            text = stringResource(R.string.search_version_select_label),
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp).padding(end = 8.dp),
        )
        VersionAudioControl(
            state = ReaderToolbarState(versionInitials = versionInitials),
            contentColor = Color.White,
            onVersionClick = onVersionClick,
            onAudioClick = {},
            modifier = Modifier.padding(end = 16.dp),
        )
    }
}
