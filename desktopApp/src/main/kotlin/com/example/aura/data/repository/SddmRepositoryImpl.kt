package com.example.aura.data.repository

import com.example.aura.domain.repository.SddmRepository
import java.io.File
import kotlin.concurrent.thread

class SddmRepositoryImpl(
    private val configFile: File = File(System.getProperty("user.home"), ".local/share/Aura/current_bg.txt"),
    private val themesDir: File = File("/usr/share/sddm/themes"),
    private val sddmConfFile: File = File("/etc/sddm.conf"),
    private val sddmConfDir: File = File("/etc/sddm.conf.d"),
) : SddmRepository {

    private val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm")

    override fun getCurrentTheme(): String {
        val themeRegex = """(?i)^\s*Current\s*=\s*["']?([^"'\s#]+)["']?""".toRegex()
        var currentTheme = ""

        try {
            readSddmConfigFiles().forEach { file ->
                file.useLines { lines ->
                    lines.forEach { line ->
                        themeRegex.find(line)?.let { match ->
                            currentTheme = match.groupValues[1].trim()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error al leer la configuración de SDDM: ${e.message}")
        }

        return currentTheme.ifBlank { "breeze" }
    }

    override fun getAppliedBackground(): Pair<File, String>? {
        if (configFile.isFile) {
            val savedPath = configFile.readText().trim().trim('"', '\'')
            if (savedPath.isNotEmpty()) {
                val savedFile = File(savedPath)
                if (savedFile.isFile) {
                    return Pair(savedFile, determineMediaType(savedFile))
                }
            }
        }

        return readBackgroundFromTheme(getCurrentTheme())
    }

    override fun saveAppliedBackground(file: File) {
        configFile.parentFile?.mkdirs()
        configFile.writeText(file.absolutePath)
    }

    override fun getGreeterPath(): String {
        val possiblePaths = listOf(
            "/usr/bin/sddm-greeter-qt6",
            "/usr/bin/sddm-greeter",
            "/usr/libexec/sddm-greeter-qt6",
            "/usr/libexec/sddm-greeter",
            "/usr/lib/sddm/sddm-greeter",
            "/usr/lib/x86_64-linux-gnu/sddm/sddm-greeter"
        )
        return possiblePaths.firstOrNull { File(it).isFile } ?: ""
    }

    override fun launchPreview() {
        thread(isDaemon = true) {
            try {
                val greeterPath = getGreeterPath()
                if (greeterPath.isBlank()) {
                    println("Error: No se encontró sddm-greeter en el sistema.")
                    return@thread
                }

                val currentTheme = getCurrentTheme()
                val themePath = File(themesDir, currentTheme).absolutePath

                println("Lanzando previsualización con tema: $themePath")
                println("Ejecutando SDDM Greeter desde: $greeterPath")

                ProcessBuilder(greeterPath, "--test-mode", "--theme", themePath)
                    .inheritIO()
                    .start()
                    .waitFor()
            } catch (e: Exception) {
                println("Error al intentar abrir el preview de SDDM: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun readSddmConfigFiles(): List<File> {
        val files = mutableListOf<File>()

        if (sddmConfFile.isFile && sddmConfFile.canRead()) {
            files.add(sddmConfFile)
        }

        if (sddmConfDir.isDirectory) {
            sddmConfDir.listFiles()
                ?.filter { it.isFile && it.canRead() }
                ?.sortedBy { it.name }
                ?.let(files::addAll)
        }

        return files
    }

    private fun readBackgroundFromTheme(themeName: String): Pair<File, String>? {
        val themeDir = File(themesDir, themeName)
        if (!themeDir.isDirectory) return null

        val configuredFile = readConfiguredThemeFile(themeDir)
        if (configuredFile != null) {
            val config = readIniFile(configuredFile)
            val backgroundValue = listOf(
                "loginscreen/background",
                "lockscreen/background",
                "general/background",
                "background"
            ).firstNotNullOfOrNull { key -> config[key]?.takeIf { it.isNotBlank() } }

            resolveBackgroundFile(themeDir, backgroundValue)?.let { file ->
                return Pair(file, determineMediaType(file))
            }
        }

        val themeConf = File(themeDir, "theme.conf")
        if (themeConf.isFile) {
            val config = readIniFile(themeConf)
            val backgroundValue = listOf("general/background", "background")
                .firstNotNullOfOrNull { key -> config[key]?.takeIf { it.isNotBlank() } }

            resolveBackgroundFile(themeDir, backgroundValue)?.let { file ->
                return Pair(file, determineMediaType(file))
            }
        }

        return null
    }

    private fun readConfiguredThemeFile(themeDir: File): File? {
        val metadata = File(themeDir, "metadata.desktop")
        if (!metadata.isFile) return null

        var configPath = ""
        metadata.useLines { lines ->
            lines.forEach { line ->
                val cleanLine = line.trim()
                if (cleanLine.startsWith("ConfigFile=")) {
                    configPath = cleanLine.substringAfter('=').trim()
                }
            }
        }

        if (configPath.isBlank()) return null

        val configFile = if (File(configPath).isAbsolute) {
            File(configPath)
        } else {
            File(themeDir, configPath)
        }

        return configFile.takeIf { it.isFile }
    }

    private fun readIniFile(file: File): Map<String, String> {
        val values = mutableMapOf<String, String>()
        var currentSection = ""

        file.useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                    return@forEach
                }

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length - 1).trim().lowercase()
                    return@forEach
                }

                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) return@forEach

                val key = line.substring(0, separatorIndex).trim().lowercase()
                val value = line.substring(separatorIndex + 1)
                    .substringBefore(" #")
                    .trim()
                    .trim('"', '\'')

                values["$currentSection/$key"] = value
                if (currentSection.isEmpty()) {
                    values[key] = value
                }
            }
        }

        return values
    }

    private fun resolveBackgroundFile(themeDir: File, rawValue: String?): File? {
        val value = rawValue?.trim()?.trim('"', '\'')?.removePrefix("./").orEmpty()
        if (value.isBlank()) return null

        val candidates = mutableListOf<File>()
        if (value.startsWith("/")) {
            candidates.add(File(value))
        }
        candidates.add(File(themeDir, value))
        candidates.add(File(themeDir, "backgrounds/$value"))

        return candidates.firstOrNull { it.isFile && it.canRead() }
    }

    private fun determineMediaType(file: File): String {
        return if (file.extension.lowercase() in videoExtensions) "video" else "image"
    }
}
