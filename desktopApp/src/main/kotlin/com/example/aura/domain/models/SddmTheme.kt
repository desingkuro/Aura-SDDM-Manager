package com.example.aura.domain.models

/**
 * Tema instalado en el directorio de temas de SDDM.
 */
data class SddmTheme(
    val name: String,
    val displayName: String,
    val path: String,
    val isCurrent: Boolean,
    val supportsSeparateScreens: Boolean,
)
