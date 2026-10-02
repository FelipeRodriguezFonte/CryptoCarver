# División de KeysController, fase 3

Rama: `luna/split-keys-3`, desde `main` / `a548f8b`. Implementación directa con Sol, sin subagentes. Sin push ni prueba manual.

## Resultado

`KeysController`: **4727 → 1422 líneas**. Se extraen las seis áreas en el orden del encargo. Se conservan 174 firmas públicas, 286 campos FXML y 101 métodos anotados, incluido initialize; los handlers de acciones son delegados de una línea. Se mantienen la navegación de secciones, la inyección y la vinculación de controles externos en la fachada.

Los coordinadores usan records de vista proporcionados al ejecutar la operación y un proveedor del StatusReporter. Los cuatro controladores incluidos se consultan por la vista actual; no se almacenan en el coordinador. Se reutilizan los coordinadores de las fases 1/2 y KeysCoordinatorSupport. La limpieza final retira estado no usado en coordinadores, imports, puentes privados sin consumidores y comentarios de bloques ya movidos.

## Commits

- `93971b6`: docs: map remaining Keys areas and shared state
- `241a161`: test: characterize remaining Keys workbenches before extraction
- `679776f`: refactor: share generated Keys workspace state
- `057dd1e`: refactor: delegate payment key blocks from Keys
- `7505914`: refactor: delegate Key Lab inventory from Keys
- `cc32dd2`: refactor: delegate Keys summaries exports and Shelf
- `63b8462`: refactor: delegate symmetric generation components and KCV
- `b69fddd`: refactor: delegate keystore material and token workbenches
- `bf114b4`: refactor: delegate certificate CSR and CRL workbenches
- Documentación: el commit final que contiene este informe y la actualización del mapa.

## Estado compartido

`KeysWorkspaceState` contiene el último par, su tipo, el material simétrico generado y los cinco resúmenes. No tiene JavaFX, referencias a controladores ni proveedores HSM. Se conservan la invalidación al cambiar algoritmo/tamaño y el borrado del buffer simétrico. Generación escribe; Shelf, resúmenes y guardado en Key Lab leen. El getter del par continúa disponible para el proveedor conectado a CipherController.

Los flujos actuales Generate Certificate y CSR generan pares propios y no sustituyen el último par de generación asimétrica. La caracterización comprueba esta regla: no se ha introducido la reutilización de ese par. Key Lab, KeyMaterial y los proveedores mantienen sus propietarios originales.

La lectura PEM, los SAN separados por comas y la comprobación del emisor se comparten en KeysMaterialSupport; el predicado hexadecimal en KeysHexValidation. Ambos helpers tienen tests unitarios sin FXML ni JavaFX. Las operaciones criptográficas siguen en las clases existentes de crypto, sin modificarlas.

## Caracterización previa

Véase [salida completa resumida](keys-split-3-characterization.md), comprometida antes de mover producción. Producción real cargada con Fxml.loader, test.mode y user.home=target/test-home. El grupo nuevo inicial pasó 31 casos en 16.620 s; fases 1/2, Shelf y servicios afectados sumaron otros 80 casos sin fallos.

Se caracterizan las tres familias de pagos, Key Lab, resúmenes/Shelf, AES/TDES/KCV, XOR, PKCS12 con extracción opt-in, los paneles incluidos, certificado, CSR, CRL, validación individual, configuración portable, redacción e idioma. Los tests existentes complementan histórico, inspector, sesiones, navegación, paleta y Process Designer.

Durante la elaboración previa se corrigieron supuestos de los tests nuevos, sin cambiar producción: Key Lab rechaza actualmente el selector AES para generación; se usa DES para alta válida y se fija el error AES. El tercer formato publica el fallo de autenticación; el formato Variant LMK no autentica. UiStateSnapshot excluye keyLabImportBytesField y conserva el CN como parámetro público. El predicado hexadecimal acepta caracteres incompletos durante la edición y no recorta espacios por sí mismo.

En el último grupo se reforzó el caso de Key Block LMK: envoltura determinista con el vector público y padding del test crypto existente, comparación con el ejemplo de producción y unwrap por el handler, comprobando el valor esperado y autenticación. Se corrigió la expectativa inicial de ese refuerzo usando el vector real preexistente; no se modificó producción ni el test crypto.

Importación/exportación de manifiestos se ejercita con el proveedor que usan los handlers y la tabla real; los diálogos nativos de selección de fichero no se automatizan. PKCS#11 se prueba sin token físico. No se realizó prueba manual.

## Verificación visual

ComputedStyleSnapshotTool, tema claro, 1400×900, las nueve pantallas solicitadas: Key Lab, Key Generation, Validation & KCV, Key Sharing, KeyStore Inspector, Generate Certificate y los tres key blocks de pagos.

```text
diff /tmp/keys3-before.txt /tmp/keys3-after.txt
exit 0; salida vacía
SHA256 ambas: 3d0d519918a6775e2812a270069679e6ce9baa9c194e546b22a464d69d7fc800
ClippedTextAuditTool es/light, antes: TOTAL = 0
ClippedTextAuditTool es/light, después: TOTAL = 0
```

La auditoría incluye también el diálogo de información que añade la herramienta. No se generaron ni subieron imágenes.

## Tests

Después de cada área se ejecutó su grupo afectado; después de las áreas 2, 4 y 6 se ejecutó mvn -o -q test, una sola instancia de Maven cada vez, heap de 2 GB. No se usó low-cpu ni se modificaron pom.xml o .mvn.

| Suite completa tras área | Tests | Fallos | Errores | Omitidos | Tiempo real |
|---|---:|---:|---:|---:|---:|
| 2 | 2674 | 0 | 0 | 1 | 236.43 s |
| 4 | 2684 | 0 | 0 | 1 | 257.88 s |
| 6 | 2694 | 0 | 0 | 1 | 256.82 s |

Los informes de Surefire se apartaron antes de la última ejecución para obtener un total final sin informes residuales. Los totales de las áreas 2/4 excluyen dos informes residuales de las herramientas opt-in de estilo y recortes; esas herramientas se ejecutaron por separado, no como parte de mvn -o -q test.

Nuevos: KeysSplit3CharacterizationUITest (31 casos), KeysHexValidationTest (10), KeysMaterialSupportTest (10): **51 casos nuevos**.

Grupo final de extracción y layout, Maven exit 0:

```text
KeysSplit3CharacterizationUITest: tests=31, failures=0, errors=0, skipped=0, time=17.608s
KeysMaterialSupportTest: tests=10, failures=0, errors=0, skipped=0, time=0.6s
KeysSplit2CharacterizationUITest: tests=31, failures=0, errors=0, skipped=0, time=22.621s
IncludedPaneI18nUITest: tests=3, failures=0, errors=0, skipped=0, time=0.685s
ComputedStyleSnapshotTool: tests=1, failures=0, errors=0, skipped=0, time=12.263s
ClippedTextAuditTool: tests=1, failures=0, errors=0, skipped=0, time=9.439s
```

Único test existente modificado: Ux28bAsymmetricShelfLiveUITest. Tres lecturas por reflexión de currentRsaSummary/currentEcdsaSummary pasan a través de workspace; sus aserciones, material y comportamiento no cambian. No se ajustaron tests existentes para aceptar otro resultado.

Las compilaciones intermedias detectaron colisiones con clases de soporte existentes y referencias mecánicas duplicadas; se restauraron las clases previas y se corrigieron nombres/accesos antes de validar y comprometer cada extracción. No quedan cambios de comportamiento destinados a sortear fallos.

## Tamaños finales

| Archivo | Líneas |
|---|---:|
| KeysController.java | 1422 |
| PaymentKeyBlockCoordinator.java | 327 |
| KeyLabCoordinator.java | 825 |
| KeySummaryCoordinator.java | 711 |
| SymmetricKeyCoordinator.java | 465 |
| KeyStoreCoordinator.java | 807 |
| CertificateCoordinator.java | 562 |
| KeysWorkspaceState.java | 18 |
| KeysMaterialSupport.java | 46 |
| KeysHexValidation.java | 11 |

La meta de menos de 1500 líneas se cumple. Los bindings, los proveedores de vistas y los contratos de navegación/configuración permanecen en la fachada; la lógica de las seis áreas reside en sus coordinadores y helpers.

Alcance comprobado mediante git diff y comparación de contratos: sin cambios en crypto, FXML, CSS, pom.xml ni .mvn.
