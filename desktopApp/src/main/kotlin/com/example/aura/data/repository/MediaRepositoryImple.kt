package com.example.aura.data.repository

import com.example.aura.data.local.CreateMediaDirectory
import com.example.aura.domain.repository.MediaRepository
import com.example.aura.presentation.ui.components.CustomAlertDialog
import java.io.File

class MediaRepositoryImple: MediaRepository {
    private val mediaDir = CreateMediaDirectory()
    // Define las extensiones permitidas en minúsculas
    private val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    private val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm")
    private val configFile = File(System.getProperty("user.home"),".local/share/Aura/current_bg.txt")

    // 1. Función utilitaria para deducir el tipo por la extensión
    private fun determineMediaType(file: File): String {
        return if (file.extension.lowercase() in videoExtensions) {
            "video"
        } else {
            "image"
        }
    }
    override fun createMediaDirectory(media: File) {
        val destinationFile = File(mediaDir, media.name)
        media.copyTo(destinationFile, overwrite = true)
    }

    override fun getAppliedbackground(): Pair<File, String>? {
        if (configFile.exists()) {
            val savepaht = configFile.readText().trim()
            val file = File(savepaht)
            if (file.exists()) {
                return Pair(file, determineMediaType(file))
            }
        }
        return getCurrentSDDMBackground()
    }

    private fun getCurrentSDDMBackground(): Pair<File, String>?{
        try {
            var currentTheme:String = ""
            val sddmConfFiles = mutableListOf<File>()
            val mainConf = File("/stc/sddm.conf")
            val confFolder = File("/stcsddm.conf.d/")

            if (mainConf.exists()) {sddmConfFiles.add(confFolder)}
            if (confFolder.exists() && confFolder.isDirectory) {
                confFolder.listFiles()?.let { sddmConfFiles.addAll(it) }
            }

            //Recorremos linea por linea de los archivos para poder encontrar la ruta
            for(file in sddmConfFiles){
                file.useLines { lines ->
                    lines.forEach { line ->
                        if(line.trim().startsWith("Current=")) {
                            currentTheme = line.split("=")[1].trim()
                        }
                    }
                }
            }

            if(currentTheme.isEmpty()){
                currentTheme = "breeze"
            }

            // 2. LEER LA IMAGEN DE ESE TEMA
            val themeDir = File("/usr/share/sddm/themes/$currentTheme")
            val themeConf = File(themeDir, "theme.conf")

            if (themeConf.exists()) {
                var bgPath = ""
                themeConf.useLines { lines ->
                    lines.forEach { line ->
                        if (line.trim().startsWith("background=")) {
                            bgPath = line.split("=")[1].trim()
                        }
                    }
                }

                if (bgPath.isNotEmpty()) {
                    // La ruta del fondo en SDDM a veces es absoluta y a veces relativa a la carpeta del tema
                    val bgFile = if (bgPath.startsWith("/")) {
                        File(bgPath)
                    } else {
                        File(themeDir, bgPath)
                    }

                    if (bgFile.exists()) {
                        return Pair(bgFile, determineMediaType(bgFile))
                    }
                }
            }
        }catch (e:Exception){
            println("Error leyendo el archivo de configuracion ${e.message}")
        }
        return null
    }

    override fun saveAppliedbackground(file: File) {
        configFile.parentFile?.mkdirs()
        configFile.writeText(file.absolutePath)
    }

    // Define las extensiones permitidas en minúsculas
    override fun getImageFiles(): List<File> {
        //println("getImageFiles ${mediaDir.listFiles().contentToString()}")
        return mediaDir.listFiles()?.filter { archivo ->
            archivo.extension.lowercase() in imageExtensions
        } ?: emptyList()
    }

    override fun getVideoFiles(): List<File> {
        //println("getVideoFiles ${mediaDir.listFiles().contentToString()}")
        return mediaDir.listFiles()?.filter { archivo ->
            archivo.extension.lowercase() in videoExtensions
        } ?: emptyList()
    }

    override fun deletedFile(file: File): Boolean {
        return if (file.exists()) {
            file.delete() // Retorna true si se borró con éxito
        } else {
            false
        }
    }

}