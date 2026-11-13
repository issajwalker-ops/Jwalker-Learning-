package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.BookmarksBottomSheet
import com.example.ui.components.GatewayOptionsSheet
import com.example.ui.components.GatewayWebController
import com.example.ui.components.GatewayWebView
import com.example.ui.components.LaunchSplashOverlay
import com.example.ui.components.OfflineErrorOverlay
import com.example.ui.components.rememberGatewayWebController
import com.example.viewmodel.GatewayUiState
import com.example.viewmodel.GatewayViewModel
import kotlinx.coroutines.launch

@Composable
fun GatewayScreen(
    viewModel: GatewayViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val webController = rememberGatewayWebController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val downloadMsgTemplate = stringResource(R.string.download_started)
    val bookmarkSavedMsg = stringResource(R.string.bookmark_saved)
    val cacheClearedMsg = stringResource(R.string.cache_cleared)

    BackHandler(enabled = uiState.canGoBack) {
        webController.goBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { _ ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpanded = maxWidth >= 840.dp

            Row(modifier = Modifier.fillMaxSize()) {
                // Expanded / Tablet Left Navigation Rail
                if (isExpanded && !uiState.immersiveMode) {
                    GatewayNavigationRail(
                        uiState = uiState,
                        controller = webController,
                        onOpenBookmarks = { viewModel.setBookmarksSheetVisible(true) },
                        onToggleFocus = { viewModel.toggleImmersiveMode() },
                        onOpenMore = { viewModel.setOptionsSheetVisible(true) }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Compact / Medium Top App Chrome
                    AnimatedVisibility(
                        visible = !uiState.immersiveMode && !isExpanded,
                        enter = fadeIn(tween(200)) + slideInVertically { -it },
                        exit = fadeOut(tween(180)) + slideOutVertically { -it }
                    ) {
                        GatewayTopBar(
                            pageTitle = uiState.pageTitle,
                            currentUrl = uiState.currentUrl,
                            isLoading = uiState.isLoading,
                            onReloadOrStop = {
                                if (uiState.isLoading) {
                                    webController.stopLoading()
                                } else {
                                    viewModel.clearErrorState()
                                    webController.reload()
                                }
                            },
                            onEnterFocusMode = { viewModel.setImmersiveMode(true) }
                        )
                    }

                    // Loading Progress Bar
                    if (uiState.isLoading && (!uiState.immersiveMode || isExpanded)) {
                        LinearProgressIndicator(
                            progress = { uiState.progress.coerceAtLeast(0.08f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    // Main Web Portal Viewport + Safe Insets when in Focus Mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(
                                if (uiState.immersiveMode) {
                                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                                } else if (isExpanded) {
                                    Modifier.statusBarsPadding().navigationBarsPadding()
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        val initialUrl = uiState.initialUrl
                        if (initialUrl != null) {
                            key(uiState.webViewGeneration) {
                                GatewayWebView(
                                    initialUrl = initialUrl,
                                    useSoftwareLayer = uiState.useSoftwareLayerFallback,
                                    controller = webController,
                                    onPageStarted = viewModel::onPageStarted,
                                    onPageFinished = viewModel::onPageFinished,
                                    onVisitedHistoryUpdated = viewModel::onVisitedHistoryUpdated,
                                    onProgressChanged = viewModel::onProgressChanged,
                                    onTitleReceived = viewModel::onTitleReceived,
                                    onMainFrameError = viewModel::onMainFrameError,
                                    onRenderProcessGone = viewModel::onRenderProcessGone,
                                    onForegroundResumed = viewModel::onForegroundResumed,
                                    onDownloadStarted = { fileName ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                String.format(downloadMsgTemplate, fileName)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Offline / Connection Error Overlay
                        androidx.compose.animation.AnimatedVisibility(
                            visible = uiState.hasError,
                            enter = fadeIn(tween(220)),
                            exit = fadeOut(tween(180))
                        ) {
                            OfflineErrorOverlay(
                                errorMessage = uiState.errorMessage,
                                onRetry = {
                                    if (uiState.rendererCrashCount > 0 || webController.webView == null) {
                                        viewModel.resetRendererAndRetry()
                                    } else {
                                        viewModel.clearErrorState()
                                        webController.reload()
                                    }
                                },
                                onOpenBookmarks = { viewModel.setBookmarksSheetVisible(true) },
                                onOpenInBrowser = { openUrlInBrowser(context, uiState.currentUrl) }
                            )
                        }

                        // Branded Launch Splash Overlay
                        androidx.compose.animation.AnimatedVisibility(
                            visible = uiState.showSplash,
                            enter = fadeIn(),
                            exit = fadeOut(tween(280))
                        ) {
                            LaunchSplashOverlay()
                        }

                        // Floating Pill to Exit Focus Mode
                        androidx.compose.animation.AnimatedVisibility(
                            visible = uiState.immersiveMode && !uiState.showSplash,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(180)),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                shadowElevation = 4.dp,
                                tonalElevation = 3.dp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { viewModel.setImmersiveMode(false) }
                                    .testTag("exit_focus_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FullscreenExit,
                                        contentDescription = stringResource(R.string.action_exit_focus),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Controls",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Compact / Medium Bottom Gateway Navigation Bar
                    AnimatedVisibility(
                        visible = !uiState.immersiveMode && !isExpanded,
                        enter = fadeIn(tween(200)) + slideInVertically { it },
                        exit = fadeOut(tween(180)) + slideOutVertically { it }
                    ) {
                        GatewayBottomControlBar(
                            canGoBack = uiState.canGoBack,
                            canGoForward = uiState.canGoForward,
                            onBack = { webController.goBack() },
                            onForward = { webController.goForward() },
                            onHome = {
                                viewModel.clearErrorState()
                                webController.goHome()
                            },
                            onBookmarks = { viewModel.setBookmarksSheetVisible(true) },
                            onMore = { viewModel.setOptionsSheetVisible(true) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showBookmarksSheet) {
        BookmarksBottomSheet(
            currentTitle = uiState.pageTitle,
            currentUrl = uiState.currentUrl,
            bookmarks = uiState.bookmarks,
            isCurrentBookmarked = viewModel.isCurrentUrlBookmarked(),
            onDismiss = { viewModel.setBookmarksSheetVisible(false) },
            onAddBookmark = { label ->
                viewModel.addCurrentBookmark(label)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(bookmarkSavedMsg)
                }
            },
            onOpenUrl = { url ->
                viewModel.clearErrorState()
                webController.loadUrl(url)
            },
            onDeleteBookmark = viewModel::removeBookmark
        )
    }

    if (uiState.showOptionsSheet) {
        GatewayOptionsSheet(
            currentUrl = uiState.currentUrl,
            immersiveMode = uiState.immersiveMode,
            resumeLastPage = uiState.resumeLastPage,
            onDismiss = { viewModel.setOptionsSheetVisible(false) },
            onToggleImmersive = viewModel::setImmersiveMode,
            onToggleResumeLast = viewModel::setResumeLastPage,
            onSharePage = { sharePageUrl(context, uiState.pageTitle, uiState.currentUrl) },
            onOpenInBrowser = { openUrlInBrowser(context, uiState.currentUrl) },
            onClearCache = {
                webController.clearCache()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(cacheClearedMsg)
                }
            }
        )
    }
}

@Composable
private fun GatewayTopBar(
    pageTitle: String,
    currentUrl: String,
    isLoading: Boolean,
    onReloadOrStop: () -> Unit,
    onEnterFocusMode: () -> Unit
) {
    val host = remember(currentUrl) {
        runCatching { Uri.parse(currentUrl).host }.getOrNull() ?: "j-walker-learning-gateway.lovable.app"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = stringResource(R.string.cd_secure_connection),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = host,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onReloadOrStop,
                    modifier = Modifier.testTag("top_bar_reload")
                ) {
                    Icon(
                        imageVector = if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = if (isLoading) {
                            stringResource(R.string.action_stop)
                        } else {
                            stringResource(R.string.action_reload)
                        },
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onEnterFocusMode,
                    modifier = Modifier.testTag("top_bar_focus")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fullscreen,
                        contentDescription = stringResource(R.string.action_focus_mode),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun GatewayBottomControlBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onBookmarks: () -> Unit,
    onMore: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlBarButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    label = stringResource(R.string.action_back),
                    enabled = canGoBack,
                    onClick = onBack,
                    testTag = "nav_back"
                )
                ControlBarButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    label = stringResource(R.string.action_forward),
                    enabled = canGoForward,
                    onClick = onForward,
                    testTag = "nav_forward"
                )
                ControlBarButton(
                    icon = Icons.Rounded.Home,
                    label = stringResource(R.string.action_home),
                    enabled = true,
                    onClick = onHome,
                    testTag = "nav_home"
                )
                ControlBarButton(
                    icon = Icons.Rounded.Bookmark,
                    label = stringResource(R.string.action_bookmarks),
                    enabled = true,
                    onClick = onBookmarks,
                    testTag = "nav_bookmarks"
                )
                ControlBarButton(
                    icon = Icons.Rounded.MoreVert,
                    label = stringResource(R.string.action_more),
                    enabled = true,
                    onClick = onMore,
                    testTag = "nav_more"
                )
            }
        }
    }
}

@Composable
private fun ControlBarButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    }

    Box(
        modifier = Modifier
            .size(width = 60.dp, height = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun GatewayNavigationRail(
    uiState: GatewayUiState,
    controller: GatewayWebController,
    onOpenBookmarks: () -> Unit,
    onToggleFocus: () -> Unit,
    onOpenMore: () -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
        header = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = stringResource(R.string.brand_full_title),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) {
        NavigationRailItem(
            selected = false,
            onClick = { controller.goBack() },
            enabled = uiState.canGoBack,
            icon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back)) },
            label = { Text(stringResource(R.string.action_back)) },
            modifier = Modifier.testTag("rail_back")
        )
        NavigationRailItem(
            selected = false,
            onClick = { controller.goForward() },
            enabled = uiState.canGoForward,
            icon = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.action_forward)) },
            label = { Text(stringResource(R.string.action_forward)) },
            modifier = Modifier.testTag("rail_forward")
        )
        NavigationRailItem(
            selected = false,
            onClick = { controller.goHome() },
            icon = { Icon(Icons.Rounded.Home, contentDescription = stringResource(R.string.action_home)) },
            label = { Text(stringResource(R.string.action_home)) },
            modifier = Modifier.testTag("rail_home")
        )
        NavigationRailItem(
            selected = false,
            onClick = { controller.reload() },
            icon = { Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_reload)) },
            label = { Text(stringResource(R.string.action_reload)) },
            modifier = Modifier.testTag("rail_reload")
        )
        NavigationRailItem(
            selected = false,
            onClick = onOpenBookmarks,
            icon = { Icon(Icons.Rounded.Bookmark, contentDescription = stringResource(R.string.action_bookmarks)) },
            label = { Text(stringResource(R.string.action_bookmarks)) },
            modifier = Modifier.testTag("rail_bookmarks")
        )
        NavigationRailItem(
            selected = false,
            onClick = onToggleFocus,
            icon = { Icon(Icons.Rounded.Fullscreen, contentDescription = stringResource(R.string.action_focus_mode)) },
            label = { Text("Focus") },
            modifier = Modifier.testTag("rail_focus")
        )
        NavigationRailItem(
            selected = false,
            onClick = onOpenMore,
            icon = { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more)) },
            label = { Text(stringResource(R.string.action_more)) },
            modifier = Modifier.testTag("rail_more")
        )
    }
}

private fun sharePageUrl(context: Context, title: String, url: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    }
    runCatching {
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share)))
    }
}

private fun openUrlInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
