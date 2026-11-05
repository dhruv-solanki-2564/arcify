package app.arcify.ui.block

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.arcify.R
import app.arcify.blocker.BlockerPermissions

/** Sub-screens of the Block tab. */
enum class BlockSection(val route: String, @param:StringRes val title: Int, @param:StringRes val summary: Int) {
    LIMITS("block/limits", R.string.section_limits, R.string.section_limits_summary),
    SHORTS("block/shorts", R.string.section_shorts, R.string.section_shorts_summary),
    YOUTUBE("block/youtube", R.string.section_youtube, R.string.section_youtube_summary),
    WEBSITES("block/websites", R.string.section_websites, R.string.section_websites_summary),
}

@Composable
fun BlockHubRoute(onOpen: (BlockSection) -> Unit, viewModel: BlockViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionTitle(R.string.block_permissions_title) }
        item {
            PermissionRow(
                title = stringResource(R.string.perm_accessibility),
                description = stringResource(R.string.perm_accessibility_desc),
                granted = ui.permissions.accessibility,
                onTurnOn = { context.startActivity(BlockerPermissions.accessibilitySettings()) },
            )
        }
        item {
            PermissionRow(
                title = stringResource(R.string.perm_usage),
                description = stringResource(R.string.perm_usage_desc),
                granted = ui.permissions.usageAccess,
                onTurnOn = { context.startActivity(BlockerPermissions.usageAccessSettings()) },
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        BlockSection.entries.forEach { section ->
            item(key = section.route) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(section) }
                        .padding(vertical = 12.dp),
                ) {
                    Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(section.summary), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleLarge)
}

@Composable
internal fun PermissionRow(title: String, description: String, granted: Boolean, onTurnOn: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        if (granted) {
            Text(stringResource(R.string.perm_on), style = MaterialTheme.typography.labelLarge)
        } else {
            Button(onClick = onTurnOn) { Text(stringResource(R.string.perm_turn_on)) }
        }
    }
}

/** Shows one-off messages (e.g. "locked until midnight") from a view model as toasts. */
@Composable
internal fun MessageToasts(messages: kotlinx.coroutines.flow.Flow<Int>) {
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
}
