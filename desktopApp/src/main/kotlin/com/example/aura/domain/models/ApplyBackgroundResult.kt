package com.example.aura.domain.models

/**
 * Resultado de intentar aplicar un fondo (imagen o video) al tema SDDM actual.
 */
sealed interface ApplyBackgroundResult {
    /** El fondo se copió al tema y la configuración quedó actualizada. */
    data class Success(
        val backgroundName: String,
        val themeName: String,
    ) : ApplyBackgroundResult

    /** No se pudo aplicar; [message] describe el motivo para mostrarlo en la UI. */
    data class Failure(
        val message: String,
    ) : ApplyBackgroundResult
}
