package com.example.aura.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import aura.desktopapp.generated.resources.Res
import aura.desktopapp.generated.resources.compose_multiplatform
import aura.desktopapp.generated.resources.video_travel_image_svgrepo_com
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
    onSelected: () -> Unit,
){
    // 1. Intentamos cargar la imagen
    val bitMap by produceState<ImageBitmap?>(initialValue = null, file, type) {
        value = withContext(Dispatchers.IO) {
            if (type == "image") fileToImageBitmap(file)
            else fileToVideoBitmap(file)
        }
    }

    Box(
        modifier = Modifier
            .height(190.dp)
            .fillMaxWidth(.3f)
            .border(width = 2.dp, color = Color.Gray, shape = RoundedCornerShape(8.dp))
            .clickable {onSelected()},
        contentAlignment = Alignment.Center
    ){
        if (bitMap != null && file != null) {
            Image(
                bitmap = bitMap!!,
                contentDescription = file.name
            )
        } else {
            Image(
                painter = painterResource(Res.drawable.video_travel_image_svgrepo_com),
                contentDescription = "Video sin miniatura"
            )
        }
    }
}