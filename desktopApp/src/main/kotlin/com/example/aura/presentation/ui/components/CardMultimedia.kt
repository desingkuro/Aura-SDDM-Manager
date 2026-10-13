package com.example.aura.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import aura.desktopapp.generated.resources.Res
import aura.desktopapp.generated.resources.compose_multiplatform
import aura.desktopapp.generated.resources.video_travel_image_svgrepo_com
import com.example.aura.presentation.ui.theme.AuraColors
import com.example.aura.presentation.utils.fileToImageBitmap
import com.example.aura.presentation.utils.fileToVideoBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import java.io.File

@Composable
fun CardMultimedia(
    file: File? = null,
    type: String = "image",
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onSelected: () -> Unit = {},
) {
    val bitMap by produceState<ImageBitmap?>(initialValue = null, file, type) {
        value = withContext(Dispatchers.IO) {
            if (type == "image") fileToImageBitmap(file)
            else fileToVideoBitmap(file)
        }
    }

    val borderColor = if (isSelected) AuraColors.Primary else AuraColors.Border
    val placeholderIcon = if (type == "video") {
        Res.drawable.video_travel_image_svgrepo_com
    } else {
        Res.drawable.compose_multiplatform
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AuraColors.SurfaceVariant)
            .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onSelected),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitMap
        if (currentBitmap != null && file != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(placeholderIcon),
                contentDescription = "Contenido multimedia no disponible",
                modifier = Modifier.size(48.dp),
                colorFilter = ColorFilter.tint(AuraColors.TextMuted)
            )
        }

        if (type == "video") {
            Text(
                text = "VIDEO",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(AuraColors.Background.copy(alpha = 0.82f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = AuraColors.TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
