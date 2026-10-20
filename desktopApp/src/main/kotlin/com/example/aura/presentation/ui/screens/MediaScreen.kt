package com.example.aura.presentation.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aura.presentation.ui.components.FooterScreen
import com.example.aura.presentation.ui.components.HeaderScreen
import com.example.aura.presentation.ui.components.MainScreen
import com.example.aura.presentation.viewmodels.MediaType
import com.example.aura.presentation.viewmodels.MediaViewModel

/**
 * Pantalla compartida para gestionar fondos estáticos (imágenes) y animados
 * (videos). El [mediaType] decide qué lista cargar y qué textos mostrar.
 */
@Composable
fun MediaScreen(
    mediaType: MediaType,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember { MediaViewModel() }

    LaunchedEffect(mediaType) {
        when (mediaType) {
            MediaType.IMAGE -> viewModel.loadImages()
            MediaType.VIDEO -> viewModel.loadVideos()
        }
        viewModel.loadFileApplied()
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HEADER
        HeaderScreen(
            title = when (mediaType) {
                MediaType.IMAGE -> "Personalización de fondo de imagen"
                MediaType.VIDEO -> "Personalización de fondo animado"
            },
            subtitle = when (mediaType) {
                MediaType.IMAGE ->
                    "Importa, selecciona y previsualiza el fondo estático para tu pantalla de inicio."

                MediaType.VIDEO ->
                    "Gestiona videos de fondo y revisa su miniatura antes de aplicarlos."
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // MAIN CONTENT
        MainScreen(
            modifier = Modifier.weight(1f),
            mediaType = mediaType,
            listFiles = when (mediaType) {
                MediaType.IMAGE -> viewModel.listImages
                MediaType.VIDEO -> viewModel.listVideo
            },
            viewModel = viewModel
        )

        Spacer(modifier = Modifier.height(16.dp))

        // FOOTER
        FooterScreen(viewModel = viewModel)
    }
}
