package app.echo.next.ui

internal fun shouldAcceptRequestedRoute(route: String, requestedRoute: String): Boolean {
    return requestedRoute.isNotEmpty() && route == requestedRoute
}
