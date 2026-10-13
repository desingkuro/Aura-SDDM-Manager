package com.example.aura.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import aura.desktopapp.generated.resources.Res
import aura.desktopapp.generated.resources.add_to_queue_svgrepo_com
import aura.desktopapp.generated.resources.images_regular_full
import aura.desktopapp.generated.resources.video_travel_image_svgrepo_com
import com.example.aura.presentation.ui.theme.AuraColors
import com.example.aura.presentation.utils.openFilePicker
import com.example.aura.presentation.viewmodels.MediaViewModel
import org.jetbrains.compose.resources.painterResource
import java.io.File

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    pahtActual: MutableState<String>,
    listFiles: List<File>,
    viewModel: MediaViewModel
) {
    val scrollState = rememberScrollState()
    val isVideo = pahtActual.value == "video"
    val contentLabel = if (isVideo) "video" else "imagen"
    val mediaType = if (isVideo) "video" else "image"
    val emptyIcon = if (isVideo) Res.drawable.video_travel_image_svgrepo_com else Res.drawable.images_regular_full
    val selectedFile = viewModel.selectedFile
    val selectedType = viewModel.selectedTypeFile ?: mediaType

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(0.72f)
                .fillMaxHeight()
                .background(AuraColors.Surface, shape = RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (listFiles.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 12.dp)
                            .verticalScroll(scrollState),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        listFiles.forEach { file ->
                            CardMultimedia(
                                file = file,
                                type = mediaType,
                                modifier = Modifier
                                    .width(220.dp)
                                    .height(150.dp),
                                isSelected = selectedFile?.absolutePath == file.absolutePath,
                                onSelected = { viewModel.selectFile(file, mediaType) },
                            )
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        adapter = rememberScrollbarAdapter(scrollState = scrollState),
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(emptyIcon),
                            contentDescription = "Sin contenido",
                            modifier = Modifier.size(64.dp),
                            colorFilter = ColorFilter.tint(AuraColors.TextMuted)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Aún no hay ${contentLabel}s importados",
                            color = AuraColors.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Importa un ${contentLabel} para empezar a personalizar tu pantalla de inicio.",
                            color = AuraColors.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AuraColors.SurfaceElevated)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { openFilePicker { archiveSelected -> viewModel.importNewFile(archiveSelected) } }
                    .padding(18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(Res.drawable.add_to_queue_svgrepo_com),
                    contentDescription = "Icono de agregar a la cola",
                    modifier = Modifier.size(34.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(AuraColors.Primary)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Importar $contentLabel",
                    color = AuraColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(0.28f)
                .fillMaxHeight()
                .background(AuraColors.Surface, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Fondo seleccionado",
                color = AuraColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (selectedFile != null) {
                CardMultimedia(
                    file = selectedFile,
                    type = selectedType,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    isSelected = true,
                    onSelected = {}
                )
                Text(
                    text = selectedFile.name,
                    color = AuraColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(AuraColors.SurfaceVariant, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(emptyIcon),
                            contentDescription = "Previsualización vacía",
                            modifier = Modifier.size(56.dp),
                            colorFilter = ColorFilter.tint(AuraColors.TextMuted)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Selecciona un archivo para verlo aquí",
                            color = AuraColors.TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    CustomAlertDialog(
        showDialog = viewModel.errorMessage != null,
        title = "Error al importar",
        message = viewModel.errorMessage.orEmpty(),
        onDismiss = { viewModel.clearError() }
    )
}
