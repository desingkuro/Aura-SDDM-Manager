package com.example.aura.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.domain.models.SddmBackup
import com.example.aura.presentation.ui.components.CustomAlertDialog
import com.example.aura.presentation.ui.components.HeaderScreen
import com.example.aura.presentation.ui.theme.AuraColors
import com.example.aura.presentation.viewmodels.SettingsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val backupDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    .withZone(ZoneId.systemDefault())

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel = remember { SettingsViewModel() }

    LaunchedEffect(Unit) {
        viewModel.loadBackups()
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HEADER
        HeaderScreen(
            title = "Ajustes avanzados",
            subtitle = "Configura las opciones de Aura y de tu tema de SDDM."
        )

        Spacer(modifier = Modifier.height(16.dp))

        // RESPALDOS
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(AuraColors.Surface, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Respaldos de configuración",
                        color = AuraColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Se crea uno automáticamente antes de aplicar un fondo.",
                        color = AuraColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { viewModel.loadBackups() },
                    enabled = !viewModel.isRestoring,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AuraColors.SurfaceElevated,
                        contentColor = AuraColors.TextPrimary,
                        disabledBackgroundColor = AuraColors.SurfaceVariant,
                        disabledContentColor = AuraColors.TextMuted
                    )
                ) {
                    Text("Actualizar", fontSize = 13.sp)
                }
            }

            if (viewModel.isRestoring) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Restaurando respaldo...",
                    color = AuraColors.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (viewModel.backups.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Aún no hay respaldos",
                            color = AuraColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Se creará uno automáticamente la próxima vez que apliques un fondo.",
                            color = AuraColors.TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        viewModel.backups.forEach { backup ->
                            BackupRow(
                                backup = backup,
                                enabled = !viewModel.isRestoring,
                                onRestore = { viewModel.requestRestore(backup) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Carpeta: ~/.local/share/Aura/backups",
                color = AuraColors.TextMuted,
                fontSize = 11.sp
            )
        }
    }

    val backupToRestore = viewModel.selectedBackup

    CustomAlertDialog(
        showDialog = viewModel.showRestoreDialog && backupToRestore != null,
        title = "Restaurar respaldo",
        message = if (backupToRestore == null) {
            ""
        } else {
            buildString {
                append("Se restaurará la configuración del tema \"${backupToRestore.themeName}\"")
                if (backupToRestore.backgroundName.isNotBlank()) {
                    append(" al estado previo a aplicar \"${backupToRestore.backgroundName}\"")
                }
                append(".")

                if (backupToRestore.targetBackgroundExisted) {
                    append("\n\nTambién se recuperará el archivo de fondo anterior.")
                } else {
                    append("\n\nEl fondo nuevo agregado por ese cambio se eliminará.")
                }

                append("\n\nSe solicitarán permisos de administrador si es necesario. ¿Deseas continuar?")
            }
        },
        confirmText = "Restaurar",
        onDismiss = { viewModel.cancelRestore() },
        onConfirm = { viewModel.confirmRestore() }
    )

    CustomAlertDialog(
        showDialog = viewModel.restoreMessage != null,
        title = if (viewModel.restoreSucceeded) "Respaldo restaurado" else "No se pudo restaurar",
        message = viewModel.restoreMessage.orEmpty(),
        onDismiss = { viewModel.clearRestoreMessage() }
    )
}

@Composable
private fun BackupRow(
    backup: SddmBackup,
    enabled: Boolean,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuraColors.SurfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatBackupDate(backup.createdAt),
                color = AuraColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Tema: ${backup.themeName.ifBlank { "desconocido" }} · " +
                    "Antes de aplicar: ${backup.backgroundName.ifBlank { "sin fondo" }}",
                color = AuraColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Button(
            onClick = onRestore,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AuraColors.Primary,
                contentColor = AuraColors.Background,
                disabledBackgroundColor = AuraColors.SurfaceElevated,
                disabledContentColor = AuraColors.TextMuted
            )
        ) {
            Text("Restaurar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatBackupDate(createdAt: Instant): String =
    backupDateFormatter.format(createdAt)
