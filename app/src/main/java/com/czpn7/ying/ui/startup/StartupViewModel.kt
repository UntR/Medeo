package com.czpn7.ying.ui.startup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.czpn7.ying.data.local.AppThemeMode
import com.czpn7.ying.data.local.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

data class StartupUiState(
    val loading: Boolean = true,
    val disclaimerAccepted: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.DAY
)

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val settingsStore: SettingsStore
) : ViewModel() {
    var uiState by mutableStateOf(StartupUiState())
        private set

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                uiState = StartupUiState(
                    loading = false,
                    disclaimerAccepted = settings.disclaimerAccepted,
                    themeMode = settings.themeMode
                )
            }
        }
    }

    fun acceptDisclaimer() {
        viewModelScope.launch {
            settingsStore.setDisclaimerAccepted(true)
        }
    }
}
