package com.example.aura.presentation.viewmodels

import java.io.File

/**
 * Tipo de archivo multimedia dentro de la galería. Se usa para decidir cómo
 * renderizar cada tarjeta y para guardar el tipo del fondo seleccionado.
 */
enum class MediaType(
    val cardType: String,
) {
    IMAGE("image"),
    VIDEO("video");

    companion object {
        /** Ruta única de la sección de fondos en la navegación. */
        const val ROUTE = "media"

        private val videoExtensions = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v"
        )

        /** Determina el tipo a partir de la extensión del archivo. */
        fun fromFile(file: File): MediaType =
            if (file.extension.lowercase() in videoExtensions) VIDEO else IMAGE
    }
}
