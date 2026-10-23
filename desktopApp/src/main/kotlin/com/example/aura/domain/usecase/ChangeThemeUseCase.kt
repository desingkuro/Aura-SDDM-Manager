package com.example.aura.domain.usecase

import com.example.aura.domain.models.ChangeThemeResult
import com.example.aura.domain.repository.SddmDataAccess
import java.io.File
import java.nio.file.Files

/**
 * Cambia el tema activo de SDDM con respaldo previo y verificación del tema
 * efectivo. Si otro archivo de configuración tiene prioridad, revierte.
 */
class ChangeThemeUseCase(
    private val repository: SddmDataAccess,
    private val createBackup: CreateBackupUseCase = CreateBackupUseCase(repository),
    private val validatePermissions: ValidatePermissionsUseCase =
        ValidatePermissionsUseCase(repository),
) {

    operator fun invoke(themeName: String): ChangeThemeResult {
        repository.resolveThemeDir(themeName)
            ?: return ChangeThemeResult.Failure("Nombre de tema inválido o tema no encontrado.")
        val theme = repository.getInstalledThemes().firstOrNull { it.name == themeName }
            ?: return ChangeThemeResult.Failure(
                "El tema \"$themeName\" no tiene una configuración compatible."
            )
        val previousTheme = repository.getCurrentTheme()
        if (previousTheme == themeName) {
            return ChangeThemeResult.Success(previousTheme, theme.copy(isCurrent = true))
        }

        val configFile = try {
            repository.themeConfigTarget()
        } catch (error: SecurityException) {
            return ChangeThemeResult.Failure(
                "No se puede escribir la configuración de SDDM: ${error.message}"
            )
        }
        val configWritable = validatePermissions.isConfigWritable(configFile)
        if (!configWritable && !validatePermissions.hasPkexec()) {
            return ChangeThemeResult.Failure(
                "No se puede cambiar el tema sin permisos de administrador. No se encontró " +
                    "pkexec: instala PolicyKit (por ejemplo, el paquete \"polkit\") para continuar."
            )
        }

        val backupDir = try {
            createBackup.createThemeBackup(previousTheme, themeName, configFile)
        } catch (e: Exception) {
            return ChangeThemeResult.Failure(
                "No se pudo crear el respaldo del tema. No se cambió la configuración. " +
                    "Detalle: ${e.message ?: "error desconocido"}"
            )
        }
        println("Respaldo de tema creado en ${backupDir.absolutePath}")

        val content = "[Theme]\nCurrent=$themeName\n"
        val error = if (configWritable) {
            try {
                repository.writeConfigAtomically(configFile, content)
                null
            } catch (e: Exception) {
                "No se pudo cambiar el tema: ${e.message ?: "error desconocido"}"
            }
        } else {
            repository.writeThemeConfigWithElevatedPrivileges(configFile, content)
        }
        if (error != null) {
            return ChangeThemeResult.Failure(error)
        }

        val effectiveTheme = repository.getCurrentTheme()
        if (effectiveTheme != themeName) {
            val configExisted = File(backupDir, "sddm-config").isFile
            val rollback = runCatching {
                repository.restoreThemeConfigDirectly(backupDir, configFile, configExisted)
            }
            val rollbackDetail = if (rollback.isSuccess) {
                " Se revirtió la configuración anterior."
            } else {
                " Además, no se pudo revertir la configuración anterior: " +
                    "${rollback.exceptionOrNull()?.message ?: "error desconocido"}"
            }
            return ChangeThemeResult.Failure(
                "Se guardó el tema en ${configFile.absolutePath}, pero SDDM sigue leyendo " +
                    "\"$effectiveTheme\". Revisa si otro archivo de configuración tiene prioridad." +
                    rollbackDetail
            )
        }

        try {
            Files.deleteIfExists(repository.appliedBackgroundFile.toPath())
        } catch (e: Exception) {
            println("No se pudo limpiar el fondo aplicado anterior: ${e.message}")
        }

        return ChangeThemeResult.Success(previousTheme, theme.copy(isCurrent = true))
    }
}
