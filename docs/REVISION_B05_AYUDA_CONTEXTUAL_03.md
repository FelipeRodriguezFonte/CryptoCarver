# Revisión B05.1 — cierre de subentrega

Fecha: 2026-10-10. Turno: `01a126bd-c4b8-7b60-98ef-28362355b2a3`.

**B05.1 APROBADO: C006/C007/C008/C011/C012/C013/C014 y A011. B05 completo continúa PARCIAL hasta B05.2, B05.3 y cierre transversal. B06 no comienza.**

R6 cerrado: `recordContextualAction` registra la acción real intentada antes de delegar en los consumidores Manual/File/Check Digits y conserva el contexto por ruta; no abre ayuda ni ejecuta acciones adicionales. La regresión consulta campos directamente tras ejecutar, cambia acción y vuelve de navegación. La sonda independiente que antes obtenía `convert` ahora obtiene `endian`.

R7 cerrado: textos propios ES/EN y referencia Random indican formato de la siguiente generación, salida anterior conservada y bytes nuevos al generar. La regresión y sonda confirman que cambiar el selector no reformatea la salida.

R8 cerrado: diferencias de las cuatro reglas de Check Digits documentadas, AMEX SE identifica la equivalencia Luhn implementada y sus límites; base/valor completo, mínimos y ceros iniciales incluidos. Fixtures no Luhn y AMEX contrastados con el consumidor. La matriz añade acciones, campos individuales y opciones con fuentes, reglas, ejemplos, evidencia y límites, conservando como pendientes las familias siguientes.

Se inspeccionaron código, recursos, encargo/inventario/matriz y pruebas. Nueva evidencia de corrección: 14 unidades (7 B05HelpCatalogTest y 7 CheckDigitCalculatorTest), 16:59:48–16:59:57 UTC; un método B051ContextualHelpUITest, cuatro combinaciones ES/EN y 1800/900, 17:00:16–17:00:35 UTC. XML y argumentos concordantes, cero fallos/errores/omisiones, en `target/b05-review/b051/correction/`. La evidencia previa de 15 unidades y un método UI se conserva separada, sin sumar repeticiones como casos distintos. No se repiten suites sin causa.

## Instalación local comprobada

Luna instaló durante su entrega `/Applications/CryptoCarver.app`, versión 2.4.0, atendiendo la petición humana aunque el encargo previo reservaba la coordinación al revisor. Codex no duplicó la instalación ni ejecutó Maven en paralelo. Se comprobó firma con `codesign --verify --deep --strict`, igualdad SHA-256 entre JAR instalado y compilación entregada, presencia de referencia local y texto ES corregido. Hay copia recuperable en `/Users/feliperodriguezfonte/Library/Application Support/CryptoCarver/backups/b051-r6-r8-2026-10-10/CryptoCarver.app` y `/Applications/CryptoCarver.app.rollback-b051`.

La sonda independiente cargó clases/FXML/recursos de aplicación del JAR instalado desde `/tmp`, con preferencias aisladas y sin `target/classes`: `AFTER_EXECUTING_ENDIAN_INPUT_HELP=endian`, `RANDOM_FORMAT_CHANGE_RENDERS=false`, salida 0. Evidencia: `target/b05-review/reviewer/b051-installed-probe.log`. Usa JDK 21 externo y auxiliares de prueba para cargar el shell; no acredita por sí sola todo el launcher/runtime distribuido ni las 120 fichas. El runtime empaquetado no contiene `bin/java`; una primera tentativa de invocarlo no pudo iniciarse y se corrigió la sonda usando JDK 21. La aceptación integral del paquete y enlaces sigue en B15.

Siguiente encargo: B05.2, C009 Batch Runner y C010 Process Designer con todas las entradas dinámicas seleccionables, sus parámetros/opciones/condiciones, ayudas ES/EN y lifecycle/privacidad/preservación. Se mantienen B05.3 y cierre integral obligatorios. Las instalaciones futuras se coordinan en punto estable sin duplicarlas ni tocar preferencias/datos del usuario.
