package com.example.aura.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HeaderScreen(
    title: String,
) {
    Row (
        modifier = Modifier
            .height(80.dp)
            .fillMaxWidth()
            .padding(start = 20.dp),
    ){
        Box(
            modifier = Modifier
                .background(Color(0xFF222222), shape = RoundedCornerShape(25))
                .padding(all = 10.dp)
                .fillMaxSize()
            ,

            ){
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = title,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}