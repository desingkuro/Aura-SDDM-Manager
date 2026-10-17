package com.example.aura.data.repository

import com.example.aura.domain.repository.SddmRepository
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

    override fun launchPreview(background: File?) {
        if (background == null || !background.isFile) {
            println("No hay un fondo seleccionado para previsualizar.")
            return
        }

        thread(isDaemon = true) {
            var tempThemeDir: File? = null
            var shutdownHook: Thread? = null
            try {
                val greeterPath = getGreeterPath()
                if (greeterPath.isBlank()) {
                    println("Error: No se encontró sddm-greeter en el sistema.")
                    return@thread
                }

                val themeName = getCurrentTheme()
                val sourceThemeDir = File(themesDir, themeName)
                if (!sourceThemeDir.isDirectory) {
                    println("Error: No se encontró el tema actual '$themeName' en $themesDir.")
                    return@thread
                }

                tempThemeDir = createTemporaryTheme(sourceThemeDir, background)
                if (tempThemeDir == null) {
                    println("Error: No se pudo preparar el tema temporal de previsualización.")
                    return@thread
                }

                val previewDir = tempThemeDir
                shutdownHook = Thread { deleteRecursively(previewDir) }
                Runtime.getRuntime().addShutdownHook(shutdownHook)

                println("Abriendo previsualización con '${background.name}' usando el tema temporal ${tempThemeDir.absolutePath}")

                ProcessBuilder(greeterPath, "--test-mode", "--theme", tempThemeDir.absolutePath)
                    .inheritIO()
                    .start()
                    .waitFor()
            } catch (e: Exception) {
                println("Error al intentar abrir el preview de SDDM: ${e.message}")
                e.printStackTrace()
            } finally {
                shutdownHook?.let { hook ->
                    try {
                        Runtime.getRuntime().removeShutdownHook(hook)
                    } catch (_: IllegalStateException) {
                        // El JVM ya está cerrando: el hook se encargará del borrado.
                    }
                }
                tempThemeDir?.let { dir ->
                    deleteRecursively(dir)
                    println("Tema temporal de previsualización eliminado: ${dir.absolutePath}")
                }
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

    /**
     * Crea una copia temporal del [sourceThemeDir] donde todas las referencias al
     * fondo apuntan a [background]. El resto del tema se enlaza simbólicamente para
     * no duplicar archivos (fuentes, componentes, iconos, etc.).
     */
    private fun createTemporaryTheme(sourceThemeDir: File, background: File): File? {
        return try {
            cleanupStalePreviewThemes()
            val tempThemeDir = Files.createTempDirectory("aura-sddm-preview-").toFile()
            val configuredRelativePath = readConfiguredThemePath(sourceThemeDir)
            val configuredDirName = configuredRelativePath?.substringBefore('/')?.takeIf { it.isNotBlank() }
            val sourceBackgroundsDir = sourceThemeDir.listFiles()
                ?.firstOrNull { it.isDirectory && it.name.equals("backgrounds", ignoreCase = true) }

            // Enlaza (o copia) el contenido del tema, excepto la carpeta de fondos y
            // el directorio que contiene el archivo de configuración, que se recrean.
            sourceThemeDir.listFiles()?.forEach { child ->
                when {
                    child.name.equals("backgrounds", ignoreCase = true) -> Unit
                    configuredDirName != null && child.name == configuredDirName ->
                        child.copyRecursively(File(tempThemeDir, child.name), overwrite = true)
                    else -> linkOrCopy(child, File(tempThemeDir, child.name))
                }
            }

            // Los temas con ConfigFile (SilentSDDM) siempre resuelven "backgrounds/".
            val backgroundsDir = File(tempThemeDir, sourceBackgroundsDir?.name ?: "backgrounds")
            backgroundsDir.mkdirs()

            sourceBackgroundsDir?.listFiles()?.forEach { backgroundFile ->
                linkOrCopy(backgroundFile, File(backgroundsDir, backgroundFile.name))
            }

            val tempBackground = File(backgroundsDir, background.name)
            Files.copy(background.toPath(), tempBackground.toPath(), StandardCopyOption.REPLACE_EXISTING)

            if (configuredRelativePath != null) {
                val sourceConfig = File(sourceThemeDir, configuredRelativePath)
                val tempConfig = File(tempThemeDir, configuredRelativePath)
                tempConfig.parentFile?.mkdirs()
                tempConfig.writeText(
                    buildConfiguredThemePreview(sourceConfig.readText(), background.name)
                )
            } else {
                val sourceThemeConf = File(sourceThemeDir, "theme.conf")
                if (sourceThemeConf.isFile) {
                    File(tempThemeDir, "theme.conf").writeText(
                        buildThemeConfPreview(
                            sourceThemeConf.readText(),
                            fileName = background.name,
                            backgroundsFolder = backgroundsDir.name
                        )
                    )
                }
            }

            tempThemeDir
        } catch (e: Exception) {
            println("Error al crear el tema temporal: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Reescribe un archivo de configuración tipo SilentSDDM para que las pantallas
     * de login y bloqueo usen [backgroundName] y no un color de fondo.
     */
    private fun buildConfiguredThemePreview(content: String, backgroundName: String): String {
        val output = mutableListOf<String>()
        var section = ""
        var loginApplied = false
        var lockApplied = false

        content.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()

            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = trimmed.substring(1, trimmed.length - 1).trim().lowercase()
                output.add(rawLine)
                return@forEach
            }

            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) {
                output.add(rawLine)
                return@forEach
            }

            val separatorIndex = trimmed.indexOf('=')
            if (separatorIndex <= 0) {
                output.add(rawLine)
                return@forEach
            }

            val key = trimmed.substring(0, separatorIndex).trim().lowercase()
            if (section == "loginscreen" || section == "lockscreen") {
                when (key) {
                    "background" -> {
                        output.add("background = \"$backgroundName\"")
                        if (section == "loginscreen") loginApplied = true else lockApplied = true
                        return@forEach
                    }
                    "use-background-color" -> {
                        output.add("use-background-color = false")
                        return@forEach
                    }
                }
            }

            output.add(rawLine)
        }

        if (!loginApplied) {
            output.add("")
            output.add("[LoginScreen]")
            output.add("background = \"$backgroundName\"")
            output.add("use-background-color = false")
        }
        if (!lockApplied) {
            output.add("")
            output.add("[LockScreen]")
            output.add("background = \"$backgroundName\"")
            output.add("use-background-color = false")
        }

        return output.joinToString("\n")
    }

    /**
     * Reescribe un `theme.conf` tradicional para apuntar al fondo seleccionado.
     */
    private fun buildThemeConfPreview(
        content: String,
        fileName: String,
        backgroundsFolder: String,
    ): String {
        val output = mutableListOf<String>()
        var applied = false
        val backgroundValue = "$backgroundsFolder/$fileName"

        content.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()

            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                output.add(rawLine)
                return@forEach
            }

            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) {
                output.add(rawLine)
                return@forEach
            }

            val separatorIndex = trimmed.indexOf('=')
            if (separatorIndex <= 0) {
                output.add(rawLine)
                return@forEach
            }

            val key = trimmed.substring(0, separatorIndex).trim().lowercase()
            if (key == "background") {
                output.add("background=$backgroundValue")
                applied = true
                return@forEach
            }

            output.add(rawLine)
        }

        if (!applied) {
            if (output.none { it.trim().equals("[General]", ignoreCase = true) }) {
                output.add("")
                output.add("[General]")
            }
            output.add("background=$backgroundValue")
        }

        return output.joinToString("\n")
    }

    private fun linkOrCopy(source: File, target: File) {
        try {
            if (target.exists()) return
            Files.createSymbolicLink(target.toPath(), source.toPath())
        } catch (e: Exception) {
            try {
                if (source.isDirectory) {
                    source.copyRecursively(target, overwrite = true)
                } else {
                    target.parentFile?.mkdirs()
                    source.copyTo(target, overwrite = true)
                }
            } catch (copyError: Exception) {
                println("No se pudo preparar ${source.absolutePath}: ${copyError.message}")
            }
        }
    }

    private fun deleteRecursively(file: File) {
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursively(it) }
            }
            file.delete()
        } catch (e: Exception) {
            println("No se pudo eliminar ${file.absolutePath}: ${e.message}")
        }
    }

    /**
     * Elimina temas temporales de previsualización que hayan quedado huérfanos
     * (por ejemplo, si la aplicación se cerró mientras el preview estaba abierto).
     */
    private fun cleanupStalePreviewThemes(maxAgeMillis: Long = 60 * 60 * 1000) {
        try {
            val now = System.currentTimeMillis()
            File(System.getProperty("java.io.tmpdir"))
                .listFiles()
                ?.filter { it.isDirectory && it.name.startsWith("aura-sddm-preview-") }
                ?.filter { now - it.lastModified() > maxAgeMillis }
                ?.forEach { deleteRecursively(it) }
        } catch (e: Exception) {
            println("No se pudieron limpiar previsualizaciones anteriores: ${e.message}")
        }
    }

    private fun readConfiguredThemePath(themeDir: File): String? {
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

        return configPath.ifBlank { null }
    }

    private fun readConfiguredThemeFile(themeDir: File): File? {
        val configPath = readConfiguredThemePath(themeDir) ?: return null

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
