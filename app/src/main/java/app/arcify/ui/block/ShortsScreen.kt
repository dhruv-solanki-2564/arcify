package app.arcify.ui.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.arcify.R
import app.arcify.block.label
import app.arcify.block.shortFormName
import app.arcify.core.content.IgSurface
import app.arcify.core.content.Platform
import app.arcify.core.content.ShortFormMode
import app.arcify.core.content.ShortFormRule

private val LimitPresets = listOf(5, 10, 20, 30, 50)
private val InstagramSurfaces = listOf(IgSurface.REELS_TAB, IgSurface.FEED, IgSurface.EXPLORE, IgSurface.DM)

/** A pending "lock for today" confirmation. */
private data class LockRequest(val platform: Platform, val surface: IgSurface?)

/** Shorts & Reels rules, Instagram surfaces, today's counts (F04). */
@Composable
fun ShortsRoute(viewModel: ContentViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    MessageToasts(viewModel.messages)
    var lockRequest by remember { mutableStateOf<LockRequest?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionTitle(R.string.shorts_today) }
        if (ui.counts.isEmpty()) {
            item { Text(stringResource(R.string.shorts_today_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        items(ui.counts, key = { "${it.viaLabel}/${it.platform}" }) { row ->
            Text(
                stringResource(R.string.shorts_count_row, row.viaLabel, stringResource(row.platform.shortFormName()), row.count),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(R.string.shorts_rules) }
        item { Text(stringResource(R.string.shorts_rules_desc), style = MaterialTheme.typography.bodySmall) }
        Platform.entries.forEach { platform ->
            item(key = "rule/$platform") {
                val rule = ui.rule(platform)
                RuleEditor(
                    title = stringResource(platform.shortFormName()),
                    rule = rule,
                    locked = ui.isLocked(rule),
                    inheritOption = false,
                    onChange = { mode, limit -> viewModel.setRule(platform, null, mode, limit) },
                    onInherit = {},
                    onLock = { lockRequest = LockRequest(platform, null) },
                )
            }
            if (platform == Platform.INSTAGRAM) {
                item(key = "ig-surfaces") {
                    Text(stringResource(R.string.shorts_ig_surfaces), style = MaterialTheme.typography.titleSmall)
                }
                InstagramSurfaces.forEach { surface ->
                    item(key = "rule/ig/$surface") {
                        val rule = ui.rule(Platform.INSTAGRAM, surface)
                        RuleEditor(
                            title = stringResource(surface.label()),
                            rule = rule,
                            locked = ui.isLocked(rule),
                            inheritOption = true,
                            onChange = { mode, limit -> viewModel.setRule(Platform.INSTAGRAM, surface, mode, limit) },
                            onInherit = { viewModel.inheritSurface(surface) },
                            onLock = { lockRequest = LockRequest(Platform.INSTAGRAM, surface) },
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        }

        if (ui.recorderAvailable) {
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle(R.string.recorder_title) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.recorder_desc),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Switch(checked = ui.recorderOn, onCheckedChange = viewModel::setRecorder)
                }
            }
        }
    }

    lockRequest?.let { request ->
        val name = request.surface?.let { stringResource(it.label()) } ?: stringResource(request.platform.shortFormName())
        ConfirmLockDialog(
            body = stringResource(R.string.shorts_lock_confirm_body, name),
            onConfirm = {
                lockRequest = null
                viewModel.lockRule(request.platform, request.surface)
            },
            onDismiss = { lockRequest = null },
        )
    }
}

/**
 * Allow / Block / Limit chips for one rule. With [inheritOption], an extra "Same as Instagram"
 * chip removes the surface rule.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(
    title: String,
    rule: ShortFormRule?,
    locked: Boolean,
    inheritOption: Boolean,
    onChange: (ShortFormMode, Int?) -> Unit,
    onInherit: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        val mode = rule?.mode
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (inheritOption) {
                FilterChip(selected = rule == null, onClick = onInherit, label = { Text(stringResource(R.string.rule_inherit)) })
            }
            FilterChip(
                selected = mode == ShortFormMode.ALLOW || (!inheritOption && rule == null),
                onClick = { onChange(ShortFormMode.ALLOW, null) },
                label = { Text(stringResource(R.string.rule_allow)) },
            )
            FilterChip(
                selected = mode == ShortFormMode.BLOCK,
                onClick = { onChange(ShortFormMode.BLOCK, null) },
                label = { Text(stringResource(R.string.rule_block)) },
            )
            FilterChip(
                selected = mode == ShortFormMode.LIMIT,
                onClick = { onChange(ShortFormMode.LIMIT, rule?.dailyLimit) },
                label = { Text(stringResource(R.string.rule_limit)) },
            )
        }
        if (mode == ShortFormMode.LIMIT) {
            Text(stringResource(R.string.rule_limit_hint), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LimitPresets.forEach { n ->
                    FilterChip(
                        selected = rule?.dailyLimit == n,
                        onClick = { onChange(ShortFormMode.LIMIT, n) },
                        label = { Text(n.toString()) },
                    )
                }
            }
        }
        if (rule != null && rule.mode != ShortFormMode.ALLOW) LockControl(locked = locked, onLock = onLock)
    }
}
