package com.example.aura.domain.models

import java.time.Instant

/**
 * Respaldo creado automáticamente antes de aplicar un fondo.
 *
 * [id] es el nombre de la carpeta dentro de `~/.local/share/Aura/backups/`.
 */
data class SddmBackup(
    val id: String,
    val createdAt: Instant,
    val themeName: String,
    val backgroundName: String,
    val targetBackgroundExisted: Boolean,
)
