package com.example.aura.domain.models

import java.time.Instant

enum class DiagnosticStatus {
    OK,
    WARNING,
    ERROR,
}

/**
 * Resultado de una comprobación del entorno de SDDM y de las dependencias de Aura.
 */
data class SddmDiagnostic(
    val label: String,
    val value: String,
    val status: DiagnosticStatus,
    val hint: String? = null,
)

data class SddmDiagnostics(
    val checkedAt: Instant,
    val items: List<SddmDiagnostic>,
)
