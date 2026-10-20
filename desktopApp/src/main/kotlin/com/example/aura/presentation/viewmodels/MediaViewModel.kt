package com.example.aura.presentation.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.aura.data.repository.MediaRepositoryImple
import com.example.aura.data.repository.SddmRepositoryImpl
import com.example.aura.domain.models.ApplyBackgroundResult
import com.example.aura.domain.models.BackgroundDestination
import com.example.aura.domain.repository.MediaRepository
import com.example.aura.domain.repository.SddmRepository
import java.io.File
import kotlin.concurrent.thread

class MediaViewModel(
    private val mediaRepository: MediaRepository = MediaRepositoryImple(),
    private val sddmRepository: SddmRepository = SddmRepositoryImpl(),
) {
    var listImages by mutableStateOf<List<File>>(emptyList())
        private set

    var listVideo by mutableStateOf<List<File>>(emptyList())
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var selectedFile by mutableStateOf<File?>(null)
        private set

    var selectedTypeFile by mutableStateOf<String?>(null)
        private set

    var fileApplied by mutableStateOf<Pair<File, String>?>(null)
        private set

    var viewDialogDeleted by mutableStateOf(false)
        private set

    var currentTheme by mutableStateOf("")
        private set

    var destination by mutableStateOf(BackgroundDestination.BOTH)
        private set

    var supportsSeparateScreens by mutableStateOf(true)
        private set

    var isApplying by mutableStateOf(false)
        private set

    var viewDialogApply by mutableStateOf(false)
        private set

    var applyMessage by mutableStateOf<String?>(null)
        private set

    var applySucceeded by mutableStateOf(false)
        private set

    fun openDeleteDialog() {
        if (selectedFile == null) return
        viewDialogDeleted = true
    }

    fun closeDeleteDialog() {
        viewDialogDeleted = false
    }

    fun openApplyDialog() {
        if (selectedFile == null || isApplying) return
        viewDialogApply = true
    }

    fun closeApplyDialog() {
        viewDialogApply = false
    }

    fun selectDestination(value: BackgroundDestination) {
        if (!supportsSeparateScreens && value != BackgroundDestination.BOTH) return
        destination = value
    }

    fun loadImages() {
        listImages = mediaRepository.getImageFiles()
    }

    fun loadVideos() {
        listVideo = mediaRepository.getVideoFiles()
    }

    /** Carga imágenes y videos para la galería unificada. */
    fun loadMedia() {
        loadImages()
        loadVideos()
    }

    fun loadFileApplied() {
        fileApplied = sddmRepository.getAppliedBackground()
        currentTheme = sddmRepository.getCurrentTheme()
        supportsSeparateScreens = sddmRepository.supportsSeparateScreens()
        if (!supportsSeparateScreens) {
            destination = BackgroundDestination.BOTH
        }
    }

    fun importNewFile(file: File) {
        try {
            mediaRepository.createMediaDirectory(file)
            errorMessage = null
            loadImages()
            loadVideos()
        } catch (e: Exception) {
            val detail = e.message ?: "error desconocido"
            errorMessage = "No se pudo importar \"${file.name}\". Detalle: $detail"
        }
    }

    fun clearError() {
        errorMessage = null
    }

    fun selectFile(file: File, type: String) {
        selectedFile = file
        selectedTypeFile = type
    }

    fun deleteMedia(file: File?) {
        if (file == null) return

        if (mediaRepository.deletedFile(file)) {
            if (selectedFile?.absolutePath == file.absolutePath) {
                selectedFile = null
                selectedTypeFile = null
            }
            loadImages()
            loadVideos()
        }
    }

    fun getCurrentThemeSddm(): String {
        return sddmRepository.getCurrentTheme()
    }

    fun getGreeterPathSddm(): String {
        return sddmRepository.getGreeterPath()
    }

    fun launchSddmPreview() {
        sddmRepository.launchPreview(selectedFile, destination)
    }

    /**
     * Aplica el fondo seleccionado (imagen o video) al tema SDDM actual. El trabajo
     * pesado se ejecuta en segundo plano porque puede solicitar la contraseña de
     * administrador mediante `pkexec`.
     */
    fun applySelectedBackground() {
        val file = selectedFile ?: return
        if (isApplying) return

        val target = destination
        isApplying = true
        thread(isDaemon = true) {
            when (val result = sddmRepository.applyBackground(file, target)) {
                is ApplyBackgroundResult.Success -> {
                    applySucceeded = true
                    applyMessage = "\"${result.backgroundName}\" se aplicó correctamente a " +
                        "${target.targetDescription} del tema \"${result.themeName}\".\n\n" +
                        "Se creó un respaldo automático que puedes restaurar desde Ajustes " +
                        "Avanzados.\n\nReinicia SDDM o cierra sesión para ver el cambio."
                }

                is ApplyBackgroundResult.Failure -> {
                    applySucceeded = false
                    applyMessage = result.message
                }
            }
            isApplying = false
            loadFileApplied()
        }
    }

    fun clearApplyMessage() {
        applyMessage = null
    }
}
