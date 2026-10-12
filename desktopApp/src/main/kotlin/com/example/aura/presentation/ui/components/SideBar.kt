package com.example.aura.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.aura.presentation.viewmodels.menuItems
import org.jetbrains.compose.resources.painterResource

@Composable
fun Sidebar(
    modifier: Modifier = Modifier,
    onNavigate: (next: String) -> Unit,
) { // 1. Recibe el modifier

    Column(
        modifier = modifier // 2. Usa el modifier del padre
            .fillMaxHeight() // Sigue llenando la altura
            .background(Color(0xFF222222))
            .padding(horizontal = 14.dp, vertical = 20.dp), // Añadí padding vertical para que no quede pegado arriba
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderSidebar()
        menuItems.forEach { item ->
            ItemSideBar(
                title = item.title,
                path = "Path",
                click = { onNavigate(item.path) },
                icon = painterResource(item.icon)
            )
        }
    }
}