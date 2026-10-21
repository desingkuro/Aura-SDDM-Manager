# Estado del proyecto — Aura SDDM Manager

> **Última actualización:** 21 de octubre de 2026
> **Rama de trabajo:** `dev` · **Último bloque:** concurrencia con corrutinas,
> validación canónica de rutas, transaccionalidad y permisos POSIX.
> **Sincronización:** `main` y `dev` se actualizan al publicar este bloque.
> **Build:** `./gradlew clean :desktopApp:build :desktopApp:test` → correcto
> (50 pruebas, 0 fallos).

Este documento resume el estado real del proyecto, lo que falta, los puntos de
mejora y un listado priorizado de ideas para seguir creciendo. Es un documento
vivo: conviene actualizarlo al cerrar cada bloque de trabajo.

---

## 1. Resumen ejecutivo

Aura es un **MVP funcional** de escritorio para gestionar el fondo de la pantalla
de inicio de **SDDM** en Linux. Hoy permite importar imágenes y videos, verlos en
una galería, previsualizar el tema real y **aplicar** el fondo seleccionado al
tema SDDM activo, eligiendo **login, bloqueo o ambos**, incluyendo videos
animados. Ajustes ya incluye respaldos restaurables, diagnóstico del entorno y
selector de tema.

El proyecto es estable a nivel de compilación y de flujo manual: importar →
seleccionar → previsualizar → aplicar funciona, cada operación deja un respaldo
restaurable, la configuración se publica de forma atómica y el conjunto
configuración + fondo se revierte si falla. La UI usa corrutinas, `StateFlow` y
`viewModelScope`; las rutas del tema se validan de forma canónica y los permisos
POSIX originales se conservan. La deuda principal ahora es extraer los casos de
uso para adelgazar el repositorio, la **apariencia del tema** y la cobertura del
flujo `pkexec` con dobles de prueba.

**Semáforo general**

| Área | Estado |
|------|--------|
| Compilación | 🟢 Estable |
| Flujo principal (imagen/video) | 🟢 Funcional |
| Aplicar cambios en SDDM | 🟢 Funcional, con destino y respaldo/restauración |
| Previsualización | 🟢 Funcional |
| Ajustes | 🟢 Respaldos, diagnóstico y selector de tema; apariencia pendiente |
| Pruebas automatizadas | 🟢 50 pruebas del repositorio; falta automatizar `pkexec` real |
| Empaquetado / distribución | 🟡 Configurado, no verificado |
| Documentación | 🟢 README + `docs/ideas-ajustes.md` + este archivo |

---

## 2. Estado actual

### 2.1 Funcionalidades disponibles

| Funcionalidad | Estado | Notas |
|---------------|--------|-------|
| Importar imágenes | ✅ | Copia a `~/.local/share/Aura/media/` |
| Importar videos | ✅ | Miniatura generada con `ffmpeg` |
| Galería de fondos | ✅ | Tarjetas con miniatura y scroll |
| Selección de fondo | ✅ | Panel derecho con vista previa |
| Vista previa dentro de la app | ✅ | Imagen o primer frame del video |
| Eliminar fondo | ✅ | Diálogo de confirmación |
| Detectar tema SDDM actual | ✅ | `/etc/sddm.conf` + `/etc/sddm.conf.d` |
| Detectar fondo actual | ✅ | `current_bg.txt` y configuración del tema |
| Previsualizar con `sddm-greeter` | ✅ | Tema temporal en `/tmp`, se borra al cerrar |
| Aplicar fondo (imagen) | ✅ | Copia a `backgrounds/` y reescribe config |
| Aplicar fondo (video animado) | ✅ | Mismo flujo; el tema anima según la extensión |
| Elegir destino al aplicar | ✅ | Login, bloqueo o ambos; temas tradicionales solo "Ambos" |
| Aplicar con permisos | ✅ | `pkexec` si el tema es del sistema; escritura directa si no |
| Confirmación y resultado al aplicar | ✅ | Diálogos de confirmación, "Aplicando...", éxito/error |
| Respaldo automático antes de aplicar | ✅ | Config del tema, fondo anterior y `current_bg.txt` |
| Restauración de respaldos de fondo | ✅ | Lista en Ajustes, confirmación y `pkexec` si hace falta |
| Selector de tema instalado | ✅ | Lista `/usr/share/sddm/themes` y marca el tema actual |
| Cambio de tema con respaldo | ✅ | `99-aura-theme.conf` + respaldo restaurable |
| Restauración de respaldos de tema | ✅ | Recupera la configuración de SDDM y el registro de fondo |
| Diagnóstico del entorno | ✅ | Tema, rutas, permisos, greeter, pkexec, Polkit, ffmpeg |
| Acciones sin selección | ✅ | Previsualizar/Eliminar/Aplicar deshabilitados |
| Barra lateral responsive | ✅ | Paneles con `weight`, nombres largos con elipsis |
| Pantalla de Ajustes | 🟢 | Diagnóstico, tema y respaldos; apariencia pendiente |
| Persistencia de preferencias | ❌ | No existe |
| Respaldo / deshacer cambios | ✅ | Respaldo y restauración; no hay redo |
| Tests del módulo desktop | 🟢 | 50 pruebas con directorios temporales |
| Internacionalización | ❌ | Textos en español hardcodeados |
| Empaquetado probado | 🟡 | `nativeDistributions` configurado, sin verificar |

### 2.2 Arquitectura actual

```text
Aura/
├── desktopApp/                  # Aplicación Compose Desktop (JVM)
│   └── src/main/kotlin/com/example/aura/
│       ├── data/
│       │   ├── local/           # StorageManager (carpeta de medios)
│       │   ├── repository/      # MediaRepositoryImple, SddmRepositoryImpl
│       │   └── security/        # PathValidator (canonicalPath, anti-traversal)
│       ├── domain/
│       │   ├── models/          # Resultados, BackgroundDestination, SddmTheme,
│       │   │                    # SddmDiagnostics, SddmBackup…
│       │   └── repository/      # MediaRepository, SddmRepository
│       ├── presentation/
│       │   ├── ui/components/   # Sidebar, MainScreen, Footer, Destino, diálogos…
│       │   ├── ui/screens/      # MediaScreen, SettingsScreen
│       │   ├── ui/theme/        # AuraColors
│       │   ├── utils/           # FilePicker suspendido, conversión de miniaturas
│       │   └── viewmodels/      # MediaViewModel, SettingsViewModel, UiState, Navigation
│       └── main.kt              # Ventana principal
├── shared/                      # Plantilla KMP heredada (sin uso)
├── docs/ideas-ajustes.md        # Ideas específicas para Ajustes
└── ESTADO-PROYECTO.md           # Este documento
```

**Capas:** `presentation` → `domain` (contratos/modelos) ← `data` (implementaciones).
Es una Clean Architecture básica. La UI ya usa `StateFlow`/`viewModelScope`, pero
todavía no hay una capa de casos de uso ni inyección de dependencias formal: ese
es el siguiente refactor arquitectónico.

**Flujo de aplicación de fondo**

1. `FooterScreen` → diálogo de confirmación con `DestinationSelector`.
2. `MediaViewModel.applySelectedBackground()` captura el destino y llama a
   `SddmRepositoryImpl.applyBackground(file, destination)` en `Dispatchers.IO`.
3. Detecta tema y archivo de configuración (`metadata.desktop` → `ConfigFile`, o `theme.conf`).
4. Valida todas las rutas con `PathValidator` y `canonicalPath`; si el
   `ConfigFile` o el fondo salen del tema, aborta con un error de seguridad.
5. Comprueba permisos; si el tema está protegido y falta `pkexec`, aborta antes
   de crear un respaldo.
6. Crea un respaldo en `~/.local/share/Aura/backups/<fecha>-<tema>/`.
7. Copia el archivo a `backgrounds/` conservando uid, gid y permisos del destino.
8. Reescribe solo las pantallas elegidas (`LoginScreen`, `LockScreen` o ambas)
   con temporal + `rename`; si falla, restaura el fondo anterior.
9. Guarda la ruta en `~/.local/share/Aura/current_bg.txt` y devuelve un resultado tipado.

**Flujo de cambio de tema**

1. `SettingsScreen` lista los temas instalados y marca el actual.
2. Al confirmar, `SddmRepositoryImpl.changeTheme()` crea un respaldo del tema y
   de `current_bg.txt`.
3. Escribe `[Theme] Current` en `/etc/sddm.conf.d/99-aura-theme.conf` (o en
   `/etc/sddm.conf` si no existe el directorio) con temporal + `rename`,
   conservando propietario/grupo/permisos.
4. Verifica que el tema efectivo cambió; si otro archivo tiene prioridad,
   revierte la configuración anterior y devuelve un error explicativo.
5. Limpia el registro de fondo del tema anterior. El respaldo permite volver al
   tema previo desde Ajustes.

### 2.3 Estado técnico

| Aspecto | Valor |
|---------|-------|
| Lenguaje | Kotlin 2.4.20 |
| UI | Compose Multiplatform 1.12.1 (Material 2 en desktopApp) |
| Build | Gradle 9.5.1 (Kotlin DSL + version catalog) |
| JDK | Toolchain 21 (Azul), descargado por Gradle |
| Módulos | `:desktopApp` (JVM), `:shared` (KMP, sin uso) |
| Config cache | Activada (`org.gradle.configuration-cache=true`) |
| Tests | `desktopApp` (36: respaldos, reescritura, destinos, temas, pkexec) + `shared` (triviales) |
| Permisos | `pkexec`/PolicyKit con script temporal |
| Git | `dev` con respaldo/restauración; `main` se actualiza al publicar |

**Deuda técnica visible**

- `shared/` es la plantilla del wizard de KMP: `Greeting`, `Platform`, `App`
  duplicado y tests de ejemplo. `desktopApp` no usa nada de ese módulo.
- `desktopApp/src/main/kotlin/.../data/local/current_bg.txt` está versionado y
  vacío; el código real usa `~/.local/share/Aura/current_bg.txt`.
- `MediaViewModel.getCurrentThemeSddm()` y `getGreeterPathSddm()` no se usan.
- `MediaRepositoryImple` (typo).
- La UI usa `viewModelScope`, `Dispatchers.IO` y `StateFlow`; ya no quedan
  `thread { }` para concurrencia.
- La configuración y el fondo se aplican de forma transaccional: si falla la
  escritura de la configuración, se revierte el fondo. Se conservan uid, gid y
  permisos POSIX del archivo original.
- `PathValidator` resuelve symlinks y `..` con `canonicalPath`; bloquea
  `ConfigFile`, fondos, respaldos y configuración de SDDM fuera de las rutas
  permitidas.
- El repositorio sigue concentrando demasiada lógica (config, pkexec, ffmpeg,
  backups); falta extraer casos de uso y separar el acceso a datos.
- El cambio de tema usa el nombre fijo `99-aura-theme.conf`; si otra distro
  tiene un drop-in posterior, `changeTheme` revierte y devuelve error en lugar
  de asumir éxito, pero convendría elegir dinámicamente el orden.
- Los métodos de diagnóstico y detección de procesos usan `ProcessHandle`; en
  sesiones muy restringidas puede no detectar el agente Polkit.

---

## 3. Puntos pendientes

### 3.1 Prioridad alta (bloqueantes para una versión usable)

- [x] **Respaldo y restauración de la configuración de SDDM** antes de aplicar.
      Implementado con manifiesto, listado y restauración desde Ajustes.
- [x] **Cubrir `SddmRepositoryImpl` con pruebas**: detección de tema/fondo,
      reescritura de `ConfigFile` y `theme.conf`, destinos, nombres con espacios,
      videos, errores, respaldos y cambio de tema. 50 pruebas con directorios
      temporales.
- [x] **Mejorar la gestión de permisos y errores de `Aplicar`**:
      - distingue cancelación de autenticación (126), autorización denegada (127)
        y fallo de PolicyKit (125),
      - avisa si faltan `pkexec` o el agente Polkit,
      - detecta temas protegidos antes de crear el respaldo.
- [x] **Vista de Ajustes funcional**: respaldos, diagnóstico y selector de tema.
      Falta apariencia del tema.
- [x] **Proteger acciones sin selección**: Previsualizar/Eliminar/Aplicar se
      deshabilitan y el diálogo ya no muestra `'null'`.
- [x] **Elegir destino al aplicar**: login, bloqueo o ambos. En temas
      tradicionales el selector se limita a "Ambos".
- [x] **Escritura atómica de configuración**: temporal en el mismo directorio +
      `rename`, tanto en escritura directa como con `pkexec`, y respeto de
      symlinks. El fondo se copia antes y la configuración se activa al final.
- [x] **Selector de tema con respaldo restaurable**: `/etc/sddm.conf.d/99-aura-theme.conf`
      o `/etc/sddm.conf`, con verificación del tema efectivo.
- [x] **Hacer transaccional la operación config+fondo** y conservar
      permisos/propietario originales. Implementado con rollback local del
      fondo y scripts `pkexec` con trampa de error.
- [x] **Validación canónica de rutas**: `PathValidator` bloquea *path traversal*
      en `ConfigFile`, fondos, respaldos y configuración de SDDM.
- [ ] **Extraer casos de uso** (`ApplySddmBackground`, `CreateBackup`,
      `RestoreBackup`, `ValidatePermissions`) y dejar el repositorio como
      fachada de acceso a datos.
- [ ] **Limpiar código muerto y restos de plantilla**: `shared/`, `current_bg.txt`
      versionado, métodos sin uso.

### 3.2 Prioridad media

- [x] **Selector de tema SDDM** instalado (listar `/usr/share/sddm/themes`).
- [x] **Diagnóstico del entorno** en Ajustes: `ffmpeg`, `sddm-greeter`, `pkexec`,
      permisos, rutas y tema actual.
- [ ] **Caché de miniaturas de video** en disco (hoy se regeneran).
- [ ] **Usar el diagnóstico antes de cada flujo**: hoy avisa en Ajustes, pero
      `ffmpeg` y `sddm-greeter` ausentes aún fallan en silencio en la galería o
      la previsualización.
- [ ] **Reiniciar SDDM / aplicar y probar** desde la app, con confirmación fuerte.
- [ ] **Soporte de fondos definidos solo por color** (hoy se detectan como vacío).
- [ ] **Manejo de temas sin carpeta `backgrounds/`** y de temas tradicionales
      con video (hoy se asume que `backgrounds/` es la ruta correcta).
- [ ] **Rutas configurables** para distros con ubicaciones distintas.
- [ ] **Ventana con tamaño inicial y mínimo** y un icono de aplicación.
- [ ] **Reemplazar `println` por un logger** y un historial de operaciones.

### 3.3 Prioridad baja

- [ ] Internacionalización (español/inglés) con Compose Resources.
- [ ] Accesibilidad: navegación por teclado, foco visible, `contentDescription`.
- [ ] Atajos de teclado (importar, previsualizar, aplicar, eliminar).
- [ ] Inicio automático / bandeja del sistema.
- [ ] Comprobador de actualizaciones y diálogo "Acerca de".
- [ ] Multi-monitor y selección de resolución.
- [ ] Exportar/importar preferencias y presets.

---

## 4. Puntos de mejora

### 4.1 Seguridad y robustez

1. **Backup automático antes de aplicar** (config y fondo anterior) y botón de
   restauración. ✅ Implementado.
2. **Escritura atómica y transaccional**: temporal + `rename` en escritura
   directa y con `pkexec`. Si falla la configuración después de copiar el fondo,
   se restaura el fondo anterior. ✅ Implementado.
3. **Modelo de privilegios más estricto**: hoy se ejecuta con `pkexec` un script
   temporal propiedad del usuario. Funciona, pero conviene evaluar un *helper*
   Polkit dedicado o `pkexec` con argumentos acotados.
4. **Validación de rutas y nombres**: `PathValidator` usa `canonicalPath` y
   bloquea cualquier ruta fuera del tema o de las configuraciones permitidas de
   SDDM. Evitar colisiones con archivos reservados (`default.jpg`, etc.) sigue
   pendiente.
5. **Restaurar permisos/propietario**: se conservan uid, gid y permisos POSIX del
   destino; los archivos nuevos se crean con `644`. ✅ Implementado.
6. **Registrar qué se cambió**: los respaldos de fondo y de tema ya guardan
   fecha, tema, rutas y banderas en `backup.properties`. Falta un historial
   legible de operaciones.

### 4.2 Arquitectura y código

1. **Casos de uso** (`ApplyBackground`, `PreviewTheme`, `DetectEnvironment`)
   para sacar lógica del repositorio y facilitar pruebas.
2. **Inyección de dependencias** sencilla (constructor/manual) en lugar de
   instanciar repositorios dentro del ViewModel.
3. ~~Unificar `ImageScreen` y `VideoScreeen`~~ ✅ Hecho: una sola sección `Fondos` con galería combinada (`MediaScreen` + `MediaType`).
4. **Corregir nombres**: `MediaRepositoryImpl`.
5. **Eliminar el módulo `shared`** si no se va a reutilizar, o mover allí los
   modelos y utilidades realmente compartidos.
6. ~~Usar corrutinas (`viewModelScope`/`Dispatchers.IO`) en vez de `thread`.~~ ✅ Hecho.
7. ~~Separar estado de UI del ViewModel con un `UiState` inmutable por pantalla.~~ ✅ Hecho con `MediaUiState` y `SettingsUiState` + `StateFlow`.

### 4.3 Rendimiento

1. **Cachear miniaturas de video** en `~/.cache/Aura/` (clave: ruta + mtime).
2. **Cargar imágenes de forma asíncrona** y con tamaño acotado; hoy se decodifica
   el archivo completo para la tarjeta.
3. **No regenerar miniaturas** al hacer scroll/recomposición.
4. **Limitar memoria** con imágenes 4K (usar `BitmapFactory` con `inSampleSize`
   o similar).
5. **Evitar copiar videos grandes** si ya existen en el tema (comparar hash/mtime).

### 4.4 UI/UX

1. **Feedback consistente**: snackbar/toast además de diálogos; estado de carga
   en importación y previsualización.
2. **Deshabilitar visualmente** todas las acciones sin selección (hoy solo
   "Aplicar" lo hace de forma correcta).
3. **Previsualización de video dentro de la app** (reproducir, no solo miniatura).
4. **Drag & drop** para importar archivos.
5. **Búsqueda, filtros y ordenación** en la galería.
6. **Favoritos/colecciones** y selección múltiple.
7. **Tema claro/oscuro** y tokens de color más completos (`MaterialTheme` real).
8. **Estados vacíos y de error** homogéneos.
9. **Tamaño mínimo de ventana** y layout adaptable a ventanas pequeñas.

### 4.5 Pruebas y CI

1. Crear `desktopApp/src/test` con `kotlin-test` + JUnit.
2. Tests de `SddmRepositoryImpl` con `themesDir`/config temporales (ya es
   inyectable por constructor, lo que facilita mucho las pruebas).
3. Tests de reescritura INI: `ConfigFile` de SilentSDDM y `theme.conf` clásico.
4. Tests de `getAppliedBackground` con `current_bg.txt` válido, inválido y vacío.
5. Tests de nombres con espacios, tildes y comillas.
6. CI (GitHub Actions) con `./gradlew build` y `./gradlew test`.
7. Sustituir los tests triviales de `shared` por pruebas reales o eliminarlos.

### 4.6 Build y distribución

1. Verificar `./gradlew :desktopApp:packageDeb` (o el formato Linux elegido).
2. Ajustar `targetFormats`: hoy incluye `Dmg` y `Msi` aunque la app es Linux.
3. Definir versión real (hoy `1.0.0` con proyecto pre-1.0).
4. Añadir icono de aplicación y metadatos del paquete.
5. Evaluar AppImage/Flatpak además de `.deb`.
6. Añadir `ktlint`/`detekt` y un `CHANGELOG.md`.

### 4.7 Documentación

1. Mantener el README y este documento sincronizados con cada release.
2. Documentar el flujo de permisos (`pkexec`) y cómo revertir manualmente.
3. Añadir `CONTRIBUTING.md`, licencia explícita y guía de arquitectura.
4. Grabar capturas/GIF del flujo principal para el README.

---

## 5. Ideas de nuevas funcionalidades

Prioridad: **P0** imprescindible · **P1** alta · **P2** media · **P3** futura.
Esfuerzo estimado: **B** bajo · **M** medio · **A** alto.

| # | Idea | Valor | Esfuerzo | Prioridad |
|---|------|-------|----------|-----------|
| 1 | Respaldo y restauración de configuración SDDM ✅ | Evita romper el login | B | **P0** |
| 2 | Selector de pantalla destino (login / bloqueo / ambos) ✅ | Control real del cambio | B | **P0** |
| 3 | Selector de tema SDDM instalado ✅ | Trabajar con varios temas | B | **P0** |
| 4 | Diagnóstico del entorno y dependencias ✅ | Menos soporte y errores | B | **P1** |
| 5 | Opciones de apariencia (relleno, blur, brillo, saturación) | Personalización real | M | **P1** |
| 6 | Placeholder del video (primer frame con `ffmpeg`) | Evita fondo negro al cargar | B | **P1** |
| 7 | Reiniciar SDDM / aplicar y probar | Ver el resultado sin reiniciar a mano | B | **P1** |
| 8 | Gestión de almacenamiento y caché | Control del espacio usado | B | **P1** |
| 9 | Presets/perfiles (fondo + ajustes) | Cambios rápidos y reutilizables | M | **P1** |
| 10 | Previsualización de video en la app | Elegir mejor el fondo | M | **P1** |
| 11 | Optimización/transcodificación de video | Compatibilidad y peso | A | **P2** |
| 12 | Rotación/slideshow de fondos | Variedad automática | M | **P2** |
| 13 | Drag & drop para importar | Flujo más rápido | B | **P2** |
| 14 | Búsqueda, filtros y favoritos | Galerías grandes | M | **P2** |
| 15 | Deshacer último cambio | Seguridad adicional | B | **P2** |
| 16 | Fondo por color (sin imagen) | Temas minimalistas | B | **P2** |
| 17 | Rutas configurables (temas, conf, greeter) | Portabilidad entre distros | B | **P2** |
| 18 | Tema claro/oscuro de la app | Preferencia del usuario | B | **P2** |
| 19 | Notificaciones e historial de operaciones | Transparencia | B | **P2** |
| 20 | Internacionalización (es/en) | Más usuarios | M | **P3** |
| 21 | Atajos de teclado | Productividad | M | **P3** |
| 22 | Multi-monitor / resolución | Equipos con varios monitores | A | **P3** |
| 23 | Inicio automático / bandeja | Comodidad | M | **P3** |
| 24 | CLI/headless para automatización | Scripts y dotfiles | M | **P3** |
| 25 | Empaquetado AppImage/Flatpak | Distribución sencilla | M | **P3** |
| 26 | Comprobador de actualizaciones / "Acerca de" | Soporte | B | **P3** |

> Las ideas 1–9 y 18–19 están desarrolladas con más detalle en
> `docs/ideas-ajustes.md`. Este documento las integra en la visión global.

### Detalle de las ideas más valiosas

**1. Respaldo y restauración — implementado**
Cada aplicación crea `~/.local/share/Aura/backups/<fecha>-<tema>/` con el
`ConfigFile` del tema, el fondo de destino si ya existía y `current_bg.txt`. Un
manifiesto escrito al final registra las rutas. La restauración está en Ajustes
Avanzados, pide confirmación y usa `pkexec` si el tema pertenece al sistema.
Los cambios de tema crean un respaldo distinto (`kind=theme`) que guarda
`sddm.conf` o `99-aura-theme.conf` y permite volver al tema anterior.

**2. Destino del fondo — implementado**
El diálogo de aplicación permite elegir login, bloqueo o ambos. En SilentSDDM
se reescriben `[LoginScreen] background` y `[LockScreen] background` de forma
selectiva; los temas con `theme.conf` solo admiten "Ambos".

**3. Selector de tema — implementado**
Ajustes lista `/usr/share/sddm/themes`, marca el `Current` real, permite cambiar
de tema con respaldo previo y verifica que SDDM lea el tema nuevo.

**4. Diagnóstico — implementado**
Ajustes comprueba tema actual, configuración del tema, rutas, permisos,
`sddm-greeter`, `pkexec`, agente Polkit, `ffmpeg` y carpeta de respaldos.

**6. Placeholder de video**
Extraer el primer frame con `ffmpeg` y escribirlo en
`animated-background-placeholder` para evitar el fondo negro mientras carga el
video. El propio tema trae `backgrounds/extract_first_frame.sh` como referencia.

**11. Optimización de video**
Transcodificar a H.264/VP9 con tamaño y bitrate razonables, y avisar de formatos
no soportados. Útil para videos grandes o códecs exóticos.

**12. Slideshow**
Cambiar el fondo del login periódicamente (cron/systemd timer o al iniciar
sesión). Requiere escribir la config y reiniciar SDDM o usar un hook.

**23. CLI/headless**
Comando `aura apply --theme silent --image fondo.jpg` para integrarlo en scripts
y dotfiles.

---

## 6. Roadmap sugerido

### Corto plazo — v0.2 (estabilizar)

1. [x] Backups + restauración.
2. [x] Tests de `SddmRepositoryImpl` (50 pruebas).
3. [x] Gestión de permisos/errores de `Aplicar` (cancelación, `pkexec` ausente).
4. [x] Selector de destino login/bloqueo/ambos.
5. [x] Concurrencia con `viewModelScope`/`StateFlow` y sin `thread { }`.
6. [x] Validación canónica de rutas y transaccionalidad config+fondo.
7. [ ] Extraer casos de uso y limpiar código muerto/módulo `shared`.
8. [x] Arreglar el diálogo de eliminar sin selección.

### Medio plazo — v0.3 (personalización)

1. [x] Vista de Ajustes con diagnóstico, tema y respaldos.
2. [ ] Opciones de apariencia (relleno, blur, brillo, saturación).
3. [ ] Rutas configurables (temas, conf, greeter, medios).
4. [ ] Caché de miniaturas y gestión de almacenamiento.
5. [ ] Placeholder de video y previsualización de video.
6. [ ] Presets/perfiles.

### Largo plazo — v1.0 (producto)

1. Empaquetado verificado (`.deb` + AppImage/Flatpak), icono y versión.
2. i18n, accesibilidad y atajos.
3. Multi-monitor.
4. CLI/headless.
5. CI, `ktlint`/`detekt` y `CHANGELOG`.

---

## 7. Riesgos y limitaciones conocidas

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Un cambio mal aplicado rompe el login | Alto | Backups + restauración + validación |
| `pkexec` cancelado o sin agente Polkit | Medio | Mensajes diferenciados (125/126/127), diagnóstico y reintento |
| Diferencias entre temas/distros | Medio | Rutas configurables (pendiente) y detección robusta |
| Otro drop-in de SDDM con más prioridad | Medio | `changeTheme` verifica el tema efectivo y avisa del conflicto |
| Videos con códecs no soportados | Medio | Validación/transcodificación |
| GIF como fondo | Alto (puede crashear SDDM) | Bloquear o convertir a video/imagen |
| Config a medio escribir | Bajo | Temporal + `rename` atómico (implementado) |
| Propiedad `root` de los archivos tras `pkexec` | Bajo | `chown`/`chmod` con uid, gid y modo originales |
| `current_bg.txt` desactualizado | Bajo | Validar existencia y caer al tema |
| Miniaturas de video regeneradas | Bajo | Caché en disco |
| Rutas hardcodeadas (`/usr/share/sddm`, `/etc/sddm.conf`) | Medio | Preferencias de rutas |

**Limitaciones funcionales actuales**

- Ajustes cubre respaldos, diagnóstico y cambio de tema; faltan las opciones de
  apariencia y las rutas configurables.
- Hay respaldo, restauración, escritura atómica y rollback de configuración+fondo;
  falta redo y un diario persistente para caídas del proceso.
- La validación de rutas y la conservación de permisos ya están implementadas;
  falta probar el flujo `pkexec` real de forma automatizada.
- No detecta fondos definidos únicamente por color.
- No reinicia SDDM; el usuario debe cerrar sesión o reiniciar el servicio.
- La cobertura es de 50 pruebas del repositorio; el flujo real con `pkexec`
  interactivo aún no se automatiza.

---

## 8. Referencias

- `README.md` — descripción, requisitos y uso.
- `docs/ideas-ajustes.md` — ideas detalladas para la pantalla de Ajustes.
- Repositorio: `https://github.com/desingkuro/Aura-SDDM-Manager`
- Tema de referencia: SilentSDDM (`/usr/share/sddm/themes/silent`).

---

### Cómo mantener este documento

- Actualizar la fecha, el commit y el semáforo al cerrar cada bloque.
- Marcar los pendientes como `[x]` al completarlos.
- Mover ideas del roadmap a "Funcionalidades disponibles" cuando estén listas.
- Si una idea se descarta, dejarla registrada con el motivo.
