package com.example.aura.presentation.viewmodels

import com.example.aura.domain.models.SddmBackup
import com.example.aura.domain.models.SddmDiagnostics
import com.example.aura.domain.models.SddmTheme

/**
 * Estado completo de Ajustes: diagnóstico, temas y respaldos.
 */
data class SettingsUiState(
    val backups: List<SddmBackup> = emptyList(),
    val selectedBackup: SddmBackup? = null,
    val showRestoreDialog: Boolean = false,
    val isRestoring: Boolean = false,
    val restoreMessage: String? = null,
    val restoreSucceeded: Boolean = false,
    val diagnostics: SddmDiagnostics? = null,
    val themes: List<SddmTheme> = emptyList(),
    val currentTheme: String = "",
    val isRefreshing: Boolean = false,
    val refreshError: String? = null,
    val selectedTheme: SddmTheme? = null,
    val showThemeDialog: Boolean = false,
    val isChangingTheme: Boolean = false,
    val themeMessage: String? = null,
    val themeChangeSucceeded: Boolean = false,
)
