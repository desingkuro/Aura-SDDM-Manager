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
- Aplicar el fondo seleccionado (imagen o video) al tema SDDM, eligiendo **login**, **bloqueo** o **ambos**, con confirmación y permisos de administrador vía `pkexec` cuando es necesario.
- Crear un respaldo automático de la configuración anterior antes de aplicar y restaurarlo desde Ajustes.
- Listar los temas instalados, mostrar el tema actual y cambiarlo con respaldo previo.
- Mostrar un diagnóstico de SDDM: tema, rutas, permisos, `sddm-greeter`, `pkexec`, agente Polkit, `ffmpeg` y carpeta de respaldos.
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
- `pkexec` (PolicyKit) y un agente Polkit en la sesión gráfica para aplicar cambios en temas protegidos

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

La suite cubre detección de tema/fondo, reescritura INI, destinos login/bloqueo/ambos, respaldos y restauración, cambio de tema y mensajes de error de `pkexec`.

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
- `SettingsScreen`: ajustes avanzados; incluye diagnóstico, selector de tema y listado/restauración de respaldos.
- `components/`: sidebar, tarjetas, header, footer, selector de destino, diálogos y panel principal.
- `viewmodels/`: estados de imágenes, videos, selección, fondo aplicado, errores y previsualización SDDM; `MediaType` distingue el tipo de cada archivo y `SettingsViewModel` gestiona respaldos, diagnóstico y cambio de tema.
- `utils/`: selector de archivos y conversión a `ImageBitmap`.

### Domain

Contiene contratos y modelos independientes de la UI.

- `MediaRepository`: contrato para listar, importar y eliminar archivos multimedia.
- `SddmRepository`: contrato para detectar tema/fondo, ubicar el greeter, previsualizar, aplicar fondos, gestionar respaldos, listar temas, diagnosticar el entorno y cambiar de tema.
- `models/`: resultados de aplicar/restaurar/cambiar tema, destinos de fondo (`BackgroundDestination`), temas instalados (`SddmTheme`), diagnóstico (`SddmDiagnostics`) y datos de los respaldos (`SddmBackup`).

### Data

Contiene las implementaciones de repositorio y utilidades de almacenamiento local.

- `MediaRepositoryImple`: filtra imágenes/videos y copia archivos al almacenamiento local.
- `SddmRepositoryImpl`: acceso y orquestación de SDDM: detecta el tema actual, resuelve el fondo aplicado, lanza `sddm-greeter`, crea respaldos, restaura configuraciones, lista temas, diagnostica dependencias y cambia el tema activo.
- `PathValidator`: valida rutas canónicas y bloquea *path traversal* en `metadata.desktop`, fondos, respaldos y configuración de SDDM.
- `StorageManager`: crea/usa el directorio local de medios.

## Consideraciones actuales

- Las imágenes se cargan directamente desde archivos locales.
- Los videos se representan mediante una miniatura extraída con `ffmpeg`.
- La previsualización con `sddm-greeter` busca el ejecutable en rutas comunes de Linux.
- La previsualización construye un **tema temporal** en `/tmp`: enlaza el tema actual, copia el fondo seleccionado en `backgrounds/` y reescribe la configuración para que las pantallas de login y bloqueo usen ese fondo. Al cerrar la ventana del greeter, el tema temporal se elimina automáticamente.
- El tema actual se lee desde `/etc/sddm.conf` y, si existe, desde `/etc/sddm.conf.d` en orden alfabético.
- Para temas tradicionales, el fondo se obtiene desde `theme.conf` (`[General] background`).
- Para SilentSDDM, el fondo se obtiene desde `metadata.desktop` → `ConfigFile=` → `configs/*.conf`, leyendo `[LoginScreen] background` o `[LockScreen] background` y resolviendo rutas relativas contra `backgrounds/`.
- La acción de **Aplicar** crea primero un respaldo de la configuración y del fondo anterior, y después copia el fondo seleccionado (imagen o video) a la carpeta `backgrounds/` del tema SDDM actual y reescribe su configuración (`ConfigFile` de `metadata.desktop` o `theme.conf`) para las pantallas elegidas: login, bloqueo o ambas. Los temas tradicionales con `theme.conf` solo tienen un fondo general, así que el selector se limita a "Ambos" en esos casos. El cambio se registra en `~/.local/share/Aura/current_bg.txt`.
- La configuración se escribe primero en un temporal del mismo directorio y se publica con `rename` atómico. Así SDDM nunca lee un archivo a medio escribir; se respetan los symlinks y se conservan el propietario, el grupo y los permisos POSIX originales. Los archivos nuevos se crean con modo `644`.
- El flujo de **Aplicar** es transaccional: si la configuración falla después de copiar el fondo, Aura restaura el fondo anterior (o elimina el nuevo si no existía) antes de devolver el error. La misma lógica se aplica en el script de `pkexec`.
- Todas las rutas que provienen del tema se resuelven con `PathValidator` y `canonicalPath`; cualquier intento de salir de `/usr/share/sddm/themes/...` o de `/etc` se rechaza con un error de seguridad.
- El **cambio de tema** escribe una configuración dedicada en `/etc/sddm.conf.d/99-aura-theme.conf` (o en `/etc/sddm.conf` si no existe `sddm.conf.d`), crea un respaldo restaurable y limpia el registro de fondo del tema anterior. Si otro archivo de configuración tiene prioridad, Aura avisa en lugar de dar el cambio por bueno.
- Los respaldos se guardan en `~/.local/share/Aura/backups/<fecha>-<tema>/` y se pueden restaurar desde **Ajustes Avanzados**. La restauración pide confirmación y usa `pkexec` solo si el tema o la configuración pertenecen al sistema.
- El **diagnóstico** comprueba el tema actual, las rutas, la configuración, la carpeta de fondos, `sddm-greeter`, `pkexec`, el agente Polkit, `ffmpeg` y la carpeta de respaldos.
- Si el tema pertenece al sistema (`root`), la escritura se realiza mediante `pkexec`/PolicyKit; si el usuario tiene permisos sobre el tema, se escribe directamente.
- Para videos animados basta con copiar el archivo con su extensión: el tema decide si lo anima (SilentSDDM admite `mp4`, `mkv`, `mov`, `webm`, `m4v`, entre otros).
- La aplicación desktop usa `--enable-native-access=ALL-UNNAMED` para compatibilidad con acceso nativo (Skiko/FFmpeg) en JVM recientes; se aplica tanto a las tareas `run`/distribuciones como a las de Compose Hot Reload (`hotRun`/`hotDev`).

## Respaldos de SDDM

Aura crea una carpeta en `~/.local/share/Aura/backups/` antes de aplicar un fondo o cambiar de tema.

**Respaldo de fondo**

- `theme-config`: copia exacta del `ConfigFile` del tema o de `theme.conf`.
- `background`: copia del fondo de destino, solo si ya existía y podía sobrescribirse.
- `current-bg.txt`: registro previo del fondo aplicado por Aura.
- `backup.properties`: manifiesto con fecha, tema, rutas y banderas de restauración.

**Respaldo de tema**

- `sddm-config`: copia de `/etc/sddm.conf` o de `99-aura-theme.conf`, si existía.
- `current-bg.txt`: registro previo del fondo aplicado, si existía.
- `backup.properties`: manifiesto con `kind=theme`, tema anterior, tema nuevo, ruta de configuración y banderas.

El manifiesto se escribe al final y se publica con un movimiento atómico: si una operación se interrumpe, la carpeta queda incompleta y no aparece en la lista de respaldos. Restaurar un fondo vuelve a dejar la configuración y el fondo anterior; si el fondo aplicado era nuevo, se elimina. Restaurar un tema recupera la configuración de SDDM anterior y el registro de fondo que existía antes. El respaldo no se borra después de restaurar, así que puede reintentarse. La configuración restaurada también usa temporal + `rename`.

## Próximos pasos

1. Extraer casos de uso (`ApplySddmBackground`, `CreateBackup`, `RestoreBackup` y `ValidatePermissions`) para que `SddmRepositoryImpl` quede como fachada de acceso a datos.
2. Automatizar el flujo con `pkexec` mediante inyección o un helper Polkit dedicado.
3. Detectar fondos definidos únicamente por color.
4. Caché de miniaturas, gestión de almacenamiento y limpieza de respaldos.
5. Opciones de apariencia del tema (relleno, blur, brillo, saturación).

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
