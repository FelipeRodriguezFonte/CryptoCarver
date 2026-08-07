# Propuesta de mejora de UX — CryptoCarver

**Destinatario:** agente implementador (Antigravity / Codex)
**Alcance:** interfaz moderna (`main-view-modern.fxml` + `ModernMainController`) y controladores de módulo.
**Fuera de alcance:** algoritmos criptográficos, contratos de `OperationResult`, formato `.ccconfig`, CLI.

---

## 1. Diagnóstico

Análisis del repositorio en el commit `c204f46`. Hallazgos ordenados por impacto en el usuario.

| # | Hallazgo | Evidencia |
|---|---|---|
| D1 | Toda la aplicación se carga y se inicializa en el arranque | 14 `fx:include` en `main-view-modern.fxml`; `ModernMainController.initialize()` llama a 9 `loadXContent()` |
| D2 | Las operaciones largas bloquean el hilo de JavaFX | Solo 8 de 47 clases de `ui/` usan `Platform.runLater`/hilos; `KeysController:2150` genera RSA en línea; 2 indicadores de progreso en toda la interfaz moderna |
| D3 | Identidad visual partida: CSS claro + estilos en línea oscuros | `styles.css` define `-color-bg-base: #f4f4f4`; 81 `style=` en línea en el FXML moderno usan `#1e293b`/`#0f172a`; ~135 `setStyle(` en Java |
| D4 | Navegación de tres saltos sin memoria ni migas | Rail (12 secciones) → árbol lateral → acordeón (`keys.fxml`: 16 `TitledPane`, 31 `TextArea`) |
| D5 | Errores como texto crudo de excepción en diálogos modales | 30 `new Alert(`, 180 `showError(..., e.getMessage())`, 22 `printStackTrace` |
| D6 | Conflicto de atajos: `Ctrl+C` secuestra el copiado de texto | `main-view-modern.fxml:34` (`accelerator="Ctrl+C"` en *Copy Output*); todos los aceleradores usan `Ctrl+` salvo `Shortcut+K` |
| D7 | Contenido de maqueta visible en producción | `main-view-modern.fxml:383-399` (historial con entradas falsas "HKDF SHA256 32B"/"AES-GCM Encrypt"), `:366` (aviso de salt fijo), `:414` ("Java 21" con `maven.compiler.release=17`) |
| D8 | Sin internacionalización | Ningún `.properties` de i18n en `src/main/resources`; interfaz en inglés, documentación y público objetivo en español |
| D9 | ~700 KB de FXML muertos en el JAR | `main-view-final-v2` (101 KB), `main-view-final` (97 KB), `main-view-sin-pin` (88 KB), `main-view-working` (87 KB), `main-view-reorganizado` (87 KB), `main-view.fxml.pre-reorganizacion` (106 KB) — ninguno referenciado desde Java |
| D10 | Iconos emoji con render inconsistente entre plataformas | `NavigationRail.Section`: `🔍◈🔒🛡🔑⚛📝📜🌐💳{}⏱` (`{}` ni siquiera es emoji) |
| D11 | La paleta de comandos existe pero no se descubre | `Shortcut+K`, solo visible en el menú *View*; sin punto de entrada en la barra de herramientas |
| D12 | No hay botón de ejecución consistente | La barra de flujo muestra `Entrada → Operación → Salida`, pero el botón de ejecutar vive dentro de cada acordeón |

**Lectura de conjunto:** la funcionalidad criptográfica es amplia y sólida; lo que penaliza la experiencia es la *carcasa*: arranque lento, congelaciones sin aviso, dos lenguajes visuales mezclados y errores que no explican cómo corregir.

---

## 2. Plan de bloques

Ocho bloques independientes. El orden recomendado es el de la tabla: cada bloque es entregable y verificable por separado.

| Bloque | Título | Esfuerzo | Impacto | Riesgo |
|---|---|---|---|---|
| UX-01 | Limpieza de FXML muertos y maqueta | S | Medio | Bajo |
| UX-02 | Corrección de aceleradores y teclado | S | Alto | Bajo |
| UX-03 | Ejecución asíncrona con progreso y cancelación | M | Alto | Medio |
| UX-04 | Tokens de estilo y tema unificado (claro/oscuro) | M | Alto | Medio |
| UX-05 | Errores accionables e inline | M | Alto | Bajo |
| UX-06 | Carga diferida de módulos | M | Medio | Medio |
| UX-07 | Navegación: recientes, favoritos, migas y paleta visible | M | Alto | Bajo |
| UX-08 | Internacionalización ES/EN | M | Medio | Bajo |

Los bloques UX-02, UX-04, UX-07 y UX-08 operacionalizan puntos ya listados como pendientes en `docs/CRYPTOCARVER_ROADMAP_EVOLUCION.md`, Fase 1.

---

## UX-01 — Limpieza de FXML muertos y contenido de maqueta

### Objetivo
Eliminar recursos no referenciados y datos falsos visibles al usuario.

### Tareas

1. Confirmar que solo `main-view.fxml` (usado por `CryptoCalculatorApp`) y `main-view-modern.fxml` (usado por `CryptoCalculatorModern`) están referenciados:
   ```bash
   grep -rn "main-view" src/main/java --include=*.java
   ```
2. Mover a `docs/legacy-fxml/` (fuera de `src/main/resources`, para que no entre en el JAR) los siguientes ficheros:
   `main-view-final.fxml`, `main-view-final-v2.fxml`, `main-view-sin-pin.fxml`, `main-view-working.fxml`, `main-view-reorganizado.fxml`, `main-view.fxml.pre-reorganizacion`.
3. En `main-view-modern.fxml`:
   - Vaciar `historyContainer` (líneas 383-399): dejar el `VBox` sin hijos; `ModernMainController.initializeHistory()` ya lo puebla.
   - `securityTipLabel` (línea 366): texto inicial vacío, `managed="false"` y `visible="false"`; hacerlo visible solo cuando `updateInspector` publique un aviso real.
   - Sustituir el literal `"Java 21 | JavaFX 21 | BouncyCastle"` (línea 414) por un `fx:id="runtimeInfoLabel"` rellenado en `initialize()` desde `System.getProperty("java.version")`, `System.getProperty("javafx.version")` y la versión de `cryptocarver-build.properties`.
   - Vaciar los textos por defecto de `contentTitleLabel`/`contentSubtitleLabel` y de las etiquetas del inspector (`operationLabel` = "HKDF-SHA256", `outputBytesLabel` = "32").

### Verificación
- `xmllint --noout src/main/resources/fxml/main-view-modern.fxml`
- `mvn -q test` (revisar `FxmlContractTest`, `ModernMainControllerFxmlStaticTest`)
- Nueva prueba en `ModernMainControllerUITest`: el `historyContainer` está vacío antes de ejecutar cualquier operación.

---

## UX-02 — Aceleradores y navegación por teclado

### Objetivo
Que los atajos no interfieran con la edición de texto y que funcionen igual en macOS, Windows y Linux.

### Tareas

1. **Eliminar** `accelerator="Ctrl+C"` de *Copy Output* (`main-view-modern.fxml:34`). Rompe el copiado nativo en los 31+ `TextArea`/`TextField` de la aplicación. Reasignar a `Shortcut+Shift+C`.
2. Sustituir **todos** los `Ctrl+` por `Shortcut+` en el `MenuBar` para que en macOS respondan a ⌘. Afecta a las líneas 20, 25, 28, 32, 33, 41, 42, 43, 44, 46, 47, 62, 63.
3. Añadir un acelerador global de ejecución: `Shortcut+Enter` → ejecuta la operación activa (método `runActiveOperation()`, definido en UX-07).
4. Añadir orden de foco explícito (`focusTraversable`) y `accessibleText` en los controles principales de la barra de flujo y del panel de resultado.
5. Documentar la tabla completa de atajos en *Help → Keyboard Shortcuts* (nuevo `MenuItem`, diálogo de solo lectura generado desde una única lista constante `KeyboardShortcuts.ALL`, de modo que menú y diálogo no se desincronicen).

### Verificación
- Prueba unitaria: recorrer `mainMenuBar.getMenus()` y afirmar que ningún acelerador es `Ctrl+C`, `Ctrl+V`, `Ctrl+X`, `Ctrl+A` ni `Ctrl+Z`.
- Prueba unitaria: todos los aceleradores usan el modificador `SHORTCUT_DOWN`.
- Prueba: `KeyboardShortcuts.ALL` cubre todos los `MenuItem` con acelerador.

---

## UX-03 — Ejecución asíncrona con progreso y cancelación

### Objetivo
Que la ventana nunca se congele y que el usuario sepa siempre qué está pasando y pueda abortar.

### Diseño

Nueva clase `com.cryptocarver.ui.OperationExecutor` (única, compartida por todos los controladores):

```java
public final class OperationExecutor {
    public static <T> void run(String label,
                               Callable<T> work,
                               Consumer<T> onSuccess,
                               BiConsumer<String, Throwable> onFailure,
                               StatusReporter reporter);
    public static void cancelActive();
    public static boolean isBusy();
}
```

Comportamiento:

- Envuelve `work` en un `javafx.concurrent.Task` sobre un `ExecutorService` de un solo hilo con hilos demonio.
- Publica en la barra de estado `label` + spinner; tras 400 ms sin terminar, muestra una barra de progreso indeterminada y un botón **Cancelar** en la barra de estado.
- `onSuccess`/`onFailure` se invocan siempre en el hilo de JavaFX.
- Deshabilita el botón de ejecución activo mientras `isBusy()` (evita dobles ejecuciones), no la ventana entera.
- Si `test.mode` está activo, ejecuta de forma síncrona para no romper las pruebas existentes.

### Migración (orden sugerido)

Migrar únicamente los manejadores con coste real o E/S de red. No convertir operaciones instantáneas (hash de texto corto, conversiones de formato): el parpadeo del spinner empeora la percepción.

| Prioridad | Ubicación | Motivo |
|---|---|---|
| 1 | `PadesController`, `CertificatesController` (CAdES-T), `XMLSignatureController` (XAdES-T) | Llamadas TSA por red; hoy congelan hasta el timeout |
| 2 | `KeysController.handleGenerateRSA` y las otras 2 rutas de generación RSA/DSA | RSA-4096 puede superar los 10 s |
| 3 | `PostQuantumController` (keygen, benchmark) | Ya tiene `pqcBenchmarkProgress`; unificar |
| 4 | `CipherController` (cifrado por streaming de ficheros) | Ficheros de hasta 1 GiB |
| 5 | `KeysController` (PBKDF2/derivación con iteraciones altas) | Coste proporcional a la configuración |
| 6 | `OpenPgpController`, `ProcessDesignerController` (ejecución de proceso) | Ya usan hilos parcialmente; homogeneizar |

### Verificación
- Prueba unitaria de `OperationExecutor`: éxito, fallo y cancelación; las devoluciones de llamada llegan en el hilo de FX.
- Prueba de regresión: con `test.mode=true` el comportamiento es síncrono y las pruebas de UI existentes siguen en verde.
- Comprobación manual: generar RSA-4096 y arrastrar la ventana durante la operación.

---

## UX-04 — Tokens de estilo y tema unificado

### Objetivo
Un único lenguaje visual, con temas claro/oscuro/alto contraste conmutables.

### Diseño

1. Crear `src/main/resources/css/tokens.css` con la definición de variables (`-color-*`, `-space-*`, `-radius-*`, `-font-size-*`) en `.root`.
2. Crear `theme-dark.css` y `theme-light.css` que **solo** redefinen los valores de los tokens. `styles.css` pasa a consumir tokens exclusivamente; no debe contener ningún literal hexadecimal.
3. **Decidir el tema base como oscuro.** Los estilos en línea actuales ya son oscuros (`#1e293b`, `#0f172a`, `#f8fafc`) y son mayoría en las pantallas nuevas; migrar `styles.css` al oscuro produce menos regresión visual que lo contrario.
4. Erradicar estilos en línea por orden de densidad:
   - `keys.fxml` (183), `process_designer.fxml` (72), `cipher.fxml` (45), `main-view-modern.fxml` (81), `key_certificate_workbench.fxml` (34), resto.
   - Cada `style="..."` se sustituye por `styleClass="..."`. Clases nuevas necesarias, deducidas de los patrones repetidos actuales: `.badge-success`, `.badge-warning`, `.badge-danger`, `.badge-neutral`, `.panel-card`, `.panel-card-title`, `.text-muted`, `.text-small`, `.button-compact`.
   - En Java, sustituir los ~135 `setStyle(` por `getStyleClass().add(...)` / `pseudoClassStateChanged`.
5. Añadir *View → Theme* con tres `RadioMenuItem` (Claro / Oscuro / Alto contraste) persistidos en `AppSettings` (nuevo campo `theme`, junto a `secretVisibility`).
6. Sustituir los emoji del `NavigationRail` y del árbol lateral por una fuente de iconos empaquetada (recomendado: **Ikonli** `ikonli-javafx` + `ikonli-material2-pack`, licencia Apache 2.0, sin red). Mantener un mapa `Section → icon literal` en un único sitio.

### Regla de aceptación
```bash
# Ningún literal de color fuera de los ficheros de tema
grep -rn "#[0-9a-fA-F]\{6\}" src/main/resources/css/styles.css | wc -l   # debe ser 0
grep -c 'style="' src/main/resources/fxml/main-view-modern.fxml           # debe ser 0
```

### Verificación
- `xmllint --noout` sobre cada FXML modificado.
- Prueba: cambiar de tema y afirmar que `scene.getStylesheets()` contiene exactamente un fichero `theme-*.css`.
- Prueba: `AppSettings` persiste y recupera el tema.
- Capturas manuales antes/después de las cinco pantallas más usadas (Keys, Cipher, Certificates, JOSE, Generic).

---

## UX-05 — Errores accionables

### Objetivo
Que el mensaje diga qué ha fallado, en qué campo y cómo corregirlo, sin bloquear con un modal.

### Diseño

1. Nuevo tipo `com.cryptocarver.model.UserFacingError`:
   ```java
   public record UserFacingError(String title,       // "La clave no tiene la longitud correcta"
                                 String detail,      // qué se esperaba y qué se recibió
                                 String remedy,      // "Usa 16, 24 o 32 bytes para AES"
                                 String fieldKey,    // clave de control para enfocar; puede ser null
                                 Throwable cause) {} // solo para la sección técnica
   ```
2. Nuevo `ErrorBanner` (un `HBox` con `styleClass="error-banner"`) montado en `mainContentArea` bajo `resultSummaryBar`. Muestra `title` + `remedy`, con enlaces **Ir al campo** (usa el `focusControl(fieldKey)` ya existente en `ModernMainController:2950`) y **Copiar detalle técnico**.
3. `StatusReporter.showError(String, String)` se mantiene por compatibilidad y delega en una nueva sobrecarga `showError(UserFacingError)`. El banner sustituye al `Alert` modal salvo en operaciones destructivas o que requieran confirmación.
4. Marcar el control en error con la pseudoclase `:error` (borde rojo) y limpiarla al modificar el campo.
5. Sustituir los 22 `printStackTrace` por `logger.error(...)` (SLF4J ya está en el proyecto) y adjuntar el stack trace solo a `UserFacingError.cause`.
6. Construir un catálogo de mensajes: mapear las excepciones más frecuentes (`IllegalArgumentException` de `InputValidator`, `InvalidKeyException`, `BadPaddingException`, `AEADBadTagException`, `CertPathValidatorException`, timeouts de TSA) a `title`/`remedy` en español e inglés claros. Este mapa es el entregable de mayor valor del bloque.

### Verificación
- Prueba: `showError(UserFacingError)` con `fieldKey` no nulo enfoca el control.
- Prueba: ningún `printStackTrace` en `src/main/java/com/cryptocarver/ui/`.
- Prueba: descifrar AES-GCM con tag corrupto produce un mensaje del catálogo, no `javax.crypto.AEADBadTagException: Tag mismatch`.

---

## UX-06 — Carga diferida de módulos

### Objetivo
Reducir el tiempo de arranque y el tamaño del grafo de escena; eliminar la clase de errores "dos módulos visibles a la vez".

### Diseño

1. Sustituir los 14 `fx:include` de `main-view-modern.fxml` por un `StackPane fx:id="moduleHost"` vacío.
2. Nueva clase `ModuleLoader`:
   ```java
   public final class ModuleLoader {
       public ModuleHandle load(UiNavigationRegistry.Module module); // carga y cachea
       public void show(UiNavigationRegistry.Module module);         // carga si hace falta y muestra
   }
   ```
   - Caché `Map<Module, ModuleHandle>` con nodo raíz y controlador; se carga la primera vez que se navega.
   - `show()` sustituye a `hideAllContainers()` + `setVisible/setManaged`: solo hay un hijo en `moduleHost`, por construcción.
3. El cableado que hoy hace `initialize()` (`setStatusReporter`, `setFormatControls`, `initModern`, …) pasa a un método `wire(Object controller)` invocado tras cada carga.
4. Precargar en segundo plano, tras mostrar la ventana, los dos módulos más usados (`KEYS_SYMMETRIC` y `GENERIC`) para que el primer salto sea instantáneo.
5. `keys.fxml` (71 KB, 16 `TitledPane`) y `jose.fxml` (37 KB) se benefician especialmente; considerar dividir `keys.fxml` en `keys-symmetric.fxml` y `keys-asymmetric.fxml`, que ya son rutas distintas en `UiNavigationRegistry`.

### Riesgo y mitigación
`UiStateSnapshot`, el histórico y las configuraciones `.ccconfig` asumen hoy que todos los controladores existen. **Mitigación:** `ModuleLoader.load()` (sin `show()`) fuerza la instanciación; los flujos de exportación/importación llaman a `load()` para cada módulo que necesiten antes de leer o escribir estado. Es la parte delicada de este bloque; abordarla explícitamente en las pruebas.

### Verificación
- `mvn -q test`: `UiNavigationRegistryTest`, `UiStateSnapshotTest`, `ScreenConfigurationFilesTest` y los smoke tests de navegación deben pasar sin cambios de comportamiento.
- Prueba nueva: tras navegar a cada ruta registrada, `moduleHost.getChildren().size() == 1`.
- Prueba nueva: exportar e importar `.ccconfig` de un módulo nunca visitado en la sesión funciona.
- Medición: registrar el tiempo entre `Application.start()` y el primer `Stage.show()` antes y después (objetivo: reducción ≥ 50 %).

---

## UX-07 — Navegación: recientes, favoritos, migas y paleta visible

### Objetivo
Reducir de tres saltos a uno el acceso a las operaciones que cada usuario repite.

### Tareas

1. **Paleta de comandos visible**: añadir en la barra de herramientas un botón/campo `Buscar operación (⌘K)` que abre `commandPaletteOverlay`. Es la ruta más rápida que ya existe y hoy está oculta en un menú.
2. **Recientes y favoritos** en la parte superior del `SidePanel`, sobre el árbol:
   - `Recientes`: últimas 8 operaciones ejecutadas, derivadas de `HistoryManager` (no de la navegación: lo relevante es lo ejecutado).
   - `Favoritos`: alternables con una estrella en el árbol y en el encabezado de contenido; persistidos en `AppSettings` (nuevo campo `favoriteOperations: List<String>`, guardando el nombre canónico de operación de `UiNavigationRegistry`).
3. **Migas de navegación**: sustituir el par `contentTitleLabel` / `contentSubtitleLabel` por un `Breadcrumb` `Sección › Módulo › Operación`, con los dos primeros niveles pulsables.
4. **Botón de ejecución persistente**: añadir a la derecha de la barra de flujo un botón primario **Ejecutar** que invoca `runActiveOperation()` — el manejador de la operación actualmente seleccionada. Requiere que `ModernMainController` mantenga `currentActiveOperation` (ya existe, usado por el preflight) mapeado a un `Runnable` registrado por cada controlador de módulo. Los botones dentro de los acordeones se mantienen; el de la barra es un acceso adicional y el destino de `Shortcut+Enter`.
5. **Recordar la última ruta**: al arrancar, restaurar la última operación visitada en lugar de forzar *Symmetric Keys*. Añadir en el menú *View* la opción de volver a la pantalla de inicio.

### Verificación
- Prueba: marcar favorito → reiniciar `AppSettings` → el favorito persiste.
- Prueba: `runActiveOperation()` con `currentActiveOperation` nulo no lanza excepción y avisa por la barra de estado.
- Prueba: las migas reflejan la ruta de `UiNavigationRegistry` para todas las rutas registradas.

---

## UX-08 — Internacionalización ES/EN

### Objetivo
Interfaz en español e inglés. La documentación, el README y el público objetivo son hispanohablantes; la interfaz es solo inglesa.

### Tareas

1. Crear `src/main/resources/i18n/messages.properties` (inglés, por defecto) y `messages_es.properties`.
2. Cargar el `ResourceBundle` en `FXMLLoader` (`new FXMLLoader(url, bundle)`) y usar la sintaxis `%clave` en los FXML. Es un cambio mecánico y verificable.
3. Orden de migración: `MenuBar` y barra de herramientas → `NavigationRail`/`SidePanel` → catálogo de errores de UX-05 → títulos y descripciones de operaciones de `OperationRegistry` → resto de módulos.
4. Selector de idioma en *View → Language* (Sistema / Español / English), persistido en `AppSettings`; aplicar en el arranque siguiente con aviso claro.
5. **No traducir** nombres de algoritmos, estándares, siglas ni salidas técnicas (AES-GCM, TR-31, ARQC, PKCS#7…). Solo se traduce texto de interfaz, ayuda y errores.

### Verificación
- Prueba: `messages.properties` y `messages_es.properties` tienen exactamente el mismo conjunto de claves.
- Prueba: ningún `%clave` de los FXML migrados queda sin resolver en el bundle.
- `xmllint --noout` sobre los FXML modificados.

---

## 3. Reglas de trabajo

Se aplican las de `docs/ANTIGRAVITY_IMPLEMENTATION_BACKLOG.md` §1. En particular:

- **Un bloque por rama y por handoff.** No mezclar bloques: UX-04 y UX-06 tocan los mismos ficheros y juntos son irrevisables.
- **No usar `mvn clean`** en iteración ordinaria.
- Comandos mínimos antes de dar un bloque por terminado:
  ```bash
  /opt/homebrew/bin/mvn -q test
  xmllint --noout src/main/resources/fxml/main-view-modern.fxml
  git diff --check
  ```
- Ningún cambio de este documento debe alterar resultados criptográficos. Si un bloque obliga a tocar una clase de `crypto/`, es señal de que el alcance se ha desviado.
- Cada bloque termina con un `docs/HANDOFF_UX-0X.md` siguiendo la plantilla de §5 del backlog.

## 4. Nota sobre el tamaño de los controladores

`KeysController` (5 152 líneas, 254 miembros `@FXML`), `CipherController` (4 257) y `ModernMainController` (3 321) hacen que cualquier cambio de interfaz sea lento y arriesgado. No se propone una refactorización dedicada: sería un bloque grande sin valor visible para el usuario. La recomendación es **extraer de forma oportunista** — cuando un bloque de esta propuesta toque una de estas clases, sacar la lógica afectada a una clase auxiliar (como ya se hizo con `OperationInspectorPresenter` y `ResultAreaTracker`) en lugar de añadir código al monolito.

## 5. Qué no se propone y por qué

- **Reescritura de la interfaz o cambio de framework.** El coste no se justifica: los problemas son de carcasa, no de arquitectura de fondo, y `UiNavigationRegistry` + `StatusReporter` ya dan una base razonable.
- **Rediseño de la estructura de módulos.** La taxonomía actual (Rail de 12 secciones) es coherente con el dominio; el problema es el acceso, que resuelve UX-07.
- **TestFX/Monocle.** Sería útil, pero añadir un framework de pruebas de interfaz es un bloque de infraestructura propio; las pruebas indicadas aquí se apoyan en el enfoque estático y de contrato ya presente en `src/test/java/com/cryptocarver/ui/`.
