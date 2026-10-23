package com.example.aura.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aura.domain.models.ChangeThemeResult
import com.example.aura.domain.models.RestoreBackupResult
import com.example.aura.domain.models.SddmBackup
import com.example.aura.domain.models.SddmBackupKind
import com.example.aura.domain.models.SddmDiagnostics
import com.example.aura.domain.models.SddmTheme
import com.example.aura.domain.repository.SddmRepository
import com.example.aura.domain.usecase.ChangeThemeUseCase
import com.example.aura.domain.usecase.RestoreBackupUseCase
import com.example.aura.presentation.di.SddmModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel de Ajustes. Expone un único [SettingsUiState] y ejecuta todo el
 * trabajo de disco/permisos fuera del hilo de UI.
 */
class SettingsViewModel(
    private val sddmRepository: SddmRepository = SddmModule.repository,
    private val restoreBackupUseCase: RestoreBackupUseCase =
        SddmModule.restoreBackup,
    private val changeThemeUseCase: ChangeThemeUseCase =
        SddmModule.changeTheme,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return

        _uiState.update { it.copy(isRefreshing = true, refreshError = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { loadSnapshot() }
            }.onSuccess { snapshot ->
                _uiState.update {
                    it.copy(
                        backups = snapshot.backups,
                        diagnostics = snapshot.diagnostics,
                        themes = snapshot.themes,
                        currentTheme = snapshot.currentTheme,
                        isRefreshing = false,
                        refreshError = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        refreshError = "No se pudo actualizar el diagnóstico: " +
                            (error.message ?: "error desconocido"),
                    )
                }
            }
        }
    }

    fun requestRestore(backup: SddmBackup) {
        if (_uiState.value.isRestoring) return
        _uiState.update {
            it.copy(
                selectedBackup = backup,
                showRestoreDialog = true,
            )
        }
    }

    fun cancelRestore() {
        _uiState.update {
            it.copy(
                showRestoreDialog = false,
                selectedBackup = null,
            )
        }
    }

    fun confirmRestore() {
        val backup = _uiState.value.selectedBackup ?: return
        if (_uiState.value.isRestoring) return

        _uiState.update {
            it.copy(
                showRestoreDialog = false,
                isRestoring = true,
                restoreMessage = null,
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { restoreBackupUseCase(backup.id) }
            }
            val snapshot = runCatching {
                withContext(Dispatchers.IO) { loadSnapshot() }
            }.getOrNull()

            _uiState.update { current ->
                when (val restoreResult = result.getOrNull()) {
                    is RestoreBackupResult.Success -> current.copy(
                        isRestoring = false,
                        selectedBackup = null,
                        restoreSucceeded = true,
                        restoreMessage = buildRestoreMessage(restoreResult.backup),
                        backups = snapshot?.backups ?: current.backups,
                        diagnostics = snapshot?.diagnostics ?: current.diagnostics,
                        themes = snapshot?.themes ?: current.themes,
                        currentTheme = snapshot?.currentTheme ?: current.currentTheme,
                    )

                    is RestoreBackupResult.Failure -> current.copy(
                        isRestoring = false,
                        selectedBackup = null,
                        restoreSucceeded = false,
                        restoreMessage = restoreResult.message,
                        backups = snapshot?.backups ?: current.backups,
                        diagnostics = snapshot?.diagnostics ?: current.diagnostics,
                        themes = snapshot?.themes ?: current.themes,
                        currentTheme = snapshot?.currentTheme ?: current.currentTheme,
                    )

                    null -> current.copy(
                        isRestoring = false,
                        selectedBackup = null,
                        restoreSucceeded = false,
                        restoreMessage = "No se pudo restaurar el respaldo: " +
                            (result.exceptionOrNull()?.message ?: "error desconocido"),
                        backups = snapshot?.backups ?: current.backups,
                        diagnostics = snapshot?.diagnostics ?: current.diagnostics,
                        themes = snapshot?.themes ?: current.themes,
                        currentTheme = snapshot?.currentTheme ?: current.currentTheme,
                    )
                }
            }
        }
    }

    fun requestThemeChange(theme: SddmTheme) {
        val state = _uiState.value
        if (state.isChangingTheme || theme.isCurrent) return
        _uiState.update {
            it.copy(
                selectedTheme = theme,
                showThemeDialog = true,
            )
        }
    }

    fun cancelThemeChange() {
        _uiState.update {
            it.copy(
                showThemeDialog = false,
                selectedTheme = null,
            )
        }
    }

    fun confirmThemeChange() {
        val theme = _uiState.value.selectedTheme ?: return
        if (_uiState.value.isChangingTheme) return

        _uiState.update {
            it.copy(
                showThemeDialog = false,
                isChangingTheme = true,
                themeMessage = null,
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { changeThemeUseCase(theme.name) }
            }
            val snapshot = runCatching {
                withContext(Dispatchers.IO) { loadSnapshot() }
            }.getOrNull()

            _uiState.update { current ->
                when (val changeResult = result.getOrNull()) {
                    is ChangeThemeResult.Success -> current.copy(
                        isChangingTheme = false,
                        selectedTheme = null,
                        themeChangeSucceeded = true,
                        themeMessage = buildString {
                            append("Se cambió el tema de SDDM de \"")
                            append(changeResult.previousTheme)
                            append("\" a \"")
                            append(changeResult.theme.displayName)
                            append("\".\n\nSe creó un respaldo automático del cambio de tema. ")
                            append("Reinicia SDDM o cierra sesión para ver el nuevo tema.")
                        },
                        backups = snapshot?.backups ?: current.backups,
                        diagnostics = snapshot?.diagnostics ?: current.diagnostics,
                        themes = snapshot?.themes ?: current.themes,
                        currentTheme = snapshot?.currentTheme ?: current.currentTheme,
                    )

                    is ChangeThemeResult.Failure -> current.copy(
                        isChangingTheme = false,
                        selectedTheme = null,
                        themeChangeSucceeded = false,
                        themeMessage = changeResult.message,
                        backups = snapshot?.backups ?: current.backups,
                        diagnostics = snapshot?.diagnostics ?: current.diagnostics,
                        themes = snapshot?.themes ?: current.themes,
                        currentTheme = snapshot?.currentTheme ?: current.currentTheme,
                    )

                    null -> current.copy(
                        isChangingTheme = false,
                        selectedTheme = null,
                        themeChangeSucceeded = false,
                        themeMessage = "No se pudo cambiar el tema: " +
                            (result.exceptionOrNull()?.message ?: "error desconocido"),
                        backups = snapshot?.backups ?: current.backups,
                        diagnostics = snapshot?.diagnostics ?: current.diagnostics,
                        themes = snapshot?.themes ?: current.themes,
                        currentTheme = snapshot?.currentTheme ?: current.currentTheme,
                    )
                }
            }
        }
    }

    fun clearRestoreMessage() {
        _uiState.update { it.copy(restoreMessage = null) }
    }

    fun clearThemeMessage() {
        _uiState.update { it.copy(themeMessage = null) }
    }

    fun clearRefreshError() {
        _uiState.update { it.copy(refreshError = null) }
    }

    /** Cancela el trabajo pendiente cuando la pantalla sale de composición. */
    fun dispose() {
        viewModelScope.cancel()
    }

    private fun loadSnapshot(): SettingsSnapshot = SettingsSnapshot(
        backups = sddmRepository.listBackups(),
        diagnostics = sddmRepository.getDiagnostics(),
        themes = sddmRepository.getInstalledThemes(),
        currentTheme = sddmRepository.getCurrentTheme(),
    )

    private fun buildRestoreMessage(backup: SddmBackup): String = buildString {
        if (backup.kind == SddmBackupKind.THEME) {
            append("Se restauró la configuración de SDDM al tema \"${backup.themeName}\"")
            backup.newThemeName?.let { newTheme ->
                append(" (se había cambiado a \"$newTheme\")")
            }
            append(".\n\nReinicia SDDM o cierra sesión para ver el cambio.")
        } else {
            append("Se restauró la configuración del tema \"${backup.themeName}\"")
            if (backup.backgroundName.isNotBlank()) {
                append(" al estado previo a aplicar \"${backup.backgroundName}\"")
            }
            append(".\n\nReinicia SDDM o cierra sesión para ver el cambio.")
        }
    }

    private data class SettingsSnapshot(
        val backups: List<SddmBackup>,
        val diagnostics: SddmDiagnostics,
        val themes: List<SddmTheme>,
        val currentTheme: String,
    )
}
