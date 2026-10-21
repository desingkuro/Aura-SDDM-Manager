package com.example.aura.presentation.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Abre el selector nativo en el hilo de Swing y suspende la corrutina hasta que
 * el usuario elige un archivo o cierra el diálogo.
 */
suspend fun openFilePicker(): File? = withContext(Dispatchers.Swing) {
    val dialog = FileDialog(null as Frame?, "Seleccionar archivo multimedia", FileDialog.LOAD)
    dialog.isVisible = true
    dialog.files.firstOrNull()
}
