package com.example.aura.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import aura.desktopapp.generated.resources.Res
import aura.desktopapp.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource

@Composable
fun CardMultimedia(){
    Box(
        modifier = Modifier
            .height(190.dp)
            .width(250.dp)
            .border(width = 2.dp, color = Color.Gray, shape = RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ){
        Image(
            painter = painterResource(resource = Res.drawable.compose_multiplatform),
            contentDescription = "Multiplatform image",
        )
    }
}