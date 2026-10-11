# Proyecto: Aura SDDM Manager

**Aura SDDM Manager** (o simplemente *Aura*) es una aplicación de escritorio multiplataforma (enfocada en Linux) construida con Kotlin y Compose Multiplatform. Su objetivo es proporcionar una interfaz gráfica moderna, intuitiva y segura para gestionar, personalizar y previsualizar temas de SDDM (Simple Desktop Display Manager), con un enfoque especial en temas avanzados como *SilentSDDM*.

## 1. Requerimientos del Sistema

### Funcionales

* **Gestión de Fondos de Pantalla:** Permitir al usuario seleccionar archivos locales (`.jpg`, `.png`) y aplicarlos como fondo del tema.

* **Soporte de Fondos Animados:** Permitir la selección de archivos de video (`.mp4`, `.mkv`) si el tema lo soporta, actualizando la configuración de SDDM en consecuencia.

* **Gestión de Fuentes:** Listar fuentes instaladas en el sistema del usuario y permitir inyectarlas en el tema SDDM.

* **Previsualización en Vivo:** Integrar un botón que ejecute `sddm-greeter --test-mode` para que el usuario pueda ver los cambios antes de reiniciar su PC.

* **Gestión de Privilegios Segura:** El aplicativo no debe ejecutarse como `root`. Debe solicitar la contraseña del usuario (vía Polkit/`pkexec`) únicamente en el instante de escribir en `/usr/share/sddm/` o `/etc/sddm.conf`.

* **Compatibilidad Multi-distro:** No debe depender de gestores de paquetes específicos (apt, pacman). Debe utilizar comandos universales de bash y utilidades estándar de Linux.

### No Funcionales

* **Arquitectura:** Seguir los principios de Clean Architecture (Presentation, Domain, Data).

* **UI/UX:** Interfaz minimalista inspirada en Material Design 3 / Libadwaita usando Jetpack Compose.

* **Rendimiento:** Las operaciones de I/O y ejecución de comandos bash deben ser asíncronas para no bloquear el hilo de la interfaz de usuario.

## 2. Tecnologías y Librerías (Tech Stack)

### Core

* **Lenguaje:** Kotlin.

* **Framework UI:** Compose Multiplatform (Desktop).

* **Build System:** Gradle (Kotlin DSL).

### Librerías Recomendadas

* **Coroutines (`org.jetbrains.kotlinx:kotlinx-coroutines-core`):** Para manejar la ejecución de los comandos Bash y lectura de archivos en hilos de fondo.

* **Koin (`io.insert-koin:koin-core`):** Para la Inyección de Dependencias (DI). Es muy ligero y perfecto para proyectos Kotlin Desktop.

* **Napier o SLF4J:** Para tener un sistema de *logging* decente y registrar cuándo falla un comando del sistema.

* **`java.lang.ProcessBuilder` (Nativo):** No necesitas una librería externa para ejecutar bash; usarás la API estándar de Java/Kotlin para lanzar comandos como `pkexec cp ...`.

* *(Opcional)* **Config4k o un parser de INI:** Para leer y modificar fácilmente los archivos `theme.conf` de SDDM sin usar expresiones regulares complejas.

## 3. Estructura de Directorios (Clean Architecture)

El código fuente (`src/jvmMain/kotlin/`) debe dividirse en capas claras para que el proyecto sea modular y fácil de probar:

```
com.kurodev.aura
│
├── 📁 di                 # Módulos de Inyección de Dependencias (Koin)
│
├── 📁 presentation       # Capa de Interfaz Gráfica (Compose)
│   ├── 📁 ui
│   │   ├── 📁 screens    # Pantallas principales (MainScreen, SettingsScreen)
│   │   ├── 📁 components # Componentes reutilizables (Botones, Tarjetas, FilePicker)
│   │   └── 📁 theme      # Colores, Tipografía de Compose
│   └── 📁 viewmodels     # Manejo de estado de la UI (MVI o MVVM)
│
├── 📁 domain             # Capa de Lógica de Negocio (Pura, sin dependencias de framework)
│   ├── 📁 models         # Clases de datos (Ej. ThemeConfig, BackgroundType)
│   ├── 📁 repository     # Interfaces de los repositorios (Ej. ISddmRepository)
│   └── 📁 usecases       # Casos de uso específicos:
│       ├── ApplyBackgroundUseCase.kt
│       ├── PreviewThemeUseCase.kt
│       └── LoadThemeConfigUseCase.kt
│
└── 📁 data               # Capa de Datos y Sistema
    ├── 📁 repository     # Implementación real de ISddmRepository
    └── 📁 system         # Clases que interactúan directamente con Linux
        ├── CommandExecutor.kt  # Wrapper para ProcessBuilder (maneja pkexec)
        └── ConfigManager.kt    # Lee/Escribe los archivos .conf

```

## 4. Estrategia de Ejecución de Comandos (El "Secreto" del App)

Para mantener la aplicación segura y compatible con cualquier distro (Debian, Arch, Fedora), **nunca debes ejecutar la app entera como sudo**.

La capa `CommandExecutor.kt` usará `pkexec` (Polkit). Un ejemplo del flujo interno será:

1. El usuario escoge una imagen en la UI (ruta: `/home/user/imagen.jpg`).

2. El ViewModel llama al UseCase `ApplyBackgroundUseCase`.

3. El Repositorio invoca al Executor construyendo este comando nativo:
   `pkexec bash -c "cp /home/user/imagen.jpg /usr/share/sddm/themes/silent/backgrounds/ && sed -i 's/Background=.*/Background=imagen.jpg/' /usr/share/sddm/themes/silent/theme.conf"`

4. Polkit levantará automáticamente la ventana nativa del sistema operativo pidiendo la contraseña al usuario.

## 5. Siguientes Pasos

1. **Inicializar el proyecto:** Crear un proyecto de Compose Multiplatform Desktop usando IntelliJ IDEA o la plantilla oficial de JetBrains.

2. **Crear el `CommandExecutor`:** Antes de hacer UI, programar la clase que lanza procesos bash y probar que `pkexec` funciona correctamente desde Kotlin.

3. **Diseñar la UI:** Crear la vista principal con botones para seleccionar fondos y probar la previsualización.


This is a Kotlin Multiplatform project targeting Desktop (JVM).

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Desktop app:
    - Hot reload: `./gradlew :desktopApp:hotRun --auto`
    - Standard run: `./gradlew :desktopApp:run`

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Desktop tests: `./gradlew :shared:jvmTest`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…