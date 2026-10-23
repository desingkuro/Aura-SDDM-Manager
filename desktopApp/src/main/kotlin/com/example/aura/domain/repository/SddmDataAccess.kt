package com.example.aura.domain.repository

import com.example.aura.domain.models.BackgroundDestination
import com.example.aura.domain.models.RestoreBackupResult
import com.example.aura.domain.models.SddmBackup
import com.example.aura.domain.models.SddmTheme
import java.io.File

/**
 * Primitivas de acceso a archivos y configuraciones de SDDM que necesitan los
 * casos de uso. No contiene orquestación de negocio: solo resuelve rutas, lee,
 * escribe y ejecuta las operaciones atómicas/elevadas.
 */
interface SddmDataAccess {
    val themesDir: File
    val appliedBackgroundFile: File
    val backupsDir: File
    val backupManifestName: String

    fun getCurrentTheme(): String
    fun getInstalledThemes(): List<SddmTheme>
    fun saveAppliedBackground(file: File)

    fun resolveThemeDir(themeName: String): File?
    fun resolveThemeConfig(themeDir: File, rawPath: String): File
    fun requireInside(themeDir: File, candidate: File, description: String): File
    fun readConfiguredThemePath(themeDir: File): String?

    fun buildConfiguredThemePreview(
        content: String,
        backgroundName: String,
        destination: BackgroundDestination,
    ): String

    fun buildThemeConfPreview(
        content: String,
        fileName: String,
        backgroundsFolder: String,
    ): String

    fun applyDirectly(
        source: File,
        targetBackground: File,
        backgroundsDir: File,
        configFile: File,
        newConfigContent: String,
        backupDir: File,
    ): String?

    fun applyWithElevatedPrivileges(
        source: File,
        targetBackground: File,
        backgroundsDir: File,
        configFile: File,
        newConfigContent: String,
        backupDir: File,
    ): String?

    fun createBackup(
        themeName: String,
        themeDir: File,
        themeConfigFile: File,
        targetBackground: File,
    ): File

    fun createThemeBackup(
        previousTheme: String,
        newTheme: String,
        configFile: File,
    ): File

    fun themeConfigTarget(): File
    fun isConfigFileWritable(file: File): Boolean
    fun getPkexecPath(): String
    fun writeConfigAtomically(configFile: File, content: String)
    fun writeThemeConfigWithElevatedPrivileges(configFile: File, content: String): String?
    fun restoreThemeConfigDirectly(
        backupDir: File,
        configFile: File,
        configExisted: Boolean,
    ): String?
    fun isDirectChild(parent: File, child: File): Boolean
    fun readBackup(backupDir: File): SddmBackup?
    fun restoreThemeBackup(backupDir: File, backup: SddmBackup): RestoreBackupResult
    fun validateBackupPaths(
        themeDir: File,
        themeConfigFile: File,
        targetBackground: File,
    ): String?

    fun restoreWithElevatedPrivileges(
        backupDir: File,
        themeConfigFile: File,
        targetBackground: File,
        targetExisted: Boolean,
    ): String?

    fun restoreDirectly(
        backupDir: File,
        themeConfigFile: File,
        targetBackground: File,
        targetExisted: Boolean,
    ): String?

    fun restoreAppliedBackgroundFile(backupDir: File, existed: Boolean)
}
