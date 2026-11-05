package app.arcify.ui.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.arcify.R
import app.arcify.core.content.WebsiteRuleKind

/** Browser study mode, website rules and monitored browsers (F06). */
@Composable
fun WebsitesRoute(viewModel: ContentViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    MessageToasts(viewModel.messages)
    var input by rememberSaveable { mutableStateOf("") }
    var pickingBrowser by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf<String?>(null) }
    var confirmAdultLock by remember { mutableStateOf(false) }
    var adultInput by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ToggleRow(
                title = stringResource(R.string.browser_study_mode),
                description = stringResource(R.string.browser_study_mode_desc),
                checked = ui.settings.browserStudyMode,
                onCheckedChange = viewModel::setBrowserStudyMode,
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(R.string.websites_title) }
        item { Text(stringResource(R.string.websites_desc), style = MaterialTheme.typography.bodySmall) }
        if (ui.websites.isEmpty()) {
            item { Text(stringResource(R.string.websites_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        items(ui.websites, key = { it.rule.pattern }) { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.rule.pattern, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(if (row.rule.kind == WebsiteRuleKind.BLOCK) R.string.website_blocked else R.string.website_allowed),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (row.rule.kind == WebsiteRuleKind.BLOCK) LockControl(locked = row.locked, onLock = { confirmLock = row.rule.pattern })
                TextButton(onClick = { viewModel.removeWebsite(row.rule.pattern) }, enabled = !row.locked) {
                    Text(stringResource(R.string.remove))
                }
            }
        }
        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.website_add_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    viewModel.addWebsite(input, WebsiteRuleKind.BLOCK)
                    input = ""
                }) { Text(stringResource(R.string.website_add_block)) }
                OutlinedButton(onClick = {
                    viewModel.addWebsite(input, WebsiteRuleKind.ALLOW)
                    input = ""
                }) { Text(stringResource(R.string.website_add_allow)) }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item {
            ToggleRow(
                title = stringResource(R.string.adult_title),
                description = stringResource(R.string.adult_desc),
                checked = ui.adultFilter.enabled,
                onCheckedChange = viewModel::setAdultFilter,
            )
        }
        if (ui.adultFilter.enabled) {
            item {
                LockControl(
                    locked = ui.adultFilter.isStrict(ui.now),
                    onLock = { confirmAdultLock = true },
                )
            }
            item { Text(stringResource(R.string.adult_exceptions), style = MaterialTheme.typography.titleSmall) }
            if (ui.adultFilter.exceptions.isEmpty()) {
                item { Text(stringResource(R.string.adult_exceptions_empty), style = MaterialTheme.typography.bodySmall) }
            }
            items(ui.adultFilter.exceptions.sorted(), key = { "adult:$it" }) { domain ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(domain, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.removeAdultException(domain) }) { Text(stringResource(R.string.remove)) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = adultInput,
                        onValueChange = { adultInput = it },
                        label = { Text(stringResource(R.string.adult_exception_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        viewModel.addAdultException(adultInput)
                        adultInput = ""
                    }) { Text(stringResource(R.string.add)) }
                }
            }
        }
        item { Text(stringResource(R.string.adult_credit), style = MaterialTheme.typography.bodySmall) }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(R.string.browsers_title) }
        item { Text(stringResource(R.string.browsers_desc), style = MaterialTheme.typography.bodySmall) }
        items(ui.browsers, key = { it.browser.packageName }) { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Spacer(Modifier.padding(4.dp))
                TextButton(onClick = { viewModel.removeBrowser(row.browser.packageName) }) { Text(stringResource(R.string.remove)) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { pickingBrowser = true }) { Text(stringResource(R.string.browsers_add)) }
                OutlinedButton(onClick = viewModel::resetBrowsers) { Text(stringResource(R.string.reset_defaults)) }
            }
        }
    }

    if (pickingBrowser) {
        val existing = ui.browsers.map { it.browser.packageName }.toSet()
        AppPickerDialog(
            apps = ui.installed?.filterNot { it.packageName in existing },
            onPick = { app ->
                pickingBrowser = false
                viewModel.addBrowser(app.packageName)
            },
            onDismiss = { pickingBrowser = false },
        )
    }

    if (confirmAdultLock) {
        ConfirmLockDialog(
            body = stringResource(R.string.adult_lock_confirm_body),
            onConfirm = {
                confirmAdultLock = false
                viewModel.lockAdultFilter()
            },
            onDismiss = { confirmAdultLock = false },
        )
    }

    confirmLock?.let { pattern ->
        ConfirmLockDialog(
            body = stringResource(R.string.website_lock_confirm_body, pattern),
            onConfirm = {
                confirmLock = null
                viewModel.lockWebsite(pattern)
            },
            onDismiss = { confirmLock = null },
        )
    }
}
