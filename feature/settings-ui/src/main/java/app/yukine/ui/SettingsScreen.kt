package app.yukine.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import app.yukine.SettingsCategoryId
import app.yukine.SettingsEntryId
import app.yukine.SettingsIssue
import app.yukine.SettingsSearchEntry
import app.yukine.TrackDownloadItem
import app.yukine.filterSettingsSearchEntries

data class SettingsMetric(
    val label: String,
    val value: String,
    val compact: Boolean = false
)

data class SettingsActionProgress(
    /** Null renders an indeterminate indicator. */
    val fraction: Float? = null,
    val contentDescription: String = ""
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
    val imageDialog: SettingsImageDialog? = null,
    val entryId: SettingsEntryId? = null,
    val categoryId: SettingsCategoryId? = null,
    val sliderDefaultLabel: String = "",
    val sliderResetLabel: String = "",
    val onSliderReset: Runnable? = null,
    val progress: SettingsActionProgress? = null
)

internal data class SettingsActionSection(
    val title: String,
    val actions: List<SettingsAction>
)

internal data class SettingsCardDensityTokens(
    val sectionSpacing: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val textSpacing: Dp,
    val sliderSpacing: Dp,
    val dividerInset: Dp
)

internal fun settingsCardDensityTokens(compact: Boolean): SettingsCardDensityTokens =
    if (compact) {
        SettingsCardDensityTokens(
            sectionSpacing = 6.dp,
            horizontalPadding = 12.dp,
            verticalPadding = 10.dp,
            textSpacing = 2.dp,
            sliderSpacing = 6.dp,
            dividerInset = 12.dp
        )
    } else {
        SettingsCardDensityTokens(
            sectionSpacing = 10.dp,
            horizontalPadding = 14.dp,
            verticalPadding = 12.dp,
            textSpacing = 3.dp,
            sliderSpacing = 8.dp,
            dividerInset = 14.dp
        )
    }

internal fun settingsActionCardGroups(
    actions: List<SettingsAction>,
    compact: Boolean
): List<List<SettingsAction>> = when {
    actions.isEmpty() -> emptyList()
    compact -> listOf(actions)
    else -> actions.map(::listOf)
}

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
    issues: List<SettingsIssue> = emptyList(),
    issuesTitle: String = "",
    searchEntries: List<SettingsSearchEntry> = emptyList(),
    searchPlaceholder: String = "",
    searchResultsTitle: String = "",
    searchEmptyMessage: String = "",
    highlightedEntryId: SettingsEntryId? = null,
    compactSettingsCards: Boolean = false,
    activeDownload: TrackDownloadItem? = null,
    playbackQuality: String = "",
    audioMotion: YukineOrbAudioMotion = YukineOrbAudioMotion.Empty
) {
    var activeImageDialog by remember { mutableStateOf<SettingsImageDialog?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val titleBackAction = actions.firstOrNull { it.isBack }
    val visibleActions = settingsContentActions(actions)
    val actionSections = settingsActionSections(visibleActions)
    val cardDensity = settingsCardDensityTokens(compactSettingsCards)
    val filteredSearchEntries = remember(searchEntries, searchQuery) {
        filterSettingsSearchEntries(searchEntries, searchQuery)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = scrollState.firstVisibleItemIndex.coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = scrollState.firstVisibleItemScrollOffset.coerceAtLeast(0)
    )
    DisposableEffect(listState) {
        onDispose {
            scrollState.save(listState)
        }
    }
    val highlightedSectionItemIndex = settingsHighlightedSectionItemIndex(
        sections = actionSections,
        highlightedEntryId = highlightedEntryId,
        hasSearch = searchEntries.isNotEmpty(),
        hasIssues = issues.isNotEmpty(),
        hasMetrics = metrics.isNotEmpty()
    )
    LaunchedEffect(highlightedEntryId, highlightedSectionItemIndex) {
        if (highlightedEntryId != null && highlightedSectionItemIndex != null) {
            listState.animateScrollToItem(highlightedSectionItemIndex)
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
        if (searchEntries.isNotEmpty()) {
            item(key = "search") {
                SettingsSearchField(
                    query = searchQuery,
                    placeholder = searchPlaceholder,
                    onQueryChange = { searchQuery = it }
                )
            }
        }
        if (searchQuery.isNotBlank()) {
            item(key = "search-results") {
                SettingsSearchResultsCard(
                    title = searchResultsTitle,
                    emptyMessage = searchEmptyMessage,
                    entries = filteredSearchEntries,
                    cardDensity = cardDensity,
                    compactSettingsCards = compactSettingsCards,
                    onSelected = { entry ->
                        scrollState.save(listState)
                        searchQuery = ""
                        entry.onClick.run()
                    }
                )
            }
        } else if (issues.isNotEmpty()) {
            item(key = "issues") {
                SettingsIssuesCard(
                    title = issuesTitle,
                    issues = issues,
                    cardDensity = cardDensity,
                    compactSettingsCards = compactSettingsCards
                )
            }
        }
        if (searchQuery.isBlank() && metrics.isNotEmpty()) {
            item(key = "overview") {
                SettingsOverviewCard(metrics)
            }
        }
        if (searchQuery.isBlank()) {
            itemsIndexed(
                items = actionSections,
                key = { index, section -> "section:${section.title}:$index" }
            ) { index, section ->
                SettingsActionSectionCard(
                    section = section,
                    highlightedEntryId = highlightedEntryId,
                    cardDensity = cardDensity,
                    compactSettingsCards = compactSettingsCards,
                    modifier = Modifier.echoEnter(index.coerceAtMost(8)),
                    onAction = { action ->
                        scrollState.save(listState)
                        if (action.imageDialog != null) {
                            activeImageDialog = action.imageDialog
                        } else {
                            action.onClick.run()
                        }
                    }
                )
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
private fun SettingsActionSectionCard(
    section: SettingsActionSection,
    highlightedEntryId: SettingsEntryId?,
    cardDensity: SettingsCardDensityTokens,
    compactSettingsCards: Boolean,
    modifier: Modifier = Modifier,
    onAction: (SettingsAction) -> Unit
) {
    val p = EchoTheme.colors()
    val cardGroups = settingsActionCardGroups(section.actions, compactSettingsCards)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(cardDensity.sectionSpacing)
    ) {
        if (section.title.isNotBlank()) {
            Text(
                text = section.title,
                style = EchoTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = p.muted,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(EchoPageDefaults.itemSpacing)
        ) {
            cardGroups.forEach { actions ->
                SettingsActionCard(
                    actions = actions,
                    highlightedEntryId = highlightedEntryId,
                    cardDensity = cardDensity,
                    onAction = onAction
                )
            }
        }
    }
}

@Composable
private fun SettingsActionCard(
    actions: List<SettingsAction>,
    highlightedEntryId: SettingsEntryId?,
    cardDensity: SettingsCardDensityTokens,
    onAction: (SettingsAction) -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        shape = EchoShapes.medium,
        color = Color.Transparent
    ) {
        Column {
            actions.forEachIndexed { index, action ->
                SettingsGroupedAction(
                    action = action,
                    highlighted = action.entryId != null && action.entryId == highlightedEntryId,
                    cardDensity = cardDensity,
                    onClick = { onAction(action) }
                )
                if (index != actions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = cardDensity.dividerInset),
                        color = p.border.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupedAction(
    action: SettingsAction,
    highlighted: Boolean,
    cardDensity: SettingsCardDensityTokens,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    val interaction = remember { MutableInteractionSource() }
    val background = if (highlighted) p.accentSoft else Color.Transparent
    when (action.style) {
        SettingsActionStyle.Slider -> Surface(color = background) {
            SettingsSliderAction(action, cardDensity)
        }
        SettingsActionStyle.Toggle -> Surface(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = action.checked,
                    enabled = action.enabled,
                    role = Role.Switch,
                    onValueChange = { onClick() }
                )
                .semantics { contentDescription = settingsActionContentDescription(action) },
            color = background
        ) {
            SettingsActionRow(action, onClick, cardDensity)
        }
        else -> Surface(
            onClick = onClick,
            enabled = action.enabled,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .echoPressScale(interaction)
                .semantics { contentDescription = settingsActionContentDescription(action) },
            color = background
        ) {
            SettingsActionRow(action, onClick, cardDensity)
        }
    }
}

@Composable
private fun SettingsSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit
) {
    val p = EchoTheme.colors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .echoFloatingLayer(p, EchoShapes.medium)
            .echoGlassLayer(p, EchoShapes.medium),
        contentAlignment = Alignment.Center
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings-search-input")
                .semantics { contentDescription = placeholder },
            singleLine = true,
            placeholder = {
                Text(placeholder, style = EchoTypography.bodyMedium, color = p.muted)
            },
            leadingIcon = {
                EchoIcon(EchoIconKind.Search, Modifier.size(18.dp), p.accent)
            },
            textStyle = EchoTypography.bodyMedium.copy(
                color = p.text,
                fontWeight = FontWeight.SemiBold
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = p.text,
                unfocusedTextColor = p.text,
                cursorColor = p.accent
            )
        )
    }
}

@Composable
private fun SettingsSearchResultsCard(
    title: String,
    emptyMessage: String,
    entries: List<SettingsSearchEntry>,
    cardDensity: SettingsCardDensityTokens,
    compactSettingsCards: Boolean,
    onSelected: (SettingsSearchEntry) -> Unit
) {
    if (entries.isEmpty()) {
        val p = EchoTheme.colors()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .echoFloatingLayer(p, EchoShapes.medium)
                .echoGlassLayer(p, EchoShapes.medium),
            shape = EchoShapes.medium,
            color = Color.Transparent
        ) {
            Text(
                text = emptyMessage,
                style = EchoTypography.bodyMedium,
                color = p.muted,
                modifier = Modifier.padding(
                    horizontal = cardDensity.horizontalPadding,
                    vertical = cardDensity.verticalPadding
                )
            )
        }
        return
    }
    val actions = entries.map { entry ->
        SettingsAction(
            label = entry.title,
            onClick = Runnable { onSelected(entry) },
            description = listOf(entry.categoryLabel, entry.description)
                .filter(String::isNotBlank)
                .joinToString(" · "),
            style = SettingsActionStyle.Navigation,
            icon = entry.icon,
            entryId = entry.id,
            categoryId = entry.categoryId
        )
    }
    SettingsActionSectionCard(
        section = SettingsActionSection(title, actions),
        highlightedEntryId = null,
        cardDensity = cardDensity,
        compactSettingsCards = compactSettingsCards,
        onAction = { action -> action.onClick.run() }
    )
}

@Composable
private fun SettingsIssuesCard(
    title: String,
    issues: List<SettingsIssue>,
    cardDensity: SettingsCardDensityTokens,
    compactSettingsCards: Boolean
) {
    val actions = issues.map { issue ->
        SettingsAction(
            label = issue.title,
            onClick = issue.onClick ?: Runnable {},
            description = issue.description,
            value = issue.actionLabel,
            style = if (issue.onClick == null) SettingsActionStyle.Default else SettingsActionStyle.Navigation,
            icon = issue.icon
        )
    }
    SettingsActionSectionCard(
        section = SettingsActionSection(title, actions),
        highlightedEntryId = null,
        cardDensity = cardDensity,
        compactSettingsCards = compactSettingsCards,
        onAction = { action -> action.onClick.run() }
    )
}

internal fun settingsActionSections(actions: List<SettingsAction>): List<SettingsActionSection> {
    if (actions.isEmpty()) return emptyList()
    val sections = mutableListOf<SettingsActionSection>()
    var currentTitle = actions.first().section
    var currentActions = mutableListOf<SettingsAction>()
    actions.forEach { action ->
        if (currentActions.isNotEmpty() && action.section != currentTitle) {
            sections += SettingsActionSection(currentTitle, currentActions.toList())
            currentActions = mutableListOf()
            currentTitle = action.section
        }
        currentActions += action
    }
    if (currentActions.isNotEmpty()) {
        sections += SettingsActionSection(currentTitle, currentActions.toList())
    }
    return sections
}

internal fun settingsHighlightedSectionItemIndex(
    sections: List<SettingsActionSection>,
    highlightedEntryId: SettingsEntryId?,
    hasSearch: Boolean,
    hasIssues: Boolean,
    hasMetrics: Boolean
): Int? {
    if (highlightedEntryId == null) return null
    val sectionIndex = sections.indexOfFirst { section ->
        section.actions.any { action -> action.entryId == highlightedEntryId }
    }
    if (sectionIndex < 0) return null
    return 1 +
        (if (hasSearch) 1 else 0) +
        (if (hasIssues) 1 else 0) +
        (if (hasMetrics) 1 else 0) +
        sectionIndex
}

@Composable
private fun SettingsSliderAction(
    action: SettingsAction,
    cardDensity: SettingsCardDensityTokens
) {
    val p = EchoTheme.colors()
    var pendingValue by remember(action.sliderValue) { mutableFloatStateOf(action.sliderValue) }
    Column(
        modifier = Modifier.padding(
            horizontal = cardDensity.horizontalPadding,
            vertical = cardDensity.verticalPadding
        ),
        verticalArrangement = Arrangement.spacedBy(cardDensity.sliderSpacing)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EchoIcon(EchoIconKind.Gauge, Modifier.size(22.dp), if (action.enabled) p.accent else p.muted)
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(cardDensity.textSpacing)
            ) {
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
                .semantics { contentDescription = settingsActionContentDescription(action) },
            colors = SliderDefaults.colors(
                thumbColor = p.accent,
                activeTrackColor = p.accent,
                inactiveTrackColor = p.border
            )
        )
        if (action.sliderDefaultLabel.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = action.sliderDefaultLabel,
                    style = EchoTypography.caption,
                    color = p.muted
                )
                if (action.onSliderReset != null && action.sliderResetLabel.isNotBlank()) {
                    TextButton(
                        onClick = { action.onSliderReset.run() },
                        enabled = action.enabled
                    ) {
                        Text(action.sliderResetLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    action: SettingsAction,
    onClick: () -> Unit,
    cardDensity: SettingsCardDensityTokens
) {
    val p = EchoTheme.colors()
    Row(
        modifier = Modifier.padding(
            horizontal = cardDensity.horizontalPadding,
            vertical = cardDensity.verticalPadding
        ),
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
            verticalArrangement = Arrangement.spacedBy(cardDensity.textSpacing)
        ) {
            Text(
                action.label,
                style = EchoTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (action.enabled) p.text else p.muted
            )
            if (action.description.isNotBlank()) {
                Text(
                    action.description,
                    style = EchoTypography.caption,
                    color = p.muted
                )
            }
            action.progress?.let { progress ->
                val progressModifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .semantics {
                        if (progress.contentDescription.isNotBlank()) {
                            contentDescription = progress.contentDescription
                        }
                    }
                if (progress.fraction == null) {
                    LinearProgressIndicator(
                        modifier = progressModifier,
                        color = p.accent,
                        trackColor = p.surfaceVariant.copy(alpha = 0.36f)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress.fraction.coerceIn(0f, 1f) },
                        modifier = progressModifier,
                        color = p.accent,
                        trackColor = p.surfaceVariant.copy(alpha = 0.36f)
                    )
                }
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

internal fun settingsActionContentDescription(action: SettingsAction): String =
    listOf(action.label, action.value, action.description)
        .filter(String::isNotBlank)
        .joinToString(". ")

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
