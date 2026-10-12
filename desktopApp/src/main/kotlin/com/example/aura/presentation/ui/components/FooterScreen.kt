package com.example.aura.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.presentation.utils.openFilePicker
import com.example.aura.presentation.viewmodels.MediaViewModel
import java.io.File

@Composable
fun FooterScreen(
    modifier: Modifier = Modifier, // 1. Recibimos el modifier desde el padre
    viewModel: MediaViewModel,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 2. En lugar de una altura fija de 70.dp, usamos padding vertical.
            // Esto permite que el contenedor abrace su contenido dinámicamente.
            .background(Color(0xFF222222), RoundedCornerShape(12.dp)) // 3. Redondeamos TODAS las esquinas
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // BOTÓN "APLICAR"
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(42.dp)
                .clip(CircleShape) // El clip siempre antes del background y clickable
                .background(Color(0xFF333333))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable {  },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aplicar",
                fontSize = 15.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}