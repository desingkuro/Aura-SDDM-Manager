package com.example.aura.domain.usecase

import com.example.aura.domain.repository.SddmDataAccess
import com.example.aura.domain.models.ApplyBackgroundResult
import com.example.aura.domain.models.BackgroundDestination
import java.io.File

/**
 * Orquesta la aplicación de un fondo al tema SDDM actual:
 * valida el archivo, resuelve el tema y su configuración, comprueba permisos,
 * crea el respaldo, escribe el fondo y la configuración de forma transaccional.
 */
class ApplySddmBackgroundUseCase(
    private val repository: SddmDataAccess,
    private val createBackup: CreateBackupUseCase = CreateBackupUseCase(repository),
    private val validatePermissions: ValidatePermissionsUseCase =
        ValidatePermissionsUseCase(repository),
) {

    operator fun invoke(
        background: File,
        destination: BackgroundDestination = BackgroundDestination.BOTH,
    ): ApplyBackgroundResult {
        validatePermissions.validateSelectedFile(background)?.let { error ->
            return ApplyBackgroundResult.Failure(error)
        }

        return try {
            val themeName = repository.getCurrentTheme()
            val themeDir = repository.resolveThemeDir(themeName)
                ?: return ApplyBackgroundResult.Failure(
                    "No se encontró el tema \"$themeName\" dentro de " +
                        "${repository.themesDir.absolutePath}."
                )

            val sourceBackgroundsDir = themeDir.listFiles()
                ?.firstOrNull { it.isDirectory && it.name.equals("backgrounds", ignoreCase = true) }
            val backgroundsDirName = sourceBackgroundsDir?.name ?: "backgrounds"
            val backgroundsDir = repository.requireInside(
                themeDir,
                File(themeDir, backgroundsDirName),
                "carpeta de fondos",
            )
            val targetBackground = repository.requireInside(
                backgroundsDir,
                File(backgroundsDir, background.name),
                "fondo de destino",
            )

            val configuredRelativePath = repository.readConfiguredThemePath(themeDir)
            val themeConfigFile: File
            val newConfigContent: String

            if (configuredRelativePath != null) {
                themeConfigFile = repository.resolveThemeConfig(
                    themeDir,
                    configuredRelativePath,
                )
                if (!themeConfigFile.isFile) {
                    return ApplyBackgroundResult.Failure(
                        "No se encontró el archivo de configuración del tema \"$themeName\"."
                    )
                }
                newConfigContent = repository.buildConfiguredThemePreview(
                    content = themeConfigFile.readText(),
                    backgroundName = background.name,
                    destination = destination,
                )
            } else {
                if (destination != BackgroundDestination.BOTH) {
                    return ApplyBackgroundResult.Failure(
                        "El tema \"$themeName\" usa un fondo general y no distingue entre login " +
                            "y bloqueo. Selecciona \"Ambos\" para aplicar el fondo."
                    )
                }
                themeConfigFile = repository.requireInside(
                    themeDir,
                    File(themeDir, "theme.conf"),
                    "configuración tradicional del tema",
                )
                if (!themeConfigFile.isFile) {
                    return ApplyBackgroundResult.Failure(
                        "El tema \"$themeName\" no tiene una configuración de fondo compatible."
                    )
                }
                newConfigContent = repository.buildThemeConfPreview(
                    content = themeConfigFile.readText(),
                    fileName = background.name,
                    backgroundsFolder = backgroundsDirName,
                )
            }

            val backgroundsWritable = if (backgroundsDir.exists()) {
                validatePermissions.isWritable(backgroundsDir)
            } else {
                validatePermissions.isWritable(themeDir)
            }
            val configWritable = validatePermissions.isWritable(themeConfigFile)
            val requiresElevation = validatePermissions.requiresElevation(
                configWritable = configWritable,
                targetWritable = backgroundsWritable,
            )

            if (requiresElevation && !validatePermissions.hasPkexec()) {
                val protectedParts = buildList {
                    if (!backgroundsWritable) add("la carpeta de fondos")
                    if (!configWritable) add("la configuración del tema")
                }
                return ApplyBackgroundResult.Failure(
                    "No se puede escribir en ${protectedParts.joinToString(" ni en ")} del tema " +
                        "\"$themeName\" sin permisos de administrador. No se encontró pkexec: " +
                        "instala PolicyKit (por ejemplo, el paquete \"polkit\") para continuar."
                )
            }

            val backupDir = try {
                createBackup.createBackgroundBackup(
                    themeName = themeName,
                    themeDir = themeDir,
                    themeConfigFile = themeConfigFile,
                    targetBackground = targetBackground,
                )
            } catch (e: Exception) {
                return ApplyBackgroundResult.Failure(
                    "No se pudo crear el respaldo de la configuración de SDDM. " +
                        "No se aplicó ningún cambio. Detalle: ${e.message ?: "error desconocido"}"
                )
            }
            println("Respaldo creado en ${backupDir.absolutePath}")

            val error = if (requiresElevation) {
                repository.applyWithElevatedPrivileges(
                    source = background,
                    targetBackground = targetBackground,
                    backgroundsDir = backgroundsDir,
                    configFile = themeConfigFile,
                    newConfigContent = newConfigContent,
                    backupDir = backupDir,
                )
            } else {
                repository.applyDirectly(
                    source = background,
                    targetBackground = targetBackground,
                    backgroundsDir = backgroundsDir,
                    configFile = themeConfigFile,
                    newConfigContent = newConfigContent,
                    backupDir = backupDir,
                )
            }

            if (error != null) {
                ApplyBackgroundResult.Failure(error)
            } else {
                repository.saveAppliedBackground(background)
                println("Fondo aplicado: \"${background.name}\" en el tema \"$themeName\".")
                ApplyBackgroundResult.Success(background.name, themeName)
            }
        } catch (e: Exception) {
            println("Error al aplicar el fondo: ${e.message}")
            e.printStackTrace()
            ApplyBackgroundResult.Failure(
                "No se pudo aplicar el fondo: ${e.message ?: "error desconocido"}"
            )
        }
    }
}
