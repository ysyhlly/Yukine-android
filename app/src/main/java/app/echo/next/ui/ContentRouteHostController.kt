package app.echo.next.ui

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class ContentRouteHostController(
    context: Context,
    private val routes: List<String>,
    initialRoute: String,
    private val onRouteSelected: ContentRouteSelectAction
) {
    private val startRoute: String =
        if (initialRoute in routes) initialRoute else if (routes.isEmpty()) "" else routes[0]
    private val selectedRoute: MutableState<String> =
        mutableStateOf(startRoute)
    private val requestedRoute: MutableState<String> =
        mutableStateOf("")

    val view: ComposeView = ComposeView(context).apply {
        setContent {
            EchoTheme.EchoTheme {
                ContentRouteHost(routes, startRoute, selectedRoute, requestedRoute, onRouteSelected)
            }
        }
    }

    fun navigate(route: String): Boolean {
        if (route.isEmpty() || selectedRoute.value == route) {
            return false
        }
        requestedRoute.value = route
        return true
    }

    fun updateSelected(route: String) {
        selectedRoute.value = route
        requestedRoute.value = ""
    }

    fun selectedRoute(): String = selectedRoute.value
}

fun interface ContentRouteSelectAction {
    fun select(route: String)
}

@Composable
private fun ContentRouteHost(
    routes: List<String>,
    startRoute: String,
    selectedRoute: MutableState<String>,
    requestedRoute: MutableState<String>,
    onRouteSelected: ContentRouteSelectAction
) {
    if (routes.isEmpty()) {
        return
    }
    val navController = rememberNavController()
    var lastDispatchedRoute by remember { mutableStateOf(startRoute) }
    val requested = requestedRoute.value

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route ?: return@collect
            val requested = requestedRoute.value
            if (!shouldAcceptRequestedRoute(route, requested)) {
                return@collect
            }
            selectedRoute.value = route
            requestedRoute.value = ""
            if (route != lastDispatchedRoute) {
                lastDispatchedRoute = route
                onRouteSelected.select(route)
            }
        }
    }

    LaunchedEffect(navController, requested) {
        if (requested.isEmpty() || navController.currentDestination?.route == requested) {
            return@LaunchedEffect
        }
        navController.navigate(requested) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = Modifier.height(0.dp)
    ) {
        routes.forEach { route ->
            composable(route) {
                Spacer(Modifier.height(0.dp))
            }
        }
    }
}
