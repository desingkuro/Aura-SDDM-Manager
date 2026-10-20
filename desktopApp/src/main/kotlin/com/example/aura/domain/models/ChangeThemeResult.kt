package com.example.aura.domain.models

/**
 * Resultado de cambiar el tema activo de SDDM.
 */
sealed interface ChangeThemeResult {
    data class Success(
        val previousTheme: String,
        val theme: SddmTheme,
    ) : ChangeThemeResult

    data class Failure(
        val message: String,
    ) : ChangeThemeResult
}
