package com.example.aura.data.repository

import com.example.aura.domain.models.ChangeThemeResult
import com.example.aura.domain.models.DiagnosticStatus
import com.example.aura.domain.models.RestoreBackupResult
import com.example.aura.domain.models.SddmBackupKind
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SddmThemeTest {

    @Test
    fun `lista los temas instalados y marca el actual`() {
        val env = SddmTestEnvironment()
        try {
            env.createTraditionalTheme(name = "traditional", makeCurrent = false)

            val themes = env.repository.getInstalledThemes()

            assertEquals(2, themes.size)
            val current = themes.first()
            assertEquals("silent", current.name)
            assertEquals("Silent", current.displayName)
            assertTrue(current.isCurrent)
            assertTrue(current.supportsSeparateScreens)
            assertEquals("traditional", themes.last().name)
            assertFalse(themes.last().supportsSeparateScreens)
        } finally {
            env.close()
        }
    }

    @Test
    fun `el diagnostico incluye tema rutas y dependencias`() {
        val env = SddmTestEnvironment()
        try {
            val diagnostics = env.repository.getDiagnostics()
            val labels = diagnostics.items.map { it.label }

            assertTrue(labels.contains("Tema actual"))
            assertTrue(labels.contains("Configuración del tema"))
            assertTrue(labels.contains("Greeter SDDM"))
            assertTrue(labels.contains("pkexec"))
            assertTrue(labels.contains("Agente Polkit"))
            assertTrue(labels.contains("FFmpeg"))
            assertTrue(labels.contains("Carpeta de temas"))
            assertTrue(labels.contains("Carpeta de fondos"))
            assertTrue(labels.contains("Respaldos"))

            val currentTheme = diagnostics.items.first { it.label == "Tema actual" }
            assertEquals("silent", currentTheme.value)
            assertEquals(DiagnosticStatus.OK, currentTheme.status)

            val themeConfig = diagnostics.items.first { it.label == "Configuración del tema" }
            assertEquals(env.themeConfig.absolutePath, themeConfig.value)
            assertEquals(DiagnosticStatus.OK, themeConfig.status)
        } finally {
            env.close()
        }
    }

    @Test
    fun `cambia el tema con respaldo y lo restaura`() {
        val env = SddmTestEnvironment()
        try {
            env.createTraditionalTheme(name = "traditional", makeCurrent = false)

            val result = env.repository.changeTheme("traditional")

            assertTrue(result is ChangeThemeResult.Success)
            assertEquals("silent", result.previousTheme)
            assertEquals("traditional", result.theme.name)
            assertEquals("traditional", env.repository.getCurrentTheme())

            val dropIn = File(env.sddmConfDir, "99-aura-theme.conf")
            assertTrue(dropIn.isFile)
            assertTrue(dropIn.readText().contains("Current=traditional"))
            assertFalse(env.currentBg.exists())

            val backup = env.repository.listBackups().single()
            assertEquals(SddmBackupKind.THEME, backup.kind)
            assertEquals("silent", backup.themeName)
            assertEquals("traditional", backup.newThemeName)

            val restoreResult = env.repository.restoreBackup(backup.id)

            assertTrue(restoreResult is RestoreBackupResult.Success)
            assertEquals("silent", env.repository.getCurrentTheme())
            assertFalse(dropIn.exists())
            assertEquals(env.oldBackground.absolutePath, env.currentBg.readText())
        } finally {
            env.close()
        }
    }

    @Test
    fun `cambia el tema en sddm conf cuando no hay sddm conf d`() {
        val env = SddmTestEnvironment()
        try {
            env.sddmConfDir.deleteRecursively()
            env.createTraditionalTheme(name = "traditional", makeCurrent = false)

            val result = env.repository.changeTheme("traditional")

            assertTrue(result is ChangeThemeResult.Success)
            assertEquals("traditional", env.repository.getCurrentTheme())
            assertTrue(env.sddmConf.readText().contains("Current=traditional"))

            val backup = env.repository.listBackups().single()
            val restoreResult = env.repository.restoreBackup(backup.id)

            assertTrue(restoreResult is RestoreBackupResult.Success)
            assertEquals("silent", env.repository.getCurrentTheme())
            assertTrue(env.sddmConf.readText().contains("Current=silent"))
        } finally {
            env.close()
        }
    }

    @Test
    fun `no crea respaldo si el tema ya es el actual`() {
        val env = SddmTestEnvironment()
        try {
            val result = env.repository.changeTheme("silent")

            assertTrue(result is ChangeThemeResult.Success)
            assertTrue(env.repository.listBackups().isEmpty())
        } finally {
            env.close()
        }
    }

    @Test
    fun `rechaza temas inexistentes y rutas fuera del directorio`() {
        val env = SddmTestEnvironment()
        try {
            assertTrue(env.repository.changeTheme("missing") is ChangeThemeResult.Failure)
            assertTrue(env.repository.changeTheme("../etc") is ChangeThemeResult.Failure)
        } finally {
            env.close()
        }
    }

    @Test
    fun `rechaza restaurar un tema fuera de las rutas permitidas`() {
        val env = SddmTestEnvironment()
        try {
            val backupDir = File(env.backupsDir, "tema-manipulado").apply { mkdirs() }
            File(backupDir, "sddm-config").writeText("[Theme]\nCurrent=malicious\n")
            File(backupDir, "backup.properties").writeText(
                """
                version=1
                kind=theme
                created_at=2026-01-01T00:00:00Z
                previous_theme=silent
                new_theme=traditional
                sddm_config=/etc/passwd
                sddm_config_existed=true
                current_bg_existed=false
                """.trimIndent()
            )

            val result = env.repository.restoreBackup("tema-manipulado")

            assertTrue(result is RestoreBackupResult.Failure)
            assertTrue(result.message.contains("fuera de las rutas permitidas"))
        } finally {
            env.close()
        }
    }

    @Test
    fun `el respaldo de tema contiene el registro del fondo anterior`() {
        val env = SddmTestEnvironment()
        try {
            env.createTraditionalTheme(name = "traditional", makeCurrent = false)

            env.repository.changeTheme("traditional")

            val backup = env.repository.listBackups().single()
            val backupDir = File(env.backupsDir, backup.id)
            assertNotNull(File(backupDir, "current-bg.txt").takeIf { it.isFile })
            assertFalse(File(backupDir, "sddm-config").exists())
        } finally {
            env.close()
        }
    }
}
