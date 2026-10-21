package com.example.aura.data.repository

import com.example.aura.data.security.PathValidationException
import com.example.aura.data.security.PathValidator
import com.example.aura.domain.models.ApplyBackgroundResult
import com.example.aura.domain.models.BackgroundDestination
import com.example.aura.domain.models.ChangeThemeResult
import com.example.aura.domain.models.DiagnosticStatus
import com.example.aura.domain.models.RestoreBackupResult
import com.example.aura.domain.models.SddmBackup
import com.example.aura.domain.models.SddmBackupKind
import com.example.aura.domain.models.SddmDiagnostic
import com.example.aura.domain.models.SddmDiagnostics
import com.example.aura.domain.models.SddmTheme
import com.example.aura.domain.repository.SddmRepository
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class SddmRepositoryImpl(
    private val appliedBackgroundFile: File = File(System.getProperty("user.home"), ".local/share/Aura/current_bg.txt"),
    private val themesDir: File = File("/usr/share/sddm/themes"),
    private val sddmConfFile: File = File("/etc/sddm.conf"),
    private val sddmConfDir: File = File("/etc/sddm.conf.d"),
    private val backupsDir: File = File(System.getProperty("user.home"), ".local/share/Aura/backups"),
    private val pathValidator: PathValidator = PathValidator(themesDir, sddmConfFile, sddmConfDir),
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

    override fun getInstalledThemes(): List<SddmTheme> {
        if (!themesDir.isDirectory) return emptyList()

        val currentTheme = getCurrentTheme()
        return themesDir.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { themeDir ->
                val safeThemeDir = pathValidator.resolveThemeDir(themeDir.name)
                    ?: return@mapNotNull null
                val configuredPath = readConfiguredThemePath(safeThemeDir)
                val configuredConfig = configuredPath?.let { path ->
                    runCatching { pathValidator.resolveThemeConfig(safeThemeDir, path) }
                        .getOrNull()
                        ?.takeIf { it.isFile }
                }
                val traditionalConfig = runCatching {
                    pathValidator.requireInside(
                        safeThemeDir,
                        File(safeThemeDir, "theme.conf"),
                        "configuración tradicional del tema",
                    )
                }.getOrNull()?.takeIf { it.isFile }
                if (configuredConfig == null && traditionalConfig == null) {
                    return@mapNotNull null
                }

                SddmTheme(
                    name = safeThemeDir.name,
                    displayName = readThemeDisplayName(safeThemeDir),
                    path = safeThemeDir.absolutePath,
                    isCurrent = safeThemeDir.name == currentTheme,
                    supportsSeparateScreens = configuredConfig != null,
                )
            }
            ?.sortedWith(
                compareByDescending<SddmTheme> { it.isCurrent }
                    .thenBy { it.displayName.lowercase() }
            )
            ?.toList()
            ?: emptyList()
    }

    override fun getDiagnostics(): SddmDiagnostics {
        val currentTheme = getCurrentTheme()
        val themeDir = resolveThemeDir(currentTheme)
        val themeExists = themeDir != null
        val themeConfig = themeDir?.let {
            runCatching { findThemeConfigFile(it) }.getOrNull()
        }
        val greeterPath = getGreeterPath()
        val pkexecPath = getPkexecPath()
        val polkitAgent = findPolkitAgent()
        val ffmpegPath = findExecutable("ffmpeg")
        val safeBackgroundsDir = themeDir?.let { dir ->
            val backgroundsDir = dir.listFiles()
                ?.firstOrNull { it.isDirectory && it.name.equals("backgrounds", ignoreCase = true) }
                ?: File(dir, "backgrounds")
            runCatching {
                pathValidator.requireInside(dir, backgroundsDir, "carpeta de fondos")
            }.getOrNull()
        }
        val backgroundsWritable = when {
            themeDir == null || safeBackgroundsDir == null -> false
            safeBackgroundsDir.exists() -> Files.isWritable(safeBackgroundsDir.toPath())
            else -> Files.isWritable(themeDir.toPath())
        }
        val sddmConfigReadable = (sddmConfFile.isFile && sddmConfFile.canRead()) ||
            (sddmConfDir.isDirectory && sddmConfDir.canRead())
        val themesReadable = themesDir.isDirectory && themesDir.canRead()
        val backupsWritable = if (backupsDir.exists()) {
            Files.isWritable(backupsDir.toPath())
        } else {
            backupsDir.parentFile?.let { Files.isWritable(it.toPath()) } == true
        }
        val sddmConfigValue = buildString {
            append(sddmConfFile.absolutePath)
            if (sddmConfDir.isDirectory) append(" + ${sddmConfDir.name}/")
        }

        val items = listOf(
            SddmDiagnostic(
                label = "Tema actual",
                value = if (themeExists) currentTheme else "$currentTheme (no encontrado)",
                status = if (themeExists) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
                hint = if (themeExists) null else "Revisa el valor Current en la configuración de SDDM.",
            ),
            SddmDiagnostic(
                label = "Configuración del tema",
                value = themeConfig?.absolutePath ?: "No encontrada",
                status = if (themeConfig != null) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
                hint = "Se usa para reescribir el fondo y detectar el tema.",
            ),
            SddmDiagnostic(
                label = "Greeter SDDM",
                value = greeterPath.ifBlank { "No encontrado" },
                status = if (greeterPath.isNotBlank()) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
                hint = "Necesario para previsualizar el tema.",
            ),
            SddmDiagnostic(
                label = "pkexec",
                value = pkexecPath.ifBlank { "No encontrado" },
                status = if (pkexecPath.isNotBlank()) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
                hint = "Necesario para modificar temas protegidos y cambiar de tema si /etc no es escribible.",
            ),
            SddmDiagnostic(
                label = "Agente Polkit",
                value = polkitAgent ?: "No detectado",
                status = if (polkitAgent != null) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
                hint = "Sin un agente Polkit no aparecerá el diálogo de autenticación.",
            ),
            SddmDiagnostic(
                label = "FFmpeg",
                value = ffmpegPath ?: "No encontrado",
                status = if (ffmpegPath != null) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
                hint = "Necesario para generar miniaturas de video.",
            ),
            SddmDiagnostic(
                label = "Configuración de SDDM",
                value = sddmConfigValue,
                status = if (sddmConfigReadable) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
                hint = if (sddmConfigReadable) null else "No se pudo leer /etc/sddm.conf.",
            ),
            SddmDiagnostic(
                label = "Carpeta de temas",
                value = themesDir.absolutePath,
                status = if (themesReadable) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
            ),
            SddmDiagnostic(
                label = "Carpeta de fondos",
                value = safeBackgroundsDir?.absolutePath ?: "No disponible",
                status = if (backgroundsWritable) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
                hint = if (backgroundsWritable) null else "Se solicitarán permisos de administrador.",
            ),
            SddmDiagnostic(
                label = "Respaldos",
                value = backupsDir.absolutePath,
                status = if (backupsWritable) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
                hint = if (backupsWritable) null else "No se puede escribir en la carpeta de respaldos.",
            ),
        )

        return SddmDiagnostics(checkedAt = Instant.now(), items = items)
    }

    override fun changeTheme(themeName: String): ChangeThemeResult {
        val themeDir = resolveThemeDir(themeName)
            ?: return ChangeThemeResult.Failure("Nombre de tema inválido o tema no encontrado.")
        val theme = getInstalledThemes().firstOrNull { it.name == themeName }
            ?: return ChangeThemeResult.Failure(
                "El tema \"$themeName\" no tiene una configuración compatible."
            )
        val previousTheme = getCurrentTheme()
        if (previousTheme == themeName) {
            return ChangeThemeResult.Success(previousTheme, theme.copy(isCurrent = true))
        }

        val configFile = try {
            themeConfigTarget()
        } catch (error: PathValidationException) {
            return ChangeThemeResult.Failure(
                "No se puede escribir la configuración de SDDM: ${error.message}"
            )
        }
        val configWritable = isConfigFileWritable(configFile)
        if (!configWritable && getPkexecPath().isBlank()) {
            return ChangeThemeResult.Failure(
                "No se puede cambiar el tema sin permisos de administrador. No se encontró " +
                    "pkexec: instala PolicyKit (por ejemplo, el paquete \"polkit\") para continuar."
            )
        }

        val backupDir = try {
            createThemeBackup(previousTheme, themeName, configFile)
        } catch (e: Exception) {
            return ChangeThemeResult.Failure(
                "No se pudo crear el respaldo del tema. No se cambió la configuración. " +
                    "Detalle: ${e.message ?: "error desconocido"}"
            )
        }
        println("Respaldo de tema creado en ${backupDir.absolutePath}")

        val content = "[Theme]\nCurrent=$themeName\n"
        val error = if (configWritable) {
            try {
                writeConfigAtomically(configFile, content)
                null
            } catch (e: Exception) {
                "No se pudo cambiar el tema: ${e.message ?: "error desconocido"}"
            }
        } else {
            writeThemeConfigWithElevatedPrivileges(configFile, content)
        }
        if (error != null) {
            return ChangeThemeResult.Failure(error)
        }

        val effectiveTheme = getCurrentTheme()
        if (effectiveTheme != themeName) {
            val configExisted = File(backupDir, "sddm-config").isFile
            val rollback = runCatching {
                restoreThemeConfigDirectly(backupDir, configFile, configExisted)
            }
            val rollbackDetail = if (rollback.isSuccess) {
                " Se revirtió la configuración anterior."
            } else {
                " Además, no se pudo revertir la configuración anterior: " +
                    "${rollback.exceptionOrNull()?.message ?: "error desconocido"}"
            }
            return ChangeThemeResult.Failure(
                "Se guardó el tema en ${configFile.absolutePath}, pero SDDM sigue leyendo " +
                    "\"$effectiveTheme\". Revisa si otro archivo de configuración tiene prioridad." +
                    rollbackDetail
            )
        }

        // El fondo registrado pertenece al tema anterior. Al cambiar de tema se
        // deja que el nuevo tema determine su propio fondo. Solo se limpia cuando
        // el cambio ya fue confirmado por SDDM.
        try {
            Files.deleteIfExists(appliedBackgroundFile.toPath())
        } catch (e: Exception) {
            println("No se pudo limpiar el fondo aplicado anterior: ${e.message}")
        }

        return ChangeThemeResult.Success(previousTheme, theme.copy(isCurrent = true))
    }

    override fun getGreeterPath(): String {
        findExecutable("sddm-greeter-qt6")?.let { return it }
        findExecutable("sddm-greeter")?.let { return it }

        val possiblePaths = listOf(
            "/usr/libexec/sddm-greeter-qt6",
            "/usr/libexec/sddm-greeter",
            "/usr/lib/sddm/sddm-greeter",
            "/usr/lib/x86_64-linux-gnu/sddm/sddm-greeter"
        )
        return possiblePaths.firstOrNull { File(it).isFile && File(it).canExecute() } ?: ""
    }

    override fun supportsSeparateScreens(): Boolean {
        val themeDir = resolveThemeDir(getCurrentTheme()) ?: return false
        val configuredPath = readConfiguredThemePath(themeDir) ?: return false
        return runCatching {
            pathValidator.resolveThemeConfig(themeDir, configuredPath)
        }.getOrNull()?.isFile == true
    }

    override suspend fun launchPreview(
        background: File?,
        destination: BackgroundDestination,
    ): String? {
        if (background == null || !background.isFile) {
            return "No hay un fondo seleccionado para previsualizar."
        }

        return withContext(Dispatchers.IO) {
            var tempThemeDir: File? = null
            try {
                val greeterPath = getGreeterPath()
                if (greeterPath.isBlank()) {
                    return@withContext "No se encontró sddm-greeter en el sistema. " +
                        "Instálalo para usar la previsualización."
                }

                val themeName = getCurrentTheme()
                val sourceThemeDir = File(themesDir, themeName)
                if (!sourceThemeDir.isDirectory) {
                    return@withContext "No se encontró el tema actual \"$themeName\" " +
                        "en ${themesDir.absolutePath}."
                }

                tempThemeDir = createTemporaryTheme(sourceThemeDir, background, destination)
                    ?: return@withContext "No se pudo preparar el tema temporal de previsualización."

                println(
                    "Abriendo previsualización con '${background.name}' usando el tema " +
                        "temporal ${tempThemeDir.absolutePath}"
                )

                val process = ProcessBuilder(
                    greeterPath,
                    "--test-mode",
                    "--theme",
                    tempThemeDir.absolutePath,
                ).inheritIO().start()

                suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation { process.destroy() }
                    process.onExit().whenComplete { _, _ ->
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Error al intentar abrir el preview de SDDM: ${e.message}")
                e.printStackTrace()
                "No se pudo abrir la previsualización: ${e.message ?: "error desconocido"}"
            } finally {
                tempThemeDir?.let { dir ->
                    deleteRecursively(dir)
                    println("Tema temporal de previsualización eliminado: ${dir.absolutePath}")
                }
            }
        }
    }

    override fun applyBackground(
        background: File,
        destination: BackgroundDestination,
    ): ApplyBackgroundResult {
        if (!background.isFile) {
            return ApplyBackgroundResult.Failure("El archivo seleccionado ya no existe.")
        }

        return try {
            val themeName = getCurrentTheme()
            val themeDir = pathValidator.resolveThemeDir(themeName)
                ?: return ApplyBackgroundResult.Failure(
                    "No se encontró el tema \"$themeName\" dentro de ${themesDir.absolutePath}."
                )

            val sourceBackgroundsDir = themeDir.listFiles()
                ?.firstOrNull { it.isDirectory && it.name.equals("backgrounds", ignoreCase = true) }
            val backgroundsDirName = sourceBackgroundsDir?.name ?: "backgrounds"
            val backgroundsDir = pathValidator.requireInside(
                themeDir,
                File(themeDir, backgroundsDirName),
                "carpeta de fondos",
            )
            val targetBackground = pathValidator.requireInside(
                backgroundsDir,
                File(backgroundsDir, background.name),
                "fondo de destino",
            )

            val configuredRelativePath = readConfiguredThemePath(themeDir)
            val themeConfigFile: File
            val newConfigContent: String

            if (configuredRelativePath != null) {
                themeConfigFile = pathValidator.resolveThemeConfig(themeDir, configuredRelativePath)
                if (!themeConfigFile.isFile) {
                    return ApplyBackgroundResult.Failure(
                        "No se encontró el archivo de configuración del tema \"$themeName\"."
                    )
                }
                newConfigContent = buildConfiguredThemePreview(
                    content = themeConfigFile.readText(),
                    backgroundName = background.name,
                    destination = destination,
                )
            } else {
                if (destination != BackgroundDestination.BOTH) {
                    return ApplyBackgroundResult.Failure(
                        "El tema \"$themeName\" usa un fondo general y no distingue entre login " +
                            "y bloqueo. Selecciona \"Ambos\" para aplicar el fondo."
                    )
                }
                themeConfigFile = pathValidator.requireInside(
                    themeDir,
                    File(themeDir, "theme.conf"),
                    "configuración tradicional del tema",
                )
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

            // Si la carpeta de fondos aún no existe, se comprueba el directorio del
            // tema que la contendrá, ya que Files.isWritable da false en rutas inexistentes.
            val backgroundsWritable = if (backgroundsDir.exists()) {
                Files.isWritable(backgroundsDir.toPath())
            } else {
                Files.isWritable(themeDir.toPath())
            }
            val configWritable = Files.isWritable(themeConfigFile.toPath())
            val requiresElevation = !backgroundsWritable || !configWritable

            if (requiresElevation && getPkexecPath().isBlank()) {
                val protectedParts = buildList {
                    if (!backgroundsWritable) add("la carpeta de fondos")
                    if (!configWritable) add("la configuración del tema")
                }
                return ApplyBackgroundResult.Failure(
                    "No se puede escribir en ${protectedParts.joinToString(" ni en ")} del tema " +
                        "\"$themeName\" sin permisos de administrador. No se encontró pkexec: " +
                        "instala PolicyKit (por ejemplo, el paquete \"polkit\") para continuar."
                )
            }

            // El respaldo se crea antes de tocar cualquier archivo. Si falla, se
            // aborta la operación para no dejar el sistema sin vuelta atrás.
            val backupDir = try {
                createBackup(
                    themeName = themeName,
                    themeDir = themeDir,
                    themeConfigFile = themeConfigFile,
                    targetBackground = targetBackground,
                )
            } catch (e: Exception) {
                return ApplyBackgroundResult.Failure(
                    "No se pudo crear el respaldo de la configuración de SDDM. " +
                        "No se aplicó ningún cambio. Detalle: ${e.message ?: "error desconocido"}"
                )
            }
            println("Respaldo creado en ${backupDir.absolutePath}")

            val error = if (requiresElevation) {
                applyWithElevatedPrivileges(
                    source = background,
                    targetBackground = targetBackground,
                    backgroundsDir = backgroundsDir,
                    configFile = themeConfigFile,
                    newConfigContent = newConfigContent,
                    backupDir = backupDir,
                )
            } else {
                applyDirectly(
                    source = background,
                    targetBackground = targetBackground,
                    backgroundsDir = backgroundsDir,
                    configFile = themeConfigFile,
                    newConfigContent = newConfigContent,
                    backupDir = backupDir,
                )
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
            publishManifest(backupDir, manifest)

            return backupDir
        } catch (e: Exception) {
            deleteRecursively(backupDir)
            throw e
        }
    }

    private fun publishManifest(backupDir: File, manifest: Properties) {
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
    }

    /**
     * Respalda la configuración de SDDM que se va a modificar y el registro del
     * fondo aplicado. El respaldo de tema se puede restaurar desde Ajustes igual
     * que los respaldos de fondos.
     */
    private fun createThemeBackup(
        previousTheme: String,
        newTheme: String,
        configFile: File,
    ): File {
        Files.createDirectories(backupsDir.toPath())

        val baseId = "${backupTimestampFormatter.format(Instant.now())}-tema-" +
            sanitizeName(previousTheme)
        var backupDir = File(backupsDir, baseId)
        var suffix = 1
        while (backupDir.exists()) {
            backupDir = File(backupsDir, "$baseId-$suffix")
            suffix++
        }
        Files.createDirectory(backupDir.toPath())

        try {
            val configExisted = configFile.isFile
            if (configExisted) {
                Files.copy(
                    configFile.toPath(),
                    File(backupDir, "sddm-config").toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }

            val currentBgExisted = appliedBackgroundFile.isFile
            if (currentBgExisted) {
                Files.copy(
                    appliedBackgroundFile.toPath(),
                    File(backupDir, "current-bg.txt").toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }

            val manifest = Properties().apply {
                setProperty("version", "1")
                setProperty("kind", "theme")
                setProperty("created_at", Instant.now().toString())
                setProperty("previous_theme", previousTheme)
                setProperty("new_theme", newTheme)
                setProperty("sddm_config", configFile.absolutePath)
                setProperty("sddm_config_existed", configExisted.toString())
                setProperty("current_bg_existed", currentBgExisted.toString())
            }
            publishManifest(backupDir, manifest)
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
            ?.filter { it.isDirectory && isDirectChild(backupsDir, it) }
            ?.mapNotNull { dir -> runCatching { readBackup(dir) }.getOrNull() }
            ?.sortedByDescending { it.createdAt }
            ?.toList()
            ?: emptyList()
    }

    private fun isDirectChild(parent: File, child: File): Boolean {
        val resolvedParent = parent.canonicalFile
        val resolvedChild = child.canonicalFile
        return resolvedChild.parentFile?.canonicalFile == resolvedParent
    }

    private fun readBackup(backupDir: File): SddmBackup? {
        val manifestFile = File(backupDir, backupManifestName)
        if (!manifestFile.isFile) return null

        val manifest = Properties().apply {
            manifestFile.inputStream().use { load(it) }
        }
        val createdAt = manifest.getProperty("created_at")
            ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
            ?: Instant.ofEpochMilli(manifestFile.lastModified())
        val kind = when (manifest.getProperty("kind")?.lowercase()) {
            "theme" -> SddmBackupKind.THEME
            else -> SddmBackupKind.BACKGROUND
        }

        if (kind == SddmBackupKind.THEME) {
            if (manifest.getProperty("sddm_config").isNullOrBlank()) return null
            val configExisted = manifest.getProperty("sddm_config_existed")
                ?.toBooleanStrictOrNull() ?: false
            if (configExisted && !File(backupDir, "sddm-config").isFile) return null
            val currentBgExisted = manifest.getProperty("current_bg_existed")
                ?.toBooleanStrictOrNull() ?: false
            if (currentBgExisted && !File(backupDir, "current-bg.txt").isFile) return null

            return SddmBackup(
                id = backupDir.name,
                createdAt = createdAt,
                themeName = manifest.getProperty("previous_theme").orEmpty(),
                backgroundName = "",
                targetBackgroundExisted = false,
                kind = SddmBackupKind.THEME,
                newThemeName = manifest.getProperty("new_theme")?.takeIf { it.isNotBlank() },
            )
        }

        if (!File(backupDir, "theme-config").isFile) return null

        return SddmBackup(
            id = backupDir.name,
            createdAt = createdAt,
            themeName = manifest.getProperty("theme_name").orEmpty(),
            backgroundName = manifest.getProperty("background_name").orEmpty(),
            targetBackgroundExisted = manifest.getProperty("target_background_existed")
                ?.toBooleanStrictOrNull() ?: false,
            kind = SddmBackupKind.BACKGROUND,
        )
    }

    override fun restoreBackup(backupId: String): RestoreBackupResult {
        if (backupId.isBlank() || backupId.contains('/') || backupId.contains('\\') || backupId.contains("..")) {
            return RestoreBackupResult.Failure("Identificador de respaldo inválido.")
        }

        val backupDir = File(backupsDir, backupId)
        if (!backupDir.isDirectory || !isDirectChild(backupsDir, backupDir)) {
            return RestoreBackupResult.Failure("No se encontró el respaldo seleccionado.")
        }

        return try {
            val backup = readBackup(backupDir)
                ?: return RestoreBackupResult.Failure("El respaldo está incompleto o dañado.")
            val manifestFile = File(backupDir, backupManifestName)
            val manifest = Properties().apply {
                manifestFile.inputStream().use { load(it) }
            }

            if (backup.kind == SddmBackupKind.THEME) {
                return restoreThemeBackup(backupDir, backup)
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

            if (requiresElevation && getPkexecPath().isBlank()) {
                return RestoreBackupResult.Failure(
                    "El tema está protegido y no se puede restaurar sin permisos de administrador. " +
                        "No se encontró pkexec: instala PolicyKit (por ejemplo, el paquete " +
                        "\"polkit\") para continuar."
                )
            }

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
    ): String? = try {
        val safeThemeDir = pathValidator.resolveThemeDir(themeDir.name)
            ?: return "El respaldo apunta a un tema fuera del directorio permitido."

        pathValidator.requireInside(
            safeThemeDir,
            themeConfigFile,
            "configuración del respaldo",
        )
        pathValidator.requireInside(
            safeThemeDir,
            targetBackground,
            "fondo del respaldo",
        )
        null
    } catch (error: PathValidationException) {
        error.message
    }

    private fun restoreDirectly(
        backupDir: File,
        themeConfigFile: File,
        targetBackground: File,
        targetExisted: Boolean,
    ): String? {
        return try {
            copyConfigAtomically(File(backupDir, "theme-config"), themeConfigFile)
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
            return "No se encontró pkexec. Instala PolicyKit (por ejemplo, el paquete \"polkit\") " +
                "para restaurar el respaldo."
        }

        val backupConfig = File(backupDir, "theme-config")
        val backupBackground = File(backupDir, "background")
        return try {
            val script = buildString {
                appendLine("#!/bin/sh")
                appendLine("set -e")
                appendAtomicConfigReplacement(
                    script = this,
                    configFile = themeConfigFile,
                    contentSource = shellQuote(backupConfig.absolutePath),
                )
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

    /**
     * Escribe la configuración en un archivo temporal del mismo directorio y la
     * publica con un rename atómico. Así SDDM nunca ve un archivo a medio escribir.
     * Se resuelve el destino canónico para no reemplazar un posible symlink.
     */
    private fun writeConfigAtomically(configFile: File, content: String) {
        val resolvedTarget = configFile.canonicalFile
        val parent = resolvedTarget.parentFile
            ?: throw IllegalArgumentException(
                "La configuración no tiene directorio padre: ${resolvedTarget.absolutePath}"
            )
        Files.createDirectories(parent.toPath())

        val metadata = readPosixMetadata(resolvedTarget.toPath())
        val temporary = Files.createTempFile(
            parent.toPath(),
            ".${resolvedTarget.name}.aura-",
            ".tmp",
        )
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8)
            applyPosixMetadata(temporary, metadata)
            moveReplacing(temporary, resolvedTarget.toPath())
        } catch (e: Exception) {
            Files.deleteIfExists(temporary)
            throw e
        }
    }

    /**
     * Restaura un archivo de configuración desde un respaldo con la misma
     * estrategia de temporal + rename atómico.
     */
    private fun copyConfigAtomically(source: File, configFile: File) {
        val resolvedTarget = configFile.canonicalFile
        val parent = resolvedTarget.parentFile
            ?: throw IllegalArgumentException(
                "La configuración no tiene directorio padre: ${resolvedTarget.absolutePath}"
            )
        Files.createDirectories(parent.toPath())

        val metadata = readPosixMetadata(resolvedTarget.toPath())
        val temporary = Files.createTempFile(
            parent.toPath(),
            ".${resolvedTarget.name}.aura-",
            ".tmp",
        )
        try {
            Files.copy(source.toPath(), temporary, StandardCopyOption.REPLACE_EXISTING)
            applyPosixMetadata(temporary, metadata)
            moveReplacing(temporary, resolvedTarget.toPath())
        } catch (e: Exception) {
            Files.deleteIfExists(temporary)
            throw e
        }
    }

    /**
     * Clona uid, gid y permisos del archivo original. En un archivo nuevo aplica
     * el modo 644 para que SDDM pueda leerlo.
     */
    private fun applyPosixMetadata(path: Path, metadata: FilePosixMetadata?) {
        if (metadata == null) {
            setDefaultPermissions(path)
            return
        }

        try {
            Files.setOwner(path, metadata.owner)
            Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
                ?.setGroup(metadata.group)
            Files.setPosixFilePermissions(path, metadata.permissions)
        } catch (e: Exception) {
            throw IOException(
                "No se pudieron conservar el propietario, grupo o permisos originales de " +
                    path.fileName,
                e,
            )
        }
    }

    private fun setDefaultPermissions(file: Path) {
        try {
            Files.setPosixFilePermissions(
                file,
                PosixFilePermissions.fromString("rw-r--r--"),
            )
        } catch (_: UnsupportedOperationException) {
            // En sistemas sin POSIX se conservan los permisos por defecto.
        }
    }

    private fun moveReplacing(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readPosixMetadata(path: Path): FilePosixMetadata? = try {
        val attributes = Files.readAttributes(path, PosixFileAttributes::class.java)
        FilePosixMetadata(
            permissions = attributes.permissions().toSet(),
            owner = attributes.owner(),
            group = attributes.group(),
        )
    } catch (_: Exception) {
        null
    }

    /**
     * Añade al script de `pkexec` la sustitución atómica de la configuración.
     * El temporal se crea en el mismo directorio que el destino para que `mv` sea
     * un rename dentro del mismo sistema de archivos.
     */
    private fun appendAtomicConfigReplacement(
        script: StringBuilder,
        configFile: File,
        contentSource: String,
    ) {
        val resolvedTarget = configFile.canonicalFile
        val parent = resolvedTarget.parentFile
            ?: throw IllegalArgumentException(
                "La configuración no tiene directorio padre: ${resolvedTarget.absolutePath}"
            )
        val temporary = File(
            parent,
            ".${resolvedTarget.name}.aura-${UUID.randomUUID()}.tmp",
        )

        script.appendLine("tmp_config=${shellQuote(temporary.absolutePath)}")
        script.appendLine("trap 'rm -f -- \"\$tmp_config\"' 0")
        script.appendLine("if [ -e ${shellQuote(resolvedTarget.absolutePath)} ]; then")
        script.appendLine("  config_meta=\$(stat -c '%u:%g:%a' -- ${shellQuote(resolvedTarget.absolutePath)})")
        script.appendLine("  config_uid=\${config_meta%%:*}")
        script.appendLine("  config_rest=\${config_meta#*:}")
        script.appendLine("  config_gid=\${config_rest%%:*}")
        script.appendLine("  config_mode=\${config_rest#*:}")
        script.appendLine("else")
        script.appendLine("  config_uid=0")
        script.appendLine("  config_gid=0")
        script.appendLine("  config_mode=644")
        script.appendLine("fi")
        script.appendLine("cp -f -- $contentSource \"\$tmp_config\"")
        script.appendLine("chown -- \"\$config_uid:\$config_gid\" \"\$tmp_config\"")
        script.appendLine("chmod -- \"\$config_mode\" \"\$tmp_config\"")
        script.appendLine("mv -f -- \"\$tmp_config\" ${shellQuote(resolvedTarget.absolutePath)}")
        script.appendLine("trap - 0")
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
        backupDir: File,
    ): String? {
        return try {
            backgroundsDir.mkdirs()
            val targetPath = targetBackground.toPath()
            val originalMetadata = readPosixMetadata(targetPath)
            val temporary = Files.createTempFile(
                backgroundsDir.toPath(),
                ".${targetBackground.name}.aura-",
                ".tmp",
            )
            try {
                Files.copy(source.toPath(), temporary, StandardCopyOption.REPLACE_EXISTING)
                applyPosixMetadata(temporary, originalMetadata)
                moveReplacing(temporary, targetPath)
            } catch (e: Exception) {
                Files.deleteIfExists(temporary)
                throw e
            }

            try {
                writeConfigAtomically(configFile, newConfigContent)
                null
            } catch (e: Exception) {
                val rollback = runCatching { rollbackBackground(targetBackground, backupDir) }
                if (rollback.isSuccess) {
                    "No se pudo escribir la configuración del tema: " +
                        "${e.message ?: "error desconocido"}. Se restauró el fondo anterior."
                } else {
                    "No se pudo escribir la configuración del tema: " +
                        "${e.message ?: "error desconocido"}. Además, no se pudo restaurar " +
                        "el fondo anterior: ${rollback.exceptionOrNull()?.message ?: "error desconocido"}"
                }
            }
        } catch (e: Exception) {
            "No se pudo aplicar el fondo: ${e.message ?: "error desconocido"}"
        }
    }

    /**
     * Deshace la copia del fondo nuevo usando el respaldo creado antes de empezar.
     * Si el fondo destino no existía, se elimina el archivo recién creado.
     */
    private fun rollbackBackground(targetBackground: File, backupDir: File) {
        val backup = File(backupDir, "background")
        if (backup.isFile) {
            val metadata = readPosixMetadata(targetBackground.toPath())
            val parent = targetBackground.parentFile
                ?: throw IllegalArgumentException("El fondo no tiene directorio padre.")
            val temporary = Files.createTempFile(
                parent.toPath(),
                ".${targetBackground.name}.aura-rollback-",
                ".tmp",
            )
            try {
                Files.copy(backup.toPath(), temporary, StandardCopyOption.REPLACE_EXISTING)
                applyPosixMetadata(temporary, metadata)
                moveReplacing(temporary, targetBackground.toPath())
            } finally {
                Files.deleteIfExists(temporary)
            }
        } else {
            Files.deleteIfExists(targetBackground.toPath())
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
        backupDir: File,
    ): String? {
        val pkexecPath = getPkexecPath()
        if (pkexecPath.isBlank()) {
            return "No se encontró pkexec. Instala PolicyKit (por ejemplo, el paquete \"polkit\") " +
                "para aplicar cambios en temas protegidos."
        }

        var tempDir: File? = null
        return try {
            tempDir = Files.createTempDirectory("aura-sddm-apply-").toFile()

            val stagedConfig = File(tempDir, "theme.conf")
            stagedConfig.writeText(newConfigContent)

            val resolvedTarget = targetBackground.canonicalFile
            val resolvedConfig = configFile.canonicalFile
            val temporaryBackground = File(
                backgroundsDir,
                ".${resolvedTarget.name}.aura-${UUID.randomUUID()}.tmp",
            )
            val temporaryConfig = File(
                resolvedConfig.parentFile,
                ".${resolvedConfig.name}.aura-${UUID.randomUUID()}.tmp",
            )
            val backupBackground = File(backupDir, "background")

            val scriptContent = buildString {
                appendLine("#!/bin/sh")
                appendLine("set -e")
                appendLine("bg_target=${shellQuote(resolvedTarget.absolutePath)}")
                appendLine("bg_tmp=${shellQuote(temporaryBackground.absolutePath)}")
                appendLine("config_target=${shellQuote(resolvedConfig.absolutePath)}")
                appendLine("tmp_config=${shellQuote(temporaryConfig.absolutePath)}")
                appendLine("bg_backup=${shellQuote(backupBackground.absolutePath)}")
                appendLine("bg_replaced=0")
                appendLine("rollback_bg() {")
                appendLine("  if [ \"\$bg_replaced\" -eq 1 ]; then")
                appendLine("    if [ -f \"\$bg_backup\" ]; then")
                appendLine("      cp -p -- \"\$bg_backup\" \"\$bg_target\"")
                appendLine("    else")
                appendLine("      rm -f -- \"\$bg_target\"")
                appendLine("    fi")
                appendLine("  fi")
                appendLine("}")
                appendLine("cleanup() { rm -f -- \"\$bg_tmp\" \"\$tmp_config\"; }")
                appendLine("on_exit() {")
                appendLine("  status=\$?")
                appendLine("  trap - 0")
                appendLine("  rollback_bg")
                appendLine("  cleanup")
                appendLine("  exit \$status")
                appendLine("}")
                appendLine("trap on_exit 0")
                appendLine("trap 'exit 1' 1 2 3 15")
                appendLine("mkdir -p -- ${shellQuote(backgroundsDir.absolutePath)}")
                appendLine("if [ -e \"\$bg_target\" ]; then")
                appendLine("  bg_meta=\$(stat -c '%u:%g:%a' -- \"\$bg_target\")")
                appendLine("  bg_uid=\${bg_meta%%:*}")
                appendLine("  bg_rest=\${bg_meta#*:}")
                appendLine("  bg_gid=\${bg_rest%%:*}")
                appendLine("  bg_mode=\${bg_rest#*:}")
                appendLine("else")
                appendLine("  bg_uid=0")
                appendLine("  bg_gid=0")
                appendLine("  bg_mode=644")
                appendLine("fi")
                appendLine("cp -f -- ${shellQuote(source.absolutePath)} \"\$bg_tmp\"")
                appendLine("chown -- \"\$bg_uid:\$bg_gid\" \"\$bg_tmp\"")
                appendLine("chmod -- \"\$bg_mode\" \"\$bg_tmp\"")
                appendLine("mv -f -- \"\$bg_tmp\" \"\$bg_target\"")
                appendLine("bg_replaced=1")
                appendLine("if [ -e \"\$config_target\" ]; then")
                appendLine("  config_meta=\$(stat -c '%u:%g:%a' -- \"\$config_target\")")
                appendLine("  config_uid=\${config_meta%%:*}")
                appendLine("  config_rest=\${config_meta#*:}")
                appendLine("  config_gid=\${config_rest%%:*}")
                appendLine("  config_mode=\${config_rest#*:}")
                appendLine("else")
                appendLine("  config_uid=0")
                appendLine("  config_gid=0")
                appendLine("  config_mode=644")
                appendLine("fi")
                appendLine("cp -f -- ${shellQuote(stagedConfig.absolutePath)} \"\$tmp_config\"")
                appendLine("chown -- \"\$config_uid:\$config_gid\" \"\$tmp_config\"")
                appendLine("chmod -- \"\$config_mode\" \"\$tmp_config\"")
                appendLine("mv -f -- \"\$tmp_config\" \"\$config_target\"")
                appendLine("trap - 0")
                appendLine("cleanup")
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

            if (exitCode == 0) null else describePkexecFailure(exitCode, output)
        } catch (e: Exception) {
            "No se pudo ejecutar la operación con permisos de administrador: ${e.message ?: "error desconocido"}"
        } finally {
            tempDir?.let { deleteRecursively(it) }
        }
    }

    private fun getPkexecPath(): String = findExecutable("pkexec") ?: ""

    /** Escapa un valor para usarlo como argumento literal en un script de shell. */
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun readSddmConfigFiles(): List<File> {
        val files = mutableListOf<File>()

        if (sddmConfFile.isFile && sddmConfFile.canRead()) {
            files.add(sddmConfFile)
        }

        if (sddmConfDir.isDirectory) {
            sddmConfDir.listFiles()
                ?.filter { it.isFile && it.canRead() && it.name.endsWith(".conf") }
                ?.sortedBy { it.name }
                ?.let(files::addAll)
        }

        return files
    }

    private fun readBackgroundFromTheme(themeName: String): Pair<File, String>? {
        val themeDir = pathValidator.resolveThemeDir(themeName) ?: return null

        val configuredFile = readConfiguredThemeFile(themeDir)
        if (configuredFile != null) {
            val config = readIniFile(configuredFile)
            val backgroundValue = listOf(
                "loginscreen/background",
                "lockscreen/background",
                "general/background",
                "background"
            ).firstNotNullOfOrNull { key -> config[key]?.takeIf { it.isNotBlank() } }

            pathValidator.resolveBackground(themeDir, backgroundValue)?.let { file ->
                return Pair(file, determineMediaType(file))
            }
        }

        val themeConf = pathValidator.requireInside(
            themeDir,
            File(themeDir, "theme.conf"),
            "configuración tradicional del tema",
        )
        if (themeConf.isFile) {
            val config = readIniFile(themeConf)
            val backgroundValue = listOf("general/background", "background")
                .firstNotNullOfOrNull { key -> config[key]?.takeIf { it.isNotBlank() } }

            pathValidator.resolveBackground(themeDir, backgroundValue)?.let { file ->
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
    private fun createTemporaryTheme(
        sourceThemeDir: File,
        background: File,
        destination: BackgroundDestination,
    ): File? {
        return try {
            cleanupStalePreviewThemes()
            val tempThemeDir = Files.createTempDirectory("aura-sddm-preview-").toFile()
            val configuredPath = readConfiguredThemePath(sourceThemeDir)
            val sourceConfig = configuredPath?.let {
                pathValidator.resolveThemeConfig(sourceThemeDir, it)
            }?.takeIf { it.isFile }
            val configuredRelativePath = sourceConfig?.let {
                relativeToTheme(sourceThemeDir, it)
            }
            val configuredDirName = configuredRelativePath
                ?.substringBefore('/')
                ?.takeIf { it.isNotBlank() }
            val sourceBackgroundsDir = sourceThemeDir.listFiles()
                ?.firstOrNull { it.isDirectory && it.name.equals("backgrounds", ignoreCase = true) }
                ?.let {
                    pathValidator.requireInside(
                        sourceThemeDir,
                        it,
                        "carpeta de fondos del tema",
                    )
                }

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

            val tempBackground = pathValidator.requireInside(
                backgroundsDir,
                File(backgroundsDir, background.name),
                "fondo temporal",
            )
            Files.copy(background.toPath(), tempBackground.toPath(), StandardCopyOption.REPLACE_EXISTING)

            if (sourceConfig != null) {
                val relativePath = relativeToTheme(sourceThemeDir, sourceConfig)
                val tempConfig = File(tempThemeDir, relativePath)
                tempConfig.parentFile?.mkdirs()
                tempConfig.writeText(
                    buildConfiguredThemePreview(
                        content = sourceConfig.readText(),
                        backgroundName = background.name,
                        destination = destination,
                    )
                )
            } else {
                val sourceThemeConf = pathValidator.requireInside(
                    sourceThemeDir,
                    File(sourceThemeDir, "theme.conf"),
                    "configuración tradicional del tema",
                )
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

    private fun relativeToTheme(themeDir: File, file: File): String {
        val resolvedTheme = pathValidator.requireInside(
            themeDir,
            themeDir,
            "raíz del tema",
        )
        val resolvedFile = pathValidator.requireInside(
            themeDir,
            file,
            "archivo de configuración del tema",
        )
        return resolvedTheme.toPath().relativize(resolvedFile.toPath()).toString()
    }

    /**
     * Reescribe un archivo de configuración tipo SilentSDDM para que las pantallas
     * indicadas en [destination] usen [backgroundName] y no un color de fondo. Las
     * pantallas no seleccionadas se conservan intactas.
     */
    private fun buildConfiguredThemePreview(
        content: String,
        backgroundName: String,
        destination: BackgroundDestination,
    ): String {
        val updateLogin = destination == BackgroundDestination.LOGIN ||
            destination == BackgroundDestination.BOTH
        val updateLock = destination == BackgroundDestination.LOCK ||
            destination == BackgroundDestination.BOTH
        val output = mutableListOf<String>()
        var section = ""
        var loginSectionSeen = false
        var lockSectionSeen = false
        var loginBackgroundApplied = false
        var lockBackgroundApplied = false
        var loginColorApplied = false
        var lockColorApplied = false

        fun flushSection() {
            when (section) {
                "loginscreen" -> if (updateLogin) {
                    if (!loginBackgroundApplied) output.add("background = \"$backgroundName\"")
                    if (!loginColorApplied) output.add("use-background-color = false")
                }

                "lockscreen" -> if (updateLock) {
                    if (!lockBackgroundApplied) output.add("background = \"$backgroundName\"")
                    if (!lockColorApplied) output.add("use-background-color = false")
                }
            }
        }

        content.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()

            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                flushSection()
                section = trimmed.substring(1, trimmed.length - 1).trim().lowercase()
                if (section == "loginscreen") loginSectionSeen = true
                if (section == "lockscreen") lockSectionSeen = true
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
            if (section == "loginscreen" && updateLogin) {
                when (key) {
                    "background" -> {
                        output.add("background = \"$backgroundName\"")
                        loginBackgroundApplied = true
                        return@forEach
                    }

                    "use-background-color" -> {
                        output.add("use-background-color = false")
                        loginColorApplied = true
                        return@forEach
                    }
                }
            }
            if (section == "lockscreen" && updateLock) {
                when (key) {
                    "background" -> {
                        output.add("background = \"$backgroundName\"")
                        lockBackgroundApplied = true
                        return@forEach
                    }

                    "use-background-color" -> {
                        output.add("use-background-color = false")
                        lockColorApplied = true
                        return@forEach
                    }
                }
            }

            output.add(rawLine)
        }

        flushSection()

        if (updateLogin && !loginSectionSeen) {
            output.add("")
            output.add("[LoginScreen]")
            output.add("background = \"$backgroundName\"")
            output.add("use-background-color = false")
        }
        if (updateLock && !lockSectionSeen) {
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
        return pathValidator.resolveThemeConfig(themeDir, configPath).takeIf { it.isFile }
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

    private fun restoreThemeBackup(
        backupDir: File,
        backup: SddmBackup,
    ): RestoreBackupResult {
        val manifestFile = File(backupDir, backupManifestName)
        val manifest = Properties().apply {
            manifestFile.inputStream().use { load(it) }
        }
        val configFile = manifest.getProperty("sddm_config")?.let(::File)
            ?: return RestoreBackupResult.Failure(
                "El respaldo no contiene la ruta de configuración de SDDM."
            )
        val configExisted = manifest.getProperty("sddm_config_existed")
            ?.toBooleanStrictOrNull() ?: false
        val currentBgExisted = manifest.getProperty("current_bg_existed")
            ?.toBooleanStrictOrNull() ?: false

        validateThemeConfigPath(configFile)?.let { error ->
            return RestoreBackupResult.Failure(error)
        }
        if (configExisted && !File(backupDir, "sddm-config").isFile) {
            return RestoreBackupResult.Failure(
                "El respaldo no contiene la configuración de SDDM."
            )
        }
        if (currentBgExisted && !File(backupDir, "current-bg.txt").isFile) {
            return RestoreBackupResult.Failure(
                "El respaldo no contiene el registro del fondo aplicado."
            )
        }

        val configWritable = isConfigFileWritable(configFile)
        if (!configWritable && getPkexecPath().isBlank()) {
            return RestoreBackupResult.Failure(
                "El tema está protegido y no se puede restaurar sin permisos de administrador. " +
                    "No se encontró pkexec: instala PolicyKit (por ejemplo, el paquete " +
                    "\"polkit\") para continuar."
            )
        }

        val error = if (configWritable) {
            restoreThemeConfigDirectly(backupDir, configFile, configExisted)
        } else {
            restoreThemeConfigWithElevatedPrivileges(backupDir, configFile, configExisted)
        }
        if (error != null) {
            return RestoreBackupResult.Failure(error)
        }

        restoreAppliedBackgroundFile(backupDir, currentBgExisted)
        println("Respaldo de tema restaurado: ${backupDir.absolutePath}")
        return RestoreBackupResult.Success(backup)
    }

    private fun validateThemeConfigPath(configFile: File): String? = try {
        pathValidator.resolveSddmConfig(configFile)
        null
    } catch (error: PathValidationException) {
        error.message
    }

    private fun restoreThemeConfigDirectly(
        backupDir: File,
        configFile: File,
        configExisted: Boolean,
    ): String? {
        return try {
            val target = configFile.canonicalFile
            if (configExisted) {
                copyConfigAtomically(File(backupDir, "sddm-config"), target)
            } else {
                Files.deleteIfExists(target.toPath())
            }
            null
        } catch (e: Exception) {
            "No se pudo restaurar la configuración de SDDM: ${e.message ?: "error desconocido"}"
        }
    }

    private fun restoreThemeConfigWithElevatedPrivileges(
        backupDir: File,
        configFile: File,
        configExisted: Boolean,
    ): String? {
        val pkexecPath = getPkexecPath()
        if (pkexecPath.isBlank()) {
            return "No se encontró pkexec. Instala PolicyKit (por ejemplo, el paquete " +
                "\"polkit\") para restaurar el tema."
        }

        return try {
            val target = configFile.canonicalFile
            val script = buildString {
                appendLine("#!/bin/sh")
                appendLine("set -e")
                if (configExisted) {
                    appendAtomicConfigReplacement(
                        script = this,
                        configFile = target,
                        contentSource = shellQuote(
                            File(backupDir, "sddm-config").absolutePath
                        ),
                    )
                } else {
                    appendLine("rm -f -- ${shellQuote(target.absolutePath)}")
                }
            }
            runPkexecScript(pkexecPath, script)
        } catch (e: Exception) {
            "No se pudo restaurar la configuración de SDDM con permisos de administrador: " +
                "${e.message ?: "error desconocido"}"
        }
    }

    private fun writeThemeConfigWithElevatedPrivileges(
        configFile: File,
        content: String,
    ): String? {
        val pkexecPath = getPkexecPath()
        if (pkexecPath.isBlank()) {
            return "No se encontró pkexec. Instala PolicyKit (por ejemplo, el paquete " +
                "\"polkit\") para cambiar el tema."
        }

        var tempDir: File? = null
        return try {
            tempDir = Files.createTempDirectory("aura-sddm-theme-").toFile()
            val stagedConfig = File(tempDir, "sddm-theme.conf")
            stagedConfig.writeText(content)

            val script = buildString {
                appendLine("#!/bin/sh")
                appendLine("set -e")
                appendAtomicConfigReplacement(
                    script = this,
                    configFile = configFile,
                    contentSource = shellQuote(stagedConfig.absolutePath),
                )
            }
            runPkexecScript(pkexecPath, script)
        } catch (e: Exception) {
            "No se pudo cambiar el tema: ${e.message ?: "error desconocido"}"
        } finally {
            tempDir?.let { deleteRecursively(it) }
        }
    }

    private fun themeConfigTarget(): File {
        val target = if (sddmConfDir.isDirectory) {
            File(sddmConfDir, "99-aura-theme.conf")
        } else {
            sddmConfFile
        }
        return pathValidator.resolveSddmConfig(target)
    }

    private fun isConfigFileWritable(file: File): Boolean =
        if (file.exists()) {
            Files.isWritable(file.toPath())
        } else {
            file.parentFile?.let { Files.isWritable(it.toPath()) } ?: false
        }

    private fun resolveThemeDir(themeName: String): File? =
        pathValidator.resolveThemeDir(themeName)

    private fun findThemeConfigFile(themeDir: File): File? {
        val configuredPath = readConfiguredThemePath(themeDir)
        if (configuredPath != null) {
            pathValidator.resolveThemeConfig(themeDir, configuredPath)
                .takeIf { it.isFile }
                ?.let { return it }
        }
        return pathValidator.requireInside(
            themeDir,
            File(themeDir, "theme.conf"),
            "configuración tradicional del tema",
        ).takeIf { it.isFile }
    }

    private fun readThemeDisplayName(themeDir: File): String {
        val metadata = File(themeDir, "metadata.desktop")
        if (metadata.isFile) {
            try {
                var displayName = ""
                metadata.useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("Name=")) {
                            displayName = trimmed.substringAfter('=').trim()
                        }
                    }
                }
                if (displayName.isNotBlank()) return displayName
            } catch (e: Exception) {
                println("No se pudo leer el nombre del tema ${themeDir.name}: ${e.message}")
            }
        }
        return themeDir.name
    }

    private fun findExecutable(name: String): String? {
        val directories = buildList {
            addAll(System.getenv("PATH")?.split(File.pathSeparatorChar).orEmpty())
            add("/usr/local/bin")
            add("/usr/bin")
            add("/bin")
        }

        return directories.asSequence()
            .filter { it.isNotBlank() }
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }

    /**
     * Detecta un agente de autenticación Polkit en ejecución. El demonio del
     * sistema (`polkitd`) no cuenta: hace falta el agente de la sesión gráfica.
     */
    private fun findPolkitAgent(): String? {
        val markers = listOf(
            "polkit-gnome-authentication-agent",
            "polkit-kde-authentication-agent",
            "polkit-mate-authentication-agent",
            "xfce-polkit",
            "lxpolkit",
            "polkit-1-auth-agent",
        )
        val processes = ProcessHandle.allProcesses()
        return try {
            processes
                .map { process ->
                    runCatching { process.info().commandLine().orElse("") }.getOrDefault("")
                }
                .filter { commandLine ->
                    markers.any { marker -> commandLine.contains(marker, ignoreCase = true) }
                }
                .findFirst()
                .orElse(null)
        } catch (e: Exception) {
            null
        } finally {
            processes.close()
        }
    }

    private fun determineMediaType(file: File): String {
        return if (file.extension.lowercase() in videoExtensions) "video" else "image"
    }
}

private data class FilePosixMetadata(
    val permissions: Set<PosixFilePermission>,
    val owner: UserPrincipal,
    val group: GroupPrincipal,
)

/**
 * Traduce los códigos de salida de `pkexec` a mensajes accionables. Los códigos
 * 125, 126 y 127 distinguen un problema de PolicyKit, una cancelación del usuario
 * y una autorización denegada.
 */
internal fun describePkexecFailure(exitCode: Int, output: String): String {
    val message = when (exitCode) {
        125 -> "PolicyKit no pudo iniciar la autenticación. Comprueba que tu escritorio " +
            "tenga un agente Polkit en ejecución (por ejemplo, polkit-gnome, polkit-kde o " +
            "xfce-polkit)."

        126 -> "Autenticación cancelada. No se aplicó ningún cambio."
        127 -> "No se autorizó la operación. Se requieren permisos de administrador."
        else -> "La operación con permisos de administrador falló (código $exitCode)."
    }

    return if (output.isBlank()) message else "$message Detalle: $output"
}
