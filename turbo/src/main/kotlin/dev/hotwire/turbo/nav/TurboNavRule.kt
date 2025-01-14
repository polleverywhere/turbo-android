package dev.hotwire.turbo.nav

import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.navOptions
import dev.hotwire.turbo.config.*
import dev.hotwire.turbo.session.TurboSessionModalResult
import dev.hotwire.turbo.util.location
import dev.hotwire.turbo.visit.TurboVisitAction
import dev.hotwire.turbo.visit.TurboVisitOptions

@Suppress("MemberVisibilityCanBePrivate")
internal class TurboNavRule(
    location: String,
    visitOptions: TurboVisitOptions,
    bundle: Bundle?,
    navOptions: NavOptions,
    extras: FragmentNavigator.Extras?,
    pathConfiguration: TurboPathConfiguration,
    val controller: NavController
) {
    // Current destination
    val previousLocation = controller.previousBackStackEntry.location
    val currentLocation = checkNotNull(controller.currentBackStackEntry.location)
    val currentProperties = pathConfiguration.properties(currentLocation)
    val currentPresentationContext = currentProperties.context
    val isAtStartDestination = controller.previousBackStackEntry == null

    // New destination
    val newLocation = location
    val newProperties = pathConfiguration.properties(newLocation)
    val newPresentationContext = newProperties.context
    val newVisitOptions = visitOptions
    val newBundle = bundle.withNavArguments()
    val newExtras = extras
    val newQueryStringPresentation = newProperties.queryStringPresentation
    val newPresentation = newPresentation()
    val newNavigationMode = newNavigationMode()
    val newModalResult = newModalResult()
    val newDestinationUri = newProperties.uri
    val newFallbackUri = newProperties.fallbackUri
    val newDestination = controller.destinationFor(newDestinationUri)
    val newFallbackDestination = controller.destinationFor(newFallbackUri)
    val newNavOptions = newNavOptions(navOptions)

    init {
        verifyNavRules()
    }

    private fun newPresentation(): TurboNavPresentation {
        // Check if we should use the custom presentation provided in the path configuration
        if (newProperties.presentation != TurboNavPresentation.DEFAULT) {
            return if (isAtStartDestination && newProperties.presentation == TurboNavPresentation.POP) {
                // You cannot pop from the start destination, prevent visit
                TurboNavPresentation.NONE
            } else {
                // Use the custom presentation
                newProperties.presentation
            }
        }

        val locationIsCurrent = locationsAreSame(newLocation, currentLocation)
        val locationIsPrevious = locationsAreSame(newLocation, previousLocation)
        val replace = newVisitOptions.action == TurboVisitAction.REPLACE

        return when {
            locationIsCurrent && isAtStartDestination -> TurboNavPresentation.REPLACE_ROOT
            locationIsPrevious -> TurboNavPresentation.POP
            locationIsCurrent || replace -> TurboNavPresentation.REPLACE
            else -> TurboNavPresentation.PUSH
        }
    }

    private fun newNavOptions(navOptions: NavOptions): NavOptions {
        // Use separate NavOptions if we need to pop up to the new root destination
        if (newPresentation == TurboNavPresentation.REPLACE_ROOT && newDestination != null) {
            return navOptions {
                popUpTo(newDestination.id) { inclusive = true }
            }
        }

        return navOptions
    }

    private fun newNavigationMode(): TurboNavMode {
        val presentationNone = newPresentation == TurboNavPresentation.NONE
        val presentationRefresh = newPresentation == TurboNavPresentation.REFRESH

        val dismissModalContext = currentPresentationContext == TurboNavPresentationContext.MODAL &&
                newPresentationContext == TurboNavPresentationContext.DEFAULT &&
                newPresentation != TurboNavPresentation.REPLACE_ROOT

        val navigateToModalContext = currentPresentationContext == TurboNavPresentationContext.DEFAULT &&
                newPresentationContext == TurboNavPresentationContext.MODAL &&
                newPresentation != TurboNavPresentation.REPLACE_ROOT

        return when {
            dismissModalContext -> TurboNavMode.DISMISS_MODAL
            navigateToModalContext -> TurboNavMode.TO_MODAL
            presentationRefresh -> TurboNavMode.REFRESH
            presentationNone -> TurboNavMode.NONE
            else -> TurboNavMode.IN_CONTEXT
        }
    }

    private fun newModalResult(): TurboSessionModalResult? {
        if (newNavigationMode != TurboNavMode.DISMISS_MODAL) {
            return null
        }

        return TurboSessionModalResult(
            location = newLocation,
            options = newVisitOptions,
            bundle = newBundle,
            shouldNavigate = newProperties.presentation != TurboNavPresentation.NONE
        )
    }

    private fun verifyNavRules() {
        if (newPresentationContext == TurboNavPresentationContext.MODAL &&
            newPresentation == TurboNavPresentation.REPLACE_ROOT) {
            throw TurboNavException("A `modal` destination cannot use presentation `REPLACE_ROOT`")
        }
    }

    private fun NavController.destinationFor(uri: Uri?): NavDestination? {
        uri ?: return null
        return graph.find { it.hasDeepLink(uri) }
    }

    private fun Bundle?.withNavArguments(): Bundle {
        val bundle = this ?: bundleOf()
        return bundle.apply {
            putString("location", newLocation)
            putSerializable("presentation-context", newPresentationContext)
        }
    }

    private fun locationsAreSame(first: String?, second: String?): Boolean {
        if (first == null || second == null) {
            return false
        }

        val firstUri = Uri.parse(first)
        val secondUri = Uri.parse(second)

        return when (newQueryStringPresentation) {
            TurboNavQueryStringPresentation.REPLACE -> {
                firstUri.path == secondUri.path
            }
            TurboNavQueryStringPresentation.DEFAULT -> {
                firstUri.path == secondUri.path && firstUri.query == secondUri.query
            }
        }
    }
}
