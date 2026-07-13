package app.yukine.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yukine.TrackDownloadItem

data class SettingsMetric(
    val label: String,
    val value: String,
    val compact: Boolean = false
)
enum class SettingsActionStyle {
    Default,
    Navigation,
    Toggle,
    Slider,
    Choice,
    Destructive
}

data class SettingsAction(
    val label: String,
    val onClick: Runnable,
    val description: String = "",
    val value: String = "",
    val style: SettingsActionStyle = SettingsActionStyle.Default,
    /** Semantic icon supplied by the feature; null falls back only to the action style. */
    val icon: EchoIconKind? = null,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val sliderValue: Float = 0f,
    val sliderRangeStart: Float = 0f,
    val sliderRangeEnd: Float = 1f,
    val sliderSteps: Int = 0,
    val onSliderValueChange: ((Float) -> Unit)? = null,
    val section: String = "",
    val isBack: Boolean = false,
    val imageDialog: SettingsImageDialog? = null
)

data class SettingsImageDialog(
    val title: String,
    val message: String,
    val imageResId: Int,
    val imageContentDescription: String,
    val dismissLabel: String
)

class SettingsListScrollState(
    var firstVisibleItemIndex: Int = 0,
    var firstVisibleItemScrollOffset: Int = 0
) {
    private var ignoreNextSave: Boolean = false

    fun save(listState: LazyListState) {
        if (ignoreNextSave) {
            ignoreNextSave = false
            return
        }
        firstVisibleItemIndex = listState.firstVisibleItemIndex
        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
    }

    fun scrollToTop() {
        firstVisibleItemIndex = 0
        firstVisibleItemScrollOffset = 0
        ignoreNextSave = true
    }
}

@Composable
fun SettingsScreen(
    title: String,
    metrics: List<SettingsMetric>,
    actions: List<SettingsAction>,
    scrollState: SettingsListScrollState,
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    var activeImageDialog by remember { mutableStateOf<SettingsImageDialog?>(null) }
    val titleBackAction = actions.firstOrNull { it.isBack }
    val visibleActions = settingsContentActions(actions)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollState.firstVisibleItemIndex.coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = scrollState.firstVisibleItemScrollOffset.coerceAtLeast(0)
    )
    DisposableEffect(listState) {
        onDispose {
            scrollState.save(listState)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = echoPagePadding(),
        verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
    ) {
        item(key = "title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EchoPageTitle(
                    title,
                    modifier = Modifier.weight(1f),
                    backLabel = titleBackAction?.label,
                    onBack = titleBackAction?.onClick
                )
                YukineDownloadOrb(
                    item = activeDownload,
                    playbackQuality = playbackQuality,
                    audioMotion = audioMotion,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        if (metrics.isNotEmpty()) {
            item(key = "overview") {
                SettingsOverviewCard(metrics)
            }
        }
        itemsIndexed(
            items = visibleActions,
            key = { index, action -> "action:${action.label}:$index" }
        ) { index, action ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val previousSection = visibleActions.getOrNull(index - 1)?.section.orEmpty()
                if (action.section.isNotBlank() && action.section != previousSection) {
                    Text(
                        text = action.section,
                        style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = EchoTheme.colors().muted,
                        modifier = Modifier.padding(start = 4.dp, top = if (index == 0) 2.dp else 8.dp)
                    )
                }
                SettingsActionButton(action, Modifier.echoEnter(index.coerceAtMost(8))) {
                    scrollState.save(listState)
                    if (action.imageDialog != null) {
                        activeImageDialog = action.imageDialog
                    } else {
                        action.onClick.run()
                    }
                }
            }
        }
    }
    activeImageDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { activeImageDialog = null },
            title = { Text(dialog.title, style = EchoTypography.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(dialog.message, style = EchoTypography.bodyMedium)
                    Image(
                        painter = painterResource(dialog.imageResId),
                        contentDescription = dialog.imageContentDescription,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 560.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { activeImageDialog = null }) {
                    Text(dialog.dismissLabel)
                }
            }
        )
    }
}

@Composable
private fun SettingsActionButton(action: SettingsAction, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    val cardModifier = modifier
        .fillMaxWidth()
        .echoFloatingLayer(p, EchoShapes.medium)
        .echoGlassLayer(p, EchoShapes.medium)

    if (action.style == SettingsActionStyle.Slider) {
        Surface(
            modifier = cardModifier,
            shape = EchoShapes.medium,
            color = Color.Transparent
        ) {
            SettingsSliderAction(action)
        }
    } else if (action.style == SettingsActionStyle.Toggle) {
        Surface(
            modifier = cardModifier
                .toggleable(
                    value = action.checked,
                    enabled = action.enabled,
                    role = Role.Switch,
                    onValueChange = { onClick() }
                )
                .semantics { contentDescription = action.label },
            shape = EchoShapes.medium,
            color = Color.Transparent
        ) {
            SettingsActionRow(action, onClick)
        }
    } else {
        Surface(
            onClick = onClick,
            enabled = action.enabled,
            interactionSource = interaction,
            modifier = cardModifier
                .echoPressScale(interaction)
                .semantics { contentDescription = action.label },
            shape = EchoShapes.medium,
            color = Color.Transparent
        ) {
            SettingsActionRow(action, onClick)
        }
    }
}

@Composable
private fun SettingsSliderAction(action: SettingsAction) {
    val p = EchoTheme.colors()
    var pendingValue by remember(action.sliderValue) { mutableFloatStateOf(action.sliderValue) }
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EchoIcon(EchoIconKind.Gauge, Modifier.size(22.dp), if (action.enabled) p.accent else p.muted)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    action.label,
                    style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (action.enabled) p.text else p.muted
                )
                if (action.description.isNotBlank()) {
                    Text(action.description, style = EchoTypography.caption, color = p.muted)
                }
            }
            if (action.value.isNotBlank()) {
                Text(
                    if (action.value.isBlank()) "${pendingValue.toInt()}" else action.value.replace(action.sliderValue.toInt().toString(), pendingValue.toInt().toString()),
                    style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = p.muted
                )
            }
        }
        Slider(
            value = pendingValue.coerceIn(action.sliderRangeStart, action.sliderRangeEnd),
            onValueChange = { value -> pendingValue = value },
            onValueChangeFinished = { action.onSliderValueChange?.invoke(pendingValue) },
            valueRange = action.sliderRangeStart..action.sliderRangeEnd,
            steps = action.sliderSteps,
            enabled = action.enabled && action.onSliderValueChange != null,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = action.label },
            colors = SliderDefaults.colors(
                thumbColor = p.accent,
                activeTrackColor = p.accent,
                inactiveTrackColor = p.border
            )
        )
    }
}

@Composable
private fun SettingsActionRow(action: SettingsAction, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EchoIcon(
            kind = iconForSettingsAction(action),
            modifier = Modifier.size(22.dp),
            color = if (!action.enabled || action.style == SettingsActionStyle.Destructive) p.muted else p.accent
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                action.label,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (action.enabled) p.text else p.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (action.description.isNotBlank()) {
                Text(
                    action.description,
                    style = EchoTypography.caption,
                    color = p.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        SettingsActionTrailing(action, onClick)
    }
}

@Composable
private fun SettingsActionTrailing(action: SettingsAction, onClick: () -> Unit) {
    val p = EchoTheme.colors()
    when (action.style) {
        SettingsActionStyle.Toggle -> Switch(
            checked = action.checked,
            onCheckedChange = null,
            enabled = action.enabled,
            modifier = Modifier.semantics { contentDescription = action.label },
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onAccent,
                checkedTrackColor = p.accent,
                uncheckedThumbColor = p.surface,
                uncheckedTrackColor = p.border
            )
        )
        SettingsActionStyle.Choice -> {
            if (action.checked) {
                EchoIcon(EchoIconKind.Check, Modifier.size(18.dp), p.accent)
            } else {
                Spacer(Modifier.width(18.dp))
            }
        }
        SettingsActionStyle.Slider -> Unit
        SettingsActionStyle.Navigation -> {
            if (action.value.isNotBlank()) {
                Text(
                    text = action.value,
                    style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = p.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            EchoIcon(EchoIconKind.ChevronRight, Modifier.size(16.dp), p.muted)
        }
        SettingsActionStyle.Default -> Unit
        SettingsActionStyle.Destructive -> Unit
    }
}

internal fun settingsContentActions(actions: List<SettingsAction>): List<SettingsAction> {
    val backActionIndex = actions.indexOfFirst { action -> action.isBack }
    if (backActionIndex < 0) {
        return actions
    }
    return actions.filterIndexed { index, _ -> index != backActionIndex }
}

@Composable
private fun SettingsOverviewCard(metrics: List<SettingsMetric>) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            metrics.forEach { metric ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = if (metric.compact) Alignment.Top else Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        metric.label,
                        style = if (metric.compact) EchoTypography.caption else EchoTypography.bodyMedium,
                        color = p.muted,
                        modifier = Modifier.weight(1f),
                        maxLines = if (metric.compact) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        metric.value,
                        style = if (metric.compact) {
                            EchoTypography.caption
                        } else {
                            EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        },
                        color = p.text,
                        maxLines = if (metric.compact) 4 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(if (metric.compact) 1.4f else 1f)
                            .padding(start = 12.dp)
                    )
                }
            }
        }
    }
}

internal fun iconForSettingsAction(action: SettingsAction): EchoIconKind {
    return action.icon ?: when (action.style) {
        SettingsActionStyle.Destructive -> EchoIconKind.Delete
        SettingsActionStyle.Slider -> EchoIconKind.Gauge
        SettingsActionStyle.Toggle,
        SettingsActionStyle.Choice -> EchoIconKind.Check
        else -> EchoIconKind.Settings
    }
}
