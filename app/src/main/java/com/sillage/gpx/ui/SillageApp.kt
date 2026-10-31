package com.sillage.gpx.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sillage.gpx.ui.library.LibraryScreen
import com.sillage.gpx.ui.viewer.ViewerScreen

@Composable
fun SillageApp(
    vm: AppViewModel,
    onImport: () -> Unit,
    onExport: (String) -> Unit,
    onShare: (Uri, String) -> Unit,
    hasLocationPermission: () -> Boolean,
    requestLocation: (onGranted: () -> Unit) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val share = rememberUpdatedState(onShare)
    LaunchedEffect(vm) {
        vm.uiEvents.collect { e ->
            when (e) {
                is UiEvent.Message -> { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(e.text) }
                is UiEvent.Share -> share.value(e.uri, e.name)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = vm.screen is Screen.Viewer,
            transitionSpec = {
                if (targetState) {
                    (fadeIn() + scaleIn(spring(dampingRatio = 0.75f, stiffness = 320f), initialScale = 0.92f))
                        .togetherWith(fadeOut() + scaleOut(targetScale = 1.04f))
                } else {
                    (fadeIn() + scaleIn(spring(dampingRatio = 0.75f, stiffness = 320f), initialScale = 1.04f))
                        .togetherWith(fadeOut() + scaleOut(targetScale = 0.92f))
                }
            },
            label = "screen",
        ) { viewer ->
            if (viewer) {
                ViewerScreen(vm, onExport, hasLocationPermission, requestLocation)
            } else {
                LibraryScreen(vm, onImport, onExport)
            }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp, start = 12.dp, end = 12.dp),
        ) { data ->
            Snackbar(
                data,
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}
