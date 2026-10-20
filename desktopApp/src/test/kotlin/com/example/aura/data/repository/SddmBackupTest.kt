package com.example.aura.data.repository

import com.example.aura.domain.models.ApplyBackgroundResult
import com.example.aura.domain.models.RestoreBackupResult
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SddmBackupTest {

    @Test
    fun `crea respaldo antes de aplicar y restaura el estado anterior`() {
        val env = TestEnvironment()
        try {
            env.source.writeText("new-bytes")

            val applyResult = env.repository.applyBackground(env.source)
            assertTrue(applyResult is ApplyBackgroundResult.Success)
            assertTrue(env.themeConfig.readText().contains("source.jpg"))
            env.assertNoAtomicTemps()
            env.assertConfigPermissions()

            val backups = env.repository.listBackups()
            assertEquals(1, backups.size)
            val backup = backups.single()
            assertEquals("silent", backup.themeName)
            assertEquals("source.jpg", backup.backgroundName)
            assertFalse(backup.targetBackgroundExisted)

            // Simular un estado posterior alterado antes de restaurar.
            env.themeConfig.writeText("broken")
            File(env.backgroundsDir, "source.jpg").delete()
            env.currentBg.writeText("broken-path")

            val restoreResult = env.repository.restoreBackup(backup.id)
            assertTrue(restoreResult is RestoreBackupResult.Success)
            assertTrue(env.themeConfig.readText().contains("old.jpg"))
            assertEquals(env.oldBackground.absolutePath, env.currentBg.readText())
            assertFalse(File(env.backgroundsDir, "source.jpg").exists())
            env.assertNoAtomicTemps()
            env.assertConfigPermissions()
        } finally {
            env.close()
        }
    }

    @Test
    fun `restaura los bytes del fondo cuando el archivo ya existia`() {
        val env = TestEnvironment()
        try {
            val replacement = File(env.root, "old.jpg").apply { writeText("new-bytes") }

            val applyResult = env.repository.applyBackground(replacement)
            assertTrue(applyResult is ApplyBackgroundResult.Success)
            assertEquals("new-bytes", env.oldBackground.readText())
            env.assertNoAtomicTemps()

            val backup = env.repository.listBackups().single()
            assertTrue(backup.targetBackgroundExisted)

            env.oldBackground.writeText("changed-after-apply")

            val restoreResult = env.repository.restoreBackup(backup.id)
            assertTrue(restoreResult is RestoreBackupResult.Success)
            assertEquals("old-bytes", env.oldBackground.readText())
            assertTrue(env.themeConfig.readText().contains("old.jpg"))
            env.assertNoAtomicTemps()
            env.assertConfigPermissions()
        } finally {
            env.close()
        }
    }

    @Test
    fun `respeta un symlink en la configuracion al escribir`() {
        val env = TestEnvironment()
        try {
            val realConfig = File(env.themeConfig.parentFile, "real.conf")
            Files.move(env.themeConfig.toPath(), realConfig.toPath())
            Files.createSymbolicLink(env.themeConfig.toPath(), realConfig.toPath())
            assertTrue(Files.isSymbolicLink(env.themeConfig.toPath()))

            env.source.writeText("new-bytes")
            val applyResult = env.repository.applyBackground(env.source)
            assertTrue(applyResult is ApplyBackgroundResult.Success)
            assertTrue(Files.isSymbolicLink(env.themeConfig.toPath()))
            assertTrue(realConfig.readText().contains("source.jpg"))
            env.assertNoAtomicTemps()
        } finally {
            env.close()
        }
    }

    @Test
    fun `ignora respaldos sin manifiesto completo`() {
        val env = TestEnvironment()
        try {
            val incomplete = File(env.backupsDir, "incomplete").apply { mkdirs() }
            File(incomplete, "theme-config").writeText("config")
            File(incomplete, "backup.properties.tmp").writeText("version=1")

            assertTrue(env.repository.listBackups().isEmpty())
        } finally {
            env.close()
        }
    }

    @Test
    fun `rechaza identificadores de respaldo con traversal`() {
        val env = TestEnvironment()
        try {
            val result = env.repository.restoreBackup("../etc")
            assertTrue(result is RestoreBackupResult.Failure)
        } finally {
            env.close()
        }
    }
}

private class TestEnvironment {
    val root: File = Files.createTempDirectory("aura-backup-test-").toFile()
    val themesDir: File = File(root, "themes").apply { mkdirs() }
    val themeDir: File = File(themesDir, "silent").apply { mkdirs() }
    val themeConfig: File = File(themeDir, "configs/default.conf")
    val backgroundsDir: File = File(themeDir, "backgrounds").apply { mkdirs() }
    val oldBackground: File = File(backgroundsDir, "old.jpg").apply { writeText("old-bytes") }
    val currentBg: File = File(root, "current_bg.txt").apply { writeText(oldBackground.absolutePath) }
    val backupsDir: File = File(root, "backups")
    val source: File = File(root, "source.jpg")
    val repository: SddmRepositoryImpl

    init {
        File(themeDir, "metadata.desktop").writeText(
            """
            [SddmGreeterTheme]
            Name=Silent
            ConfigFile=configs/default.conf
            """.trimIndent()
        )
        themeConfig.parentFile.mkdirs()
        themeConfig.writeText(
            """
            [LoginScreen]
            background = "old.jpg"
            use-background-color = false

            [LockScreen]
            background = "old.jpg"
            use-background-color = false
            """.trimIndent()
        )

        val sddmConf = File(root, "sddm.conf").apply {
            writeText("[Theme]\nCurrent=silent\n")
        }
        val sddmConfDir = File(root, "sddm.conf.d").apply { mkdirs() }

        repository = SddmRepositoryImpl(
            appliedBackgroundFile = currentBg,
            themesDir = themesDir,
            sddmConfFile = sddmConf,
            sddmConfDir = sddmConfDir,
            backupsDir = backupsDir,
        )
    }

    fun assertNoAtomicTemps() {
        val leftovers = themeConfig.parentFile.listFiles()
            ?.filter { it.name.startsWith(".${themeConfig.name}.aura-") }
            .orEmpty()
        assertTrue(leftovers.isEmpty(), "Temporales sin limpiar: ${leftovers.map { it.name }}")
    }

    fun assertConfigPermissions() {
        val permissions = Files.getPosixFilePermissions(themeConfig.toPath())
        assertEquals("rw-r--r--", PosixFilePermissions.toString(permissions))
    }

    fun close() {
        root.deleteRecursively()
    }
}
