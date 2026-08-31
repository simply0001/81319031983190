package com.pocketpass.app.model

/**
 * Routes must implement androidx.navigation3's NavKey on Android so they can live in a
 * NavBackStack, but that interface only exists there. On iOS the marker is inert.
 */
expect interface NavKeyMarker
