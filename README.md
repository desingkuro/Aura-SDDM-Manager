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
- Crear un respaldo automático de la configuración anterior antes de aplicar y restaurarlo desde Ajustes.
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
- Respaldos locales en `~/.local/share/Aura/backups/` con manifiesto y restauración

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

Ejecutar pruebas del módulo desktop:

```bash
./gradlew :desktopApp:test
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
- `MediaScreen`: pantalla única de fondos que muestra imágenes y videos en la misma galería.
- `SettingsScreen`: ajustes avanzados; hoy incluye el listado y la restauración de respaldos.
- `components/`: sidebar, tarjetas, header, footer, diálogos y panel principal.
- `viewmodels/`: estados de imágenes, videos, selección, fondo aplicado, errores y previsualización SDDM; `MediaType` distingue el tipo de cada archivo y `SettingsViewModel` gestiona los respaldos.
- `utils/`: selector de archivos y conversión a `ImageBitmap`.

### Domain

Contiene contratos y modelos independientes de la UI.

- `MediaRepository`: contrato para listar, importar y eliminar archivos multimedia.
- `SddmRepository`: contrato para detectar tema/fondo, ubicar el greeter, previsualizar, aplicar fondos y gestionar respaldos.
- `models/`: resultados de aplicar/restaurar y datos de los respaldos (`SddmBackup`).

### Data

Contiene las implementaciones de repositorio y utilidades de almacenamiento local.

- `MediaRepositoryImple`: filtra imágenes/videos y copia archivos al almacenamiento local.
- `SddmRepositoryImpl`: detecta el tema actual, resuelve el fondo aplicado, lanza `sddm-greeter`, crea respaldos y restaura configuraciones.
- `StorageManager`: crea/usa el directorio local de medios.

## Consideraciones actuales

- Las imágenes se cargan directamente desde archivos locales.
- Los videos se representan mediante una miniatura extraída con `ffmpeg`.
- La previsualización con `sddm-greeter` busca el ejecutable en rutas comunes de Linux.
- La previsualización construye un **tema temporal** en `/tmp`: enlaza el tema actual, copia el fondo seleccionado en `backgrounds/` y reescribe la configuración para que las pantallas de login y bloqueo usen ese fondo. Al cerrar la ventana del greeter, el tema temporal se elimina automáticamente.
- El tema actual se lee desde `/etc/sddm.conf` y, si existe, desde `/etc/sddm.conf.d` en orden alfabético.
- Para temas tradicionales, el fondo se obtiene desde `theme.conf` (`[General] background`).
- Para SilentSDDM, el fondo se obtiene desde `metadata.desktop` → `ConfigFile=` → `configs/*.conf`, leyendo `[LoginScreen] background` o `[LockScreen] background` y resolviendo rutas relativas contra `backgrounds/`.
- La acción de **Aplicar** crea primero un respaldo de la configuración y del fondo anterior, y después copia el fondo seleccionado (imagen o video) a la carpeta `backgrounds/` del tema SDDM actual y reescribe su configuración (`ConfigFile` de `metadata.desktop` o `theme.conf`) para que las pantallas de login y bloqueo lo usen. El cambio se registra en `~/.local/share/Aura/current_bg.txt`.
- La configuración se escribe primero en un temporal del mismo directorio y se publica con `rename` atómico. Así SDDM nunca lee un archivo a medio escribir; se respetan los symlinks y se fijan permisos `644`.
- Los respaldos se guardan en `~/.local/share/Aura/backups/<fecha>-<tema>/` y se pueden restaurar desde **Ajustes Avanzados**. La restauración pide confirmación y usa `pkexec` solo si el tema pertenece al sistema.
- Si el tema pertenece al sistema (`root`), la escritura se realiza mediante `pkexec`/PolicyKit; si el usuario tiene permisos sobre el tema, se escribe directamente.
- Para videos animados basta con copiar el archivo con su extensión: el tema decide si lo anima (SilentSDDM admite `mp4`, `mkv`, `mov`, `webm`, `m4v`, entre otros).
- La aplicación desktop usa `--enable-native-access=ALL-UNNAMED` para compatibilidad con acceso nativo (Skiko/FFmpeg) en JVM recientes; se aplica tanto a las tareas `run`/distribuciones como a las de Compose Hot Reload (`hotRun`/`hotDev`).

## Respaldos de SDDM

Antes de aplicar un fondo, Aura crea una carpeta en `~/.local/share/Aura/backups/` con:

- `theme-config`: copia exacta del `ConfigFile` del tema o de `theme.conf`.
- `background`: copia del fondo de destino, solo si ya existía y podía sobrescribirse.
- `current-bg.txt`: registro previo del fondo aplicado por Aura.
- `backup.properties`: manifiesto con fecha, tema, rutas y banderas de restauración.

El manifiesto se escribe al final y se publica con un movimiento atómico: si una operación se interrumpe, la carpeta queda incompleta y no aparece en la lista de respaldos. Restaurar vuelve a dejar la configuración y el fondo anterior; si el fondo aplicado era nuevo, se elimina. El respaldo no se borra después de restaurar, así que puede reintentarse. La configuración restaurada también usa temporal + `rename`.

## Próximos pasos

1. Selector de destino al aplicar: login, bloqueo o ambos.
2. Restauración de permisos/propietario originales y operación transaccional entre configuración y fondo.
3. Ampliar las pruebas a detección de temas, reescritura INI y flujo con `pkexec`.
4. Diagnóstico del entorno y selector de tema en Ajustes.
5. Detectar fondos definidos únicamente por color.

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
