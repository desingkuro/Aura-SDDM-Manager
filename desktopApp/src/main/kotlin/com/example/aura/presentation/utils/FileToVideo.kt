package com.example.aura.presentation.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import java.io.File

fun fileToVideoBitmap(file: File?): ImageBitmap? {
    if (file == null || !file.exists()) return null

    return try {
        val thumbnail = File.createTempFile("aura-video-thumb-", ".png")
        thumbnail.deleteOnExit()

        val process = ProcessBuilder(
            "ffmpeg",
            "-y",
            "-i", file.absolutePath,
            "-frames:v", "1",
            thumbnail.absolutePath
        ).start()

        val exitCode = process.waitFor()
        if (exitCode != 0 || !thumbnail.exists()) return null

        SkiaImage.makeFromEncoded(thumbnail.readBytes()).toComposeImageBitmap()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}