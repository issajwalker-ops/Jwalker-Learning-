package com.example.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GatewayBookmark
import com.example.data.GatewayPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GatewayUiState(
    val initialUrl: String? = null,
    val currentUrl: String = GatewayPreferencesRepository.PORTAL_HOME_URL,
    val pageTitle: String = "J. Walker Learning Gateway",
    val progress: Float = 0f,
    val isLoading: Boolean = true,
    val showSplash: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val isNetworkConnected: Boolean = true,
    val immersiveMode: Boolean = false,
    val resumeLastPage: Boolean = false,
    val bookmarks: List<GatewayBookmark> = emptyList(),
    val showBookmarksSheet: Boolean = false,
    val showOptionsSheet: Boolean = false,
    val webViewGeneration: Int = 0,
    val rendererCrashCount: Int = 0,
    val pendingRendererRecovery: Boolean = false,
    val useSoftwareLayerFallback: Boolean = false
)

class GatewayViewModel(
    private val repository: GatewayPreferencesRepository,
    appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(GatewayUiState())
    val uiState: StateFlow<GatewayUiState> = _uiState.asStateFlow()

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _uiState.update { it.copy(isNetworkConnected = true) }
        }

        override fun onLost(network: Network) {
            _uiState.update { it.copy(isNetworkConnected = checkIsConnected()) }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            _uiState.update { it.copy(isNetworkConnected = hasInternet) }
        }
    }

    init {
        _uiState.update { it.copy(isNetworkConnected = checkIsConnected()) }

        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }

        viewModelScope.launch {
            val initialSettings = repository.settingsFlow.first()
            val startUrl = if (initialSettings.resumeLastPage &&
                initialSettings.lastVisitedUrl.startsWith("https://")
            ) {
                initialSettings.lastVisitedUrl
            } else {
                GatewayPreferencesRepository.PORTAL_HOME_URL
            }
            _uiState.update {
                it.copy(
                    initialUrl = startUrl,
                    currentUrl = startUrl,
                    immersiveMode = initialSettings.immersiveMode,
                    resumeLastPage = initialSettings.resumeLastPage,
                    bookmarks = initialSettings.bookmarks
                )
            }
        }

        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        immersiveMode = settings.immersiveMode,
                        resumeLastPage = settings.resumeLastPage,
                        bookmarks = settings.bookmarks
                    )
                }
            }
        }

        // Guarantee splash overlay dismisses cleanly even on slow networks
        viewModelScope.launch {
            delay(2800L)
            _uiState.update { it.copy(showSplash = false) }
        }
    }

    fun onPageStarted(url: String) {
        _uiState.update {
            it.copy(
                currentUrl = url,
                isLoading = true,
                hasError = false,
                errorMessage = null
            )
        }
    }

    fun onPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update { state ->
            state.copy(
                currentUrl = url,
                pageTitle = title?.takeIf { it.isNotBlank() && it != "about:blank" }
                    ?: state.pageTitle,
                isLoading = false,
                progress = 1f,
                showSplash = false,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                rendererCrashCount = 0
            )
        }
        viewModelScope.launch {
            repository.setLastVisitedUrl(url)
        }
    }

    fun onVisitedHistoryUpdated(url: String, canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update { state ->
            state.copy(
                currentUrl = url,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
        viewModelScope.launch {
            repository.setLastVisitedUrl(url)
        }
    }

    fun onProgressChanged(newProgress: Int) {
        val fraction = (newProgress / 100f).coerceIn(0f, 1f)
        _uiState.update { state ->
            state.copy(
                progress = fraction,
                isLoading = newProgress in 1..99,
                showSplash = if (newProgress >= 80) false else state.showSplash
            )
        }
    }

    fun onTitleReceived(title: String?) {
        if (!title.isNullOrBlank() && title != "about:blank") {
            _uiState.update { it.copy(pageTitle = title) }
        }
    }

    fun onMainFrameError(description: String?) {
        _uiState.update {
            it.copy(
                isLoading = false,
                showSplash = false,
                hasError = true,
                errorMessage = description?.takeIf { msg -> msg.isNotBlank() }
                    ?: "The portal could not be reached right now."
            )
        }
    }

    fun onRenderProcessGone(didCrash: Boolean, isForeground: Boolean) {
        _uiState.update { state ->
            if (!isForeground) {
                state.copy(
                    pendingRendererRecovery = true,
                    useSoftwareLayerFallback = state.useSoftwareLayerFallback || didCrash
                )
            } else {
                recoverFromRendererTermination(state, didCrash)
            }
        }
    }

    fun onForegroundResumed() {
        _uiState.update { state ->
            if (state.pendingRendererRecovery) {
                recoverFromRendererTermination(
                    state.copy(pendingRendererRecovery = false),
                    didCrash = state.useSoftwareLayerFallback
                )
            } else {
                state
            }
        }
    }

    fun resetRendererAndRetry() {
        _uiState.update { state ->
            state.copy(
                initialUrl = state.currentUrl.ifBlank { GatewayPreferencesRepository.PORTAL_HOME_URL },
                hasError = false,
                errorMessage = null,
                isLoading = true,
                rendererCrashCount = 0,
                pendingRendererRecovery = false,
                webViewGeneration = state.webViewGeneration + 1
            )
        }
    }

    private fun recoverFromRendererTermination(
        state: GatewayUiState,
        didCrash: Boolean
    ): GatewayUiState {
        val nextCrashCount = state.rendererCrashCount + 1
        return if (nextCrashCount <= MAX_RENDERER_RECOVERIES) {
            state.copy(
                initialUrl = state.currentUrl.ifBlank { GatewayPreferencesRepository.PORTAL_HOME_URL },
                rendererCrashCount = nextCrashCount,
                webViewGeneration = state.webViewGeneration + 1,
                useSoftwareLayerFallback = state.useSoftwareLayerFallback || didCrash,
                isLoading = true,
                hasError = false,
                errorMessage = null
            )
        } else {
            state.copy(
                rendererCrashCount = nextCrashCount,
                isLoading = false,
                showSplash = false,
                hasError = true,
                errorMessage = "The portal display engine stopped unexpectedly. Tap Retry to reload or open in your browser."
            )
        }
    }

    fun clearErrorState() {
        _uiState.update { it.copy(hasError = false, errorMessage = null, isLoading = true) }
    }

    fun setBookmarksSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showBookmarksSheet = visible) }
    }

    fun setOptionsSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showOptionsSheet = visible) }
    }

    fun toggleImmersiveMode() {
        val next = !_uiState.value.immersiveMode
        viewModelScope.launch {
            repository.setImmersiveMode(next)
        }
    }

    fun setImmersiveMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setImmersiveMode(enabled)
        }
    }

    fun setResumeLastPage(enabled: Boolean) {
        viewModelScope.launch {
            repository.setResumeLastPage(enabled)
        }
    }

    fun addCurrentBookmark(customTitle: String) {
        val url = _uiState.value.currentUrl
        val title = customTitle.trim().ifEmpty { _uiState.value.pageTitle }
        viewModelScope.launch {
            repository.addBookmark(title = title, url = url)
        }
    }

    fun removeBookmark(id: String) {
        viewModelScope.launch {
            repository.removeBookmark(id)
        }
    }

    fun isCurrentUrlBookmarked(): Boolean {
        val current = _uiState.value.currentUrl.trimEnd('/')
        return _uiState.value.bookmarks.any { it.url.trimEnd('/').equals(current, ignoreCase = true) }
    }

    private fun checkIsConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    class Factory(
        private val repository: GatewayPreferencesRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GatewayViewModel(repository, appContext.applicationContext) as T
        }
    }

    companion object {
        private const val MAX_RENDERER_RECOVERIES = 2
    }
}
