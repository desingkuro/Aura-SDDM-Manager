package com.example.aura.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.aura.presentation.ui.components.Sidebar
import com.example.aura.presentation.ui.screens.MediaScreen
import com.example.aura.presentation.ui.screens.SettingsScreen
import com.example.aura.presentation.ui.theme.AuraColors
import com.example.aura.presentation.viewmodels.MediaType

@Composable
@Preview
fun App() {
    val routActual = remember{ mutableStateOf("image") }
    MaterialTheme {
        Row(
            modifier = Modifier
                .background(AuraColors.Background)
                .safeContentPadding()
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Le damos el 25% del ancho de la pantalla al Sidebar
            Sidebar(
                modifier = Modifier.weight(0.22f),
                currentRoute = routActual.value,
                onNavigate = {
                    routActual.value = it
                }
            )
            Box(modifier = Modifier.fillMaxWidth(.78f).fillMaxHeight()) {
                when (routActual.value) {
                    MediaType.IMAGE.route -> MediaScreen(mediaType = MediaType.IMAGE, modifier = Modifier)
                    MediaType.VIDEO.route -> MediaScreen(mediaType = MediaType.VIDEO, modifier = Modifier)
                    "ajustes_avanzados" -> SettingsScreen(modifier = Modifier)
                    else -> MediaScreen(mediaType = MediaType.IMAGE, modifier = Modifier)
                }
            }
        }
    }
}