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
- Mostrar errores de importación mediante un diálogo visual.
- Navegar entre secciones desde una barra lateral.

## Tecnologías principales

- Kotlin
- Compose Multiplatform Desktop
- Gradle Kotlin DSL
- Clean Architecture básica (`presentation`, `domain`, `data`)
- `ProcessBuilder` / `ffmpeg` para generación de miniaturas de video
- `ProcessBuilder` / `sddm-greeter` para previsualización de temas SDDM

## Requisitos

- JDK 17 o superior
- Gradle Wrapper incluido en el repositorio (`./gradlew`)
- Linux con escritorio compatible si se desea aplicar cambios en SDDM
- `ffmpeg` instalado para generar miniaturas de videos
- `sddm-greeter` instalado si se desea previsualizar el tema localmente

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
- La acción de aplicar cambios directamente sobre SDDM aún está pendiente.
- La integración con `pkexec`/Polkit y edición de configuración SDDM sigue en planeación.
- La aplicación desktop usa `--enable-native-access=ALL-UNNAMED` para compatibilidad con acceso nativo en JVM recientes.

## Próximos pasos

1. Implementar la acción real de `Aplicar` para escribir configuración de SDDM.
2. Detectar fondos definidos únicamente por color para mostrarlos como vista previa.
3. Mejorar la gestión de errores y permisos.
4. Separar mejor las pantallas y estados por tipo de medio.
5. Añadir pruebas para repositorio, detección SDDM, selección e importación.
6. Mostrar en la UI un error claro cuando `sddm-greeter` no esté instalado.

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
