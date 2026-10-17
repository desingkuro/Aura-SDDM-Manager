package com.example.aura.domain.repository

import com.example.aura.domain.models.ApplyBackgroundResult
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

    /**
     * Aplica [background] (imagen o video) al tema SDDM actual: copia el archivo a
     * la carpeta `backgrounds/` del tema y reescribe su configuración para que las
     * pantallas de login y bloqueo lo usen. Si el tema requiere privilegios de
     * administrador, se solicitan mediante `pkexec`.
     */
    fun applyBackground(background: File): ApplyBackgroundResult
}
