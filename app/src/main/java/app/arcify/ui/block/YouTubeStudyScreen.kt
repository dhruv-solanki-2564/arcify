package app.arcify.ui.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.arcify.R

/** YouTube study mode and the editable study-channel list (F05). */
@Composable
fun YouTubeStudyRoute(viewModel: ContentViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    MessageToasts(viewModel.messages)
    var input by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ToggleRow(
                title = stringResource(R.string.youtube_study_mode),
                description = stringResource(R.string.youtube_study_mode_desc),
                checked = ui.settings.youtubeStudyMode,
                onCheckedChange = viewModel::setYoutubeStudyMode,
            )
        }
        item { Text(stringResource(R.string.youtube_detection_note), style = MaterialTheme.typography.bodySmall) }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(R.string.youtube_channels) }
        if (ui.channels.isEmpty()) {
            item { Text(stringResource(R.string.youtube_channels_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        items(ui.channels, key = { it.id }) { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.channel.displayName, style = MaterialTheme.typography.bodyLarge)
                    item.channel.handle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                TextButton(onClick = { viewModel.removeChannel(item.id) }) { Text(stringResource(R.string.remove)) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.youtube_add_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = {
                    viewModel.addChannel(input)
                    input = ""
                }) { Text(stringResource(R.string.add)) }
            }
        }
        item {
            OutlinedButton(onClick = viewModel::resetChannels) { Text(stringResource(R.string.reset_defaults)) }
        }
    }
}

@Composable
internal fun ToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
