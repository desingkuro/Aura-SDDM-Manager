package com.example.aura.presentation.utils

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun openFilePicker(onFileSelected: (File) -> Unit) {
    // Lo ejecutamos en un hilo secundario para no congelar la interfaz de Compose
    Thread {
        // FileDialog abre el selector nativo del sistema (GTK en tu Linux)
        val dialog = FileDialog(null as Frame?, "Seleccionar archivo multimedia", FileDialog.LOAD)

        // Muestra la ventana. El hilo se pausará aquí hasta que el usuario elija algo o cierre.
        dialog.isVisible = true

        // Obtenemos el archivo seleccionado
        val selectedFile = dialog.files.firstOrNull()
        if (selectedFile != null) {
            onFileSelected(selectedFile)
        }
    }.start()
}