# Aura SDDM Manager

Aura es una aplicación de escritorio construida con **Kotlin** y **Compose Multiplatform** para gestionar fondos de pantalla de **SDDM** en Linux. Actualmente se encuentra en desarrollo activo sobre la rama `dev`.

## Estado actual

La aplicación ya cuenta con una base funcional para:

- Importar imágenes desde el sistema.
- Importar videos y generar una miniatura con `ffmpeg` cuando está disponible.
- Mostrar una galería de fondos importados.
- Seleccionar un fondo y ver una previsualización dentro de la aplicación.
- Eliminar fondos importados con diálogo de confirmación.
- Detectar y mostrar el fondo actualmente aplicado.
- Lanzar una previsualización del tema con `sddm-greeter --test-mode` cuando está disponible.
- Aplicar el fondo seleccionado (imagen o video) al tema SDDM, con confirmación y permisos de administrador vía `pkexec` cuando es necesario.
- Mostrar errores de importación mediante un diálogo visual.
- Navegar entre secciones desde una barra lateral.

## Tecnologías principales

- Kotlin
- Compose Multiplatform Desktop
- Gradle Kotlin DSL
- Clean Architecture básica (`presentation`, `domain`, `data`)
- `ProcessBuilder` / `ffmpeg` para generación de miniaturas de video
- `ProcessBuilder` / `sddm-greeter` para previsualización de temas SDDM
- `pkexec` / PolicyKit para aplicar cambios en el tema SDDM con privilegios

## Requisitos

- JDK 17 o superior
- Gradle Wrapper incluido en el repositorio (`./gradlew`)
- Linux con escritorio compatible si se desea aplicar cambios en SDDM
- `ffmpeg` instalado para generar miniaturas de videos
- `sddm-greeter` instalado si se desea previsualizar el tema localmente
- `pkexec` (PolicyKit) para aplicar cambios en el tema SDDM

Para comprobar `ffmpeg`:

```bash
ffmpeg -version
```

Para comprobar `sddm-greeter`:

```bash
command -v sddm-greeter sddm-greeter-qt6
```

## Cómo ejecutar

Ejecutar la aplicación desktop:

```bash
./gradlew :desktopApp:run
```

Ejecutar con recarga en caliente:

```bash
./gradlew :desktopApp:hotRun --auto
```

Compilar el módulo principal:

```bash
./gradlew :desktopApp:compileKotlin
```

Ejecutar pruebas compartidas:

```bash
./gradlew :shared:jvmTest
```

## Estructura principal del proyecto

```text
Aura/
├── desktopApp/
│   └── src/main/kotlin/com/example/aura/
│       ├── data/                 # Implementaciones de almacenamiento y acceso local
│       ├── domain/               # Modelos e interfaces de repositorio
│       └── presentation/         # UI Compose, navegación, utilidades y viewmodels
├── shared/                       # Código compartido de Kotlin Multiplatform
└── gradle/                       # Configuración de dependencias y wrapper
```

## Arquitectura actual

### Presentation

Contiene la interfaz de usuario, navegación, componentes visuales, utilidades de archivos y viewmodels.

- `App`: punto de composición principal.
- `ImageScreen` / `VideoScreeen`: pantallas para fondos estáticos y animados.
- `components/`: sidebar, tarjetas, header, footer, diálogos y panel principal.
- `viewmodels/`: estados de imágenes, videos, selección, fondo aplicado, errores y previsualización SDDM.
- `utils/`: selector de archivos y conversión a `ImageBitmap`.

### Domain

Contiene contratos y modelos independientes de la UI.

- `MediaRepository`: contrato para listar, importar y eliminar archivos multimedia.
- `SddmRepository`: contrato para detectar tema/fondo, ubicar el greeter y lanzar la previsualización.
- `models/`: modelos base de la aplicación.

### Data

Contiene las implementaciones de repositorio y utilidades de almacenamiento local.

- `MediaRepositoryImple`: filtra imágenes/videos y copia archivos al almacenamiento local.
- `SddmRepositoryImpl`: detecta el tema actual, resuelve el fondo aplicado y lanza `sddm-greeter`.
- `StorageManager`: crea/usa el directorio local de medios.

## Consideraciones actuales

- Las imágenes se cargan directamente desde archivos locales.
- Los videos se representan mediante una miniatura extraída con `ffmpeg`.
- La previsualización con `sddm-greeter` busca el ejecutable en rutas comunes de Linux.
- La previsualización construye un **tema temporal** en `/tmp`: enlaza el tema actual, copia el fondo seleccionado en `backgrounds/` y reescribe la configuración para que las pantallas de login y bloqueo usen ese fondo. Al cerrar la ventana del greeter, el tema temporal se elimina automáticamente.
- El tema actual se lee desde `/etc/sddm.conf` y, si existe, desde `/etc/sddm.conf.d` en orden alfabético.
- Para temas tradicionales, el fondo se obtiene desde `theme.conf` (`[General] background`).
- Para SilentSDDM, el fondo se obtiene desde `metadata.desktop` → `ConfigFile=` → `configs/*.conf`, leyendo `[LoginScreen] background` o `[LockScreen] background` y resolviendo rutas relativas contra `backgrounds/`.
- La acción de **Aplicar** copia el fondo seleccionado (imagen o video) a la carpeta `backgrounds/` del tema SDDM actual y reescribe su configuración (`ConfigFile` de `metadata.desktop` o `theme.conf`) para que las pantallas de login y bloqueo lo usen. El cambio se registra en `~/.local/share/Aura/current_bg.txt`.
- Si el tema pertenece al sistema (`root`), la escritura se realiza mediante `pkexec`/PolicyKit; si el usuario tiene permisos sobre el tema, se escribe directamente.
- Para videos animados basta con copiar el archivo con su extensión: el tema decide si lo anima (SilentSDDM admite `mp4`, `mkv`, `mov`, `webm`, `m4v`, entre otros).
- La aplicación desktop usa `--enable-native-access=ALL-UNNAMED` para compatibilidad con acceso nativo (Skiko/FFmpeg) en JVM recientes; se aplica tanto a las tareas `run`/distribuciones como a las de Compose Hot Reload (`hotRun`/`hotDev`).

## Próximos pasos

1. Detectar fondos definidos únicamente por color para mostrarlos como vista previa.
2. Mejorar la gestión de errores y permisos.
3. Separar mejor las pantallas y estados por tipo de medio.
4. Añadir pruebas para repositorio, detección SDDM, selección e importación.
5. Mostrar en la UI un error claro cuando `sddm-greeter` no esté instalado.

## Flujo de desarrollo

La rama principal de trabajo es `dev`.

Se recomienda:

1. Crear cambios pequeños y verificables.
2. Compilar con `./gradlew :desktopApp:compileKotlin`.
3. Crear commits descriptivos por funcionalidad o mejora visual.
4. Subir los cambios cuando la rama esté estable:

```bash
git push origin dev
```
