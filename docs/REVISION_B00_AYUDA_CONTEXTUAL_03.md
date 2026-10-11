# Tercera revisión de B00: cierre de contexto y validación

Fecha: 10 de octubre de 2026. Implementadora: Luna. Revisor: Codex.

Entrega revisada: turno de Luna `01a12508-cec2-7d10-93e4-a165cf9a5aa8`.

**Dictamen: REQUIERE CORRECCIÓN de A002 antes de aprobar B00.** R1–R5 y el contrato mínimo de contexto/adaptadores quedan verificados. La integración de A002 contiene un defecto en su acceso principal y todavía no permite consultar las comprobaciones ejecutables del panel.

## Aspectos aceptados de esta entrega

`HelpContextState` proporciona una fuente compartida a ambas presentaciones. Crear y recrear la ventana lee esa fuente y conserva la acción, corrigiendo R5. Los coordinadores liberan sus suscripciones al cerrarse. El adaptador de selección se conecta explícitamente al selector simétrico diferido, publica su valor al activarse y libera listeners al salir de la ruta o cerrar el shell. Esta es la integración mínima prevista para B00; la cobertura completa de parámetros sigue asignada a B01.

El revisor ejecutó la suite declarada: **45 pruebas, cero fallos, errores y omisiones**, incluyendo las cinco regresiones JavaFX de ayuda. `git diff --check` también pasó. Las pruebas existentes no ejercitan el botón del banner después de un error con regla ni la consulta de comprobaciones READY/WARNING.

```sh
mvn -o -q -Dtest=HelpContextTest,OperationHelpRegistryTest,LocalHelpServiceTest,PreferencesServiceTest,ModernMainControllerFxmlStaticTest,FxmlContractTest,FxmlQualityGateTest,ContextualHelpRegressionUITest,ShellWindowLifecycleUITest,ModernMainControllerI18nUITest -DrunUiTests=true test
git diff --check
```

## R6 — P2: «Ayuda de este error» elimina la regla del error de preflight

**Fuente:** [ModernMainController.java](../src/main/java/com/cryptocarver/ui/ModernMainController.java), líneas 1210–1220.

`ReadinessPanelCoordinator` acaba de enlazar la regla `preflight.input_format.blocked`, el estado BLOCKED y el campo `cipherInputArea`. Sin embargo, el botón `errorBannerHelpBtn` siempre llama a `showGenericErrorHelp`, que construye un contexto nuevo sin regla ni estado. Al consultar precisamente la ayuda del error visible, se pierde su explicación específica y se afirma que el error no tiene regla vinculada.

**Reproducción confirmada:** seleccionar Symmetric Ciphers y entrada Hexadecimal, introducir `NOT_HEX_SENTINEL` como payload y una clave sintética válida, solicitar la comprobación de preparación, y pulsar «Ayuda de este error» en el banner. Antes del clic aparecen regla y estado; después ambos desaparecen y se muestra el fallback sin regla.

Sonda: `target/b00-review/B00PreflightProbe.java`. Evidencia: `target/b00-review/preflight.log`.

```text
preflight_executable=false
rule_before_banner_help=PublicSelection[code=preflight.input_format.blocked]
status_before_banner_help=PublicSelection[code=BLOCKED]
specific_rule_before_banner_help=true
rule_after_banner_help=null
status_after_banner_help=null
no_rule_fallback_after_banner_help=true
specific_rule_after_banner_help=false
```

**Corrección:** asociar el contexto público de ayuda al error que muestra el banner, conservándolo aunque el usuario consulte después la ficha general de la operación. El botón debe abrir ese contexto; el fallback sin regla debe reservarse para errores que realmente carecen de ella. No basta con reutilizar cualquier contexto actual o el último informe de preflight: un error nuevo sin regla no debe heredar la anterior. Invalidar el vínculo cuando se sustituya o cierre el error según el ciclo existente.

**Aceptación:** el recorrido real de error con regla → botón del banner mantiene regla/estado/campo y explicación ES/EN. Consultar la ficha general y volver al banner también conserva el vínculo. Mostrar un error nuevo sin regla utiliza el fallback, sin metadatos del anterior. Probar inspector y ventana compacta, preservación de datos y títulos coherentes con la ficha consultada.

## R7 — P2: la ficha A002 no está conectada a las comprobaciones READY/WARNING

**Fuente:** [ReadinessPanelCoordinator.java](../src/main/java/com/cryptocarver/ui/ReadinessPanelCoordinator.java), líneas 107–111 y 200–215; [main-view-modern.fxml](../src/main/resources/fxml/main-view-modern.fxml), líneas 232–238.

La única publicación de ayuda de preflight está en el rechazo de ejecución. Los informes READY/WARNING retornan antes de ella. Los botones de comprobación del panel solo enfocan el campo; «Mostrar detalles» amplía la lista. Por tanto, no hay una consulta que abra la regla/estado de una advertencia o una comprobación lista. Los textos para esos estados existen en el bundle, pero la UI no los enlaza con la comprobación seleccionada.

A002 debe explicar lo que falta, lo que bloquea y lo que es una advertencia, sin cambiar la política de ejecución. Marcarla completa exige que esa información pueda consultarse desde los estados reales del panel, antes de ejecutar.

**Corrección:** añadir un acceso explícito a la ayuda de cada comprobación o del estado actual del panel, reutilizando la misma construcción de contexto por IDs que para el error. Mantener la acción de foco y la política READY/WARNING/INCOMPLETE/BLOCKED existente. No simular un error ni ejecutar la operación para abrir ayuda.

**Aceptación:** una comprobación READY y una WARNING reales pueden consultarse desde el panel con sus IDs/estado/campo y explicación ES/EN. La consulta no cambia `isExecutable`, no escribe valores y no ejecuta ninguna acción criptográfica. Probar también el foco de una comprobación no ejecutable.

## Ajuste editorial de A002

Los textos visibles deberían explicar el uso de la aplicación. Sustituir expresiones como «Resultado del modelo PreflightStatus existente» o la derivación interna del ID por el significado de estado/comprobación para el usuario. Presentar el campo con una etiqueta comprensible cuando esté disponible; su identidad técnica puede mantenerse internamente para el vínculo y el foco. No es necesario mostrar detalles de implementación para ayudar a decidir cómo corregir una entrada.

## Encargo de corrección

Corregir R6–R7 y ajustar el texto de A002 como una entrega acotada del mismo B00. Mantener lo aceptado R1–R5/contexto/adaptadores y sus pruebas. Propiedad: conexiones/coordinador de ayuda de error y panel, recursos ES/EN de A002, pruebas y matriz. Si resulta necesario añadir metadatos de ayuda al modelo de error, conservar los constructores y consumidores existentes.

Respetar cambios ajenos, no crear subagentes, no hacer commits/pushes ni cambiar validación, reglas, valores predeterminados o política criptográfica. Entregar evidencia de los accesos reales, no solo de la apertura automática de ayuda al rechazar preflight. B01 sigue pendiente de aprobación de B00; no se reduce ni se reordena el alcance de los bloques restantes.
