package com.example.aura.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import aura.desktopapp.generated.resources.Res
import aura.desktopapp.generated.resources.compose_multiplatform
import com.example.aura.presentation.utils.fileToImageBitmap
import org.jetbrains.compose.resources.painterResource
import java.io.File
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun CardMultimedia(
    file: File? = null,
    type: String = "image",
){
    // 1. Intentamos cargar la imagen
    val bitMap: ImageBitmap? = remember(file) {
        fileToImageBitmap(file)
    }

    Box(
        modifier = Modifier
            .height(190.dp)
            .fillMaxWidth(.3f)
            .border(width = 2.dp, color = Color.Gray, shape = RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ){
        if(bitMap != null && file != null){
            Image(
                bitmap = bitMap,
                contentDescription = file.name,
            )
        }else{
            Image(
                painter = painterResource(resource = Res.drawable.compose_multiplatform),
                contentDescription = "Multiplatform image",
            )
        }
    }
}