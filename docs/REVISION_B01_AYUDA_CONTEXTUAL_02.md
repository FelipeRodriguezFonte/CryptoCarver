# Segunda revisión de B01: corrección R1–R6

**Corrección de evidencia del 10 de octubre, 13:18:** el recuento integrado de 61 que figura abajo mezcló XML anteriores con pruebas realmente ejecutadas bajo el filtro UI. No acredita una sola ejecución de todas esas clases. Véase [revisión 03](REVISION_B01_AYUDA_CONTEXTUAL_03.md) para la causa, ejecuciones nuevas y conteos verificados. Se mantiene el dictamen de corrección y sus reproducciones independientes.

Fecha: 10 de octubre de 2026. Implementadora: Luna. Revisor: Codex.

Entrega revisada: turno `01a12564-d245-7b33-8837-683a3d2de723`.

**Dictamen: REQUIERE CORRECCIÓN. B01 conserva dos problemas reproducibles: R2 sigue abierto al volver desde auxiliares y R7 registra explicaciones de formatos incompatibles con la ruta real. B02 no debe iniciarse.**

## Correcciones aceptadas

- R1: las explicaciones y ejemplos se resuelven antes de presentarlos y se renderizan una vez. La sonda no encuentra claves internas en ES/EN, inspector ni ventana compacta.
- R2, parte de campos: «Volver a la operación» conserva las selecciones al salir del detalle de C001. La parte de retorno desde A001/A002 continúa pendiente.
- R3: la visibilidad de los accesos usa la ruta activa. Reactivar desde A001/A002 conserva los botones visibles/managed en la ruta simétrica.
- R4: los ejemplos auxiliares C001 están traducidos y las etiquetas de material se adaptan al origen manual/Key Lab. Se eliminaron duplicados del piloto.
- R5, parte de C001: los paddings tienen diferencias y condiciones explícitas; ChaCha20 distingue 8/12 bytes aceptados de los 12 generados. Las restricciones del proveedor y de AAD siguen documentadas sin cambiar el motor. La ampliación de A001 necesita la corrección R7 indicada abajo.
- R6: el acceso de Inspect sigue a la acción real; el inventario y la matriz clasifican plantillas y Reset. El flujo integral A003 permanece asignado a B13.

La sonda confirma además Hashing → JWE → Hashing con recuperación del perfil y formato Hex, y actualización a Base64 mediante selección programática. Esto acredita ese recorrido; no acredita por sí solo ausencia de listeners duplicados en todas las restauraciones.

## Comprobaciones del revisor

Se ejecutó una sola suite pertinente de **61 pruebas, cero fallos, errores y omisiones**, con `user.home` aislado por Surefire y fixtures de preferencias de UI. Incluye las nuevas regresiones B01 y los consumidores compartidos B00/A002 afectados por el renderer. No se repitieron las caracterizaciones criptográficas ya correctas en la primera revisión, pues estos cambios no modifican el núcleo.

```sh
mvn -o -q -Dtest=ContextualHelpRegressionUITest,OperationHelpRegistryTest,SymmetricCapabilityPolicyTest,HelpContextTest,PreflightHelpCatalogTest,LocalHelpServiceTest,ShellWindowLifecycleUITest,ModernMainControllerI18nUITest,ModernMainReadinessCharacterizationTest,ModernMainControllerFxmlStaticTest,FxmlContractTest,FxmlQualityGateTest -DrunUiTests=true test
```

Log: `target/b01-review/review-corrections.log`. `git diff --check` pasó.

Sonda independiente: `target/b01-review/B01SecondContextProbe.java`; evidencia `target/b01-review/context-probe-second.log`. Usa FXML de producción, preferencias independientes y datos sintéticos; recorre ES/EN y anchos 1800/900. La parte de consultas conserva clave, entrada y resultado centinela. Para comprobar formatos invoca los métodos reales de parsing/presentación del coordinador sin ejecutar cifrado; no modifica código de aplicación ni pruebas de producción.

## R2 pendiente — P2: volver desde A001/A002 continúa mostrando el fallback

**Fuentes:** `ModernMainController.handleShowOperationHelp`, líneas 1308–1322; `ContextualHelpCoordinator.showOperation`, líneas 57–60; `HelpContext.withOperation`; ciclo de `HelpContextAdapter`.

Abrir la ficha general desde un auxiliar cambia su ID mediante `setOperation`. `withOperation` elimina las selecciones cuando cambia el ID. El adaptador simétrico sigue activo y `activate()` no vuelve a publicar elecciones si ya estaba activo. Por ello, la ficha general se resuelve sin algoritmo/modo/origen vigentes y presenta AES-256/CBC.

**Reproducción:** configurar ChaCha20-Poly1305; abrir un `?` simétrico; abrir ayuda de formato de entrada A001; pulsar «Ayuda de esta operación». Repetir desde la ayuda del error A002. La pantalla sigue configurada para ChaCha20-Poly1305, mientras la ayuda muestra AES-256/CBC. Se confirma en las cuatro combinaciones de idioma/ancho:

```text
return_from_A001_algorithm=null
return_from_A001_wrong_default=true
preflight_help_operation=aux_preflight_error
return_from_A002_algorithm=null
return_from_A002_wrong_default=true
```

**Corrección y aceptación:** al abrir la ayuda de la ruta real, publicar todas sus selecciones públicas actuales y la presencia derivada de material. Conservar separación respecto al resultado y los metadatos propios del error. No basta con conservar una copia antigua: probar un cambio de selector mientras el auxiliar está abierto y regresar después. Probar retorno desde A001/A002, reactivación del modo y selección posterior de acción en ES/EN, inspector y compacto, sin valores fallback incorrectos, entradas borradas ni suscripciones duplicadas. Mantener las garantías de selección de acción y recreación de ventana aceptadas en B00.

## R7 — P2: A001 promete formatos que el coordinador simétrico rechaza

**Fuentes:** `ContextualHelpCoordinator.renderGlobalFormatContext`, líneas 243 y 251; `messages_es.properties`, líneas 4522–4535, y bundle EN; `SymmetricCipherCoordinator`, líneas 366–386 y 399–413; `InputValidator` y los codecs específicos.

El renderer diferencia la lista permitida por perfil, pero selecciona la misma explicación de formato para todas las rutas. Las explicaciones añadidas describen ciertos codecs y omiten el validador/coordinador que realmente procesa los datos. En Symmetric Ciphers se confirma:

| Formato | Ayuda visible ES/EN | Comportamiento real confirmado |
|---|---|---|
| Entrada Hex | Ignora dos puntos y guiones | `41:42` se rechaza en `InputValidator` antes de llegar a `hexToBytes`. |
| Entrada Binary | Ignora dos puntos y guiones | `01000001:01000010` se rechaza en `InputValidator`. |
| Entrada Base64 | Elimina cabeceras/pies PEM | `-----BEGIN DATA-----\nQQ==\n-----END DATA-----` se rechaza en la validación Base64 previa. |
| Salida Text (UTF-8) | UTF-8 estricto; rechaza bytes inválidos | El byte `FF` se representa con U+FFFD mediante `new String`, sin rechazo. |

La sonda confirma las cuatro divergencias en ES/EN y ambas presentaciones. El codec UTF-8 estricto y el codec Base64 que elimina PEM existen, pero su existencia no demuestra que los use esta ruta. El significado base se puede compartir; las condiciones específicas deben reflejar la cadena completa de validación/conversión de cada perfil.

**Corrección y aceptación:** resolver explicaciones por perfil, dirección y formato cuando el comportamiento difiera. Revisar los perfiles de A001 contra sus consumidores reales y registrar la fuente comprobada, incluidas las rutas con contrato propio/deshabilitado. No prometer separadores, PEM, padding o rechazo estricto sin verificar el camino efectivo. Añadir regresiones con entradas sintéticas que comparen el texto específico de la ruta y su aceptación/rechazo real. Mantener el recorrido de memoria/restauración Hashing → JWE → Hashing. Corregir los textos y la resolución de ayuda; no cambiar codecs, validadores o motor para hacer verdadera la documentación.

## Encargo siguiente

Cerrar R2 pendiente y R7 manteniendo el alcance completo C001/A001 y las correcciones aceptadas. Actualizar la matriz con pendientes y evidencia reales; no mantener una declaración global «R1–R6 corregidos» mientras R2 continúe abierto. La revisión anterior y este dictamen son documentos del revisor: registrar la implementación y sus comprobaciones en la matriz, sin editar los informes de revisión ni el registro de coordinación.

Luna conserva la propiedad de aplicación, recursos, adaptadores, pruebas e inventario/matriz de B01. Codex conserva dictámenes y coordinación. Respetar cambios locales de JOSE/tutorial 25 y ajenos. No subagentes, commits, pushes, publicaciones ni cambios de motor, algoritmos, parámetros, valores predeterminados o políticas. B02 espera aprobación completa de B01.
