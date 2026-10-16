package com.example.aura.presentation.viewmodels

import androidx.compose.runtime.mutableStateOf
import com.example.aura.data.repository.MediaRepositoryImple
import com.example.aura.domain.repository.MediaRepository
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.concurrent.thread

class MediaViewModel(
    private val mediaRepository: MediaRepository = MediaRepositoryImple()
){
    var listImages by mutableStateOf<List<File>>(emptyList())
    private set
    var listVideo by mutableStateOf<List<File>>(emptyList())
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var selectedFile by mutableStateOf<File?>(null)
        private set

    var selectedTypeFile by mutableStateOf<String?>(null)
        private set

    var fileApplied by mutableStateOf< Pair<File, String>?>(null)
        private set

    var viewDialogDeleted by mutableStateOf<Boolean>(false)
        private set

    fun toggleViewDialog() {
        viewDialogDeleted = !viewDialogDeleted
    }

    fun loadImages(){
        listImages = mediaRepository.getImageFiles()
        print("loadImages $listImages")
    }

    fun loadVideos(){
        listVideo = mediaRepository.getVideoFiles()
        print("loadVideo $listVideo")
    }

    fun loadFileApplied(){
        fileApplied = mediaRepository.getAppliedbackground()
    }

    fun importNewFile(file: File){
        try {
            mediaRepository.createMediaDirectory(file)
            errorMessage = null
            loadImages()
            loadVideos()
        } catch (e: Exception) {
            val detail = e.message ?: "error desconocido"
            errorMessage = "No se pudo importar \"${file.name}\". Detalle: $detail"
        }
    }

    fun clearError() {
        errorMessage = null
    }

    fun selectFile(file: File, type: String){
        print("Selecione o file ${file.name}")
        selectedFile = file
        selectedTypeFile = type
    }

    fun deleteMedia(file: File?) {
        if(file == null) return
        val success = mediaRepository.deletedFile(file)
        if (success) {
            // Si se borró del disco, volvemos a cargar la lista de la carpeta.
            // Esto hará que el archivo desaparezca mágicamente de la pantalla.
            loadImages()

            // Opcional: Si el archivo borrado era el que estaba seleccionado como "borrador", lo limpiamos
            if (selectedFile?.absolutePath == file.absolutePath) {
                selectedFile = null
            }
        }
    }

    fun getPahtSddm(): String?{
        val possiblePaths = listOf(
            "/usr/bin/sddm-greeter-qt6",     // Tu ruta exacta en PikaOS
            "/usr/bin/sddm-greeter",         // Clásico
            "/usr/libexec/sddm-greeter-qt6",
            "/usr/libexec/sddm-greeter",
            "/usr/lib/sddm/sddm-greeter",
            "/usr/lib/x86_64-linux-gnu/sddm/sddm-greeter"
        )
        val executablePaht = possiblePaths.find { java.io.File(it).exists() }
        return executablePaht
    }

    fun getCurrentThemeSddm(): String{
        return mediaRepository.getCurrentTheme()
    }

    fun launchSddmPreview(){
        thread {
            try {
                val paht = getPahtSddm()
                if(paht != null){
                    // Tumchya active theme cha path ithe taka (udaharanarth "breeze", "maldives", kiva ji theme tumhi vapartay)
                    val currentThemeName = getCurrentThemeSddm()
                    val themePath = "/usr/share/sddm/themes/$currentThemeName"

                    println("Lanzando previsualización con tema: $themePath")
                    println("Ejecutando SDDM Greeter desde: $paht")
                    //Ejecutamos el codigo nativo del sddm
                    val process = ProcessBuilder(paht, "--test-mode", "--theme", themePath)
                        .inheritIO().start()
                    //esperar a que el usuario cierre la ventana
                    process.waitFor()
                }else {
                    // Si no existe en ninguna parte (el usuario no tiene SDDM instalado)
                    println("Error: No se encontró sddm-greeter en el sistema.")
                    // Opcional: Aquí podrías cambiar una variable para mostrar un CustomAlertDialog
                }
            }
            catch (e: Exception){
                println("Error al intentar abrir el preview de SDDM: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}