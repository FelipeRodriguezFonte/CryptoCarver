# Mejoras de UX — CryptoCarver (análisis 2026-08-03)

Estado tras los handoffs UX-01 a UX-25. La mayoría de la propuesta original (`HANDOFF_UX_PROPUESTA.md`) ya está resuelta: FXML muerto eliminado, aceleradores corregidos, errores accionables (`UserFacingError`/`InlineErrorPresenter`), navegación con recientes/favoritos/migas/paleta, e i18n ES/EN de base. Quedan 2 bloques grandes sin empezar y varias deudas puntuales.

## 1. Tema y tokens de estilo — PENDIENTE (no iniciado)

**Estado:** `styles.css` (2313 líneas) sigue con 265 literales hexadecimales. `main-view-modern.fxml` mantiene 65 `style="..."` en línea; el resto de FXML (keys, cipher, process_designer) no se ha tocado. 129 `setStyle(` en Java. Los iconos del `NavigationRail` siguen siendo emoji, uno de ellos literal `{}` sin sentido visual. No hay tema oscuro/claro conmutable ni campo `theme` en `AppSettings`.

**Qué hacer:**
- Crear `tokens.css` con variables `-color-*`/`-space-*`/`-radius-*` y dos hojas `theme-dark.css`/`theme-light.css` que solo redefinan tokens.
- Migrar `styles.css` a consumir tokens (0 literales hex).
- Sustituir `style=` inline por `styleClass=` en FXML, empezando por `keys.fxml` (mayor densidad) y `main-view-modern.fxml`.
- Añadir *View → Theme* (Claro/Oscuro/Alto contraste), persistido en `AppSettings`.
- Sustituir emoji por una librería de iconos vectorial (Ikonli + Material2, sin red, Apache 2.0) con un mapa único `Section → icono`.

## 2. Carga diferida de módulos — PENDIENTE (no iniciado)

**Estado:** `main-view-modern.fxml` sigue con 13 `fx:include`; todo el árbol de escena se construye en el arranque.

**Qué hacer:**
- Sustituir los `fx:include` por un `StackPane fx:id="moduleHost"` vacío.
- Crear `ModuleLoader` que cargue y cachee cada módulo la primera vez que se navega a él, y muestre solo el nodo activo.
- Mover el cableado de `initialize()` (`setStatusReporter`, `initModern`, etc.) a un método `wire(controller)` invocado tras cada carga.
- Precargar en segundo plano los 2 módulos más usados (Symmetric Keys, Generic) tras mostrar la ventana.
- Verificar que exportar/importar `.ccconfig` de un módulo nunca visitado sigue funcionando (fuerza `load()` sin `show()`).

## 3. Ejecución asíncrona — terminar la migración

**Estado:** `OperationExecutor` existe y ya cubre `CipherController`, `KeysController` (RSA/DSA) y `PostQuantumController`. Faltan exactamente los casos que más bloqueaban la interfaz: llamadas TSA por red.

**Qué hacer:** migrar a `OperationExecutor.run(...)`:
- `PadesController` (firma con sello de tiempo).
- `CertificatesController` — flujo CAdES-T.
- `XMLSignatureController` — flujo XAdES-T.

Sin esto, cualquier timeout de TSA sigue congelando la ventana entera.

## 4. Diálogos legacy sin migrar al banner de error

**Estado:** el catálogo de errores accionables (UX-22 a UX-25) cubre Cipher, Keys, TR-31, XML/WSS, Payments, ASN.1, CMS, JOSE, ASiC y PAdES, pero varios handoffs dejan constancia explícita de "diálogos legacy con `Alert` en vez de banner" pendientes en Payments/CMS/PIN, y quedan 18 `printStackTrace` sin convertir a log.

**Qué hacer:** localizar los `Alert` residuales en esos tres módulos y sustituirlos por `InlineErrorPresenter`; sustituir los `printStackTrace` restantes por `logger.error(...)`.

## 5. Cobertura de i18n incompleta

**Estado:** infraestructura y cobertura núcleo resueltas (`I18nService`, `messages.properties`/`messages_es.properties`). Los handoffs UX-15A-D dejan explícita "deuda" de textos dinámicos secundarios y diálogos legacy compartidos sin migrar.

**Qué hacer:** barrido final sobre textos generados en Java (no en FXML) que aún concatenan literales en inglés, y sobre los diálogos compartidos (`LocalizedDialogSupport`) que quedaron fuera del primer paso.

## 6. Deuda técnica que ya está frenando la UX

`ModernMainController` (4300 líneas), `KeysController` (5397) y `CipherController` (4434) han **crecido** en vez de reducirse pese a la recomendación de extracción oportunista del documento original — cada bloque UX añadió código a estos tres archivos. Riesgo: cualquier cambio de interfaz futuro es cada vez más lento y arriesgado.

**Qué hacer:** la próxima vez que un bloque de UX toque uno de estos tres controladores, extraer la lógica afectada a una clase auxiliar dedicada (patrón ya usado con `OperationInspectorPresenter`, `ResultAreaTracker`, `ModuleResetPolicy`) en lugar de seguir añadiendo al monolito. No se propone una refactorización dedicada aparte: no tiene valor visible por sí sola.

## 7. Verificación pendiente de confirmar

Varios handoffs UX-16 a UX-25 señalan que las pruebas de UI real (`-DrunUiTests=true`) no se pudieron correr en local por falta de entorno gráfico, y que se mitigó con CI headless (Xvfb, `ui-tests.yml`, UX-20R). **Confirmar que esa tanda de CI corre en verde de forma estable** antes de dar por cerrada la cobertura de regresión de todo el trabajo UX-14 a UX-25.

---

### Prioridad sugerida
1. Bloque 3 (async TSA) — riesgo de congelación real, esfuerzo bajo.
2. Bloque 7 (confirmar CI de UI) — sin esto, no hay red de seguridad para el resto.
3. Bloque 4 (diálogos legacy) — esfuerzo bajo, cierra una inconsistencia visible.
4. Bloque 1 (tema/tokens) — el de mayor impacto visual, esfuerzo medio-alto.
5. Bloque 2 (carga diferida) — impacto en arranque, requiere tocar exportación/importación con cuidado.
6. Bloques 5 y 6 — continuos, oportunistas, sin bloque dedicado.
