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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aura.domain.models.DiagnosticStatus
import com.example.aura.domain.models.SddmBackup
import com.example.aura.domain.models.SddmBackupKind
import com.example.aura.domain.models.SddmDiagnostic
import com.example.aura.domain.models.SddmTheme
import com.example.aura.presentation.ui.components.CustomAlertDialog
import com.example.aura.presentation.ui.components.HeaderScreen
import com.example.aura.presentation.ui.theme.AuraColors
import com.example.aura.presentation.viewmodels.SettingsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val backupDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    .withZone(ZoneId.systemDefault())
private val diagnosticsTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    .withZone(ZoneId.systemDefault())

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel = remember { SettingsViewModel() }
    val state by viewModel.uiState.collectAsState()

    DisposableEffect(viewModel) {
        onDispose { viewModel.dispose() }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderScreen(
            title = "Ajustes avanzados",
            subtitle = "Diagnóstico, tema de SDDM y respaldos de configuración."
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DiagnosticsCard(viewModel = viewModel)
                ThemesCard(viewModel = viewModel)
            }

            BackupsCard(
                viewModel = viewModel,
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxHeight(),
            )
        }
    }

    val backupToRestore = state.selectedBackup

    CustomAlertDialog(
        showDialog = state.showRestoreDialog && backupToRestore != null,
        title = "Restaurar respaldo",
        message = backupToRestore?.let(::buildRestoreDialogMessage).orEmpty(),
        confirmText = "Restaurar",
        onDismiss = { viewModel.cancelRestore() },
        onConfirm = { viewModel.confirmRestore() }
    )

    CustomAlertDialog(
        showDialog = state.restoreMessage != null,
        title = if (state.restoreSucceeded) "Respaldo restaurado" else "No se pudo restaurar",
        message = state.restoreMessage.orEmpty(),
        onDismiss = { viewModel.clearRestoreMessage() }
    )

    val themeToChange = state.selectedTheme

    CustomAlertDialog(
        showDialog = state.showThemeDialog && themeToChange != null,
        title = "Cambiar tema de SDDM",
        message = themeToChange?.let { theme ->
            buildString {
                append("Se cambiará el tema actual \"")
                append(state.currentTheme.ifBlank { "desconocido" })
                append("\" por \"${theme.displayName}\".")
                append("\n\nSe creará un respaldo automático de la configuración de SDDM ")
                append("antes del cambio. Se solicitarán permisos de administrador si es necesario.")
                append("\n\n¿Deseas continuar?")
            }
        }.orEmpty(),
        confirmText = "Cambiar tema",
        onDismiss = { viewModel.cancelThemeChange() },
        onConfirm = { viewModel.confirmThemeChange() }
    )

    CustomAlertDialog(
        showDialog = state.themeMessage != null,
        title = if (state.themeChangeSucceeded) "Tema cambiado" else "No se pudo cambiar el tema",
        message = state.themeMessage.orEmpty(),
        onDismiss = { viewModel.clearThemeMessage() }
    )
}

@Composable
private fun DiagnosticsCard(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuraColors.Surface, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Diagnóstico",
                    color = AuraColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Estado de SDDM y dependencias externas.",
                    color = AuraColors.TextSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = { viewModel.refresh() },
                enabled = !state.isRefreshing && !state.isChangingTheme &&
                    !state.isRestoring,
                colors = secondaryButtonColors()
            ) {
                Text(
                    text = if (state.isRefreshing) "Comprobando..." else "Actualizar",
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val diagnostics = state.diagnostics
        if (state.refreshError != null) {
            Text(
                text = state.refreshError.orEmpty(),
                color = AuraColors.Danger,
                fontSize = 12.sp
            )
        } else if (diagnostics == null) {
            Text(
                text = "Comprobando el entorno...",
                color = AuraColors.TextMuted,
                fontSize = 12.sp
            )
        } else {
            Text(
                text = "Actualizado a las ${diagnosticsTimeFormatter.format(diagnostics.checkedAt)}",
                color = AuraColors.TextMuted,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                diagnostics.items.forEach { item ->
                    DiagnosticRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(item: SddmDiagnostic) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(diagnosticColor(item.status))
        )

        Spacer(modifier = Modifier.width(9.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                color = AuraColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = item.value,
                color = AuraColors.TextPrimary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            item.hint?.let { hint ->
                Text(
                    text = hint,
                    color = AuraColors.TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ThemesCard(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuraColors.Surface, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text(
            text = "Tema de SDDM",
            color = AuraColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tema actual: ${state.currentTheme.ifBlank { "desconocido" }}",
            color = AuraColors.TextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (state.themes.isEmpty()) {
            Text(
                text = if (state.isRefreshing) {
                    "Cargando temas instalados..."
                } else {
                    "No se encontraron temas instalados en /usr/share/sddm/themes."
                },
                color = AuraColors.TextMuted,
                fontSize = 12.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.themes.forEach { theme ->
                    ThemeRow(
                        theme = theme,
                        enabled = !state.isChangingTheme && !state.isRestoring,
                        onChange = { viewModel.requestThemeChange(theme) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Los temas se leen de /usr/share/sddm/themes.",
            color = AuraColors.TextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ThemeRow(
    theme: SddmTheme,
    enabled: Boolean,
    onChange: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuraColors.SurfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.displayName,
                color = AuraColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = theme.name,
                color = AuraColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!theme.supportsSeparateScreens) {
                Text(
                    text = "Fondo general",
                    color = AuraColors.TextMuted,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (theme.isCurrent) {
            Text(
                text = "Actual",
                color = AuraColors.Primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Button(
                onClick = onChange,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AuraColors.Primary,
                    contentColor = AuraColors.Background,
                    disabledBackgroundColor = AuraColors.SurfaceElevated,
                    disabledContentColor = AuraColors.TextMuted
                )
            ) {
                Text("Usar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BackupsCard(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
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
                    text = "Se crean automáticamente antes de aplicar un fondo o cambiar de tema.",
                    color = AuraColors.TextSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = { viewModel.refresh() },
                enabled = !state.isRestoring && !state.isRefreshing,
                colors = secondaryButtonColors()
            ) {
                Text("Actualizar", fontSize = 13.sp)
            }
        }

        if (state.isRestoring) {
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
            if (state.backups.isEmpty()) {
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
                        text = "Se creará uno automáticamente la próxima vez que apliques un fondo o cambies de tema.",
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
                    state.backups.forEach { backup ->
                        BackupRow(
                            backup = backup,
                            enabled = !state.isRestoring,
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
                text = backupDescription(backup),
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

private fun backupDescription(backup: SddmBackup): String =
    if (backup.kind == SddmBackupKind.THEME) {
        "Cambio de tema: ${backup.themeName.ifBlank { "desconocido" }} → " +
            "${backup.newThemeName ?: "desconocido"}"
    } else {
        "Tema: ${backup.themeName.ifBlank { "desconocido" }} · " +
            "Antes de aplicar: ${backup.backgroundName.ifBlank { "sin fondo" }}"
    }

private fun buildRestoreDialogMessage(backup: SddmBackup): String = buildString {
    if (backup.kind == SddmBackupKind.THEME) {
        append("Se restaurará la configuración de SDDM para volver al tema ")
        append("\"${backup.themeName.ifBlank { "desconocido" }}\"")
        backup.newThemeName?.let { newTheme ->
            append(" (se había cambiado a \"$newTheme\")")
        }
        append(".\n\nTambién se recuperará el registro local del fondo aplicado, si existía.")
    } else {
        append("Se restaurará la configuración del tema \"${backup.themeName}\"")
        if (backup.backgroundName.isNotBlank()) {
            append(" al estado previo a aplicar \"${backup.backgroundName}\"")
        }
        append(".")

        if (backup.targetBackgroundExisted) {
            append("\n\nTambién se recuperará el archivo de fondo anterior.")
        } else {
            append("\n\nEl fondo nuevo agregado por ese cambio se eliminará.")
        }
    }

    append("\n\nSe solicitarán permisos de administrador si es necesario. ¿Deseas continuar?")
}

@Composable
private fun secondaryButtonColors() = ButtonDefaults.buttonColors(
    backgroundColor = AuraColors.SurfaceElevated,
    contentColor = AuraColors.TextPrimary,
    disabledBackgroundColor = AuraColors.SurfaceVariant,
    disabledContentColor = AuraColors.TextMuted
)

private fun diagnosticColor(status: DiagnosticStatus): Color = when (status) {
    DiagnosticStatus.OK -> AuraColors.Success
    DiagnosticStatus.WARNING -> AuraColors.Warning
    DiagnosticStatus.ERROR -> AuraColors.Danger
}

private fun formatBackupDate(createdAt: Instant): String =
    backupDateFormatter.format(createdAt)
