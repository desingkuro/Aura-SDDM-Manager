package com.example.aura.presentation.viewmodels

import aura.desktopapp.generated.resources.Res
import aura.desktopapp.generated.resources.images_regular_full
import aura.desktopapp.generated.resources.setting_svgrepo_com
import org.jetbrains.compose.resources.DrawableResource

data class NavigationItem(
    val title: String,
    val icon: DrawableResource,
    val path: String
)

val menuItems = listOf(
    NavigationItem(
        "Fondos",
        Res.drawable.images_regular_full,
        path = MediaType.ROUTE
    ),
    NavigationItem(
        "Ajustes Avanzados",
        Res.drawable.setting_svgrepo_com,
        path = "ajustes_avanzados"
    )
)