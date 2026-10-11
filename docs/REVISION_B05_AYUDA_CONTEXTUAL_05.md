# Revisión B05.2a — cierre C009 Batch Runner

Fecha: 2026-10-10. Turno revisado: `01a126e1-c347-7a22-853a-c67f58184d9a`.

**B05.2a/C009 APROBADO. C010 continúa PARCIAL; B05.2 total, B05.3 y cierre B05 siguen pendientes. B06 no comienza.**

Se contrastaron encargo, inventario/matriz, catálogo, contratos/textos ES/EN, renderer de caso seleccionado, conexiones Batch y pruebas. Las 40 entradas seleccionables cuentan con descripción, ejemplo y error propios; la matriz identifica cada ID, fuente y evidencia. Los vectores se cotejan con `BatchOperationCatalog.execute`, incluidos errores de fuente ausente y representaciones inválidas según consumidor; validación PAN distingue `false` de excepción. C010 conserva `complete=false` y borrador editorial; no se confunde inventario generado con ayuda completa.

La interacción conserva selección, entrada, resultado y reporte después de consultar, sigue acciones reales y no vuelve a ejecutar un lote por abrir ayuda. La evidencia UI incluye controles, teclado, ES/EN a 1800/900, navegación, ventana compacta y reapertura. Se inspeccionaron XML nuevos de `target/b05-review/b052a/`: 44 unidades (catálogo 11, codec 4, operaciones 18, validator 3, runner 8) y dos métodos UI. Cero fallos/errores/omisiones; argumentos y ejecuciones finales concordantes, 17:46:13–17:46:19 y 17:46:59–17:47:22 UTC. No se suman repeticiones de las entregas anteriores ni se repiten suites sin causa.

## Instalación de la entrega revisada

Codex construyó y empaquetó secuencialmente sin limpiar evidencia ni ejecutar Maven junto a Luna, que estaba detenida. Actualización instalada en `/Applications/CryptoCarver.app`, versión 2.4.0, 17:55:12 UTC. Firma estricta correcta y JAR idéntico SHA-256 a la compilación entregada. Evidencia en `target/b05-review/b052a/package-build.log`, `package.log` e `installation.json`.

Copias recuperables: `/Users/feliperodriguezfonte/Library/Application Support/CryptoCarver/backups/b052a-2026-10-10/CryptoCarver.app` y `/Applications/CryptoCarver.app.rollback-b052a`. No se modificaron preferencias ni se cerró el proceso abierto del usuario; ese proceso debe reabrirse para usar la nueva instalación.

Sonda independiente desde `/tmp`, con clases/FXML/recursos del JAR instalado, preferencias aisladas, JDK 21 externo y auxiliares de prueba, sin `target/classes`: 40 ejemplos ES de Batch se muestran en ayuda compacta y Process Designer permanece incompleto. Salida 0; log `target/b05-review/reviewer/b052a-installed-probe.log`. Es comprobación focalizada del paquete, no aceptación integral de todo el launcher/runtime ni de B15.

## Continuación C010

Categorías reales del catálogo instalado: Inputs 2, Outputs 2, Conversions 20, Plumbing 6, Utilities 6; Crypto 10; Key Material 15, Key Operations 13, Key Derivation 5, Generators 1; JOSE-COSE 13, Signatures 21, WS-Security 6; Wallet/eIDAS 22 y Payments 52. Total 194. La clasificación se usa para dividir entregas conservando todos los IDs.

Siguiente B05.2b1: acciones/controles generales del diseñador más las cinco categorías iniciales (36 nodos). Luego familias criptográficas/material de clave, envoltorios/firmas/WS-Security, Wallet/eIDAS y Payments, con subdivisiones si hacen falta. C010 no se declara completo mientras permanezca una familia obligatoria pendiente; cada nodo necesita significado/condiciones/opciones/ejemplo específico y evidencia, no paráfrasis del nombre.
