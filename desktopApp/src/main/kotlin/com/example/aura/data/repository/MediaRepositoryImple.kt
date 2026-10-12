package com.example.aura.data.repository

import com.example.aura.data.local.CreateMediaDirectory
import com.example.aura.domain.repository.MediaRepository
import java.io.File

class MediaRepositoryImple: MediaRepository {
    private val mediaDir = CreateMediaDirectory()
    // Define las extensiones permitidas en minúsculas
    private val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    private val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm")

    override fun createMediaDirectory(media: File) {
        val destinationFile = File(mediaDir, media.name)
        media.copyTo(destinationFile, overwrite = true)
    }

    // Define las extensiones permitidas en minúsculas
    override fun getImageFiles(): List<File> {
        //println("getImageFiles ${mediaDir.listFiles().contentToString()}")
        return mediaDir.listFiles()?.filter { archivo ->
            archivo.extension.lowercase() in imageExtensions
        } ?: emptyList()
    }

    override fun getVideoFiles(): List<File> {
        //println("getVideoFiles ${mediaDir.listFiles().contentToString()}")
        return mediaDir.listFiles()?.filter { archivo ->
            archivo.extension.lowercase() in videoExtensions
        } ?: emptyList()
    }

}