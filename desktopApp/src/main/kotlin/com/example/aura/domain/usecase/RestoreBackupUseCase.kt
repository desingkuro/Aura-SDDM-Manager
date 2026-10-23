package com.example.aura.domain.usecase

import com.example.aura.domain.repository.SddmDataAccess
import com.example.aura.domain.models.RestoreBackupResult
import com.example.aura.domain.models.SddmBackupKind
import java.io.File
import java.util.Properties

/**
 * Restaura un respaldo de fondo o de tema. Valida el manifiesto, comprueba los
 * permisos y elige la ruta directa o la elevada con `pkexec`.
 */
class RestoreBackupUseCase(
    private val repository: SddmDataAccess,
    private val validatePermissions: ValidatePermissionsUseCase =
        ValidatePermissionsUseCase(repository),
) {

    operator fun invoke(backupId: String): RestoreBackupResult {
        if (backupId.isBlank() ||
            backupId.contains('/') ||
            backupId.contains('\\') ||
            backupId.contains("..")
        ) {
            return RestoreBackupResult.Failure("Identificador de respaldo inválido.")
        }

        val backupDir = File(repository.backupsDir, backupId)
        if (!backupDir.isDirectory || !repository.isDirectChild(repository.backupsDir, backupDir)) {
            return RestoreBackupResult.Failure("No se encontró el respaldo seleccionado.")
        }

        return try {
            val backup = repository.readBackup(backupDir)
                ?: return RestoreBackupResult.Failure("El respaldo está incompleto o dañado.")
            val manifestFile = File(backupDir, repository.backupManifestName)
            val manifest = Properties().apply {
                manifestFile.inputStream().use { load(it) }
            }

            if (backup.kind == SddmBackupKind.THEME) {
                return repository.restoreThemeBackup(backupDir, backup)
            }

            val themeDir = manifest.getProperty("theme_dir")?.let(::File)
                ?: return RestoreBackupResult.Failure("El respaldo no contiene la ruta del tema.")
            val themeConfigFile = manifest.getProperty("theme_config")?.let(::File)
                ?: return RestoreBackupResult.Failure(
                    "El respaldo no contiene la ruta de configuración."
                )
            val targetBackground = manifest.getProperty("target_background")?.let(::File)
                ?: return RestoreBackupResult.Failure("El respaldo no contiene la ruta del fondo.")
            val targetExisted = manifest.getProperty("target_background_existed")
                ?.toBooleanStrictOrNull() ?: false
            val currentBgExisted = manifest.getProperty("current_bg_existed")
                ?.toBooleanStrictOrNull() ?: false

            repository.validateBackupPaths(themeDir, themeConfigFile, targetBackground)
                ?.let { error -> return RestoreBackupResult.Failure(error) }

            if (!File(backupDir, "theme-config").isFile) {
                return RestoreBackupResult.Failure(
                    "El respaldo no contiene la configuración del tema."
                )
            }
            if (targetExisted && !File(backupDir, "background").isFile) {
                return RestoreBackupResult.Failure("El respaldo no contiene el fondo anterior.")
            }
            if (currentBgExisted && !File(backupDir, "current-bg.txt").isFile) {
                return RestoreBackupResult.Failure(
                    "El respaldo no contiene el registro del fondo aplicado."
                )
            }

            val configWritable = if (themeConfigFile.exists()) {
                validatePermissions.isWritable(themeConfigFile)
            } else {
                themeConfigFile.parentFile?.let(validatePermissions::isWritable) ?: false
            }
            val backgroundWritable = when {
                targetExisted ->
                    targetBackground.parentFile?.let(validatePermissions::isWritable) ?: false
                targetBackground.exists() ->
                    targetBackground.parentFile?.let(validatePermissions::isWritable) ?: false
                else -> true
            }
            val requiresElevation = validatePermissions.requiresElevation(
                configWritable = configWritable,
                targetWritable = backgroundWritable,
            )

            if (requiresElevation && !validatePermissions.hasPkexec()) {
                return RestoreBackupResult.Failure(
                    "El tema está protegido y no se puede restaurar sin permisos de administrador. " +
                        "No se encontró pkexec: instala PolicyKit (por ejemplo, el paquete " +
                        "\"polkit\") para continuar."
                )
            }

            val error = if (requiresElevation) {
                repository.restoreWithElevatedPrivileges(
                    backupDir,
                    themeConfigFile,
                    targetBackground,
                    targetExisted,
                )
            } else {
                repository.restoreDirectly(
                    backupDir,
                    themeConfigFile,
                    targetBackground,
                    targetExisted,
                )
            }
            if (error != null) {
                return RestoreBackupResult.Failure(error)
            }

            repository.restoreAppliedBackgroundFile(backupDir, currentBgExisted)
            println("Respaldo restaurado: ${backupDir.absolutePath}")
            RestoreBackupResult.Success(backup)
        } catch (e: Exception) {
            println("Error al restaurar el respaldo: ${e.message}")
            e.printStackTrace()
            RestoreBackupResult.Failure(
                "No se pudo restaurar el respaldo: ${e.message ?: "error desconocido"}"
            )
        }
    }
}
