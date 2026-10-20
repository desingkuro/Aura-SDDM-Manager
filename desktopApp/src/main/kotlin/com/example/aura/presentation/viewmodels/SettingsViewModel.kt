package com.example.aura.presentation.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.aura.data.repository.SddmRepositoryImpl
import com.example.aura.domain.models.ChangeThemeResult
import com.example.aura.domain.models.RestoreBackupResult
import com.example.aura.domain.models.SddmBackup
import com.example.aura.domain.models.SddmBackupKind
import com.example.aura.domain.models.SddmDiagnostics
import com.example.aura.domain.models.SddmTheme
import com.example.aura.domain.repository.SddmRepository
import kotlin.concurrent.thread

/**
 * Estado de la pantalla de Ajustes: respaldos, diagnóstico y temas instalados.
 */
class SettingsViewModel(
    private val sddmRepository: SddmRepository = SddmRepositoryImpl(),
) {
    var backups by mutableStateOf<List<SddmBackup>>(emptyList())
        private set

    var selectedBackup by mutableStateOf<SddmBackup?>(null)
        private set

    var showRestoreDialog by mutableStateOf(false)
        private set

    var isRestoring by mutableStateOf(false)
        private set

    var restoreMessage by mutableStateOf<String?>(null)
        private set

    var restoreSucceeded by mutableStateOf(false)
        private set

    var diagnostics by mutableStateOf<SddmDiagnostics?>(null)
        private set

    var themes by mutableStateOf<List<SddmTheme>>(emptyList())
        private set

    var currentTheme by mutableStateOf("")
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var selectedTheme by mutableStateOf<SddmTheme?>(null)
        private set

    var showThemeDialog by mutableStateOf(false)
        private set

    var isChangingTheme by mutableStateOf(false)
        private set

    var themeMessage by mutableStateOf<String?>(null)
        private set

    var themeChangeSucceeded by mutableStateOf(false)
        private set

    fun loadSettings() {
        loadBackups()
        refresh()
    }

    fun loadBackups() {
        backups = sddmRepository.listBackups()
    }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        thread(isDaemon = true) {
            try {
                refreshRepositoryState()
            } catch (e: Exception) {
                println("No se pudo actualizar el diagnóstico: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    private fun refreshRepositoryState() {
        diagnostics = sddmRepository.getDiagnostics()
        themes = sddmRepository.getInstalledThemes()
        currentTheme = sddmRepository.getCurrentTheme()
        backups = sddmRepository.listBackups()
    }

    fun requestRestore(backup: SddmBackup) {
        if (isRestoring) return
        selectedBackup = backup
        showRestoreDialog = true
    }

    fun cancelRestore() {
        showRestoreDialog = false
        selectedBackup = null
    }

    fun confirmRestore() {
        val backup = selectedBackup ?: return
        showRestoreDialog = false
        if (isRestoring) return

        isRestoring = true
        thread(isDaemon = true) {
            try {
                when (val result = sddmRepository.restoreBackup(backup.id)) {
                    is RestoreBackupResult.Success -> {
                        restoreSucceeded = true
                        restoreMessage = buildRestoreMessage(result.backup)
                    }

                    is RestoreBackupResult.Failure -> {
                        restoreSucceeded = false
                        restoreMessage = result.message
                    }
                }
            } finally {
                isRestoring = false
                selectedBackup = null
                runCatching { refreshRepositoryState() }
            }
        }
    }

    fun requestThemeChange(theme: SddmTheme) {
        if (isChangingTheme || theme.isCurrent) return
        selectedTheme = theme
        showThemeDialog = true
    }

    fun cancelThemeChange() {
        showThemeDialog = false
        selectedTheme = null
    }

    fun confirmThemeChange() {
        val theme = selectedTheme ?: return
        showThemeDialog = false
        if (isChangingTheme) return

        isChangingTheme = true
        thread(isDaemon = true) {
            try {
                when (val result = sddmRepository.changeTheme(theme.name)) {
                    is ChangeThemeResult.Success -> {
                        themeChangeSucceeded = true
                        themeMessage = buildString {
                            append("Se cambió el tema de SDDM de \"${result.previousTheme}\" ")
                            append("a \"${result.theme.displayName}\".")
                            append("\n\nSe creó un respaldo automático del cambio de tema. ")
                            append("Reinicia SDDM o cierra sesión para ver el nuevo tema.")
                        }
                    }

                    is ChangeThemeResult.Failure -> {
                        themeChangeSucceeded = false
                        themeMessage = result.message
                    }
                }
            } finally {
                isChangingTheme = false
                selectedTheme = null
                runCatching { refreshRepositoryState() }
            }
        }
    }

    fun clearRestoreMessage() {
        restoreMessage = null
    }

    fun clearThemeMessage() {
        themeMessage = null
    }

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
}
