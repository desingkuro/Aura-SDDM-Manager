package com.example.aura.presentation.viewmodels

import androidx.compose.runtime.mutableStateOf
import com.example.aura.data.repository.MediaRepositoryImple
import com.example.aura.domain.repository.MediaRepository
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MediaViewModel(
    private val mediaRepository: MediaRepository = MediaRepositoryImple()
){
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

    fun loadImages(){
        listImages = mediaRepository.getImageFiles()
        print("loadImages $listImages")
    }

    fun loadVideos(){
        listVideo = mediaRepository.getVideoFiles()
        print("loadVideo $listVideo")
    }

    fun importNewFile(file: File){
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

    fun selectFile(file: File, type: String){
        print("Selecione o file ${file.name}")
        selectedFile = file
        selectedTypeFile = type
    }
}