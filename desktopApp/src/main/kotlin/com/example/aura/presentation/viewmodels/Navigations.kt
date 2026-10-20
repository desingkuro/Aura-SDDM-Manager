package com.example.aura.presentation.viewmodels

import aura.desktopapp.generated.resources.Res
import aura.desktopapp.generated.resources.images_regular_full
import aura.desktopapp.generated.resources.setting_svgrepo_com
import aura.desktopapp.generated.resources.video_travel_image_svgrepo_com
import org.jetbrains.compose.resources.DrawableResource

data class NavigationItem(
    val title: String,
    val icon: DrawableResource,
    val path: String
)

val menuItems = listOf(
    NavigationItem(
        "Fondo Imagenes",
        Res.drawable.images_regular_full,
        path = MediaType.IMAGE.route
    ),
    NavigationItem(
        "Fondo animado (Video)",
        Res.drawable.video_travel_image_svgrepo_com,
        path = MediaType.VIDEO.route
    ),
    NavigationItem(
        "Ajustes Avanzados",
        Res.drawable.setting_svgrepo_com,
        path = "ajustes_avanzados"
    )
)