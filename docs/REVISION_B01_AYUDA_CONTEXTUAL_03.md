# Tercera revisión de B01: R2 y R7

Fecha: 10 de octubre de 2026. Implementadora: Luna. Revisor: Codex.

Entrega revisada: turno `01a12577-077d-7a20-9e05-6555983246eb`.

**Dictamen: REQUIERE CORRECCIÓN. R2 queda cerrado. Las cuatro divergencias simétricas de R7 están corregidas, pero falta la variante Base64URL de Manual Conversion. Dos comprobaciones unitarias también fallan: recurso de regla de plantillas ausente y contrato FXML de sus menús. B02 no debe iniciarse.**

## Evidencia aceptada

`handleShowOperationHelp` vuelve a publicar selecciones públicas actuales del controller simétrico en inspector y ventana compacta. La publicación conserva acción/campo/idioma cuando pertenecen a la misma ficha y calcula presencia de material sin incorporar secretos. La regresión incluye cambio de selector mientras A002 está abierto. La sonda independiente vuelve desde A001/A002 con ChaCha20-Poly1305 vigente y ya no muestra AES-256/CBC por fallback en ES/EN y anchos 1800/900.

La resolución de formatos permite excepciones por perfil/dirección/formato. Las explicaciones simétricas de Hex, Binary, Base64 y UTF-8 ahora coinciden con el parsing/presentación reales. La matriz identifica consumidores de los 16 perfiles actuales. La extracción de los métodos de conversión de `SymmetricCipherCoordinator` conserva el comportamiento; sus 33 caracterizaciones de UI/resultados pasaron.

Sonda: `target/b01-review/B01ThirdContextProbe.java`. Log: `target/b01-review/context-probe-third.log`. Se conserva el recorrido de memoria/restauración Hashing → JWE → Hashing y las garantías de entradas/resultado. La ampliación usa únicamente conversiones de datos sintéticos para contrastar Base64URL.

## Corrección de los recuentos de pruebas

Los comandos anteriores con `-DrunUiTests=true` activan el perfil Maven `ui-tests`, que configura `<groups>ui</groups>`. Por ello, las clases sin esa etiqueta no se ejecutan aunque figuren en `-Dtest`. Sus XML anteriores permanecen en `target/surefire-reports`; sumarlos sin comprobar fecha/clase da un recuento incorrecto.

**El revisor incluyó informes antiguos en los recuentos integrados anteriores de 91, 61 y 72. Esas cifras no acreditan una única ejecución de los comandos publicados. Se corrige aquí la evidencia, sin ocultar los registros anteriores.** La implementadora también debe corregir los recuentos 17/63 de esta entrega: la clase de catálogo contiene cuatro pruebas actuales y no se ejecutó bajo el filtro de UI.

La comprobación nueva separó las ejecuciones y verificó los XML recién escritos:

| Ejecución real | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| Cinco clases UI de ayuda, cifrado/resultados, lifecycle e idioma | 48 | 0 | 0 | 0 |
| Ocho clases de catálogo/contexto/recursos y contratos FXML, sin perfil UI | 42 | 2 | 0 | 0 |
| Caracterización del panel de readiness, con perfil UI desactivado explícitamente | 7 | 0 | 0 | 0 |
| Total de estas tres ejecuciones | **97** | **2** | **0** | **0** |

Comandos y logs:

```sh
mvn -o -q -Dtest=ContextualHelpRegressionUITest,OperationHelpRegistryTest,SymmetricCapabilityPolicyTest,HelpContextTest,SymmetricCipherCharacterizationUITest,SymmetricOutputClassificationCharacterizationUITest,ShellWindowLifecycleUITest,ModernMainControllerI18nUITest -DrunUiTests=true test
# Log: target/b01-review/review-r2-r7-final.log. El perfil ejecutó solo las cinco clases UI: 48 pruebas.

mvn -o -q -Dtest=OperationHelpRegistryTest,SymmetricCapabilityPolicyTest,HelpContextTest,PreflightHelpCatalogTest,LocalHelpServiceTest,ModernMainControllerFxmlStaticTest,FxmlContractTest,FxmlQualityGateTest -DrunUiTests=false test
# Log: target/b01-review/review-r2-r7-unit-final.log. 42 pruebas, dos fallos.

mvn -o -q -P'!ui-tests' -Dtest=ModernMainReadinessCharacterizationTest -DrunUiTests=true test
# Log: target/b01-review/review-readiness-final.log. 7 pruebas correctas.
```

Preferencias aisladas por Surefire; ejecuciones secuenciales, sin Maven paralelo. Copias de los XML y fechas: `target/b01-review/r2-r7-verified-reports/evidence.json`. `git diff --check` pasó. Las comprobaciones actuales revalidan los consumidores B00/A002 afectados; no se ha reproducido una regresión de B00.

En próximas entregas, omitir `-DrunUiTests=true` si se usa el valor predeterminado del proyecto, o desactivar explícitamente el perfil con `-P'!ui-tests'` cuando se necesite ese argumento para una ejecución mixta. También es válido separar UI/unidad. Contar solo clases efectivamente ejecutadas e informes de esa ejecución.

## R7 pendiente — P2: Base64URL manual sigue usando una explicación incompatible

**Fuentes:** `help.format.option.base64url.input` de los bundles ES/EN; resolución por perfil en `ContextualHelpCoordinator`; `ManualConversionCoordinator.convert`, `InputValidator.validateBase64` y `DataConverter.decodeBase64Url`; sincronización de barra/controles en `GenericController`.

La ayuda de entrada en Manual Conversion sigue diciendo «no admite padding ni espacios». No existe excepción `help.format.profile.manual_conversion.option.base64url.input`. Sin embargo, Convert estándar permite padding y elimina espacios antes de decodificar. La barra sí está sincronizada con el selector local.

**Reproducción real:** navegar a Manual Conversion, entrada global Base64URL, salida Hexadecimal, EBCDIC desactivado. Abrir ayuda de entrada y convertir los datos sintéticos siguientes:

```text
manual_base64url_synced=Base64URL
manual_base64url_input=QQ== output=41
manual_base64url_input=Q Q== output=41
manual_base64url_input=QQ output=41
manual_base64url_input=Q Q output=41
```

Se confirma en ES/EN y ambas presentaciones. El codec Base64URL estricto usado por otras subacciones mantiene sus restricciones; la ayuda no puede trasladarlas a Convert estándar.

**Aceptación:** añadir la explicación específica Manual Conversion/entrada/Base64URL en ES/EN, distinguiendo Convert estándar de las subacciones con codec estricto, como ya se hace en los demás formatos manuales. Probar la ayuda renderizada y la conversión real con/sin padding y espacios, más un caso inválido. Mantener la restricción del codec en las subacciones que lo usan. No cambiar parser/validador para ajustar el texto.

## R8 — P2: falta la traducción de la regla de plantillas

`OperationHelpRegistryTest.b01SymmetricCatalogueAndTranslationsCoverEveryLiveOption` falla al exigir `help.rule.symmetric.template.allowlist`. `OperationHelpRegistry` asigna esa regla al campo `template_selection`, pero la clave no existe en ninguno de los bundles.

**Aceptación:** entregar la explicación de esta regla en ES/EN, consistente con `SafeTemplateAllowlist` y las acciones actuales, sin declarar implementado A003 completo. El test de catálogo debe pasar cuando se ejecute realmente fuera del filtro UI. Conservar las definiciones de campos/acciones y exclusión de material sensible.

## R9 — P2: nuevos IDs FXML de menú no cumplen el contrato del controller

`ModernMainControllerFxmlStaticTest.testCipherFxml` falla porque `saveCipherTemplateItem` está declarado en `cipher.fxml` pero falta en `CipherController`. Los IDs nuevos `exportCipherTemplateItem`, `importCipherTemplateItem` y `deleteCipherTemplateItem` tampoco tienen sus campos correspondientes. El gate termina en el primer ID.

**Aceptación:** alinear explícitamente los cuatro IDs y su controller conforme al contrato existente; mantener accesos y acciones de ayuda sin ejecutar gestión de plantillas al consultarlas. Corregir la integración, no debilitar ni excluir el gate para eludirla. Pasar `ModernMainControllerFxmlStaticTest`, `FxmlContractTest` y `FxmlQualityGateTest` en una ejecución que realmente los incluya.

## Encargo siguiente

Cerrar la parte Base64URL de R7, R8 y R9; conservar R2 y las correcciones previamente aceptadas. Corregir en la matriz la evidencia 17/63 y documentar comandos, clases y conteos efectivos. Las fuentes del encargo e inventario siguen exigiendo C001/A001 completos; los bloques restantes y A003/B13 mantienen su alcance.

Luna modifica aplicación, recursos, pruebas e inventario/matriz. Codex conserva revisión y coordinación. No editar informes `REVISION_*` ni `COORDINACION_AYUDA_CONTEXTUAL.md`. Respetar JOSE/tutorial 25 y otros cambios locales, sin revertirlos. No subagentes, commits, push, publicaciones ni cambios de modelo, motor, algoritmos, parámetros, defaults o políticas. B02 espera la aprobación completa de B01.
