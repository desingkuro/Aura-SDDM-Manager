package com.example.aura.presentation.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.presentation.ui.components.HeaderScreen
import com.example.aura.presentation.ui.theme.AuraColors

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HEADER
        HeaderScreen(
            title = "Ajustes avanzados",
            subtitle = "Configura las opciones de Aura y de tu tema de SDDM."
        )

        Spacer(modifier = Modifier.height(16.dp))

        // CONTENIDO
        // Vista vacía por ahora: aquí vivirán las opciones de ajustes.

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(AuraColors.Surface, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Vista de ajustes en construcción",
                color = AuraColors.TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
