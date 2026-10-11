# Cuarta revisión de B00: errores, acceso a comprobaciones y cobertura

Fecha: 10 de octubre de 2026. Implementadora: Luna. Revisor: Codex.

Entrega revisada: turno de Luna `01a12526-452d-7fe2-9530-0133c7ce6db3`.

**Dictamen: REQUIERE CORRECCIÓN de A002 antes de aprobar B00.** R6 queda corregido y los accesos de R7 funcionan. Persisten una falta de cobertura específica y una regresión de visibilidad en el panel automático. B01 permanece pendiente; no se repite el encargo R6–R7.

## Comprobaciones aceptadas

El banner conserva metadatos públicos asociados a su propio error, independientemente de la ficha general consultada. Sustituir o cerrar el error invalida el vínculo. Los constructores existentes de `UserFacingError` se conservan y `ShellTextResolver` preserva el contexto al traducir. Las pruebas recorren ES/EN, inspector y ventana compacta, y comprueban conservación de datos y títulos.

Los nuevos accesos del toolbar, panel y filas permiten consultar comprobaciones READY y WARNING sin ejecutar criptografía ni alterar `isExecutable`. La acción de foco sigue conectada. Los textos revisados explican el significado de estados y campos al usuario. Se mantiene lo aceptado en las revisiones anteriores sobre contexto compartido, adaptadores base, referencias y lifecycle.

El revisor ejecutó una sola suite integrada, ampliada por los consumidores del modelo de error y el panel afectados: **70 pruebas, cero fallos, errores y omisiones**. Incluye las 47 de la entrega y 23 de `ModernMainReadinessCharacterizationTest`, `ShellTextResolverTest` y `UserFacingErrorMapperTest`. Preferencias aisladas por Surefire (`target/test-home`) y fixtures de UI. `git diff --check` pasó.

```sh
mvn -o -q -Dtest=HelpContextTest,OperationHelpRegistryTest,LocalHelpServiceTest,PreferencesServiceTest,ModernMainControllerFxmlStaticTest,FxmlContractTest,FxmlQualityGateTest,ContextualHelpRegressionUITest,ShellWindowLifecycleUITest,ModernMainControllerI18nUITest,ModernMainReadinessCharacterizationTest,ShellTextResolverTest,UserFacingErrorMapperTest -DrunUiTests=true test
git diff --check
```

Log de ejecución: `target/b00-review/review-r6-r7.log`. Las pruebas pasan, pero no cubren los dos casos siguientes. Una sonda JavaFX sobre FXML de producción los confirma en ES/EN, con valores sintéticos y preferencias separadas: `target/b00-review/B00FinalCoverageProbe.java`; resultados en `target/b00-review/final-coverage.log`. La sonda y sus archivos temporales están bajo `target`, sin modificar la aplicación.

## R8 — P2: comprobaciones con regla real siguen sin explicación específica

**Fuente:** [ContextualHelpCoordinator.java](../src/main/java/com/cryptocarver/ui/ContextualHelpCoordinator.java), líneas 166–168; bundles `i18n/messages.properties` y `messages_es.properties`, claves `help.preflight.rule.*`.

La presentación resuelve una clave por ID de comprobación, pero solo hay siete explicaciones específicas. Numerosos IDs que ya produce `OperationPreflightEngine` recurren a «No hay una explicación específica para esta comprobación». A002 figura COMPLETO en la matriz aunque la acción de abrir la regla no explica comprobaciones existentes como tag requerido, IV/nonce o seguridad del digest. El encargo exige abrir la regla correcta para una entrada inválida; el fallback no acredita esa cobertura.

**Reproducciones confirmadas:**

1. Symmetric Ciphers, AES-256/GCM, entrada Text (UTF-8), payload sintético, clave sintética válida de 32 bytes, nonce de 12 bytes y tag vacío. Solicitar preflight de descifrado y pulsar la ayuda del banner: se conserva `preflight.aead_auth_tag.incomplete`, pero la explicación de la comprobación es genérica.
2. Hashing, entrada Text (UTF-8), payload sintético y MD5. Abrir la ayuda de preparación: se conserva `preflight.digest_security.warning`, pero tampoco hay explicación específica en ES o EN.

```text
es missing_tag rule=PublicSelection[code=preflight.aead_auth_tag.incomplete] generic=true
es digest_warning rule=PublicSelection[code=preflight.digest_security.warning] generic=true
en missing_tag rule=PublicSelection[code=preflight.aead_auth_tag.incomplete] generic=true
en digest_warning rule=PublicSelection[code=preflight.digest_security.warning] generic=true
```

**Corrección:** inventariar y cubrir los IDs de comprobación que pueden emitir actualmente las rutas integradas por `ReadinessPanelCoordinator`/`OperationPreflightEngine`, incluidos los nombres variables de IV/nonce. Proporcionar explicación de cada comprobación/estado y campo comprensible en ES/EN, con relación por identidad estable. Una referencia común explícita puede servir para comprobaciones equivalentes si explica su condición; un texto de ausencia de explicación sigue siendo cobertura pendiente. No copiar mensajes libres, material de campos ni secretos al contexto. No cambiar validaciones, parámetros o política de ejecución, ni adelantar las fichas detalladas de los módulos asignados a bloques posteriores.

**Aceptación:** prueba de cobertura con informes reales/fixtures del motor existente que falle cuando un ID emitido carezca de explicación ES/EN; ninguna comprobación cubierta puede resolver al texto de ausencia. Recorridos de UI del tag ausente y la advertencia de MD5 muestran significado y corrección pertinentes, campo legible y mismos IDs/estados, con datos y ejecutabilidad preservados. Mantener también el fallback explícito para un error que realmente carece de regla.

## R9 — P2: el panel automático no se oculta al completar una entrada

**Fuente:** [ReadinessPanelCoordinator.java](../src/main/java/com/cryptocarver/ui/ReadinessPanelCoordinator.java), líneas 185–195.

La actualización ordinaria eliminó la condición que desactivaba y ocultaba el panel al obtener un informe ejecutable. Ahora solo consulta `panelActivated`. Después de mostrar un estado incompleto, el panel sigue visible al alcanzar READY aunque no se haya solicitado la ayuda de preparación en esa ruta. Ocultarlo únicamente al ejecutar preflight no conserva el recorrido previo de edición.

**Reproducción confirmada:** volver a entrar en Hashing para reiniciar el panel, seleccionar Text (UTF-8) y SHA-256, dejar vacía la entrada y activar su actualización ordinaria. Introducir un payload sintético válido y actualizar. El informe es READY y el panel continúa visible en ambos idiomas. Este recorrido no pulsa ayuda tras volver a entrar en la ruta.

```text
ES automatic_incomplete_panel_visible=true
ES automatic_ready_panel_visible=true status=READY
EN automatic_incomplete_panel_visible=true
EN automatic_ready_panel_visible=true status=READY
```

El test existente `readinessPanelHidesAfterHashInputBecomesComplete` pasa, pero no establece explícitamente el formato adecuado ni comprueba que su informe final sea READY/ejecutable. Por tanto, no demuestra que el recorrido de entrada realmente válida siga funcionando.

**Corrección:** diferenciar la apertura explícita de ayuda/checklist de la visibilidad automática por edición o intento fallido. Conservar el ocultamiento automático cuando la entrada pasa a ser ejecutable; una consulta explícita sí debe permitir leer READY/WARNING y sus comprobaciones. Reiniciar esa intención al cambiar de ruta según el lifecycle del panel. No cambiar `PreflightPolicy` ni sus estados.

**Aceptación:** con formato y algoritmo explícitos, comprobar INCOMPLETE → READY/ejecutable y ocultamiento automático sin pulsar ayuda. Comprobar además apertura explícita posterior en READY y acceso a la regla de cada comprobación; mantener el recorrido WARNING de R7 y la preservación de datos. Fortalecer la prueba existente para que afirme el estado final real, en lugar de aceptar accidentalmente otro informe.

## Encargo de cierre enviado a Luna

Corregir R8–R9 como continuación de B00/A002. Propiedad: resolución/recursos de comprobaciones, visibilidad del coordinador de preparación, pruebas de cobertura y UI, y matriz de implementación. Codex conserva los documentos de revisión y coordinación. La cobertura detallada C001/A001 permanece asignada a B01.

Mantener R1–R7 y el contrato base ya revisado. Respetar cambios ajenos de JOSE/tutorial 25 y documentos del revisor. Sin subagentes, commits, pushes ni cambios de motor, algoritmos, parámetros o reglas. Se permiten subentregas conservando alcance, sin marcar B00 completo con requisitos obligatorios pendientes. Entregar conclusión, archivos, pruebas y cualquier pendiente concreto; B01 solo comienza tras dictamen favorable del bloque completo.
