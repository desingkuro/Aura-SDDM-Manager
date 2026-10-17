package com.example.aura.presentation.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.aura.data.repository.MediaRepositoryImple
import com.example.aura.data.repository.SddmRepositoryImpl
import com.example.aura.domain.repository.MediaRepository
import com.example.aura.domain.repository.SddmRepository
import java.io.File

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

    fun toggleViewDialog() {
        viewDialogDeleted = !viewDialogDeleted
    }

    fun loadImages() {
        listImages = mediaRepository.getImageFiles()
    }

    fun loadVideos() {
        listVideo = mediaRepository.getVideoFiles()
    }

    fun loadFileApplied() {
        fileApplied = sddmRepository.getAppliedBackground()
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
        sddmRepository.launchPreview()
    }
}
