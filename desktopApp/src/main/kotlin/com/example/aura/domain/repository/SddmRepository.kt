package com.example.aura.domain.repository

import com.example.aura.domain.models.ApplyBackgroundResult
import com.example.aura.domain.models.BackgroundDestination
import com.example.aura.domain.models.ChangeThemeResult
import com.example.aura.domain.models.RestoreBackupResult
import com.example.aura.domain.models.SddmBackup
import com.example.aura.domain.models.SddmDiagnostics
import com.example.aura.domain.models.SddmTheme
import java.io.File

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

    /**
     * Aplica [background] (imagen o video) al tema SDDM actual: crea un respaldo
     * de la configuración anterior, copia el archivo a la carpeta `backgrounds/`
     * del tema y reescribe su configuración para que las pantallas indicadas en
     * [destination] lo usen. Si el tema requiere privilegios de administrador, se
     * solicitan mediante `pkexec`.
     */
    fun applyBackground(
        background: File,
        destination: BackgroundDestination = BackgroundDestination.BOTH,
    ): ApplyBackgroundResult

    /** Lista los respaldos disponibles, del más reciente al más antiguo. */
    fun listBackups(): List<SddmBackup>

    /** Lista los temas instalados y marca el que SDDM usa actualmente. */
    fun getInstalledThemes(): List<SddmTheme>

    /** Comprueba el tema actual, las rutas, los permisos y las dependencias externas. */
    fun getDiagnostics(): SddmDiagnostics

    /**
     * Cambia el tema activo de SDDM. Antes de escribir la configuración crea un
     * respaldo restaurable; si hace falta, solicita permisos con `pkexec`.
     */
    fun changeTheme(themeName: String): ChangeThemeResult

    /**
     * Restaura la configuración guardada en el respaldo [backupId]. Para fondos
     * recupera también el archivo anterior; para temas, la configuración de SDDM.
     * No elimina el respaldo, por lo que puede reintentarse.
     */
    fun restoreBackup(backupId: String): RestoreBackupResult
}
