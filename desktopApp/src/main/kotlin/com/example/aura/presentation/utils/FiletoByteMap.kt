package com.example.aura.presentation.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import java.io.File

// Es buena práctica que las funciones empiecen con minúscula en Kotlin
fun fileToImageBitmap(file: File?): ImageBitmap? {
    // Si el archivo es nulo o no existe, salimos rápido
    if (file == null || !file.exists()) return null

    return try {
        val bytes = file.readBytes()
        // Convertimos y devolvemos directamente el resultado
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        println("Error de lectura de archivo: ${file.name}")
        e.printStackTrace()
        null
    }
}