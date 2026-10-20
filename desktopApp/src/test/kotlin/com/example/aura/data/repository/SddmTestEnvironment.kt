package com.example.aura.data.repository

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Entorno temporal compartido por las pruebas del repositorio SDDM.
 *
 * Por defecto prepara un tema tipo SilentSDDM con `metadata.desktop` y dos
 * pantallas configuradas (login y bloqueo), además de una configuración de SDDM
 * que lo marca como tema actual.
 */
internal class SddmTestEnvironment {
    val root: File = Files.createTempDirectory("aura-backup-test-").toFile()
    val themesDir: File = File(root, "themes").apply { mkdirs() }
    val themeDir: File = File(themesDir, "silent").apply { mkdirs() }
    val themeConfig: File = File(themeDir, "configs/default.conf")
    val backgroundsDir: File = File(themeDir, "backgrounds").apply { mkdirs() }
    val oldBackground: File = File(backgroundsDir, "old.jpg").apply { writeText("old-bytes") }
    val currentBg: File = File(root, "current_bg.txt").apply { writeText(oldBackground.absolutePath) }
    val backupsDir: File = File(root, "backups")
    val source: File = File(root, "source.jpg")
    val sddmConf: File = File(root, "sddm.conf").apply {
        writeText("[Theme]\nCurrent=silent\n")
    }
    val sddmConfDir: File = File(root, "sddm.conf.d").apply { mkdirs() }
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

        repository = SddmRepositoryImpl(
            appliedBackgroundFile = currentBg,
            themesDir = themesDir,
            sddmConfFile = sddmConf,
            sddmConfDir = sddmConfDir,
            backupsDir = backupsDir,
        )
    }

    /**
     * Crea un tema tradicional (solo `theme.conf`) y lo marca como tema actual.
     */
    fun createTraditionalTheme(
        name: String = "traditional",
        backgroundName: String = "old.jpg",
    ): File {
        val traditionalThemeDir = File(themesDir, name).apply { mkdirs() }
        val traditionalBackgroundsDir = File(traditionalThemeDir, "backgrounds").apply { mkdirs() }
        File(traditionalBackgroundsDir, backgroundName).writeText("old-bytes")
        File(traditionalThemeDir, "theme.conf").writeText(
            "[General]\nbackground=$backgroundName\n"
        )
        sddmConf.writeText("[Theme]\nCurrent=$name\n")
        return traditionalThemeDir
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
