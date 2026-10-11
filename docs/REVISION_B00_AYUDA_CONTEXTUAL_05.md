# Quinta revisión de B00: aprobación del bloque

**Corrección de evidencia del 10 de octubre, 13:18:** el recuento integrado de 72 que figura abajo mezcló XML anteriores con pruebas realmente ejecutadas bajo el filtro UI. No acredita una sola ejecución de todas esas clases. La [revisión B01 03](REVISION_B01_AYUDA_CONTEXTUAL_03.md) documenta la causa y revalida por separado ayuda/contexto, catálogo de preflight, recursos, lifecycle y las siete caracterizaciones de readiness. B00 mantiene su aprobación; los dos fallos nuevos de catálogo/FXML allí registrados pertenecen a los controles de plantillas añadidos en B01.

Fecha: 10 de octubre de 2026. Implementadora: Luna. Revisor: Codex.

Entrega revisada: turno de Luna `01a12538-3dff-79e3-b008-9ad084419774`.

**Dictamen: B00 APROBADO.** Las correcciones R8–R9 cierran los pendientes de A002. Se mantiene la aceptación previa de R1–R7, contexto compartido, adaptadores base y lifecycle. El siguiente bloque autorizado es B01: C001 `op_sym_ciphers` y A001 `aux_global_formats`.

## Evidencia de cierre

`PreflightHelpCatalog` enumera las 58 identidades de comprobación/estado de las cinco rutas integradas actuales —simétrico, hashing, firmas, MAC y RSA— y relaciona sus controles con etiquetas legibles. La resolución de ayuda usa ese catálogo y conserva el fallback explícito para errores sin regla o identidades desconocidas. La prueba de catálogo construye informes con el motor existente, contrasta los IDs emitidos con el catálogo y comprueba explicaciones y etiquetas ES/EN; no acepta el texto de ausencia de explicación como cobertura.

El revisor inspeccionó las condiciones del motor y los recursos para verificar la cobertura actual, incluidos los nombres variables IV / Nonce y Nonce. Las explicaciones de tag ausente y digest débil ahora son específicas. No se han cambiado el motor ni las reglas de preflight para conseguir que los casos pasen.

La intención de consultar explícitamente la preparación se almacena separada de la activación automática del panel y se reinicia al navegar. Un informe ejecutable oculta el panel automático; una consulta explícita permite leer READY/WARNING. La prueba de Hashing fija formato y algoritmo, exige INCOMPLETE → READY/ejecutable y comprueba tanto ocultamiento automático como apertura explícita posterior.

El revisor ejecutó una sola suite integrada de **72 pruebas: cero fallos, errores y omisiones**, con las preferencias aisladas de Surefire y los fixtures de UI. Incluye ocho regresiones JavaFX de ayuda, cobertura de reglas, recursos, traducción, persistencia, contratos FXML, lifecycle y consumidores del modelo de error. `git diff --check` pasó.

```sh
mvn -o -q -Dtest=HelpContextTest,OperationHelpRegistryTest,PreflightHelpCatalogTest,LocalHelpServiceTest,PreferencesServiceTest,ModernMainControllerFxmlStaticTest,FxmlContractTest,FxmlQualityGateTest,ContextualHelpRegressionUITest,ShellWindowLifecycleUITest,ModernMainControllerI18nUITest,ModernMainReadinessCharacterizationTest,ShellTextResolverTest,UserFacingErrorMapperTest -DrunUiTests=true test
git diff --check
```

Log del revisor: `target/b00-review/review-r8-r9.log`. No se repitió toda la criptografía al no haber cambios de motor.

Además, se volvió a ejecutar la misma sonda JavaFX de la revisión 04, con FXML de producción, datos sintéticos y preferencias independientes. Fuente: `target/b00-review/B00FinalCoverageProbe.java`. Evidencia nueva: `target/b00-review/final-coverage-corrected.log`.

```text
es missing_tag rule=PublicSelection[code=preflight.aead_auth_tag.incomplete] generic=false
es digest_warning rule=PublicSelection[code=preflight.digest_security.warning] generic=false
ES automatic_incomplete_panel_visible=true
ES automatic_ready_panel_visible=false status=READY
en missing_tag rule=PublicSelection[code=preflight.aead_auth_tag.incomplete] generic=false
en digest_warning rule=PublicSelection[code=preflight.digest_security.warning] generic=false
EN automatic_incomplete_panel_visible=true
EN automatic_ready_panel_visible=false status=READY
```

## Alcance aprobado y continuación

B00 entrega infraestructura de resolución por IDs, recursos locales, presentación inspector/ventana, selección de acción, preferencia persistente, actualización de idioma/ruta, contexto público compartido, contrato mínimo de adaptadores y A002 integrada. Las referencias se resuelven desde classpath por ID explícito. Se preservan entradas y resultado, y los listeners se liberan según el lifecycle revisado.

La ficha simétrica sigue siendo un piloto parcial. Los botones `?`, toda su explicación por campo/opción/acción y su matriz de variantes pertenecen a B01. La cobertura de las otras operaciones, auxiliares y entradas dinámicas conserva los bloques B02–B14; la aceptación transversal y comprobación del artefacto fuera del checkout pertenecen a B15. Aprobar B00 no acredita ninguno de esos contenidos pendientes.

Se envía a Luna B01 conforme a las fichas C001/A001 y al contrato general del encargo, sin reducir alcance. Luna actualiza la matriz para registrar este dictamen y su siguiente entrega; Codex mantiene revisión y coordinación. La automatización continúa activa hasta el cierre integral de B15.
