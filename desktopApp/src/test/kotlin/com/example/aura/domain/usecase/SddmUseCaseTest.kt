package com.example.aura.domain.usecase

import com.example.aura.data.repository.SddmTestEnvironment
import com.example.aura.domain.models.BackgroundDestination
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SddmUseCaseTest {

    @Test
    fun `validate permissions rechaza un archivo que ya no existe`() {
        val env = SddmTestEnvironment()
        try {
            val useCase = ValidatePermissionsUseCase(env.repository)

            val error = useCase.validateSelectedFile(File(env.root, "missing.jpg"))

            assertNotNull(error)
            assertTrue(error.contains("ya no existe"))
        } finally {
            env.close()
        }
    }

    @Test
    fun `validate permissions distingue temas con y sin pantallas separadas`() {
        val env = SddmTestEnvironment()
        try {
            val useCase = ValidatePermissionsUseCase(env.repository)

            assertNull(
                useCase.supportsDestination(
                    BackgroundDestination.BOTH,
                    supportsSeparateScreens = false,
                )
            )
            assertNotNull(
                useCase.supportsDestination(
                    BackgroundDestination.LOGIN,
                    supportsSeparateScreens = false,
                )
            )
            assertNull(
                useCase.supportsDestination(
                    BackgroundDestination.LOGIN,
                    supportsSeparateScreens = true,
                )
            )
        } finally {
            env.close()
        }
    }

    @Test
    fun `create backup use case guarda configuracion y fondo existente`() {
        val env = SddmTestEnvironment()
        try {
            val target = File(env.backgroundsDir, "source.jpg").apply { writeText("old") }
            val useCase = CreateBackupUseCase(env.repository)

            val backupDir = useCase.createBackgroundBackup(
                themeName = "silent",
                themeDir = env.themeDir,
                themeConfigFile = env.themeConfig,
                targetBackground = target,
            )

            assertTrue(File(backupDir, "theme-config").isFile)
            assertTrue(File(backupDir, "background").isFile)
            assertEquals(1, env.repository.listBackups().size)
        } finally {
            env.close()
        }
    }

    @Test
    fun `create backup use case guarda la configuracion de sddm al cambiar tema`() {
        val env = SddmTestEnvironment()
        try {
            val useCase = CreateBackupUseCase(env.repository)

            val backupDir = useCase.createThemeBackup("silent", "traditional", env.sddmConf)

            assertTrue(File(backupDir, "sddm-config").isFile)
            assertTrue(File(backupDir, "backup.properties").isFile)
        } finally {
            env.close()
        }
    }
}
