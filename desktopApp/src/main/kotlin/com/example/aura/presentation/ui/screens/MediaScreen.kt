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
import com.example.aura.presentation.viewmodels.MediaViewModel

/**
 * Pantalla única de fondos: muestra imágenes y videos en la misma galería.
 */
@Composable
fun MediaScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel = remember { MediaViewModel() }

    LaunchedEffect(Unit) {
        viewModel.loadMedia()
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
            title = "Personalización de fondo",
            subtitle = "Importa, selecciona y previsualiza imágenes o videos para tu pantalla de inicio."
        )

        Spacer(modifier = Modifier.height(16.dp))

        // MAIN CONTENT
        MainScreen(
            modifier = Modifier.weight(1f),
            listFiles = viewModel.listImages + viewModel.listVideo,
            viewModel = viewModel
        )

        Spacer(modifier = Modifier.height(16.dp))

        // FOOTER
        FooterScreen(viewModel = viewModel)
    }
}
