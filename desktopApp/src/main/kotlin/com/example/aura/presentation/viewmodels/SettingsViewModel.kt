package com.example.aura.presentation.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.aura.data.repository.SddmRepositoryImpl
import com.example.aura.domain.models.RestoreBackupResult
import com.example.aura.domain.models.SddmBackup
import com.example.aura.domain.repository.SddmRepository
import kotlin.concurrent.thread

/**
 * Estado de la pantalla de Ajustes relacionado con los respaldos de SDDM.
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

    fun loadBackups() {
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
            when (val result = sddmRepository.restoreBackup(backup.id)) {
                is RestoreBackupResult.Success -> {
                    restoreSucceeded = true
                    restoreMessage = buildString {
                        append("Se restauró la configuración del tema \"${result.backup.themeName}\"")
                        if (result.backup.backgroundName.isNotBlank()) {
                            append(" al estado previo a aplicar \"${result.backup.backgroundName}\"")
                        }
                        append(".\n\nReinicia SDDM o cierra sesión para ver el cambio.")
                    }
                }

                is RestoreBackupResult.Failure -> {
                    restoreSucceeded = false
                    restoreMessage = result.message
                }
            }
            isRestoring = false
            selectedBackup = null
            loadBackups()
        }
    }

    fun clearRestoreMessage() {
        restoreMessage = null
    }
}
