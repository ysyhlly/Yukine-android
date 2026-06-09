package app.echo.next.ui

import android.content.Context
import app.echo.next.MainRoutes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination

data class AppTabUiState(val label: String, val key: String)

class TabBarController(
    context: Context,
    tabs: List<AppTabUiState>,
    initialSelectedKey: String,
    private val onSelect: TabSelectAction
) {
    private val tabsState: MutableState<List<AppTabUiState>> =
        mutableStateOf(tabs)
    private val selectedKey: MutableState<String> =
        mutableStateOf(initialTabKey(tabs, initialSelectedKey))
    private val requestedKey: MutableState<String> =
        mutableStateOf("")

    val view: ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                TabNavigationHost(tabsState.value, selectedKey, requestedKey, onSelect)
            }
        }
    }

    fun updateSelected(key: String) {
        selectedKey.value = key
        requestedKey.value = ""
    }

    fun updateTabs(tabs: List<AppTabUiState>) {
        tabsState.value = tabs
    }
}

internal fun initialTabKey(tabs: List<AppTabUiState>, initialSelectedKey: String): String {
    if (tabs.isEmpty()) {
        return ""
    }
    return if (tabs.any { it.key == initialSelectedKey }) initialSelectedKey else tabs[0].key
}

fun interface TabSelectAction { fun select(tabKey: String, userInitiated: Boolean) }

@Composable
private fun TabNavigationHost(
    tabs: List<AppTabUiState>,
    selectedKey: MutableState<String>,
    requestedKey: MutableState<String>,
    onSelect: TabSelectAction
) {
    if (tabs.isEmpty()) {
        return
    }
    val navController = rememberNavController()
    var lastDispatchedRoute by remember { mutableStateOf("") }
    var userRequestedRoute by remember { mutableStateOf("") }
    val requestedRoute = requestedKey.value

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route ?: return@collect
            val requested = requestedKey.value
            if (!shouldAcceptRequestedRoute(route, requested)) {
                return@collect
            }
            selectedKey.value = route
            requestedKey.value = ""
            if (route != lastDispatchedRoute) {
                lastDispatchedRoute = route
                val userInitiated = route == userRequestedRoute
                if (userInitiated) {
                    userRequestedRoute = ""
                }
                onSelect.select(route, userInitiated)
            }
        }
    }

    LaunchedEffect(navController, requestedRoute) {
        if (requestedRoute.isEmpty() || navController.currentDestination?.route == requestedRoute) {
            return@LaunchedEffect
        }
        navController.navigate(requestedRoute) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        NavHost(
            navController = navController,
            startDestination = tabs[0].key,
            modifier = Modifier.height(0.dp)
        ) {
            tabs.forEach { tab ->
                composable(tab.key) {
                    Spacer(Modifier.height(0.dp))
                }
            }
        }
        TabBar(tabs, selectedKey.value) { tabKey ->
            if (navController.currentDestination?.route == tabKey) {
                onSelect.select(tabKey, true)
                return@TabBar
            }
            userRequestedRoute = tabKey
            requestedKey.value = tabKey
        }
    }
}

@Composable
private fun TabBar(tabs: List<AppTabUiState>, selectedKey: String, onSelect: (String) -> Unit) {
    EchoGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = EchoShapes.large
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            tabs.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab.key == selectedKey,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(tab.key) }
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: AppTabUiState, selected: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = tab.label },
        shape = EchoShapes.medium,
        color = if (selected) p.accentSoft else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(vertical = EchoMobileLayoutMetrics.bottomTabVerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            EchoIcon(
                kind = iconForTab(tab.key),
                modifier = Modifier.size(EchoMobileLayoutMetrics.bottomTabIconSize),
                color = if (selected) p.accent else p.muted
            )
            Text(
                tab.label,
                style = EchoTypography.small.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (selected) p.accent else p.muted,
                maxLines = 1
            )
        }
    }
}

private fun iconForTab(key: String): EchoIconKind = when (key) {
    MainRoutes.TAB_HOME -> EchoIconKind.Mark
    MainRoutes.TAB_LIBRARY -> EchoIconKind.Library
    MainRoutes.TAB_COLLECTIONS -> EchoIconKind.Heart
    MainRoutes.TAB_QUEUE -> EchoIconKind.Queue
    MainRoutes.TAB_NETWORK -> EchoIconKind.Network
    MainRoutes.TAB_SETTINGS -> EchoIconKind.Settings
    else -> EchoIconKind.Mark
}
