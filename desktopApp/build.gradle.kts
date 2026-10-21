import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.compose.uiToolingPreview)

    implementation(libs.compose.components.resources)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}

// Desde JDK 24 el acceso nativo (Skiko, FFmpeg, etc.) emite un warning si no se
// habilita explícitamente. La opción es válida también en JDK 16+.
val nativeAccessArgs = listOf("--enable-native-access=ALL-UNNAMED")

compose.desktop {
    application {
        mainClass = "com.example.aura.MainKt"
        jvmArgs(*nativeAccessArgs.toTypedArray())

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.example.aura"
            packageVersion = "1.0.0"
        }
    }
}

// Compose Hot Reload ('hotRun'/'hotDev') crea su propio JavaExec y no hereda los
// 'jvmArgs' de compose.desktop.application, por eso se configuran aparte.
tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("hotRun") || name.startsWith("hotDev")) {
        jvmArgs(*nativeAccessArgs.toTypedArray())
    }
}
