package app.yukine.ui

fun shouldAcceptRequestedRoute(route: String, requestedRoute: String): Boolean {
    return requestedRoute.isNotEmpty() && route == requestedRoute
}
