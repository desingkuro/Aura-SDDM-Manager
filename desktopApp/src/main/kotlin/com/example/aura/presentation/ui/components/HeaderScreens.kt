package com.example.aura.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.presentation.ui.theme.AuraColors

@Composable
fun HeaderScreen(
    title: String,
    subtitle: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuraColors.Surface, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 22.sp,
                color = AuraColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            subtitle?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 13.sp,
                    color = AuraColors.TextSecondary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
