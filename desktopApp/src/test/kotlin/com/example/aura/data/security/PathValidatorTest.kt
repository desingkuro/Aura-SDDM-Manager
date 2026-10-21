package com.example.aura.data.security

import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathValidatorTest {

    @Test
    fun `acepta configuracion y fondo dentro del tema`() {
        val env = Environment()
        try {
            val config = File(env.themeDir, "configs/default.conf").apply {
                parentFile.mkdirs()
                writeText("[General]\nbackground=wallpaper.jpg\n")
            }
            val background = File(env.themeDir, "backgrounds/wallpaper.jpg").apply {
                parentFile.mkdirs()
                writeText("bytes")
            }
            val validator = env.validator()

            assertEquals(config.canonicalFile, validator.resolveThemeConfig(env.themeDir, "configs/default.conf"))
            assertEquals(background.canonicalFile, validator.resolveBackground(env.themeDir, "wallpaper.jpg"))
        } finally {
            env.close()
        }
    }

    @Test
    fun `rechaza configuracion con path traversal`() {
        val env = Environment()
        try {
            val validator = env.validator()

            val error = assertFailsWith<PathValidationException> {
                validator.resolveThemeConfig(env.themeDir, "../../etc/shadow")
            }

            assertTrue(error.message.orEmpty().contains("fuera"))
        } finally {
            env.close()
        }
    }

    @Test
    fun `rechaza configuracion absoluta fuera del tema`() {
        val env = Environment()
        try {
            val validator = env.validator()

            assertFailsWith<PathValidationException> {
                validator.resolveThemeConfig(env.themeDir, "/etc/shadow")
            }
        } finally {
            env.close()
        }
    }

    @Test
    fun `rechaza symlink que sale del tema`() {
        val env = Environment()
        try {
            val outside = File(env.root, "outside.conf").apply {
                writeText("[Theme]\nCurrent=evil\n")
            }
            val link = File(env.themeDir, "linked.conf")
            Files.createSymbolicLink(link.toPath(), outside.toPath())

            assertFailsWith<PathValidationException> {
                env.validator().resolveThemeConfig(env.themeDir, "linked.conf")
            }
        } finally {
            env.close()
        }
    }

    @Test
    fun `rechaza symlink de tema que sale de la raiz de temas`() {
        val env = Environment()
        try {
            val outsideTheme = File(env.root, "outside-theme").apply { mkdirs() }
            File(outsideTheme, "theme.conf").writeText("[General]\nbackground=x.jpg\n")
            Files.createSymbolicLink(
                File(env.themesDir, "evil").toPath(),
                outsideTheme.toPath(),
            )

            assertNull(env.validator().resolveThemeDir("evil"))
        } finally {
            env.close()
        }
    }

    @Test
    fun `rechaza nombre de tema con traversal`() {
        val env = Environment()
        try {
            val validator = env.validator()

            assertNull(validator.resolveThemeDir(".."))
            assertNull(validator.resolveThemeDir("../etc"))
            assertNull(validator.resolveThemeDir("/etc"))
        } finally {
            env.close()
        }
    }

    @Test
    fun `rechaza fondo absoluto fuera del tema`() {
        val env = Environment()
        try {
            assertFailsWith<PathValidationException> {
                env.validator().resolveBackground(env.themeDir, "/etc/passwd")
            }
        } finally {
            env.close()
        }
    }

    @Test
    fun `solo permite escribir configuraciones conocidas de sddm`() {
        val env = Environment()
        try {
            val validator = env.validator()

            assertEquals(
                env.sddmConf.canonicalFile,
                validator.resolveSddmConfig(env.sddmConf),
            )
            assertEquals(
                File(env.sddmConfDir, "99-aura-theme.conf").canonicalFile,
                validator.resolveSddmConfig(File(env.sddmConfDir, "99-aura-theme.conf")),
            )
            assertFailsWith<PathValidationException> {
                validator.resolveSddmConfig(File(env.root, "otro.conf"))
            }
        } finally {
            env.close()
        }
    }

    private class Environment {
        val root: File = Files.createTempDirectory("aura-path-validator-").toFile()
        val themesDir: File = File(root, "themes").apply { mkdirs() }
        val themeDir: File = File(themesDir, "silent").apply { mkdirs() }
        val sddmConf: File = File(root, "sddm.conf").apply { writeText("[Theme]\nCurrent=silent\n") }
        val sddmConfDir: File = File(root, "sddm.conf.d").apply { mkdirs() }

        fun validator(): PathValidator = PathValidator(themesDir, sddmConf, sddmConfDir)

        fun close() {
            root.deleteRecursively()
        }
    }
}