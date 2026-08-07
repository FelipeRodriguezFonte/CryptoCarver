# Handoff UX-14 — Internacionalización ES/EN

## Alcance terminado

- `AppSettings` persiste `LanguagePreference.SYSTEM`, `ES` o `EN` sin incluir secretos.
- `I18nService` resuelve el locale del sistema, cambia idioma en vivo, notifica listeners y aplica fallback `messages_es` → `messages_en`/base inglesa → clave.
- La shell moderna localiza `View → Language`, menús globales, toolbar, breadcrumbs, NavigationRail, SidePanel, estados globales, progreso, errores accionables, inspector y paleta de comandos.
- `accessibleText` y tooltips de los controles migrados se actualizan con el mismo cambio de idioma.
- Los nombres de algoritmos, rutas/IDs, formatos, payloads y valores técnicos se mantienen como datos de operación; la localización solo envuelve textos descriptivos.

## Compatibilidad y fallback

Los FXML mantienen texto inglés seguro durante la carga para no romper cargadores existentes. La shell reaplica las claves después de la inyección FXML y al cambiar idioma. Un bundle, clave o listener defectuoso se diagnostica con un warning no sensible y no impide arrancar.

## Deuda explícita

La migración progresiva de textos internos de los módulos incluidos (`keys`, `cipher`, `authentication`, `certificates`, `pqc`, `xml_security`, `wss_security`, `process_designer`, `history`, `clipboard_shelf`, `compare_results` y subdiálogos Java) queda pendiente. En esta entrega se conserva su inglés estable para evitar una reescritura destructiva y no alterar contratos de operación. Debe continuarse con claves por módulo, empezando por encabezados, vacíos, validaciones y acciones.

## Revisión manual

1. Arrancar la aplicación con locale del sistema español y comprobar `View → Language → System`.
2. Cambiar `Español` ↔ `English` ↔ `System` con una operación abierta; comprobar menú, toolbar, rail, panel, breadcrumbs, inspector y paleta sin reiniciar.
3. Cerrar y arrancar de nuevo para comprobar persistencia.
4. Ejecutar una operación y verificar que algoritmo, IDs, HEX/Base64/PEM/JSON/XML, hashes y bytes son idénticos entre idiomas.
5. Provocar un error accionable y comprobar texto, tooltip y navegación al campo en ambos idiomas.
