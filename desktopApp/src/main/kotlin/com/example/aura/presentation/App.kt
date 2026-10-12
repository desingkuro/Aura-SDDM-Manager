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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.aura.presentation.ui.components.Sidebar
import com.example.aura.presentation.ui.screens.ImageScreen
import com.example.aura.presentation.ui.screens.VideoScreeen

@Composable
@Preview
fun App() {
    val routActual = remember{ mutableStateOf("image") }
    MaterialTheme {
        Row(
            modifier = Modifier
                .background(Color(0xFF131313))
                .safeContentPadding()
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Le damos el 25% del ancho de la pantalla al Sidebar
            Sidebar(
                modifier = Modifier.weight(0.2f),
                onNavigate = {
                    routActual.value = it
                }
            )
            Box(modifier = Modifier.fillMaxWidth(.8f).fillMaxHeight()) {
                when (routActual.value) {
                    "image" -> ImageScreen(modifier = Modifier,routActual)
                    "video" -> VideoScreeen(modifier = Modifier,routActual)
                    "ajustes_avanzados" -> ImageScreen(modifier = Modifier,routActual)
                    else -> ImageScreen(modifier = Modifier,routActual)
                }
            }
        }
    }
}