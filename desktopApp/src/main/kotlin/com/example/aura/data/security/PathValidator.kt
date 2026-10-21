package com.example.aura.data.security

import java.io.File
import java.io.IOException

/**
 * Error de seguridad producido cuando una ruta de configuración o un nombre de
 * tema intenta salir del directorio permitido.
 */
class PathValidationException(message: String) : SecurityException(message)

/**
 * Validación canónica de todas las rutas que Aura lee o escribe.
 *
 * La regla es simple: después de resolver symlinks y `..`, el resultado debe
 * seguir dentro de la raíz esperada. Esto se aplica especialmente a valores
 * provenientes de `metadata.desktop`, que no deben poder apuntar a
 * `/etc/shadow` ni a archivos fuera del tema.
 */
class PathValidator(
    private val themesRoot: File,
    private val sddmConfFile: File,
    private val sddmConfDir: File,
) {
    private val themesRootCanonical: File = canonical(themesRoot, "carpeta de temas")
    private val allowedSddmConfigs: Set<File> = setOf(
        canonical(sddmConfFile, "configuración de SDDM"),
        canonical(File(sddmConfDir, "99-aura-theme.conf"), "configuración de Aura"),
    )

    /**
     * Resuelve un nombre de tema y comprueba que el directorio real siga dentro
     * de la raíz de temas. Devuelve `null` si el nombre es inválido o no existe.
     */
    fun resolveThemeDir(themeName: String): File? {
        if (themeName.isBlank() ||
            themeName.contains('/') ||
            themeName.contains('\\') ||
            themeName.contains("..")
        ) {
            return null
        }

        val candidate = canonicalOrNull(File(themesRoot, themeName)) ?: return null
        if (!candidate.isDirectory) return null
        if (candidate.parentFile != themesRootCanonical) return null
        return candidate
    }

    /**
     * Resuelve el `ConfigFile` de un tema y exige que el archivo real esté dentro
     * del directorio del tema.
     */
    fun resolveThemeConfig(themeDir: File, rawPath: String): File {
        if (rawPath.isBlank()) {
            throw PathValidationException("El tema no declara un archivo de configuración.")
        }

        val raw = File(rawPath)
        val candidate = if (raw.isAbsolute) raw else File(themeDir, rawPath)
        return requireInside(themeDir, candidate, "configuración del tema")
    }

    /**
     * Resuelve un valor de `background` respetando las variantes que usan los
     * temas: ruta absoluta, relativa al tema o relativa a `backgrounds/`.
     *
     * Si encuentra un archivo existente fuera del tema, aborta en vez de leerlo.
     */
    fun resolveBackground(themeDir: File, rawValue: String?): File? {
        val value = rawValue?.trim()?.trim('"', '\'')?.removePrefix("./").orEmpty()
        if (value.isBlank()) return null

        val candidates = mutableListOf<File>()
        if (value.startsWith("/")) {
            candidates.add(File(value))
        }
        candidates.add(File(themeDir, value))
        candidates.add(File(themeDir, "backgrounds/$value"))

        var outsideTheme: File? = null
        candidates.forEach { candidate ->
            if (!candidate.exists()) return@forEach
            val resolved = canonicalOrNull(candidate) ?: return@forEach
            if (!isInside(themeDir, resolved)) {
                outsideTheme = resolved
                return@forEach
            }
            if (resolved.isFile && resolved.canRead()) return resolved
        }

        outsideTheme?.let {
            throw PathValidationException(
                "El fondo configurado apunta fuera del tema: ${it.absolutePath}"
            )
        }
        return null
    }

    /** Resuelve y valida una ruta que debe quedar dentro de [root]. */
    fun requireInside(root: File, candidate: File, description: String): File {
        val resolvedRoot = canonical(root, description)
        val resolvedCandidate = canonical(candidate, description)
        if (!resolvedCandidate.toPath().startsWith(resolvedRoot.toPath())) {
            throw PathValidationException(
                "La $description apunta fuera de la ruta permitida: " +
                    resolvedCandidate.absolutePath
            )
        }
        return resolvedCandidate
    }

    /** Comprueba si [candidate], ya canónico, está dentro de [root]. */
    fun isInside(root: File, candidate: File): Boolean =
        candidate.toPath().startsWith(canonical(root, "raíz").toPath())

    /**
     * Solo se permite escribir en `/etc/sddm.conf` o en el drop-in de Aura.
     */
    fun resolveSddmConfig(configFile: File): File {
        val resolved = canonical(configFile, "configuración de SDDM")
        if (resolved !in allowedSddmConfigs) {
            throw PathValidationException(
                "La configuración de SDDM apunta fuera de las rutas permitidas: " +
                    resolved.absolutePath
            )
        }
        return resolved
    }

    private fun canonical(file: File, description: String): File =
        canonicalOrNull(file) ?: throw PathValidationException(
            "No se pudo resolver la ruta de $description: ${file.absolutePath}"
        )

    private fun canonicalOrNull(file: File): File? = try {
        file.canonicalFile
    } catch (_: IOException) {
        null
    }
}
