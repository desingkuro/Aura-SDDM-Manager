package com.example.aura.presentation.viewmodels

import com.example.aura.domain.models.BackgroundDestination
import java.io.File

/**
 * Estado completo de la pantalla de fondos. La UI solo consume este objeto y
 * envía intenciones al ViewModel; no modifica el estado directamente.
 */
data class MediaUiState(
    val images: List<File> = emptyList(),
    val videos: List<File> = emptyList(),
    val selectedFile: File? = null,
    val selectedType: String? = null,
    val appliedBackground: Pair<File, String>? = null,
    val currentTheme: String = "",
    val destination: BackgroundDestination = BackgroundDestination.BOTH,
    val supportsSeparateScreens: Boolean = true,
    val isLoading: Boolean = false,
    val isApplying: Boolean = false,
    val isPreviewing: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showApplyDialog: Boolean = false,
    val errorMessage: String? = null,
    val applyMessage: String? = null,
    val applySucceeded: Boolean = false,
    val previewMessage: String? = null,
    val previewSucceeded: Boolean = true,
) {
    /** Imágenes y videos en una sola lista para la galería unificada. */
    val mediaFiles: List<File>
        get() = images + videos
}
