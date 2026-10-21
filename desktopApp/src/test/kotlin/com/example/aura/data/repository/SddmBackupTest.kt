package com.example.aura.data.repository

import com.example.aura.domain.models.ApplyBackgroundResult
import com.example.aura.domain.models.RestoreBackupResult
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SddmBackupTest {

    @Test
    fun `crea respaldo antes de aplicar y restaura el estado anterior`() {
        val env = SddmTestEnvironment()
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
        val env = SddmTestEnvironment()
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
        val env = SddmTestEnvironment()
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
        val env = SddmTestEnvironment()
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
        val env = SddmTestEnvironment()
        try {
            val result = env.repository.restoreBackup("../etc")
            assertTrue(result is RestoreBackupResult.Failure)
        } finally {
            env.close()
        }
    }

    @Test
    fun `ignora respaldos que son symlinks fuera de la carpeta`() {
        val env = SddmTestEnvironment()
        try {
            val outside = File(env.root, "outside-backup").apply { mkdirs() }
            File(outside, "backup.properties").writeText("version=1\nkind=theme\n")
            Files.createDirectories(env.backupsDir.toPath())
            Files.createSymbolicLink(
                File(env.backupsDir, "evil").toPath(),
                outside.toPath(),
            )

            assertTrue(env.repository.listBackups().isEmpty())
            val result = env.repository.restoreBackup("evil")
            assertTrue(result is RestoreBackupResult.Failure)
        } finally {
            env.close()
        }
    }
}

