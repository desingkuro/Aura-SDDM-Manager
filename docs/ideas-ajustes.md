# Ideas para la vista de Ajustes Avanzados

Documento de trabajo para decidir qué colocar en la sección **Ajustes Avanzados**
del sidebar de Aura (ruta `setting` / `ajustes_avanzados`).

La lista está ordenada de **más óptima a menos importante**, entendiendo "óptima"
como la mejor relación entre **impacto real para el usuario**, **esfuerzo de
implementación** y **riesgo** (recordando que Aura toca archivos de SDDM y puede
romper el arranque del login si se hace mal).

> Regla práctica: primero lo que **hace que la app cumpla su propósito** y lo que
> **protege al usuario**; después lo que da comodidad; y al final lo accesorio.

---

## Resumen por prioridad

| # | Idea | Nivel | Impacto | Esfuerzo | Riesgo |
|---|------|-------|---------|----------|--------|
| 1 | Aplicar fondo a SDDM (login/bloqueo/ambos) | Imprescindible | Alto | Alto | Alto |
| 2 | Copia de seguridad y restauración de config SDDM | Imprescindible | Alto | Medio | Bajo |
| 3 | Selector de tema SDDM instalado | Imprescindible | Alto | Bajo | Bajo |
| 4 | Diagnóstico del entorno (dependencias y estado) | Imprescindible | Alto | Bajo | Bajo |
| 5 | Opciones del tema (relleno, desenfoque, brillo, saturación) | Muy útil | Alto | Medio | Bajo |
| 6 | Rutas personalizables (temas, conf, greeter, medios) | Muy útil | Medio | Bajo | Medio |
| 7 | Gestión de almacenamiento y caché | Muy útil | Medio | Bajo | Bajo |
| 8 | Reiniciar SDDM / abrir previsualización desde ajustes | Muy útil | Medio | Bajo | Medio |
| 9 | Perfiles o presets de fondo | Útil | Medio | Medio | Bajo |
| 10 | Tema claro / oscuro / sistema | Útil | Medio | Bajo | Bajo |
| 11 | Idioma (español / inglés) | Útil | Medio | Medio | Bajo |
| 12 | Notificaciones y registro de operaciones | Útil | Medio | Bajo | Bajo |
| 13 | Atajos de teclado | Opcional | Bajo | Medio | Bajo |
| 14 | Inicio automático / bandeja del sistema | Opcional | Bajo | Medio | Bajo |
| 15 | Exportar / importar preferencias de Aura | Opcional | Bajo | Bajo | Bajo |
| 16 | Comprobador de actualizaciones | Futuro | Bajo | Alto | Bajo |
| 17 | "Acerca de" (versión, licencia, créditos) | Futuro | Bajo | Bajo | Bajo |
| 18 | Multi-monitor y ajustes de resolución | Futuro | Medio | Alto | Medio |
| 19 | Telemetría / métricas de uso | No recomendado | Bajo | Medio | Medio |

---

## Nivel 1 — Imprescindibles

### 1. Aplicar el fondo seleccionado a SDDM

**Qué permite**
- Botón "Aplicar" funcional (hoy está pendiente en el README).
- Elegir destino: pantalla de **login**, pantalla de **bloqueo** o **ambas**.
- Escribir el cambio en la configuración correcta del tema activo.

**Para qué sirve**
- Es el objetivo central de la app: pasar de "previsualizar" a "dejar aplicado"
  el fondo que el usuario eligió. Sin esto, Aura es solo un visor.

**Notas técnicas**
- SilentSDDM: reescribir `[LoginScreen] background`, `[LockScreen] background`,
  `use-background-color = false` en el `ConfigFile` del tema.
- Temas tradicionales: reescribir `[General] background` en `theme.conf`.
- Requiere permisos: `pkexec`/Polkit o elevar para escribir en
  `/usr/share/sddm/themes/...` o `/etc/sddm.conf.d/`.
- Riesgo alto: conviene hacerlo **después** de la idea 2 (backups).

**Esfuerzo:** Alto · **Depende de:** idea 2 y 4.

---

### 2. Copia de seguridad y restauración de la configuración de SDDM

**Qué permite**
- Guardar una copia de `sddm.conf`, `sddm.conf.d/*` y del archivo de tema
  afectado antes de cualquier cambio.
- Botón "Restaurar última copia" y listado de copias con fecha.

**Para qué sirve**
- Red de seguridad: si un cambio deja el login roto, el usuario puede volver
  atrás desde la propia app (o desde TTY). Es lo que hace "seguro" aplicar
  cambios automáticos.

**Notas técnicas**
- Guardar en `~/.local/share/Aura/backups/<fecha>/`.
- Registrar qué archivos se tocaron en cada operación.

**Esfuerzo:** Medio · **Depende de:** nada.

---

### 3. Selector de tema SDDM instalado

**Qué permite**
- Listar temas detectados en `/usr/share/sddm/themes`.
- Mostrar cuál es el `Current` real y permitir previsualizar/aplicar sobre otro.
- Indicar el tipo de tema detectado (tradicional con `theme.conf` o por
  `metadata.desktop`/`ConfigFile`).

**Para qué sirve**
- Hoy la app asume "el tema actual". Si el usuario tiene varios temas, no puede
  elegir ni entender por qué una opción aplica a uno y no a otro.

**Notas técnicas**
- Reutiliza `getCurrentTheme()` y amplía el repositorio SDDM con
  `listThemes()`.
- Puede incluir la miniatura del tema (`Screenshot=` en `metadata.desktop`).

**Esfuerzo:** Bajo · **Depende de:** nada.

---

### 4. Diagnóstico del entorno (dependencias y estado)

**Qué permite**
- Ver en verde/rojo: `ffmpeg`, `sddm-greeter`, `pkexec`/Polkit, permisos de
  escritura, ruta de temas, ruta de configuración, tema actual.
- Sugerir el comando de instalación cuando falte algo.

**Para qué sirve**
- Evita el clásico "no funciona y no sé por qué". Centraliza los errores que hoy
  salen como diálogos sueltos o solo en consola.

**Notas técnicas**
- Ya existen `getGreeterPath()` y detección de tema: extenderlos.
- Comprobar `command -v ffmpeg`, `command -v pkexec`, etc.

**Esfuerzo:** Bajo · **Recomendado como primera pantalla visible de ajustes.**

---

## Nivel 2 — Muy útiles

### 5. Opciones de apariencia del fondo

**Qué permite** (cuando el tema lo soporta, p. ej. SilentSDDM)
- Modo de relleno: `fill` / `fit` / `stretch`.
- Desenfoque (`blur`), brillo (`brightness`), saturación (`saturation`).
- Usar color de fondo en vez de imagen.

**Para qué sirve**
- Ajustar cómo se ve el fondo sin editar archivos a mano. Muy ligado al flujo de
  previsualización: cambiar y ver el resultado al instante.

**Notas técnicas**
- Claves en el `ConfigFile`: `background-fill-mode`, `blur`, `brightness`,
  `saturation`, `use-background-color`, `background-color`.
- Se puede combinar con el tema temporal para previsualizar en vivo.

**Esfuerzo:** Medio · **Depende de:** detección de tema (idea 3).

---

### 6. Rutas personalizables

**Qué permite**
- Cambiar: carpeta de temas, ruta de `sddm.conf`, ejecutable del greeter,
  carpeta de medios de Aura.
- Botón "detectar automáticamente".

**Para qué sirve**
- En distros el greeter puede estar en `/usr/bin`, `/usr/libexec/sddm`, etc., y
  los temas no siempre están en `/usr/share/sddm/themes`. Aura ya tiene varias
  rutas "hardcodeadas"; esto lo hace portable.

**Notas técnicas**
- `SddmRepositoryImpl` ya recibe `themesDir`, `sddmConfFile`, `sddmConfDir` por
  constructor: se pueden alimentar desde estas preferencias.

**Esfuerzo:** Bajo · **Riesgo:** medio (una ruta mal puesta = nada funciona).

---

### 7. Gestión de almacenamiento y caché

**Qué permite**
- Ver cuánto ocupan los fondos importados y las miniaturas.
- Limpiar miniaturas de `ffmpeg`, temas temporales huérfanos
  (`/tmp/aura-sddm-preview-*`) y archivos borrados.
- Abrir la carpeta de medios en el gestor de archivos.

**Para qué sirve**
- Los videos y miniaturas crecen rápido. Es mantenimiento sencillo con impacto
  visible.

**Notas técnicas**
- Ya existe limpieza de temas huérfanos: exponerla como acción manual.

**Esfuerzo:** Bajo · **Depende de:** nada.

---

### 8. Reiniciar SDDM / probar el tema

**Qué permite**
- Botón "Aplicar cambios" / "Reiniciar SDDM" de forma controlada.
- Aviso claro de que reiniciar SDDM **cerrará la sesión gráfica**.
- Alternativa segura: solo previsualizar con `sddm-greeter --test-mode`.

**Para qué sirve**
- Los cambios de tema SDDM no se ven hasta reiniciar el servicio. Sin esto, el
  usuario no sabe cómo ver el resultado real.

**Notas técnicas**
- `systemctl restart sddm` (requiere privilegios) o `systemctl isolate` para
  probar. Añadir confirmación fuerte y cuenta atrás.

**Esfuerzo:** Bajo · **Riesgo:** alto (aquí sí se puede quedar fuera de la
sesión), por eso va después de backups.

---

### 9. Perfiles o presets

**Qué permite**
- Guardar una combinación (fondo + modo de relleno + efectos) con un nombre.
- Aplicar un preset en un clic; exportarlo a `.json`/`.conf`.

**Para qué sirve**
- Cambiar entre "modo trabajo", "modo noche", etc.; compartir configuraciones
  con otros usuarios de SilentSDDM.

**Esfuerzo:** Medio · **Depende de:** ideas 1 y 5.

---

## Nivel 3 — Mejoras de experiencia

### 10. Tema claro / oscuro / seguir al sistema

**Qué permite** escoger el aspecto de Aura.
**Para qué sirve** adaptarse a la preferencia del usuario y a la iluminación.
**Notas** ya existe una paleta oscura refinada (`AuraColors`); habría que crear
una variante clara y exponerla con `MaterialTheme`.
**Esfuerzo:** Bajo.

### 11. Idioma (español / inglés)

**Qué permite** cambiar los textos de la interfaz.
**Para qué sirve** ampliar público; el código está en español hoy.
**Notas** requiere extraer cadenas a recursos (Compose Resources ya está en uso).
**Esfuerzo:** Medio.

### 12. Notificaciones y registro de operaciones

**Qué permite** mostrar un historial de "qué se importó / aplicó / eliminó" y
avisos toast.
**Para qué sirve** transparencia y trazabilidad, sobre todo al aplicar cambios.
**Notas** puede reutilizarse para el log de la previsualización (hoy imprime el
stdout del greeter en consola).
**Esfuerzo:** Bajo.

### 13. Atajos de teclado

**Qué permite** importar, previsualizar, aplicar, borrar con atajos.
**Para qué sirve** agilidad para usuarios de teclado.
**Esfuerzo:** Medio.

### 14. Inicio automático / bandeja del sistema

**Qué permite** que Aura arranque con la sesión o viva en la bandeja.
**Para qué sirve** utilidad para "cambiar de fondo" en caliente.
**Esfuerzo:** Medio.

### 15. Exportar / importar preferencias de Aura

**Qué permite** respaldar la configuración de Aura (rutas, tema elegido,
presets) en un archivo.
**Para qué sirve** migrar de equipo o reinstalación limpia.
**Esfuerzo:** Bajo.

---

## Nivel 4 — Futuro / opcional

### 16. Comprobador de actualizaciones
Avisar de nuevas versiones. **Esfuerzo alto, valor bajo** hoy.

### 17. "Acerca de"
Versión, licencia, enlaces al repositorio y créditos de SilentSDDM/ffmpeg.
**Esfuerzo bajo**, útil para soporte.

### 18. Multi-monitor y resolución
Elegir fondo por pantalla y detectar resolución. Complejo y depende del tema.
**Esfuerzo alto.**

### 19. Telemetría / métricas de uso
**No recomendado** para una herramienta local por privacidad y porque aporta poco.

---

## MVP sugerido para la primera versión de Ajustes

Si quieres algo funcional y de bajo riesgo ya mismo, el orden recomendado es:

1. **Diagnóstico del entorno** (idea 4) — barato y muy útil.
2. **Selector de tema SDDM** (idea 3) — barato y desbloquea lo demás.
3. **Gestión de almacenamiento y caché** (idea 7) — barato.
4. **Copia de seguridad** (idea 2) — habilita aplicar sin miedo.
5. **Aplicar fondo** (idea 1) — el objetivo real de la app.

Luego, en una segunda iteración, apariencia (5), rutas (6) y presets (9).

---

## Nota técnica detectada

En `Navigations.kt` el ítem de ajustes usa `path = "setting"`, pero `App.kt`
compara contra `"ajustes_avanzados"`. Por eso hoy el clic cae en el `else` y
muestra `ImageScreen`. Al crear la pantalla real conviene unificar la ruta.

Además, el `Aplicar` del `FooterScreen.kt` sigue con `clickable { }` vacío:
es el punto natural donde empezará a vivir la idea 1.
