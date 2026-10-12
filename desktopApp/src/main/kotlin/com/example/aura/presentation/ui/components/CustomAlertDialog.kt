package com.example.aura.presentation.ui.components

import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
fun CustomAlertDialog(
    showDialog: Boolean,           // Controla si se muestra o no
    title: String,                 // Título dinámico
    message: String,               // Mensaje dinámico
    confirmText: String = "Aceptar", // Texto del botón (con valor por defecto)
    onDismiss: () -> Unit,         // Acción al cerrar (clic fuera o en botón)
    onConfirm: () -> Unit = onDismiss // Acción del botón (por defecto, solo cierra)
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(text = title, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = message)
            },
            confirmButton = {
                Button(onClick = onConfirm) {
                    Text(confirmText)
                }
            }
        )
    }
}