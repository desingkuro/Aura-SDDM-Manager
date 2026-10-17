package com.example.aura.domain.repository

import java.io.File

interface SddmRepository {
    fun getCurrentTheme(): String
    fun getAppliedBackground(): Pair<File, String>?
    fun saveAppliedBackground(file: File)
    fun getGreeterPath(): String
    fun launchPreview()
}
