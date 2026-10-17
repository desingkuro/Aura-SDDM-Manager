package com.example.aura.data.repository

import com.example.aura.data.local.CreateMediaDirectory
import com.example.aura.domain.repository.MediaRepository
import java.io.File

class MediaRepositoryImple : MediaRepository {
    private val mediaDir = CreateMediaDirectory()
    private val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    private val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v")

    override fun createMediaDirectory(media: File) {
        val destinationFile = File(mediaDir, media.name)
        media.copyTo(destinationFile, overwrite = true)
    }

    override fun getImageFiles(): List<File> {
        return mediaDir.listFiles()
            ?.filter { file -> file.extension.lowercase() in imageExtensions }
            ?: emptyList()
    }

    override fun getVideoFiles(): List<File> {
        return mediaDir.listFiles()
            ?.filter { file -> file.extension.lowercase() in videoExtensions }
            ?: emptyList()
    }

    override fun deletedFile(file: File): Boolean {
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}
