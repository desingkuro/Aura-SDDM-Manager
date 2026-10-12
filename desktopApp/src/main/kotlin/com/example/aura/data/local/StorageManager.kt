package com.example.aura.data.local

import java.io.File

fun CreateMediaDirectory(): File{
    val userHomeDir = System.getProperty("user.home")
    val appDir = File(userHomeDir, ".local/share/Aura/media")
    if (!appDir.exists()) {
        appDir.mkdirs()
    }
    return appDir
}