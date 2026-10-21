package com.example.aura.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.example.aura.presentation.ui.theme.AuraColors
import com.example.aura.presentation.viewmodels.MediaUiState

@Composable
fun FooterScreen(
    modifier: Modifier = Modifier,
    state: MediaUiState,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
    onApply: () -> Unit,
) {
    val canAct = state.selectedFile != null && !state.isApplying && !state.isPreviewing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AuraColors.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {

        // Previsualizar
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(42.dp)
                .clip(CircleShape)
                .background(
                    if (canAct) AuraColors.PrimaryMuted else AuraColors.SurfaceElevated
                )
                .pointerHoverIcon(if (canAct) PointerIcon.Hand else PointerIcon.Default)
                .clickable(enabled = canAct, onClick = onPreview),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.isPreviewing) "Previsualizando..." else "Previsualizar",
                fontSize = 15.sp,
                color = if (canAct) AuraColors.TextPrimary else AuraColors.TextSecondary,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        // Eliminar
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(42.dp)
                .clip(CircleShape)
                .background(
                    if (canAct) AuraColors.Danger else AuraColors.SurfaceElevated
                )
                .pointerHoverIcon(if (canAct) PointerIcon.Hand else PointerIcon.Default)
                .clickable(enabled = canAct, onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Eliminar",
                fontSize = 15.sp,
                color = if (canAct) AuraColors.TextPrimary else AuraColors.TextSecondary,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        // Aplicar
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(42.dp)
                .clip(CircleShape)
                .background(
                    if (canAct) AuraColors.Primary else AuraColors.SurfaceElevated
                )
                .pointerHoverIcon(if (canAct) PointerIcon.Hand else PointerIcon.Default)
                .clickable(enabled = canAct, onClick = onApply),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.isApplying) "Aplicando..." else "Aplicar",
                fontSize = 15.sp,
                color = if (canAct) AuraColors.Background else AuraColors.TextSecondary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
