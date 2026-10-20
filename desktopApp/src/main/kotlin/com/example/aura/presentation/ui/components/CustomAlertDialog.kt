package com.example.aura.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CustomAlertDialog(
    showDialog: Boolean,           // Controla si se muestra o no
    title: String,                 // Título dinámico
    message: String,               // Mensaje dinámico
    confirmText: String = "Aceptar", // Texto del botón (con valor por defecto)
    onDismiss: () -> Unit,         // Acción al cerrar (clic fuera o en botón)
    onConfirm: () -> Unit = onDismiss, // Acción del botón (por defecto, solo cierra)
    content: (@Composable () -> Unit)? = null, // Contenido adicional opcional
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(text = title, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(text = message)
                    if (content != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        content()
                    }
                }
            },
            confirmButton = {
                Button(onClick = onConfirm) {
                    Text(confirmText)
                }
            }
        )
    }
}