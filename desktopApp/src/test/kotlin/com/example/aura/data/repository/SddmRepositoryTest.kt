package com.example.aura.data.repository

import com.example.aura.domain.models.ApplyBackgroundResult
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SddmRepositoryTest {

    @Test
    fun `lee el tema actual desde sddm conf`() {
        val env = SddmTestEnvironment()
        try {
            env.sddmConf.writeText("[Theme]\nCurrent = \"silent\"\n")

            assertEquals("silent", env.repository.getCurrentTheme())
        } finally {
            env.close()
        }
    }

    @Test
    fun `sddm conf d sobreescribe el tema de sddm conf`() {
        val env = SddmTestEnvironment()
        try {
            env.sddmConf.writeText("[Theme]\nCurrent=silent\n")
            File(env.sddmConfDir, "10-theme.conf").writeText("[Theme]\nCurrent=other\n")

            assertEquals("other", env.repository.getCurrentTheme())
        } finally {
            env.close()
        }
    }

    @Test
    fun `usa breeze cuando no hay tema configurado`() {
        val env = SddmTestEnvironment()
        try {
            env.sddmConf.writeText("[Theme]\n")

            assertEquals("breeze", env.repository.getCurrentTheme())
        } finally {
            env.close()
        }
    }

    @Test
    fun `aplica el fondo a login y bloqueo en un tema con ConfigFile`() {
        val env = SddmTestEnvironment()
        try {
            env.source.writeText("new-bytes")

            val result = env.repository.applyBackground(env.source)

            assertTrue(result is ApplyBackgroundResult.Success)
            assertEquals("source.jpg", result.backgroundName)
            assertEquals("silent", result.themeName)
            assertEquals(env.source.absolutePath, env.currentBg.readText())
            assertTrue(File(env.backgroundsDir, "source.jpg").isFile)

            val config = env.themeConfig.readText()
            assertTrue(config.contains("[LoginScreen]"))
            assertTrue(config.contains("[LockScreen]"))
            assertEquals(2, Regex("""background\s*=\s*"source\.jpg"""").findAll(config).count())
            assertEquals(2, Regex("use-background-color\\s*=\\s*false").findAll(config).count())
        } finally {
            env.close()
        }
    }

    @Test
    fun `agrega las secciones de login y bloqueo cuando faltan`() {
        val env = SddmTestEnvironment()
        try {
            env.themeConfig.writeText("[General]\nfoo=bar\n")
            env.source.writeText("new-bytes")

            val result = env.repository.applyBackground(env.source)

            assertTrue(result is ApplyBackgroundResult.Success)
            val config = env.themeConfig.readText()
            assertTrue(config.contains("[LoginScreen]"))
            assertTrue(config.contains("[LockScreen]"))
            assertEquals(2, Regex("""background\s*=\s*"source\.jpg"""").findAll(config).count())
        } finally {
            env.close()
        }
    }

    @Test
    fun `aplica el fondo a un tema tradicional con theme conf`() {
        val env = SddmTestEnvironment()
        try {
            val traditionalThemeDir = env.createTraditionalTheme()
            env.source.writeText("new-bytes")

            val result = env.repository.applyBackground(env.source)

            assertTrue(result is ApplyBackgroundResult.Success)
            assertEquals("traditional", result.themeName)
            assertTrue(File(traditionalThemeDir, "backgrounds/source.jpg").isFile)
            assertTrue(
                File(traditionalThemeDir, "theme.conf").readText()
                    .contains("background=backgrounds/source.jpg")
            )
            assertEquals("traditional", env.repository.listBackups().single().themeName)
        } finally {
            env.close()
        }
    }

    @Test
    fun `conserva nombres de archivo con espacios y acentos`() {
        val env = SddmTestEnvironment()
        try {
            val source = File(env.root, "fondo de prueba ñ.jpg").apply { writeText("new-bytes") }

            val result = env.repository.applyBackground(source)

            assertTrue(result is ApplyBackgroundResult.Success)
            assertEquals("fondo de prueba ñ.jpg", result.backgroundName)
            assertTrue(File(env.backgroundsDir, "fondo de prueba ñ.jpg").isFile)
            assertTrue(
                env.themeConfig.readText()
                    .contains("""background = "fondo de prueba ñ.jpg"""")
            )
        } finally {
            env.close()
        }
    }

    @Test
    fun `detecta un video desde el registro de fondo aplicado`() {
        val env = SddmTestEnvironment()
        try {
            val video = File(env.root, "clip.mp4").apply { writeText("video-bytes") }
            env.currentBg.writeText(video.absolutePath)

            val applied = env.repository.getAppliedBackground()

            assertNotNull(applied)
            assertEquals(video.absolutePath, applied.first.absolutePath)
            assertEquals("video", applied.second)
        } finally {
            env.close()
        }
    }

    @Test
    fun `detecta el fondo desde ConfigFile si no hay registro local`() {
        val env = SddmTestEnvironment()
        try {
            env.currentBg.delete()

            val applied = env.repository.getAppliedBackground()

            assertNotNull(applied)
            assertEquals(env.oldBackground.absolutePath, applied.first.absolutePath)
            assertEquals("image", applied.second)
        } finally {
            env.close()
        }
    }

    @Test
    fun `detecta el fondo desde theme conf en un tema tradicional`() {
        val env = SddmTestEnvironment()
        try {
            env.createTraditionalTheme(backgroundName = "old.jpg")
            env.currentBg.delete()

            val applied = env.repository.getAppliedBackground()

            assertNotNull(applied)
            assertEquals("old.jpg", applied.first.name)
            assertEquals("image", applied.second)
        } finally {
            env.close()
        }
    }

    @Test
    fun `devuelve null cuando no hay fondo configurado`() {
        val env = SddmTestEnvironment()
        try {
            env.currentBg.delete()
            env.themeConfig.writeText("[General]\nbackground=#000000\n")

            assertNull(env.repository.getAppliedBackground())
        } finally {
            env.close()
        }
    }

    @Test
    fun `falla si el tema actual no existe`() {
        val env = SddmTestEnvironment()
        try {
            env.source.writeText("new-bytes")
            env.sddmConf.writeText("[Theme]\nCurrent=missing\n")

            val result = env.repository.applyBackground(env.source)

            assertTrue(result is ApplyBackgroundResult.Failure)
            assertTrue(result.message.contains("No se encontró el tema"))
        } finally {
            env.close()
        }
    }

    @Test
    fun `falla si falta la configuracion del tema`() {
        val env = SddmTestEnvironment()
        try {
            val brokenThemeDir = File(env.themesDir, "broken").apply { mkdirs() }
            File(brokenThemeDir, "metadata.desktop").writeText(
                "[SddmGreeterTheme]\nConfigFile=configs/missing.conf\n"
            )
            env.sddmConf.writeText("[Theme]\nCurrent=broken\n")
            env.source.writeText("new-bytes")

            val result = env.repository.applyBackground(env.source)

            assertTrue(result is ApplyBackgroundResult.Failure)
            assertTrue(result.message.contains("No se encontró el archivo de configuración"))
        } finally {
            env.close()
        }
    }

    @Test
    fun `falla si el archivo seleccionado ya no existe`() {
        val env = SddmTestEnvironment()
        try {
            val missing = File(env.root, "missing.jpg")

            val result = env.repository.applyBackground(missing)

            assertTrue(result is ApplyBackgroundResult.Failure)
            assertTrue(result.message.contains("ya no existe"))
        } finally {
            env.close()
        }
    }
}
