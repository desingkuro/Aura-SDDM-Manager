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
import com.example.aura.presentation.viewmodels.MediaViewModel

@Composable
fun FooterScreen(
    modifier: Modifier = Modifier,
    viewModel: MediaViewModel,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AuraColors.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {

        //Previsualizar
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(42.dp)
                .clip(CircleShape)
                .background(
                    if (viewModel.selectedFile != null) AuraColors.PrimaryMuted else AuraColors.SurfaceElevated
                )
                .pointerHoverIcon(
                    if (viewModel.selectedFile != null) PointerIcon.Hand else PointerIcon.Default
                )
                .clickable(enabled = viewModel.selectedFile != null) {
                    viewModel.launchSddmPreview()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Previsualizar",
                fontSize = 15.sp,
                color = if (viewModel.selectedFile != null) AuraColors.TextPrimary else AuraColors.TextSecondary,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        //Eliminar
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(42.dp)
                .clip(CircleShape)
                .background(
                    if (viewModel.selectedFile != null) AuraColors.Danger else AuraColors.SurfaceElevated
                )
                .pointerHoverIcon(
                    if (viewModel.selectedFile != null) PointerIcon.Hand else PointerIcon.Default
                )
                .clickable(enabled = viewModel.selectedFile != null) {
                    viewModel.openDeleteDialog()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Eliminar",
                fontSize = 15.sp,
                color = if (viewModel.selectedFile != null) AuraColors.TextPrimary else AuraColors.TextSecondary,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        //Aplicar
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(42.dp)
                .clip(CircleShape)
                .background(
                    if (viewModel.selectedFile != null && !viewModel.isApplying) AuraColors.Primary else AuraColors.SurfaceElevated
                )
                .pointerHoverIcon(
                    if (viewModel.selectedFile != null && !viewModel.isApplying) {
                        PointerIcon.Hand
                    } else {
                        PointerIcon.Default
                    }
                )
                .clickable(enabled = viewModel.selectedFile != null && !viewModel.isApplying) {
                    viewModel.openApplyDialog()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (viewModel.isApplying) "Aplicando..." else "Aplicar",
                fontSize = 15.sp,
                color = if (viewModel.selectedFile != null && !viewModel.isApplying) AuraColors.Background else AuraColors.TextSecondary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
