# Fase 5: fallos antes de fijar la caracterización

Base: `d685778`, producción sin extraer. Ejecución aislada: `mvn -o -q test -Plow-cpu -Dtest=JoseInitializationCharacterizationUITest`.

La captura con `PENDING` falló únicamente en la comparación de digest: 1 XML, 1 prueba, 1 fallo, 0 errores, 0 omitidas, exit 1. SHA obtenido: `80dc874fb430a236d5f5d24cde7ad558951ff4b9aa5c9edc4a5c17af2f2f09a9`. Las aserciones funcionales de filtros de captura e historial pasaron. Es una captura provisional deliberada, no un defecto previo ni una relajación de aserciones.

Se normalizan exclusivamente los tiempos `iat`/`exp` de plantillas y su UUID `jti`; no se fijan mensajes de proveedor, rutas, fechas ni orden de mapas. Controles ordenados por identificador/ruta del árbol lógico; listas mantienen el orden definido por FXML y código. El digest se fija antes de extraer y se repite sobre la misma producción.

Las dos capturas posteriores ampliaron el inventario del propio test: (1) Accordion/panes anónimos, paneles nombrados y prompt de combos (`d314b43f43a98675140cb6288b8077d2e8d169d1f7b68b15173f39c47de60469`); (2) títulos/visible de columnas JWA (`61e52224fbedb92086d81b19dbd2f3b2aad54df991d7e9dcb8c637e5b011b6df`). Cada una produjo 1 XML / 1 test / 1 fallo de PENDING / 0 errores / 0 omitidos / exit 1. No había producción extraída. El primer digest provisional había pasado antes de ampliar su cobertura; se descarta por inventario incompleto. El último es el digest definitivo, y se verifica verde antes de extraer. No se observó un defecto funcional previo.

Verificación definitiva sobre producción sin extraer: test de arranque (1) y contratos fuente/FXML (39), 5 XML / 40 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0. Inventario FXML: 255 controles; transcripción inicial: 268 entradas contando también nodos nombrados y columnas.
