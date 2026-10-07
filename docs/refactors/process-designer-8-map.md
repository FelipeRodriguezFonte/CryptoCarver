# Process Designer: mapa de fase 8 — paleta

Base de fase: `23a7540`, controlador de 1030 líneas. Worktree CryptoCarver-pd-4, rama codex/process-designer-4.

| Método / líneas iniciales | Estado vivo leído | Escritura / clase de lógica |
|---|---|---|
| buildPalette (234–236) | Texto actual de paletteSearchField, incl. null | Delega filtro. Cableado; listener de texto y locale quedan en initialize. |
| filterPalette (238–302) | Catálogo por categorías; traducciones live; contenedor; cantidad de nodos al doble clic | Vacía y construye tarjetas/headers en orden original. Formato y UI; conserva estilos y sentinel de ancho preferido. |
| matchesSearch (304–310) | Tipo, categoría EN y etiqueta/descripción traducidas | Predicado puro con trim y Locale.ROOT; cadena vacía admite todo. |
| Doble clic instalado por filterPalette | nodes.size() live, descriptor y traducción al pulsar | Calcula x/y, addNode (modelo/undo/redraw), luego select (inspector). No capturar cantidad de nodos ni etiqueta traducida al construir la tarjeta. |

ProcessPaletteCoordinator usa record View con supplier de VBox y cantidad de nodos, traductor, callback NodeAdder y selección. Getter perezoso; buildPalette y filterPalette quedan delegados de una línea. Sin referencia directa al controlador ni View almacenado. initialize y sus listeners quedan idénticos. Sin acceso directo a definición ejecutable, conexiones, telemetría, secretos, AppSettings, StatusReporter, historial o Shelf; el add/select callback conserva el comportamiento de los propietarios existentes. No se añade Supplier<StatusReporter> sin consumidor.

Caracterización existente: ProcessDesignerPaletteUITest (texto, vacío, doble clic), ProcessDesignerExecutionCharacterizationUITest (digest texto/vacío y privacidad en tres perfiles). Se añade ProcessDesignerPaletteCharacterizationUITest para categoría, cero coincidencias, null/espacios/case y lectura de cantidad de nodos después de construir la tarjeta. Su transcripción solo fija etiquetas/headers y geometría determinista, sin IDs, tiempos ni aleatoriedad; se registra el marcador provisional antes de fijar SHA-256. Los tests nuevos restauran AppSettings, Shelf e historial.

Privacidad: ejecutar de nuevo la caracterización de ejecución y los tests inalterados de secretos/traza/telemetría mediante las tres puertas; comprobar superficies MASKED y REDACTED. Ninguna nueva operación de paleta consume secretos ni persiste resultados.

Tras extracción: tres puertas Maven en serie, informes nuevos únicamente. No comenzar fase 9 si existe fallo distinto de la excepción expresamente autorizada para ExpandedViewerLifecycleUITest.

## Cierre

Controlador 1030 → 965 líneas. Tres puertas limpias: headless 446 informes / 2912 pruebas / 0 fallos / 0 errores / 1 omitida; UI opt-in 126 / 542 / 0 / 0 / 0; UI CI 126 / 542 / 0 / 0 / 0. Digest paleta `e5919c4b74289c4943dec6ca56218164ddff40ffee4baeac5c957bfdaf4a6a76` verificado en ambas puertas UI. Ejecución/privacidad conserva sus dos digests y las comprobaciones de las siete superficies sensibles en los tres perfiles. No hubo fallos preexistentes de visor expandido ni cambios de comportamiento.
