package com.example.aura.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aura.presentation.ui.theme.AuraColors
import com.example.aura.presentation.viewmodels.menuItems
import org.jetbrains.compose.resources.painterResource

@Composable
fun Sidebar(
    modifier: Modifier = Modifier,
    currentRoute: String,
    onNavigate: (next: String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(AuraColors.Surface)
            .padding(horizontal = 14.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeaderSidebar()
        menuItems.forEach { item ->
            ItemSideBar(
                title = item.title,
                path = item.path,
                isSelected = item.path == currentRoute,
                click = onNavigate,
                icon = painterResource(item.icon)
            )
        }
    }
}