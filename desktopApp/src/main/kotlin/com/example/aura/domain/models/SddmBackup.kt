package com.example.aura.domain.models

import java.time.Instant

enum class SddmBackupKind {
    /** Respaldo creado antes de aplicar un fondo al tema. */
    BACKGROUND,

    /** Respaldo creado antes de cambiar el tema activo de SDDM. */
    THEME,
}

/**
 * Respaldo creado automáticamente antes de una operación sobre SDDM.
 *
 * [id] es el nombre de la carpeta dentro de `~/.local/share/Aura/backups/`.
 * En un respaldo de tema, [themeName] es el tema anterior y [newThemeName] el
 * tema que se iba a activar.
 */
data class SddmBackup(
    val id: String,
    val createdAt: Instant,
    val themeName: String,
    val backgroundName: String,
    val targetBackgroundExisted: Boolean,
    val kind: SddmBackupKind = SddmBackupKind.BACKGROUND,
    val newThemeName: String? = null,
)
