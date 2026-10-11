package com.example.aura

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.example.aura.presentation.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Aura",
    ) {
        App()
    }
}