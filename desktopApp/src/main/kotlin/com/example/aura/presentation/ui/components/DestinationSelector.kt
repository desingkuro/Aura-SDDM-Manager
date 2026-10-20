package com.example.aura.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.domain.models.BackgroundDestination
import com.example.aura.presentation.ui.theme.AuraColors

/**
 * Selector compacto para elegir si el fondo se aplica al login, al bloqueo o a
 * ambas pantallas. En temas con un único fondo general solo se permite "Ambos".
 */
@Composable
fun DestinationSelector(
    selected: BackgroundDestination,
    supportsSeparateScreens: Boolean,
    onSelected: (BackgroundDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Aplicar en",
            color = AuraColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackgroundDestination.entries.forEach { destination ->
                val enabled = supportsSeparateScreens || destination == BackgroundDestination.BOTH
                DestinationChip(
                    label = destination.label,
                    selected = selected == destination,
                    enabled = enabled,
                    onClick = { onSelected(destination) },
                )
            }
        }

        if (!supportsSeparateScreens) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Este tema usa un fondo general: el cambio se aplica a login y bloqueo.",
                color = AuraColors.TextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun DestinationChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    selected -> AuraColors.Primary
                    enabled -> AuraColors.SurfaceElevated
                    else -> AuraColors.SurfaceVariant
                }
            )
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                selected -> AuraColors.Background
                enabled -> AuraColors.TextPrimary
                else -> AuraColors.TextMuted
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
