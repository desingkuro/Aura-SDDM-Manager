package com.example.aura.presentation.viewmodels

/**
 * Tipo de fondo gestionado por la aplicación. Centraliza la ruta de navegación y
 * los textos que antes se repetían entre las pantallas de imagen y video.
 */
enum class MediaType(
    val route: String,
    val singular: String,
    val plural: String,
    val article: String,
    val cardType: String,
) {
    IMAGE(
        route = "image",
        singular = "imagen",
        plural = "imágenes",
        article = "una",
        cardType = "image",
    ),
    VIDEO(
        route = "video",
        singular = "video",
        plural = "videos",
        article = "un",
        cardType = "video",
    ),
}
