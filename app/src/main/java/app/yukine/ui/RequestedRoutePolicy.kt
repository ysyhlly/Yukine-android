package app.yukine.ui

internal fun shouldAcceptRequestedRoute(route: String, requestedRoute: String): Boolean {
    return requestedRoute.isNotEmpty() && route == requestedRoute
}
