package com.example.aura.domain.repository

import java.io.File

interface MediaRepository{
    fun getImageFiles(): List<File>
    fun getVideoFiles(): List<File>
    fun createMediaDirectory(media:File)

    fun getAppliedbackground(): Pair<File, String>?
    fun saveAppliedbackground(file:File)

    fun deletedFile(file:File): Boolean
    fun getCurrentTheme():String
}