package com.example.aura.domain.usecase

import com.example.aura.domain.models.BackgroundDestination
import com.example.aura.domain.repository.SddmDataAccess
import java.io.File
import java.nio.file.Files

/**
 * Centraliza las comprobaciones de permisos y de viabilidad previas a una
 * operación sobre SDDM. Los casos de uso no deben repetir `Files.isWritable` ni
 * la detección de `pkexec`.
 */
class ValidatePermissionsUseCase(
    private val repository: SddmDataAccess,
) {

    fun validateSelectedFile(file: File): String? {
        if (!file.exists()) {
            return "El archivo seleccionado ya no existe."
        }
        if (!file.isFile || !file.canRead()) {
            return "No se puede leer \"${file.name}\". Revisa sus permisos."
        }
        return null
    }

    fun supportsDestination(
        destination: BackgroundDestination,
        supportsSeparateScreens: Boolean,
    ): String? {
        if (!supportsSeparateScreens &&
            destination != BackgroundDestination.BOTH
        ) {
            return "Este tema usa un fondo general y no distingue entre login y bloqueo. " +
                "Selecciona \"Ambos\" para aplicar el fondo."
        }
        return null
    }

    fun isWritable(file: File): Boolean = Files.isWritable(file.toPath())

    fun isConfigWritable(configFile: File): Boolean =
        repository.isConfigFileWritable(configFile)

    fun hasPkexec(): Boolean = repository.getPkexecPath().isNotBlank()

    fun requiresElevation(configWritable: Boolean, targetWritable: Boolean): Boolean =
        !configWritable || !targetWritable
}
