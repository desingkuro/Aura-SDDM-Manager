package com.example.aura.domain.repository

import com.example.aura.domain.models.BackgroundDestination
import com.example.aura.domain.models.SddmBackup
import com.example.aura.domain.models.SddmDiagnostics
import com.example.aura.domain.models.SddmTheme
import java.io.File

/**
 * Contrato de acceso a los datos de SDDM.
 *
 * Las operaciones que combinan varios pasos (aplicar un fondo, cambiar de tema o
 * restaurar un respaldo) viven en `domain/usecase`; este repositorio solo lee,
 * escribe y lista los recursos del sistema.
 */
interface SddmRepository {
    fun getCurrentTheme(): String
    fun getAppliedBackground(): Pair<File, String>?
    fun saveAppliedBackground(file: File)
    fun getGreeterPath(): String

    /**
     * Indica si el tema actual distingue entre pantalla de login y de bloqueo.
     * Los temas tradicionales con `theme.conf` solo tienen un fondo general.
     */
    fun supportsSeparateScreens(): Boolean

    /**
     * Abre una previsualización de SDDM usando un tema temporal que apunta al
     * [background] seleccionado. El tema temporal se elimina al cerrar la ventana.
     * [destination] permite previsualizar solo una pantalla o ambas.
     */
    suspend fun launchPreview(
        background: File?,
        destination: BackgroundDestination = BackgroundDestination.BOTH,
    ): String?

    /** Lista los respaldos disponibles, del más reciente al más antiguo. */
    fun listBackups(): List<SddmBackup>

    /** Lista los temas instalados y marca el que SDDM usa actualmente. */
    fun getInstalledThemes(): List<SddmTheme>

    /** Comprueba el tema actual, las rutas, los permisos y las dependencias externas. */
    fun getDiagnostics(): SddmDiagnostics
}
