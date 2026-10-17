package com.example.aura.domain.repository

import java.io.File

interface SddmRepository {
    fun getCurrentTheme(): String
    fun getAppliedBackground(): Pair<File, String>?
    fun saveAppliedBackground(file: File)
    fun getGreeterPath(): String

    /**
     * Abre una previsualización de SDDM usando un tema temporal que apunta al
     * [background] seleccionado. El tema temporal se elimina al cerrar la ventana.
     */
    fun launchPreview(background: File?)
}
