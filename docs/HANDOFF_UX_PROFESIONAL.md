# Handoff UX — De laboratorio a aplicación profesional

**Fecha de auditoría:** 2026-09-17
**Rama auditada:** `feat/icsf-parity-with-python` @ `2e1d60a` (al día con `origin`, contiene `main` / release 2.4.0)
**Método:** app arrancada (`mvn -DskipTests compile javafx:run`, JDK 21) y recorrida a 1400×928 y 1280×800 en macOS, idioma del sistema = español; revisión de `main-view-modern.fxml`, `styles.css`, `NavigationRail`, `SidePanel`, `ModernMainController` e `history.fxml`.
**Documento anterior:** `docs/MEJORAS_UX.md` (2026-08-03). Este documento lo **sustituye**: recoge sus bloques aún abiertos (marcados como *arrastrado de MEJORAS_UX*) y añade los hallazgos de la revisión visual.

---

## 0. Diagnóstico en una frase

CryptoCarver tiene funcionalidad de herramienta profesional, pero la interfaz transmite "prototipo acumulado": **tres temas visuales mezclados en la misma pantalla, dos idiomas en la misma pantalla, texto ilegible o truncado en puntos críticos, acciones duplicadas en tres sitios y navegación que se desincroniza**. Nada de esto requiere rediseñar la app; requiere **un sistema de diseño aplicado con disciplina** y corregir una docena de defectos concretos.

### Evidencia observada (capturas tomadas durante la auditoría)

| # | Pantalla | Qué se ve |
|---|---|---|
| E1 | Cifrado simétrico | Barra de menús, rail y cabecera oscuros; árbol lateral y tarjetas claros; Inspector con cabecera oscura, tarjeta clara y tarjetas de historial oscuras. Tres lenguajes visuales a la vez. |
| E2 | Cifrado simétrico | Mezcla ES/EN en la misma vista: "Aplicar plantilla: *Select a template…*", "Origen de la clave: *Manual Input*", "Pegar clave / *Usar desde Shelf*", nota "*Note: Select 'Simulated HSM'…*", árbol "*Symmetric / Key Lab / Tools*", Inspector "*Reopen*, *11h ago*". |
| E3 | Paleta de comandos (⌘K, búsqueda "mac") | El **título de cada resultado es invisible** (texto blanco sobre fila blanca). Solo 2 resultados para "mac" pese a existir HMAC/CMAC/Retail MAC. Categorías en crudo "[Navigation · COSE]". |
| E4 | Process Designer / PIN / Hashing / Historial abiertos desde la paleta | El **árbol lateral sigue mostrando "Keys"** y el rail no resalta la sección activa: navegación desincronizada. |
| E5 | Historial a 1280×800 | Botones truncados "Li…", "C…", "Impo…", "Limpi…"; título "Operaciones r…"; 11 botones con 7 colores distintos (verde, gris, azul, azul oscuro, teal, morado, rojo); "Limpiar historial" (destructivo, rojo) pegado a "Importar". |
| E6 | Barra superior | A 1400 px la barra de herramientas ya ocupa **dos filas**; "Ampliar resultado / Añadir a Shelf / Copiar salida" aparecen también en la barra de resultado y en el menú Editar. |
| E7 | Breadcrumb | "Pagos › Clear PIN Blocks › Clear PIN Blocks" (segmento repetido); "Histórico › HISTORY › Export History" (clave interna en mayúsculas y operación incorrecta); el segmento actual tiene contraste casi nulo. |
| E8 | Inspector | Etiquetas truncadas "Pur…", "Exp…", "K…"; contador "0" flotante; fila vacía con un "<" no interactivo; botones "Añadir/Exportar" deshabilitados casi invisibles; valores de enum en crudo "STABLE", "HIGH", "EXPERIMENTAL". |
| E9 | Barra de estado | Franja inferior oscura **sin texto visible** (el estado y la info de runtime no se leen). |
| E10 | Formularios | Placeholders en monoespaciada y en inglés usados como instrucciones ("Hex IV (required for CBC mode)…", "Key (Hex) - e.g., for" truncado); botones principales ("Cifrar/Descifrar") quedan bajo el pliegue. |
| E11 | Rail y árbol | Iconos emoji de colores y estilos dispares; icono literal `{}` para ASN.1; el árbol lateral tiene scroll horizontal y etiquetas cortadas ("ICSF / CCA Key Token Analyzer EX"). |
| E12 | Menús | Orden "Archivo · Editar · Ver · Seguridad · Herramientas · Ayuda · **Laboratorio**" (Ayuda no es el último); "Laboratory" se crea en código con nombres de enum ("TR31 - …"); en macOS el menú vive dentro de la ventana; no hay Preferencias (⌘,), ni Deshacer/Cortar/Copiar/Pegar/Seleccionar todo en Editar. Radio items "Visibility: FULL_LAB (Debug/Learning)". |
| E13 | Process Designer | Lienzo diminuto encajonado dentro del scroll general, con árbol lateral + Inspector abiertos; la barra de herramientas del diseñador queda cortada arriba; un estado ("Selecciona 2 bloques para conectar") se pinta como botón primario deshabilitado; el selector de formato de payload global aparece deshabilitado aunque no aplica. |
| E14 | Utilidades (Hashing) | El acordeón de "Utilidades" contiene "Process Designer" y "Workbench de formatos…", que ya son secciones propias: arquitectura de información duplicada. |

### Métricas de deuda visual (medidas en el código actual)

| Métrica | Valor | Objetivo |
|---|---|---|
| Literales hex en `styles.css` (2 394 líneas) | 291 | 0 fuera de `tokens.css` |
| Selectores **duplicados** en `styles.css` (p. ej. `.status-bar` en l. 490 y 974, `.button`, `.action-button`) | 85 | 0 |
| `style="…"` inline en FXML (keys 246, main-view-modern 68, cipher 47, key_certificate_workbench 34, process_designer 32…) | ~500 (sin contar `main-view.fxml` legacy: 319) | 0 salvo casos justificados |
| `setStyle(` en Java | 135 | < 10 (solo valores dinámicos) |
| Tamaños de fuente distintos | 11 (8–20 px); 382 usos de 11 px y 140 de 10 px con `.root` a 15 px | 5 pasos de escala |
| Clases de botón distintas | 15 (`secondary-button`, `action-button`, `action-button,primary-action`, `action-button-primary`, `button-primary`, `button-danger`, `danger-button`, `btn-danger`…) | 4 variantes + 3 tamaños |
| `new Alert(` fuera del banner de errores | 37 en 12 controladores | Solo confirmaciones destructivas vía un helper |
| `printStackTrace` | 25 (eran 18 en agosto) | 0 |
| `fx:include` cargados al arrancar | 15 | 0 (carga diferida) |

---

## 1. Reglas para el agente que implemente esto

1. **Build/arranque:** el `java` por defecto de la máquina es 8. Usar `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` (o cualquier JDK ≥ 17) y `./run-modern.sh`. Tests de UI: `mvn -DrunUiTests=true test` (CI: `.github/workflows/ui-tests.yml`, Xvfb).
2. **Un ticket = un commit** (Conventional Commits, como el historial). No mezclar tickets.
3. **Captura antes/después** de cada ticket visual a 1280×800 y 1920×1080, en ES y EN, adjunta al PR.
4. **i18n obligatorio:** todo texto nuevo o tocado va a `i18n/messages.properties` **y** `messages_es.properties` (y `messages_en.properties` si difiere). Nunca literales en FXML/Java.
5. **No engordar los monolitos** (`ModernMainController` 4 812 l., `KeysController` 6 538, `CipherController` 4 615): la lógica nueva va a clases auxiliares (patrón existente: `OperationInspectorPresenter`, `ResultAreaTracker`, `InlineErrorPresenter`). Si un ticket toca un bloque de estos archivos, extraerlo.
6. **Sin dependencias con red** en tiempo de ejecución; librerías nuevas solo con licencia compatible (Apache 2.0/MIT/BSD) y añadidas a `pom.xml` y al empaquetado (`package_*.sh`).
7. **Seguridad de datos:** no romper los perfiles FULL_LAB/MASKED/REDACTED ni la política de secretos del Clipboard Shelf/Historial al mover componentes.

---

## 2. Plan por fases

Orden recomendado: **Fase 0 → 1 → 2 → 3 → 4**. La Fase 0 son defectos visibles de bajo esfuerzo que conviene sacar ya; la Fase 1 es la base sin la cual el resto se rehará dos veces.

| Fase | Objetivo | Tickets | Esfuerzo |
|---|---|---|---|
| 0 | Quitar lo que parece roto | UXP-01 … UXP-07 | S (1–3 días) |
| 1 | Sistema de diseño y tema único | UXP-10 … UXP-15 | L (2–3 semanas) |
| 2 | Estructura del shell y navegación | UXP-20 … UXP-27 | M-L |
| 3 | Patrones de formulario, resultado y feedback | UXP-30 … UXP-37 | M |
| 4 | Pulido de plataforma, rendimiento y accesibilidad | UXP-40 … UXP-47 | M |

---

## Fase 0 — Defectos visibles (quick wins)

### UXP-01 · Paleta de comandos: títulos invisibles y búsqueda pobre
- **Problema (E3):** la celda usa `history-card-title` (texto blanco pensado para tarjetas oscuras) sobre la `ListView` clara. `ModernMainController.java:4682-4720`. La búsqueda de "mac" no devuelve HMAC/CMAC.
- **Cambio:**
  - Crear clases propias `.command-palette-item`, `.command-palette-item-title`, `.command-palette-item-desc`, `.command-palette-category` y estilar la `ListView` de la paleta coherente con la tarjeta (fondo y selección definidos, no heredados).
  - Categoría como chip sin corchetes y localizada ("Navegación · COSE"), alineada a la derecha o en gris; atajo de teclado como `kbd`.
  - `CommandSearchEngine`: buscar también en descripción, alias ("Also found as" del Inspector ya existe), y por subcadena dentro de palabras (`mac` ⊂ `hmac`). Ranking: prefijo de título > palabra del título > alias > descripción. Resaltar la coincidencia.
  - Sección "Recientes" cuando la consulta está vacía.
- **Aceptación:** buscar "mac" lista HMAC, CMAC, Retail MAC y COSE_Mac0; todos los títulos contrastan ≥ 4.5:1; test unitario de `CommandSearchEngine` con estos casos.

### UXP-02 · Sincronizar rail + árbol lateral con cualquier navegación
- **Problema (E4):** navegar desde paleta, recientes, favoritos, breadcrumb o "Reabrir" cambia el contenido pero el `SidePanel` sigue en la sección anterior y el rail no marca la activa. Además `handleBreadcrumbSectionClick` (`ModernMainController.java:1283-1320`) no mapea `GENERIC` ni `ASN1` y cae en `showQuickStart()`; su rama de respaldo compara textos traducidos.
- **Cambio:** un único punto de verdad — `NavigationController.navigate(OperationId)` — que (1) selecciona la sección del rail **sin** disparar `selectFirstOperation()`, (2) actualiza el árbol y selecciona/hace scroll al ítem, (3) actualiza breadcrumb, toolbar e Inspector, (4) registra en recientes. Todas las entradas de navegación llaman a este método. Eliminar la rama de respaldo por texto.
- **Aceptación:** test de UI: abrir "PIN block" por paleta → rail = Pagos, árbol = Pagos con "Clear PIN Blocks" seleccionado. Clic en cada segmento de breadcrumb de cada sección navega correctamente (incluidas Utilidades y ASN.1).

### UXP-03 · Breadcrumb correcto y legible
- **Problema (E7):** segmentos repetidos, claves internas ("HISTORY"), operación errónea ("Export History" en la vista Historial), segmentos con aspecto de botón con borde, segmento actual sin contraste.
- **Cambio:** omitir segmentos iguales al anterior; usar siempre la etiqueta localizada del registro (`UiNavigationRegistry`), nunca el enum; segmentos como enlaces planos (subrayado en hover), actual en `-color-text-primary` con peso semibold. Mover el subtítulo "Stable · Sensitive material" a chips (ver UXP-33).
- **Aceptación:** ninguna vista muestra segmentos duplicados ni texto en mayúsculas de enum; contraste del segmento actual ≥ 4.5:1.

### UXP-04 · Barra de estado visible
- **Problema (E9):** la franja inferior no muestra texto legible (colores `-color-text-subtle`/`-color-text-muted` de 10–11 px sobre `-color-bg-sidebar`, reglas `.status-bar` duplicadas en `styles.css:490` y `:974`). Verificar además que no queda recortada por la altura de la ventana.
- **Cambio:** altura fija 26 px, texto 12 px con contraste ≥ 4.5:1. Contenido: izquierda = último estado (con icono ok/error y hora), centro = progreso asíncrono existente, derecha = **perfil de visibilidad activo** (FULL_LAB/MASKED/REDACTED como chip clicable que abre el menú Seguridad) e idioma. Mover "Java | JavaFX | BouncyCastle" a *Ayuda → Diagnóstico/Acerca de*.
- **Aceptación:** tras cifrar, la barra muestra "Cifrado completado · 17:42" legible en ambos temas.

### UXP-05 · Idioma consistente en toda la pantalla
- **Problema (E2):** mezcla ES/EN en árbol lateral ("Symmetric", "Tools", "Key Lab"), selectores ("Select a template…", "Manual Input"), notas, placeholders, Inspector ("Purpose", "Reopen", "11h ago"), paleta y menú Laboratorio.
- **Cambio:** barrido completo de literales en `SidePanel`/`OperationDescriptor`, `ModuleTextCatalog`, celdas de historial, Inspector, plantillas, `setupLaboratoryMenu()` (`ModernMainController.java:4265+`), `ComboBox` con valores de enum (usar `StringConverter` localizado) y placeholders. Tiempos relativos con `I18nService` ("hace 11 h"). Mantener en inglés solo nombres propios/estándares (AES, TR-31, PKCS#11, "Key Check Value" si así se decide en glosario).
- **Herramienta:** añadir test que cargue cada FXML con locale `es` y falle si encuentra `Labeled#getText()`/`promptText` con palabras de una lista negra inglesa (Select, Enter, Manual, Input, Reopen, ago, required, Note). Crear `docs/GLOSARIO_UI.md` con términos que no se traducen.
- **Aceptación:** capturas de las 14 secciones en ES sin palabras en inglés fuera del glosario; el test pasa.

### UXP-06 · Truncados del Inspector e Historial
- **Problema (E5, E8):** `GridPane` de metadatos con primera columna sin `minWidth` (etiquetas "Pur…"); toolbar de Historial en `HBox` sin wrap.
- **Cambio:** Inspector: etiqueta encima del valor (layout apilado) en vez de dos columnas en 280 px. Historial: ver UXP-31 (rediseño de barra de acciones); como mínimo inmediato, `FlowPane` con wrap y `minWidth = USE_PREF_SIZE` en botones.
- **Aceptación:** a 1280×800 no hay ninguna etiqueta con elipsis en Inspector ni en la cabecera de Historial.

### UXP-07 · Menús: orden, nombres y convenciones
- **Problema (E12).**
- **Cambio:**
  - Orden: Archivo · Editar · Ver · Laboratorio · Herramientas · Seguridad · Ventana (macOS) · **Ayuda** (siempre último). Mover la construcción de "Laboratorio" a FXML o a un `MenuFactory` con i18n; perfiles con nombre humano ("TR-31 · Perfil X"), no `TR31 - …`.
  - Editar: añadir Deshacer/Rehacer, Cortar/Copiar/Pegar/Seleccionar todo (delegando en el `TextInputControl` enfocado) antes de las acciones propias.
  - Seguridad: "Visibilidad completa (laboratorio)", "Enmascarada (demo/aula)", "Redactada (estricta)" con descripción en tooltip; nada de `FULL_LAB` en la UI.
  - Añadir *Preferencias…* (⌘, / Ctrl+,) — ver UXP-44.
  - Revisar conflictos: ⌘T (Epoch) choca con la convención "nueva pestaña"; ⌘I alterna Inspector pero ⌘⇧I limpia entrada (acción destructiva a un modificador de distancia) → mover "Limpiar entrada" a ⌘⌫ o sin atajo.
  - "Ver → Ampliar/Reducir (fuente)" → "Aumentar/Reducir tamaño de texto" + "Tamaño real" (⌘0).
- **Aceptación:** `KeyboardShortcutsDialog`/F1 refleja la tabla final; test que verifica que no hay aceleradores duplicados.

---

## Fase 1 — Sistema de diseño y tema único

> *Arrastrado de MEJORAS_UX §1*, ampliado. Es la fase de mayor impacto percibido. Hacer UXP-10 y UXP-11 primero; el resto puede ir en paralelo por módulos.

### UXP-10 · Tokens de diseño
- **Cambio:** crear `css/tokens.css` (solo variables) y `css/theme-light.css` / `css/theme-dark.css` (solo redefinen tokens). `styles.css` pasa a `components.css` y consume **exclusivamente** tokens.
- **Tokens mínimos:**
  - Color semántico: `-cc-bg-app`, `-cc-bg-surface`, `-cc-bg-surface-raised`, `-cc-bg-sunken` (campos), `-cc-border`, `-cc-border-strong`, `-cc-text`, `-cc-text-secondary`, `-cc-text-disabled`, `-cc-accent`, `-cc-accent-hover`, `-cc-on-accent`, `-cc-focus-ring`, `-cc-success/-warning/-danger/-info` (+ `-bg` y `-text` de cada uno), `-cc-secret` (para material sensible), `-cc-selection`.
  - Espaciado (base 4): `-cc-space-1`=4 … `-cc-space-8`=32.
  - Radio: 4 (campos/botones), 8 (tarjetas), 12 (diálogos/overlays).
  - Tipografía (UXP-12) y elevación (2 sombras: `raised`, `overlay`).
- **Criterio de paleta:** un acento único (partir del azul oscuro que ya usan los botones "Cifrar"/"Calcular hash"), neutros fríos tipo slate; estados solo para estado, nunca para decorar botones.
- **Aceptación:** `grep -E '#[0-9a-fA-F]{3,8}' components.css` = 0; ambos temas cargan sin warnings de CSS en consola.

### UXP-11 · Un solo tema coherente (claro por defecto, oscuro opcional)
- **Problema (E1):** chrome oscuro + contenido claro + Inspector mixto + tarjetas oscuras dentro de panel claro + Quick Start con colores de estado para fondo claro (`#b54708`, `#027a48`) sobre fondo oscuro.
- **Cambio:** decidir que **todas** las superficies (menú, toolbar, rail, árbol, contenido, Inspector, barra de estado, diálogos, paleta) derivan del mismo tema. El rail puede ir un tono más oscuro que el árbol, pero dentro de la misma familia. Añadir *Ver → Apariencia: Sistema / Claro / Oscuro* persistido en `AppSettings.theme`; "Sistema" sigue el modo del SO al arrancar.
- **Eliminar:** `-fx-base: -color-bg-base` global que tiñe controles de gris y los mezcla con los estilos custom; definir explícitamente `.text-field`, `.combo-box`, `.button`, `.titled-pane`, `.table-view`, `.list-view`, `.scroll-bar`, `.tooltip`, `.context-menu`.
- **Aceptación:** capturas de las 14 secciones + paleta + un diálogo en claro y oscuro sin ninguna superficie "del otro tema"; contraste AA verificado (texto normal 4.5:1, texto grande/iconos 3:1).

### UXP-12 · Escala tipográfica
- **Problema:** 11 tamaños; `.root` a 15 px con la mayoría de controles a 10–11 px → botones de toolbar enormes y botones de formulario diminutos en la misma pantalla (E1: "Guardar sesión" vs "Aplicar").
- **Cambio:** `.root` 13 px. Escala: 11 (caption/metadatos), 13 (cuerpo, controles), 15 (títulos de grupo), 18 (título de vista), 22 (título de página vacía/onboarding). Pesos: 400/600. Fuente del sistema (`System` → SF en macOS, Segoe UI en Windows) en vez de la lista actual. **Monoespaciada solo para valores** (hex, base64, PEM, JSON, resultados), nunca para placeholders ni etiquetas.
- **Integrar con "Aumentar/Reducir texto":** que escale `.root` (em-based) en lugar de clases `font-small/…` por `TextArea`.
- **Aceptación:** ≤ 5 tamaños en `components.css`; 0 `-fx-font-size` en FXML/Java.

### UXP-13 · Componentes base: botones, campos, tarjetas
- **Botones — 4 variantes × 3 tamaños:** `primary` (1 por grupo de acción: la acción que ejecuta la operación), `secondary` (borde), `ghost` (sin borde: acciones terciarias, toolbar), `danger` (solo destructivas; en ghost hasta que se confirme). Tamaños `sm` 24 px, `md` 30 px, `lg` 36 px. Estados hover/pressed/focus-visible/disabled definidos con tokens. Eliminar las 15 clases actuales y migrar con un script (`secondary-button`→`btn btn-secondary`, etc.).
- **Campos:** altura 30 px, `-cc-bg-sunken`, borde 1 px, anillo de foco 2 px `-cc-focus-ring`, estado `:error` con borde `-cc-danger` + mensaje debajo (ver UXP-32), `:readonly` distinguible.
- **Tarjeta/grupo:** `.card` con título 15 px semibold, descripción opcional 11 px secundaria, padding `space-5`. Sustituye `form-group-box` y los `TitledPane` usados como tarjeta.
- **Aceptación:** catálogo visual interno (`Ayuda → Diagnóstico → Galería de componentes`, solo en build de desarrollo) que muestra todas las variantes en ambos temas; tests de snapshot opcionales.

### UXP-14 · Iconografía vectorial
- *Arrastrado de MEJORAS_UX §1.*
- **Cambio:** Ikonli (`ikonli-javafx` + `ikonli-materialdesign2-pack` o `feather`, Apache 2.0, sin red). Un único `IconRegistry` `OperationId/Section → Ikon`. Iconos monocromos que heredan `-cc-text-secondary` (activo: `-cc-accent`). Sustituir: enum `NavigationRail.Section` (`NavigationRail.java:22-36`, incluido el `{}`), `SidePanel.java:117` (icono por ítem), emojis de `TitledPane` de módulos, Quick Start, banner de error (`⚠️`), paleta (`🔍`), favorito (`☆`), cierre (`✕`).
- **Regla:** en el árbol lateral, icono solo a nivel de grupo, no en cada hoja (reduce ruido; hoy cada hoja tiene un emoji distinto).
- **Aceptación:** `grep -P '[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}]' src/main` = 0 en código de UI.

### UXP-15 · Eliminar estilos inline
- *Arrastrado de MEJORAS_UX §1.*
- **Orden:** `main-view-modern.fxml` (68) → `history.fxml` (botones de colores, l. 43-83) → `keys.fxml` (246) → `cipher.fxml` (47) → `key_certificate_workbench.fxml` (34) → `process_designer.fxml` (32) → resto. `setStyle(` en Java: solo se permiten valores realmente dinámicos (p. ej. color de un nodo del diseñador calculado en runtime); el resto → `getStyleClass()` / `pseudoClassStateChanged`.
- **Legacy:** decidir si `main-view.fxml` + `CryptoCalculatorApp` siguen en el producto. Si no se empaquetan ni se lanzan desde `run*.sh`, eliminarlos (319 estilos inline y los "legacy shell bridge" de `MainController.java:1475+`).
- **Consolidar** los 85 selectores duplicados de `styles.css`.
- **Aceptación:** 0 `style="` en FXML; `setStyle(` < 10 con comentario que lo justifique.

---

## Fase 2 — Estructura del shell y navegación

### UXP-20 · Layout redimensionable y paneles con memoria
- **Problema:** `HBox` fijo con árbol 280 px + Inspector `minWidth=maxWidth=280` (`main-view-modern.fxml:133-141, 359`). A 1280 px el contenido útil queda en ~660 px (E5, E13).
- **Cambio:** `SplitPane` horizontal (árbol | contenido | Inspector) con divisores arrastrables, anchos mínimos (árbol 200, contenido 560, Inspector 260) y persistencia en `AppSettings`. Colapso automático: por debajo de 1366 px de ancho, Inspector cerrado por defecto; por debajo de 1200, árbol colapsado a rail. Botones de colapso en el propio borde del panel (además de ⌘B / ⌘I).
- **Aceptación:** divisores persisten entre sesiones; a 1280×800 el formulario de Cifrado cabe sin scroll horizontal y con Inspector abierto.

### UXP-21 · Rail con etiquetas, estado activo y agrupación
- **Problema (E11):** 14 iconos sin etiqueta, sin agrupación, con "Buscar" como sección (duplicando ⌘K) y "Historial" mezclado con dominios criptográficos.
- **Cambio:**
  - Rail de 64 px con icono + etiqueta corta de 10–11 px debajo, o rail expandible (hover/pin) a 200 px con etiquetas.
  - Grupos separados: **Trabajo** (Diseñador de procesos), **Criptografía** (Utilidades, Cifrado, Autenticación/MAC, Claves, PQC), **Formatos y estándares** (Certificados/CMS, JOSE, COSE, XML/WSS, ASN.1), **Dominio** (Pagos/EMV), y abajo del todo **Historial**, **Shelf**, **Ajustes**.
  - Quitar "Buscar" del rail; el campo de búsqueda vive en la toolbar (UXP-22).
  - Indicador de activo: barra de acento de 3 px + fondo; foco de teclado visible; navegación con ↑/↓.
- **Aceptación:** test de UI que recorre el rail con teclado; cada botón tiene `accessibleText` y tooltip con atajo.

### UXP-22 · Barra superior única y sin duplicados
- **Problema (E6):** toolbar en `FlowPane` que salta a dos filas; "Ampliar resultado / Añadir a Shelf / Copiar salida" repetidas en toolbar, barra de resultado y menú; "Guardar sesión" y "Limpiar" con peso de botón primario.
- **Cambio:** toolbar de **una fila, 44 px**:
  - Izquierda: botón colapsar árbol · título de la vista actual (breadcrumb compacto).
  - Centro: campo "Buscar operaciones… ⌘K" (abre paleta).
  - Derecha: indicador de perfil de visibilidad · menú "Sesión" (Guardar, Exportar registro, Restaurar) · toggle Inspector.
  - **Eliminar** de la toolbar Ampliar/Shelf/Copiar (viven en el área de resultado, UXP-30) y "Limpiar" (vive en el formulario, UXP-34).
  - La **barra de formato de payload** (`formatFlowBar`) sale de la toolbar global y se integra en la cabecera del formulario de las operaciones que la usan; oculta (no deshabilitada) donde no aplica (Diseñador, Historial, PIN…) (E13).
- **Aceptación:** a 1200 px la toolbar ocupa una fila; ninguna acción aparece en más de dos lugares (uno visible + menú).

### UXP-23 · Una sola noción de "historial"
- **Problema:** hay cuatro representaciones del mismo concepto: "Ejecuciones recientes" en el árbol lateral, tarjetas "History" en el Inspector, "Session log" en el Inspector y la vista Historial; con botones "Reopen"/"Reabrir" y exportaciones repetidas.
- **Cambio:**
  - Árbol lateral: solo **Favoritos** y **Recientes (navegación)** como grupos plegables arriba, máx. 5 elementos, sin fechas en la etiqueta (fecha en tooltip).
  - Inspector: se queda con **detalles de la operación actual** + **consejos de seguridad** + **registro de la sesión** (pasos guardados). Se elimina la lista de historial del Inspector (hoy `historyContainer`, `main-view-modern.fxml:427-437`).
  - Vista Historial: único sitio para buscar, filtrar, reabrir, comparar y exportar.
  - Definir en `docs/GLOSARIO_UI.md` la diferencia *Historial* (todas las ejecuciones persistidas) vs *Registro de sesión* (pasos que el usuario decide guardar para un informe).
- **Aceptación:** el Inspector no contiene tarjetas de historial; "Reabrir" solo existe en Historial y en la paleta ("Reabrir última operación").

### UXP-24 · Arquitectura de información de secciones
- **Problema (E14):** "Utilidades" contiene como paneles de acordeón al Diseñador de procesos y al Workbench de claves/certificados, que también son destinos propios; "Keys" mezcla ICSF/CCA, PKCS#11, TR-31/TR-34, RSA Key Exchange; badges "EXP" cortados.
- **Cambio:** inventario completo en `UiNavigationRegistry` → tabla `docs/IA_MAPA_NAVEGACION.md` (sección › grupo › operación, estado, sensibilidad). Reglas: cada operación vive en **un** sitio canónico (los demás accesos son atajos a ese sitio); máximo 3 niveles; grupos de 3–9 hojas. Propuesta de partida para "Claves": *Laboratorio de claves* · *Generación* · *Validación y KCV* · *Derivación y reparto* · *Envoltura y transporte* (AES KW, TR-31, TR-34, RSA KE) · *HSM y tokens* (PKCS#11, ICSF/CCA ×3, KeyStore).
- **Badge "Experimental":** chip compacto a la derecha que nunca se corta (el texto de la hoja hace elipsis antes que el chip); tooltip con qué implica.
- **Aceptación:** mapa aprobado por el propietario **antes** de mover nada; tras la migración, los `.ccconfig` y favoritos antiguos siguen resolviendo (tabla de alias).

### UXP-25 · Cada operación en su propia página (fuera del acordeón)
- **Problema:** módulos como Pagos o Utilidades cargan todas sus operaciones en un `Accordion` gigante dentro de un `ScrollPane`; seleccionar una hoja solo expande un panel. Resultado: doble scroll (E1: scroll del módulo + scroll exterior), foco perdido, acción principal fuera de pantalla.
- **Cambio:** la hoja del árbol muestra **solo** su formulario. Layout de página estándar:
  1. Cabecera: título 18 px, descripción de una línea, chips (estado, sensibilidad, estándar de referencia), menú "⋯" (plantillas, restablecer, exportar configuración, ayuda).
  2. Cuerpo con scroll: grupos `.card` (Parámetros, Clave, Entrada).
  3. **Barra de acciones fija** al pie del formulario (no scrollea): acción primaria + secundarias + "Limpiar".
  4. Panel de resultado (UXP-30) debajo o a la derecha si hay ancho (≥ 1600 px).
- **Estrategia:** empezar por Cifrado simétrico (plantilla de referencia), luego Hashing, PIN, MAC; migrar un módulo por PR.
- **Aceptación:** sin `ScrollPane` anidados en páginas migradas; a 1280×800 el botón primario es visible sin hacer scroll.

### UXP-26 · Process Designer como espacio de trabajo a pantalla completa
- **Problema (E13).**
- **Cambio:** al entrar en el Diseñador: árbol lateral e Inspector global colapsados automáticamente (restaurados al salir), sin `ScrollPane` exterior, lienzo ocupando todo el alto; paleta de bloques e inspector de nodo como paneles laterales propios redimensionables; barra de herramientas del diseñador fija arriba (Ejecutar, Validar, Deshacer/Rehacer, Zoom, Ajustar a pantalla, Exportar). Los mensajes de estado ("Selecciona 2 bloques para conectar") se muestran como texto de ayuda en la barra, no como botón. "Estado de ejecución" y "Resultados" en un panel inferior plegable con pestañas.
- **Aceptación:** a 1280×800 el lienzo tiene ≥ 800×450 px visibles; ⌘Z/⌘⇧Z funcionan sobre el lienzo.

### UXP-27 · Carga diferida de módulos
- *Arrastrado de MEJORAS_UX §2.* Hoy 15 `fx:include` (`main-view-modern.fxml:301-352`) se construyen al arrancar.
- **Cambio:** `StackPane moduleHost` + `ModuleLoader` con caché; `wire(controller)` tras la carga; precarga en segundo plano de Cifrado y Utilidades; exportar/importar `.ccconfig` de un módulo no visitado fuerza `load()` sin `show()`. Pantalla de arranque (splash) con progreso si el arranque supera 1,5 s.
- **Aceptación:** tiempo hasta ventana interactiva medido antes/después y anotado en el PR; tests de `.ccconfig` verdes.

---

## Fase 3 — Formularios, resultados y feedback

### UXP-30 · Panel de resultado estándar
- **Problema:** salida en `TextArea` sin acciones propias; las acciones sobre el resultado están en la toolbar global y en una `resultSummaryBar` separada; valores sensibles sin tratamiento visual.
- **Cambio:** componente `ResultPanel` reutilizable:
  - Cabecera: estado (✓ Éxito / ✕ Error / ⚠ Advertencia) · operación · duración · tamaño in/out · formato de salida (selector local).
  - Cuerpo: valor en monoespaciada, seleccionable, con agrupación opcional de hex (bytes/bloques de 8/16) y numeración de offset; múltiples salidas (p. ej. ciphertext + tag + IV) como filas etiquetadas cada una con su botón copiar.
  - Acciones (iconos con tooltip): Copiar, Añadir al Shelf, Ampliar, Guardar paso, Usar como entrada de… (encadenar).
  - Secretos: si el perfil es MASKED/REDACTED, valor enmascarado con "Mostrar" temporal (y aviso) según política existente; icono candado + `-cc-secret`.
  - Feedback "Copiado" in situ (tooltip 1,5 s), no solo en la barra de estado.
- **Aceptación:** Cifrado, Hashing, PIN, MAC y KCV usan `ResultPanel`; `resultSummaryBar` global eliminada.

### UXP-31 · Barras de acciones con jerarquía (caso Historial)
- **Problema (E5):** 11 botones de 7 colores; destructivo junto a importar; exportaciones repartidas en 5 botones.
- **Cambio en Historial:** cabecera = título + contador; barra de filtros (búsqueda, módulo, visibilidad, "Limpiar filtros" como enlace). Acciones sobre la selección: **Reabrir** (primario), Comparar, Añadir al Shelf, menú **Exportar ▾** (Registro JSON, Visibles JSON, Informe Markdown, Copiar informe, Receta JSON). Acciones globales en menú "⋯": Importar receta, **Vaciar historial…** (danger, con confirmación que indica cuántos registros se borran y ofrece exportar antes). Tabla con acciones por fila en hover/menú contextual, en vez de un botón azul por fila.
- **Regla general (añadir a guía):** una primaria por vista; ≤ 3 secundarias visibles; el resto en menú; los colores de estado nunca se usan para diferenciar acciones.
- **Aceptación:** barra de Historial sin truncados a 1200 px; confirmación destructiva probada.

### UXP-32 · Validación en línea, etiquetas y placeholders
- **Problema (E10):** placeholders como instrucciones y ejemplo ("Hex IV (required for CBC mode)…"), cortados y en monoespaciada; asterisco de obligatorio inconsistente ("Origen de la clave *:"); el error solo llega tras pulsar la acción (banner global).
- **Cambio:**
  - Etiqueta siempre visible (encima del campo en anchos < 700 px, a la izquierda alineada en grid en anchos mayores). Placeholder solo como ejemplo corto de formato ("00112233…").
  - Texto de ayuda permanente debajo del campo (11 px) para requisitos ("16 bytes (32 hex) en modo CBC").
  - Indicador de longitud en vivo para campos hex/base64: "24 / 32 hex · 12 bytes" que cambia a error si no es válida la longitud para el algoritmo elegido.
  - Validación al salir del campo (y al cambiar algoritmo/modo) con mensaje bajo el campo; el `InlineErrorPresenter` global se reserva para fallos de ejecución, y su "Ir al campo" enfoca y hace scroll.
  - Obligatorios: marcar los **opcionales** "(opcional)" en vez de asteriscos, o asterisco consistente con leyenda.
  - Botón primario deshabilitado mientras haya errores, con tooltip que lista qué falta (el `readinessPanel` actual puede alimentar este tooltip y retirarse como panel).
- **Aceptación:** en Cifrado, introducir una clave de 30 hex con AES-256 muestra el error bajo el campo antes de pulsar "Cifrar".

### UXP-33 · Metadatos como chips, no como enums
- **Problema (E8):** "Maturity: STABLE", "Sensitivity: HIGH", subtítulos "Stable · Sensitive material".
- **Cambio:** componente `Chip` con variantes neutral/info/warning/danger. Estado: *Estable* (neutral), *Experimental* (warning). Sensibilidad: *Sin secretos* (neutral), *Material sensible* (warning), *Claves privadas* (danger) — con icono y tooltip explicativo. Estándar: "ISO 9564-1", "RFC 3394" como chips enlazables a la ayuda. Mostrarlos en la cabecera de página (UXP-25) y en el Inspector.
- **Aceptación:** ningún enum en mayúsculas visible en la UI (`grep` de `.name()` en código de presentación revisado).

### UXP-34 · Operaciones asíncronas y cancelables en todas partes
- *Arrastrado de MEJORAS_UX §3.* `CertificatesController` (CAdES-T) y `XMLSignatureController` (XAdES-T, 112 menciones de TSA) siguen **sin** `OperationExecutor`; un timeout de TSA congela la ventana.
- **Cambio:** migrarlos; además, regla: toda operación > 300 ms (RSA ≥ 3072, PQC, PBKDF2 con muchas iteraciones, lotes ICSF, llamadas PKCS#11/TSA) usa `OperationExecutor`, deshabilita **solo** su formulario, muestra spinner en el botón primario ("Firmando…") y ofrece Cancelar. Timeouts de red configurables en Preferencias.
- **Aceptación:** TSA inaccesible → la ventana sigue respondiendo y el usuario puede cancelar; test con TSA simulada lenta.

### UXP-35 · Diálogos y confirmaciones unificados
- *Arrastrado de MEJORAS_UX §4.* 37 `new Alert(` en 12 controladores (ModernMain 11, History 5, Cipher 4, Main 3, Keys 3…).
- **Cambio:** `DialogService` único con: `confirmDestructive(title, consequence, confirmLabel)` (botón de confirmación con verbo concreto, "Vaciar historial", no "OK"; foco por defecto en Cancelar), `info`, `pickFile` (recuerda último directorio por tipo), y estilo del tema aplicado a `DialogPane`. Errores de operación → `InlineErrorPresenter`, nunca `Alert`. Los 25 `printStackTrace` → `logger.error`.
- **Aceptación:** `grep 'new Alert('` solo en `DialogService`; 0 `printStackTrace`.

### UXP-36 · Estados vacíos, primer uso y Quick Start
- **Problema:** Quick Start con 5 tarjetas en inglés hardcodeado, colores de estado de tema claro sobre fondo oscuro, estilos inline (`main-view-modern.fxml:221-293`); tablas vacías con "Tabla sin contenido" genérico; placeholder "Select an operation from the side panel".
- **Cambio:**
  - Pantalla de inicio: "Continuar donde lo dejaste" (última operación + sesión), Favoritos, Flujos guiados (tarjetas localizadas con icono, descripción y chip de sensibilidad), enlace a atajos (F1) y a la guía.
  - Primer arranque: selector de idioma, apariencia y perfil de visibilidad en un único paso opcional ("Puedes cambiarlo en Preferencias").
  - Estados vacíos con icono + frase + acción: Historial vacío → "Aún no has ejecutado operaciones · Ir a Cifrado"; ejecución del Diseñador vacía → "Añade un bloque de entrada para empezar".
  - Opción "Mostrar al iniciar" persistida.
- **Aceptación:** todas las tablas/listas de la app tienen un estado vacío específico.

### UXP-37 · Tablas profesionales
- **Cambio:** estilo único de `TableView`: cabecera 11 px semibold secundaria alineada con el contenido (números a la derecha), filas 28 px, zebra sutil o separadores, hover, selección con `-cc-selection`, columnas redimensionables con anchos persistidos, ordenación visible, menú contextual (Copiar celda, Copiar fila, Copiar como JSON), fechas en formato local relativo+absoluto en tooltip. Aplicar en Historial, ejecución del Diseñador, análisis en lote ICSF, inspector de KeyStore/PKCS#11, ASN.1.
- **Aceptación:** mismo aspecto en las 5 tablas en ambos temas.

---

## Fase 4 — Plataforma, accesibilidad y pulido

### UXP-40 · Integración nativa macOS / Windows
- macOS: `MenuBar.setUseSystemMenuBar(true)`; menú de aplicación con *Acerca de CryptoCarver*, *Preferencias… ⌘,*, *Salir ⌘Q* (quitar "Salir" de Archivo en macOS); menú *Ventana* (Minimizar ⌘M, Zoom).
- Windows: atajos con Ctrl, *Salir* en Archivo, *Opciones* en Herramientas; comprobar escalado 125/150 %.
- Recordar tamaño, posición, estado maximizado y monitor (con corrección si el monitor ya no existe).
- **Aceptación:** checklist por SO añadido a `docs/RELEASE_CHECKLIST.md`.

### UXP-41 · Accesibilidad (WCAG 2.2 AA aplicable a escritorio)
- Orden de tabulación lógico en cada página; foco visible en todos los controles (anillo `-cc-focus-ring`, nunca solo cambio de color); todos los botones de solo icono con `accessibleText` y tooltip; contraste AA en ambos temas; objetivos clicables ≥ 24×24 px (hoy hay botones "Añadir/Exportar" de ~18 px en el Inspector); no transmitir estado solo con color (chips con icono/texto); soporte de "Aumentar texto" sin cortes hasta 150 %; anuncios de VoiceOver/Narrador al terminar una operación (`AccessibleAttribute`/notificación de estado).
- **Aceptación:** pasada manual con VoiceOver documentada; test automatizado que recorre nodos y falla si un `Button` sin texto no tiene `accessibleText`.

### UXP-42 · Atajos de teclado coherentes y descubribles
- ⌘↩ / Ctrl+↩ ejecuta la acción primaria de la página actual; ⌘⇧C copia el resultado principal; ⌘1…⌘9 secciones del rail; ⌘[ / ⌘] atrás/adelante en historial de navegación (hoy no existe atrás/adelante: añadir botones en toolbar); Esc cierra overlays.
- Mostrar el atajo en tooltips de botones y en la paleta.
- **Aceptación:** diálogo F1 generado desde el mismo registro de acciones que menús y paleta (una sola fuente).

### UXP-43 · Microinteracciones y rendimiento percibido
- Transiciones de 120–160 ms solo para: apertura de paleta/overlays, colapso de paneles y aparición de banners. Nada de animaciones en navegación de páginas.
- Skeleton/spinner solo si la carga supera 300 ms.
- Toasts no bloqueantes (esquina inferior derecha, 4 s, con "Deshacer" cuando aplique: limpiar formulario, borrar del Shelf, quitar favorito).

### UXP-44 · Preferencias centralizadas
- Ventana *Preferencias* con pestañas: **General** (idioma, pantalla de inicio, confirmar acciones destructivas), **Apariencia** (tema, tamaño de texto, densidad compacta/cómoda), **Seguridad** (perfil de visibilidad por defecto, retención de historial y si guarda secretos, limpiar portapapeles tras N s, limpiar caché de claves), **Red** (TSA por defecto, timeouts, proxy), **Avanzado** (rutas PKCS#11, logs, restablecer todo).
- Hoy estas opciones están repartidas entre Ver, Seguridad, Herramientas y diálogos sueltos.

### UXP-45 · Seguridad visible en la UI
- Banner persistente discreto cuando el perfil es FULL_LAB y hay secretos en pantalla ("Modo laboratorio: los secretos se muestran en claro"), hoy solo aparece en Historial como texto rojo truncado.
- Botón "Borrar datos sensibles de la pantalla" (⌘⇧Delete) que limpia todos los campos marcados como secretos en todos los módulos cargados.
- Portapapeles: al copiar un secreto, aviso "Se borrará del portapapeles en 30 s" (configurable).
- Campos de clave/PIN con opción mostrar/ocultar.

### UXP-46 · Ayuda contextual
- Icono "?" en la cabecera de cada página que abre un panel lateral con la sección correspondiente de `docs/guide_es_extended.md`/`guide_en_extended.md` (renderizado local, sin red), vectores de prueba de ejemplo con botón "Cargar ejemplo" y referencias a estándares.
- Tooltips de ayuda en parámetros técnicos (modo, padding, formato de PIN block) con una frase y enlace a la ayuda.

### UXP-47 · Calidad continua
- *Arrastrado de MEJORAS_UX §7.* Confirmar que `ui-tests.yml` corre en verde de forma estable (≥ 10 ejecuciones seguidas) antes de empezar la Fase 1.
- Añadir a CI: test de i18n (UXP-05), test de aceleradores duplicados (UXP-07), test de `accessibleText` (UXP-41), `grep` de estilos inline/hex/emoji (UXP-13/14/15) como *check* que falla si el número sube.
- Capturas automáticas (TestFX) de las páginas principales en claro/oscuro/ES/EN como artefacto del workflow para revisión visual en PR.

---

## 3. Guía de diseño resumida (para usar en todos los tickets)

| Aspecto | Regla |
|---|---|
| Densidad | Herramienta técnica = densidad media. Controles 30 px, filas 28 px, gutter 16 px, separación entre grupos 24 px. |
| Jerarquía | 1 acción primaria por página; título 18 px; grupos con título 15 px; metadatos 11 px secundarios. |
| Color | Acento solo en primaria, foco, selección y enlaces. Estado (verde/ámbar/rojo) solo para resultados, validación y sensibilidad. |
| Tipografía | Sistema para UI; monoespaciada solo para datos. |
| Texto | Verbos concretos en botones ("Cifrar", "Exportar informe"), sentence case, sin "OK"/"Enviar" genéricos, sin puntos suspensivos salvo si abre diálogo. |
| Secretos | Siempre identificables (icono candado + color `-cc-secret`), respetando el perfil de visibilidad. |
| Errores | Qué pasó + por qué + cómo arreglarlo, junto al campo; técnica copiable aparte (patrón `UserFacingError` existente). |
| Idioma | Todo localizado; glosario para términos que no se traducen. |

---

## 4. Checklist de verificación por PR

- [ ] Capturas antes/después a 1280×800 y 1920×1080, ES y EN (y claro/oscuro desde la Fase 1).
- [ ] Sin textos truncados ni scroll horizontal en las vistas tocadas.
- [ ] Sin literales de texto nuevos fuera de `messages*.properties`.
- [ ] Sin `style=`, hex, `-fx-font-size` ni emoji nuevos.
- [ ] Navegación por teclado y foco visible en lo tocado.
- [ ] Perfiles FULL_LAB/MASKED/REDACTED comprobados en lo tocado.
- [ ] `mvn test` y, si aplica, `mvn -DrunUiTests=true test` en verde.
- [ ] Ningún monolito ha crecido en líneas netas.

## 5. Fuera de alcance

- Cambios en la lógica criptográfica, formatos o CLI.
- Migración de framework (JavaFX se mantiene).
- Rediseño de marca/logotipo.
