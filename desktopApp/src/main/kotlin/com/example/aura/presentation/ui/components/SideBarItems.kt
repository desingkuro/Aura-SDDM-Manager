package com.example.aura.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun ItemSideBar(
    title: String,
    path: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false, // Útil para saber si estamos en esa ruta
    click: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHover by interactionSource.collectIsHoveredAsState()

    // Estado combinado: activo si está seleccionado o si el ratón está encima
    val isActive = isSelected || isHover

    // Animación suave del fondo
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF2C2C2E) else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "hover_background"
    )

    // Animación del color de los elementos (ícono y texto)
    val contentColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color(0xFFA0A0A5),
        animationSpec = tween(durationMillis = 200),
        label = "hover_content"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .clip(RoundedCornerShape(12.dp)) // El clip debe ir ANTES del background y clickable
            .background(backgroundColor)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null // Quitamos el ripple nativo
            ) { click(path) }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            modifier = Modifier.size(24.dp), // Usamos size en lugar de width y height
            painter = icon,
            contentDescription = "Icono de $title",
            colorFilter = ColorFilter.tint(contentColor) // El color se anima junto con el texto
        )

        Spacer(modifier = Modifier.width(16.dp)) // Mejor que usar fillMaxWidth por porcentajes

        Text(
            text = title,
            color = contentColor,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
        )
    }
}