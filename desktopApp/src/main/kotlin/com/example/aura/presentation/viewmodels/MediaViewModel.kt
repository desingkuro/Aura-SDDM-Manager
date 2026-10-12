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

    fun loadImages(){
        listImages = mediaRepository.getImageFiles()
        print("loadImages $listImages")
    }

    fun loadVideos(){
        listVideo = mediaRepository.getVideoFiles()
        print("loadVideo $listVideo")
    }

    fun importNewFile(file: File){
        mediaRepository.createMediaDirectory(file)
        loadImages()
        loadVideos()
    }


}