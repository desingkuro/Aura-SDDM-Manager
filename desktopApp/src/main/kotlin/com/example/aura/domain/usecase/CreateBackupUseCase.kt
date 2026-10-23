package com.example.aura.domain.usecase

import com.example.aura.domain.repository.SddmDataAccess
import java.io.File

/**
 * Punto único para crear los respaldos que las operaciones de SDDM necesitan
 * antes de tocar archivos del sistema.
 *
 * El repositorio solo aporta las primitivas de copia y el manifiesto; este caso
 * de uso decide cuándo y con qué datos se debe crear cada respaldo.
 */
class CreateBackupUseCase(
    private val repository: SddmDataAccess,
) {

    fun createBackgroundBackup(
        themeName: String,
        themeDir: File,
        themeConfigFile: File,
        targetBackground: File,
    ): File = repository.createBackup(
        themeName = themeName,
        themeDir = themeDir,
        themeConfigFile = themeConfigFile,
        targetBackground = targetBackground,
    )

    fun createThemeBackup(
        previousTheme: String,
        newTheme: String,
        configFile: File,
    ): File = repository.createThemeBackup(
        previousTheme = previousTheme,
        newTheme = newTheme,
        configFile = configFile,
    )
}
