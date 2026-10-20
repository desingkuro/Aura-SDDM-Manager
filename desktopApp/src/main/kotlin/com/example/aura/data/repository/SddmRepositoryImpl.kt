package com.example.aura.data.repository

import com.example.aura.domain.models.ApplyBackgroundResult
import com.example.aura.domain.models.RestoreBackupResult
import com.example.aura.domain.models.SddmBackup
import com.example.aura.domain.repository.SddmRepository
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties
import kotlin.concurrent.thread

class SddmRepositoryImpl(
    private val appliedBackgroundFile: File = File(System.getProperty("user.home"), ".local/share/Aura/current_bg.txt"),
    private val themesDir: File = File("/usr/share/sddm/themes"),
    private val sddmConfFile: File = File("/etc/sddm.conf"),
    private val sddmConfDir: File = File("/etc/sddm.conf.d"),
    private val backupsDir: File = File(System.getProperty("user.home"), ".local/share/Aura/backups"),
) : SddmRepository {

    private val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v")
    private val backupTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())
    private val backupManifestName = "backup.properties"

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
        if (appliedBackgroundFile.isFile) {
            val savedPath = appliedBackgroundFile.readText().trim().trim('"', '\'')
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
        appliedBackgroundFile.parentFile?.mkdirs()
        appliedBackgroundFile.writeText(file.absolutePath)
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

    override fun applyBackground(background: File): ApplyBackgroundResult {
        if (!background.isFile) {
            return ApplyBackgroundResult.Failure("El archivo seleccionado ya no existe.")
        }

        return try {
            val themeName = getCurrentTheme()
            val themeDir = File(themesDir, themeName)
            if (!themeDir.isDirectory) {
                return ApplyBackgroundResult.Failure(
                    "No se encontró el tema \"$themeName\" en ${themesDir.absolutePath}."
                )
            }

            val sourceBackgroundsDir = themeDir.listFiles()
                ?.firstOrNull { it.isDirectory && it.name.equals("backgrounds", ignoreCase = true) }
            val backgroundsDirName = sourceBackgroundsDir?.name ?: "backgrounds"
            val backgroundsDir = File(themeDir, backgroundsDirName)
            val targetBackground = File(backgroundsDir, background.name)

            val configuredRelativePath = readConfiguredThemePath(themeDir)
            val themeConfigFile: File
            val newConfigContent: String

            if (configuredRelativePath != null) {
                themeConfigFile = File(themeDir, configuredRelativePath)
                if (!themeConfigFile.isFile) {
                    return ApplyBackgroundResult.Failure(
                        "No se encontró el archivo de configuración del tema \"$themeName\"."
                    )
                }
                newConfigContent = buildConfiguredThemePreview(themeConfigFile.readText(), background.name)
            } else {
                themeConfigFile = File(themeDir, "theme.conf")
                if (!themeConfigFile.isFile) {
                    return ApplyBackgroundResult.Failure(
                        "El tema \"$themeName\" no tiene una configuración de fondo compatible."
                    )
                }
                newConfigContent = buildThemeConfPreview(
                    content = themeConfigFile.readText(),
                    fileName = background.name,
                    backgroundsFolder = backgroundsDirName,
                )
            }

            // El respaldo se crea antes de tocar cualquier archivo. Si falla, se
            // aborta la operación para no dejar el sistema sin vuelta atrás.
            try {
                val backupDir = createBackup(
                    themeName = themeName,
                    themeDir = themeDir,
                    themeConfigFile = themeConfigFile,
                    targetBackground = targetBackground,
                )
                println("Respaldo creado en ${backupDir.absolutePath}")
            } catch (e: Exception) {
                return ApplyBackgroundResult.Failure(
                    "No se pudo crear el respaldo de la configuración de SDDM. " +
                        "No se aplicó ningún cambio. Detalle: ${e.message ?: "error desconocido"}"
                )
            }

            // Si la carpeta de fondos aún no existe, se comprueba el directorio del
            // tema que la contendrá, ya que Files.isWritable da false en rutas inexistentes.
            val backgroundsWritable = if (backgroundsDir.exists()) {
                Files.isWritable(backgroundsDir.toPath())
            } else {
                Files.isWritable(themeDir.toPath())
            }
            val requiresElevation = !backgroundsWritable || !Files.isWritable(themeConfigFile.toPath())
            val error = if (requiresElevation) {
                applyWithElevatedPrivileges(background, targetBackground, backgroundsDir, themeConfigFile, newConfigContent)
            } else {
                applyDirectly(background, targetBackground, backgroundsDir, themeConfigFile, newConfigContent)
            }

            if (error != null) {
                ApplyBackgroundResult.Failure(error)
            } else {
                saveAppliedBackground(background)
                println("Fondo aplicado: \"${background.name}\" en el tema \"$themeName\".")
                ApplyBackgroundResult.Success(background.name, themeName)
            }
        } catch (e: Exception) {
            println("Error al aplicar el fondo: ${e.message}")
            e.printStackTrace()
            ApplyBackgroundResult.Failure("No se pudo aplicar el fondo: ${e.message ?: "error desconocido"}")
        }
    }

    /**
     * Crea una copia inmutable del estado que puede cambiar `applyBackground`:
     * la configuración del tema, el fondo de destino si ya existía y el registro
     * local de fondo aplicado. El manifiesto se escribe al final, de modo que una
     * carpeta sin manifiesto se considera un respaldo incompleto y se ignora.
     */
    private fun createBackup(
        themeName: String,
        themeDir: File,
        themeConfigFile: File,
        targetBackground: File,
    ): File {
        Files.createDirectories(backupsDir.toPath())

        val baseId = "${backupTimestampFormatter.format(Instant.now())}-${sanitizeName(themeName)}"
        var backupDir = File(backupsDir, baseId)
        var suffix = 1
        while (backupDir.exists()) {
            backupDir = File(backupsDir, "$baseId-$suffix")
            suffix++
        }
        Files.createDirectory(backupDir.toPath())

        try {
            Files.copy(
                themeConfigFile.toPath(),
                File(backupDir, "theme-config").toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )

            if (appliedBackgroundFile.isFile) {
                Files.copy(
                    appliedBackgroundFile.toPath(),
                    File(backupDir, "current-bg.txt").toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }

            val targetExisted = targetBackground.isFile
            if (targetExisted) {
                Files.copy(
                    targetBackground.toPath(),
                    File(backupDir, "background").toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }

            val manifest = Properties().apply {
                setProperty("version", "1")
                setProperty("created_at", Instant.now().toString())
                setProperty("theme_name", themeName)
                setProperty("theme_dir", themeDir.absolutePath)
                setProperty("theme_config", themeConfigFile.absolutePath)
                setProperty("background_name", targetBackground.name)
                setProperty("target_background", targetBackground.absolutePath)
                setProperty("target_background_existed", targetExisted.toString())
                setProperty("current_bg_existed", appliedBackgroundFile.isFile.toString())
            }
            // El manifiesto se publica con un movimiento atómico: hasta que existe,
            // la carpeta se considera incompleta y se ignora al listar respaldos.
            val manifestFile = File(backupDir, backupManifestName)
            val temporaryManifest = File(backupDir, "$backupManifestName.tmp")
            temporaryManifest.outputStream().use { output ->
                manifest.store(output, "Aura SDDM backup")
            }
            try {
                Files.move(
                    temporaryManifest.toPath(),
                    manifestFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryManifest.toPath(),
                    manifestFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }

            return backupDir
        } catch (e: Exception) {
            deleteRecursively(backupDir)
            throw e
        }
    }

    override fun listBackups(): List<SddmBackup> {
        if (!backupsDir.isDirectory) return emptyList()

        return backupsDir.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir -> runCatching { readBackup(dir) }.getOrNull() }
            ?.sortedByDescending { it.createdAt }
            ?.toList()
            ?: emptyList()
    }

    private fun readBackup(backupDir: File): SddmBackup? {
        val manifestFile = File(backupDir, backupManifestName)
        if (!manifestFile.isFile) return null
        if (!File(backupDir, "theme-config").isFile) return null

        val manifest = Properties().apply {
            manifestFile.inputStream().use { load(it) }
        }
        val createdAt = manifest.getProperty("created_at")
            ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
            ?: Instant.ofEpochMilli(manifestFile.lastModified())

        return SddmBackup(
            id = backupDir.name,
            createdAt = createdAt,
            themeName = manifest.getProperty("theme_name").orEmpty(),
            backgroundName = manifest.getProperty("background_name").orEmpty(),
            targetBackgroundExisted = manifest.getProperty("target_background_existed")
                ?.toBooleanStrictOrNull() ?: false,
        )
    }

    override fun restoreBackup(backupId: String): RestoreBackupResult {
        if (backupId.isBlank() || backupId.contains('/') || backupId.contains('\\') || backupId.contains("..")) {
            return RestoreBackupResult.Failure("Identificador de respaldo inválido.")
        }

        val backupDir = File(backupsDir, backupId)
        if (!backupDir.isDirectory) {
            return RestoreBackupResult.Failure("No se encontró el respaldo seleccionado.")
        }

        return try {
            val backup = readBackup(backupDir)
                ?: return RestoreBackupResult.Failure("El respaldo está incompleto o dañado.")
            val manifestFile = File(backupDir, backupManifestName)
            val manifest = Properties().apply {
                manifestFile.inputStream().use { load(it) }
            }

            val themeDir = manifest.getProperty("theme_dir")?.let(::File)
                ?: return RestoreBackupResult.Failure("El respaldo no contiene la ruta del tema.")
            val themeConfigFile = manifest.getProperty("theme_config")?.let(::File)
                ?: return RestoreBackupResult.Failure("El respaldo no contiene la ruta de configuración.")
            val targetBackground = manifest.getProperty("target_background")?.let(::File)
                ?: return RestoreBackupResult.Failure("El respaldo no contiene la ruta del fondo.")
            val targetExisted = manifest.getProperty("target_background_existed")
                ?.toBooleanStrictOrNull() ?: false
            val currentBgExisted = manifest.getProperty("current_bg_existed")
                ?.toBooleanStrictOrNull() ?: false

            validateBackupPaths(themeDir, themeConfigFile, targetBackground)?.let { error ->
                return RestoreBackupResult.Failure(error)
            }

            if (!File(backupDir, "theme-config").isFile) {
                return RestoreBackupResult.Failure("El respaldo no contiene la configuración del tema.")
            }
            if (targetExisted && !File(backupDir, "background").isFile) {
                return RestoreBackupResult.Failure("El respaldo no contiene el fondo anterior.")
            }
            if (currentBgExisted && !File(backupDir, "current-bg.txt").isFile) {
                return RestoreBackupResult.Failure("El respaldo no contiene el registro del fondo aplicado.")
            }

            val configWritable = if (themeConfigFile.exists()) {
                Files.isWritable(themeConfigFile.toPath())
            } else {
                themeConfigFile.parentFile?.let { Files.isWritable(it.toPath()) } ?: false
            }
            val backgroundWritable = when {
                targetExisted -> targetBackground.parentFile?.let { Files.isWritable(it.toPath()) } ?: false
                targetBackground.exists() -> Files.isWritable(targetBackground.parentFile.toPath())
                else -> true
            }
            val requiresElevation = !configWritable || !backgroundWritable

            val error = if (requiresElevation) {
                restoreWithElevatedPrivileges(backupDir, themeConfigFile, targetBackground, targetExisted)
            } else {
                restoreDirectly(backupDir, themeConfigFile, targetBackground, targetExisted)
            }
            if (error != null) {
                return RestoreBackupResult.Failure(error)
            }

            restoreAppliedBackgroundFile(backupDir, currentBgExisted)
            println("Respaldo restaurado: ${backupDir.absolutePath}")
            RestoreBackupResult.Success(backup)
        } catch (e: Exception) {
            println("Error al restaurar el respaldo: ${e.message}")
            e.printStackTrace()
            RestoreBackupResult.Failure(
                "No se pudo restaurar el respaldo: ${e.message ?: "error desconocido"}"
            )
        }
    }

    /**
     * Solo se permite restaurar archivos dentro del tema actual. Esto evita que un
     * manifiesto manipulado pueda hacer que `pkexec` escriba fuera de la ruta
     * esperada.
     */
    private fun validateBackupPaths(
        themeDir: File,
        themeConfigFile: File,
        targetBackground: File,
    ): String? {
        val themesRoot = themesDir.canonicalFile
        val themeRoot = themeDir.canonicalFile
        if (themeRoot.parentFile?.canonicalFile != themesRoot) {
            return "El respaldo apunta a un tema fuera del directorio permitido."
        }

        val themeRootPath = themeRoot.toPath()
        if (!themeConfigFile.canonicalFile.toPath().startsWith(themeRootPath)) {
            return "El respaldo apunta a una configuración fuera del tema."
        }
        if (!targetBackground.canonicalFile.toPath().startsWith(themeRootPath)) {
            return "El respaldo apunta a un fondo fuera del tema."
        }

        return null
    }

    private fun restoreDirectly(
        backupDir: File,
        themeConfigFile: File,
        targetBackground: File,
        targetExisted: Boolean,
    ): String? {
        return try {
            Files.copy(
                File(backupDir, "theme-config").toPath(),
                themeConfigFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            if (targetExisted) {
                Files.copy(
                    File(backupDir, "background").toPath(),
                    targetBackground.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } else {
                Files.deleteIfExists(targetBackground.toPath())
            }
            null
        } catch (e: Exception) {
            "No se pudo restaurar el respaldo: ${e.message ?: "error desconocido"}"
        }
    }

    private fun restoreWithElevatedPrivileges(
        backupDir: File,
        themeConfigFile: File,
        targetBackground: File,
        targetExisted: Boolean,
    ): String? {
        val pkexecPath = getPkexecPath()
        if (pkexecPath.isBlank()) {
            return "Se requieren permisos de administrador. Instala \"pkexec\" (PolicyKit) para restaurar el respaldo."
        }

        val backupConfig = File(backupDir, "theme-config")
        val backupBackground = File(backupDir, "background")
        return try {
            val script = buildString {
                appendLine("#!/bin/sh")
                appendLine("set -e")
                appendLine("cp -f -- ${shellQuote(backupConfig.absolutePath)} ${shellQuote(themeConfigFile.absolutePath)}")
                appendLine("chmod 644 -- ${shellQuote(themeConfigFile.absolutePath)}")
                if (targetExisted) {
                    appendLine("cp -f -- ${shellQuote(backupBackground.absolutePath)} ${shellQuote(targetBackground.absolutePath)}")
                    appendLine("chmod 644 -- ${shellQuote(targetBackground.absolutePath)}")
                } else {
                    appendLine("rm -f -- ${shellQuote(targetBackground.absolutePath)}")
                }
            }
            runPkexecScript(pkexecPath, script)
        } catch (e: Exception) {
            "No se pudo restaurar el respaldo con permisos de administrador: ${e.message ?: "error desconocido"}"
        }
    }

    private fun restoreAppliedBackgroundFile(backupDir: File, existed: Boolean) {
        if (existed) {
            appliedBackgroundFile.parentFile?.mkdirs()
            Files.copy(
                File(backupDir, "current-bg.txt").toPath(),
                appliedBackgroundFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } else {
            Files.deleteIfExists(appliedBackgroundFile.toPath())
        }
    }

    private fun sanitizeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "-").ifBlank { "tema" }

    /**
     * Aplica el fondo escribiendo directamente en el tema (cuando el usuario tiene
     * permisos sobre él).
     */
    private fun applyDirectly(
        source: File,
        targetBackground: File,
        backgroundsDir: File,
        configFile: File,
        newConfigContent: String,
    ): String? {
        return try {
            backgroundsDir.mkdirs()
            Files.copy(source.toPath(), targetBackground.toPath(), StandardCopyOption.REPLACE_EXISTING)
            configFile.writeText(newConfigContent)
            null
        } catch (e: Exception) {
            "No se pudo aplicar el fondo: ${e.message ?: "error desconocido"}"
        }
    }

    /**
     * Aplica el fondo mediante `pkexec`, que solicita la contraseña del
     * administrador a través de PolicyKit. Se prepara un pequeño script que copia
     * el fondo y la nueva configuración con permisos de root.
     */
    private fun applyWithElevatedPrivileges(
        source: File,
        targetBackground: File,
        backgroundsDir: File,
        configFile: File,
        newConfigContent: String,
    ): String? {
        val pkexecPath = getPkexecPath()
        if (pkexecPath.isBlank()) {
            return "Se requieren permisos de administrador. Instala \"pkexec\" (PolicyKit) para aplicar cambios en SDDM."
        }

        var tempDir: File? = null
        return try {
            tempDir = Files.createTempDirectory("aura-sddm-apply-").toFile()

            val stagedConfig = File(tempDir, "theme.conf")
            stagedConfig.writeText(newConfigContent)

            val scriptContent = buildString {
                appendLine("#!/bin/sh")
                appendLine("set -e")
                appendLine("mkdir -p -- ${shellQuote(backgroundsDir.absolutePath)}")
                appendLine("cp -f -- ${shellQuote(source.absolutePath)} ${shellQuote(targetBackground.absolutePath)}")
                appendLine("chmod 644 -- ${shellQuote(targetBackground.absolutePath)}")
                appendLine("cp -f -- ${shellQuote(stagedConfig.absolutePath)} ${shellQuote(configFile.absolutePath)}")
                appendLine("chmod 644 -- ${shellQuote(configFile.absolutePath)}")
            }
            runPkexecScript(pkexecPath, scriptContent)
        } catch (e: Exception) {
            "No se pudo aplicar el fondo: ${e.message ?: "error desconocido"}"
        } finally {
            tempDir?.let { deleteRecursively(it) }
        }
    }

    /**
     * Ejecuta un script con `pkexec`. El script se escribe en un directorio
     * temporal propiedad del usuario y solo contiene las rutas ya validadas.
     */
    private fun runPkexecScript(pkexecPath: String, scriptContent: String): String? {
        var tempDir: File? = null
        return try {
            tempDir = Files.createTempDirectory("aura-sddm-script-").toFile()
            val script = File(tempDir, "action.sh")
            script.writeText(scriptContent)
            script.setExecutable(true)

            val process = ProcessBuilder(pkexecPath, "/bin/sh", script.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.waitFor()

            when (exitCode) {
                0 -> null
                126, 127 -> "Autenticación cancelada o no fue posible completar la operación con pkexec."
                else -> "La operación falló (código $exitCode).${if (output.isNotBlank()) " $output" else ""}"
            }
        } catch (e: Exception) {
            "No se pudo ejecutar la operación con permisos de administrador: ${e.message ?: "error desconocido"}"
        } finally {
            tempDir?.let { deleteRecursively(it) }
        }
    }

    private fun getPkexecPath(): String {
        val possiblePaths = listOf(
            "/usr/bin/pkexec",
            "/bin/pkexec",
            "/usr/local/bin/pkexec"
        )
        return possiblePaths.firstOrNull { File(it).isFile } ?: ""
    }

    /** Escapa un valor para usarlo como argumento literal en un script de shell. */
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

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
