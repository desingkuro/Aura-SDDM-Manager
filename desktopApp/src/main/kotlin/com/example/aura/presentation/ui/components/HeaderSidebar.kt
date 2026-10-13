package com.example.aura.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import aura.desktopapp.generated.resources.Res
import aura.desktopapp.generated.resources.logo
import com.example.aura.presentation.ui.theme.AuraColors
import org.jetbrains.compose.resources.painterResource

@Composable
fun HeaderSidebar() {
    Row  (
        modifier = Modifier
            .wrapContentSize()
            .fillMaxWidth()
            .height(70.dp)
            .padding(start = 16.dp)
        ,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ){
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(40.dp),
            contentAlignment = Alignment.Center // Centra la imagen perfectamente en la caja
        ) {
            Image(
                painter = painterResource(
                    resource = Res.drawable.logo
                ), // Carga tu imagen real aquí
                contentDescription = "logotipo",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), // Obliga a la imagen a respetar los 40x40dp del Box
                contentScale = ContentScale.Fit // Mantiene las proporciones originales para que no se estire
            )
        }
        Text("Aura SDDM Manager",
            color = AuraColors.TextPrimary,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
    }
}