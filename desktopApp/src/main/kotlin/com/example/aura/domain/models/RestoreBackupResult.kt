package com.example.aura.domain.models

/**
 * Resultado de restaurar un respaldo de la configuración de SDDM.
 */
sealed interface RestoreBackupResult {
    /** La configuración y el fondo anterior quedaron restaurados. */
    data class Success(
        val backup: SddmBackup,
    ) : RestoreBackupResult

    /** No se pudo restaurar; [message] describe el motivo para la UI. */
    data class Failure(
        val message: String,
    ) : RestoreBackupResult
}
