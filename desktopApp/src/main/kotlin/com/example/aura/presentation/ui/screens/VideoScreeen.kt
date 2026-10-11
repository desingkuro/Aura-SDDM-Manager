package com.example.aura.presentation.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aura.presentation.ui.components.FooterScreen
import com.example.aura.presentation.ui.components.HeaderScreen
import com.example.aura.presentation.ui.components.MainScreen

@Composable
fun VideoScreeen(modifier: Modifier = Modifier, routActual: MutableState<String>) {
    Column(
        modifier = modifier // 2. Usa el modifier del padre (el weight(0.75f))
            .fillMaxHeight()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HEADER
        HeaderScreen(
            title = "Personalización de fondo animado"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // MAIN CONTENT
        // Ya que MainScreen ahora acepta modifier (lo arreglamos en el paso anterior),
        // le pasamos directamente weight(1f) y nos ahorramos el Box.
        MainScreen(
            modifier = Modifier.weight(1f),
            pahtActual = routActual
        )

        Spacer(modifier = Modifier.height(16.dp))

        // FOOTER
        FooterScreen(
            click = {}
        )
    }
}