# Segunda revisión de B00 y secuencia de entregas para Luna

Fecha: 10 de octubre de 2026. Implementadora: Luna. Revisor: Codex.

**Dictamen: R1–R4 corregidos; B00 requiere cierre antes de pasar a B01.** Las 39 pruebas de la entrega se han vuelto a ejecutar y pasan sin fallos, errores ni omisiones. Se ha confirmado un caso adicional de pérdida de acción, R5, y permanecen tres requisitos de infraestructura del B00 original.

## Correcciones verificadas

La selección de acción ya renderiza descripción y ejemplo. La ventana sigue la ruta activa, actualiza textos/referencia/título con el ciclo de localización existente, se reutiliza y cierra al sustituir la escena. Las nuevas regresiones JavaFX ejercitan estos recorridos y preservan entradas y resultado. El coordinador de ventana evita añadir un listener global de idioma y mantiene esta responsabilidad separada del shell.

La ejecución del revisor fue:

```sh
mvn -o -q -Dtest=OperationHelpRegistryTest,LocalHelpServiceTest,PreferencesServiceTest,ModernMainControllerFxmlStaticTest,FxmlContractTest,FxmlQualityGateTest,ContextualHelpRegressionUITest,ShellWindowLifecycleUITest,ModernMainControllerI18nUITest -DrunUiTests=true test
git diff --check
```

La suite contiene 35 pruebas de catálogo/recursos/preferencia/FXML y 4 pruebas JavaFX. No se ha repetido toda la criptografía al no haber cambios de motor.

## R5 — P2: se pierde la acción al crear o recrear la ventana compacta

**Fuente:** [ContextualHelpWindowCoordinator.java](../src/main/java/com/cryptocarver/ui/ContextualHelpWindowCoordinator.java), líneas 32–35 y 41–44; [ModernMainController.java](../src/main/java/com/cryptocarver/ui/ModernMainController.java), líneas 1118–1119 y 1135.

Las dos presentaciones sincronizan acciones solo mientras existe la ventana. Al crear su coordinador de contenido, únicamente se copia la ruta y se parte de una acción vacía. Al cerrar se elimina ese coordinador y la reapertura vuelve a perder la acción. El inspector conserva la selección, por lo que las vistas dejan de describir el mismo contexto.

**Reproducción confirmada con JavaFX real:**

1. Con ancho de 1400 píxeles, abrir ayuda de Symmetric Ciphers y seleccionar Descifrar.
2. Reducir a 900 píxeles y solicitar Ayuda por primera vez: la ventana no muestra la descripción seleccionada.
3. Seleccionar Descifrar en la ventana, cerrarla y volverla a abrir: el inspector conserva Descifrar, la ventana no.

Observaciones de `target/b00-review/B00BoundaryProbe.java`, guardadas en `target/b00-review/boundary.log`:

```text
docked_decrypt_before_first_compact_open=true
compact_decrypt_after_first_open=false
compact_decrypt_before_close=true
docked_decrypt_after_reopen=true
compact_decrypt_after_reopen=false
```

**Corrección y aceptación:** conservar el contexto de ayuda fuera de la vida de las vistas. Una nueva presentación recibe la operación y la acción seleccionadas antes de mostrarse. Cubrir primera apertura tras selección en el inspector y cierre/reapertura, en ES y EN. La acción puede reiniciarse cuando se cambia a otra operación, pero abrir una vista de la misma operación no debe perderla. Este estado compartido debe incorporarse al cierre del requisito de `HelpContext`, evitando dos fuentes de verdad.

## Requisitos pendientes del B00 original

La matriz ahora los refleja correctamente. Declararlos pendientes conserva la trazabilidad, pero no completa el bloque.

| Requisito | Entrega necesaria para cerrar B00 |
|---|---|
| `HelpContext` | Conectar el modelo a la resolución y presentación de ayuda; utilizar IDs estables y el contexto de operación/acción vigente. No transportar material secreto ni valores libres del formulario. |
| Contrato base de adaptadores | Definir una fuente/adaptador explícito de contexto, con registro y liberación de suscripciones para módulos diferidos. Probar con el piloto mínimo; las explicaciones completas por campo siguen en B01. |
| A002 `aux_preflight_error` | Entregar su ficha ES/EN y enlace por regla/campo con los modelos reales de validación y error. La entrada inválida enfoca su campo y abre la regla correcta; un error sin regla ofrece explicación general explícita. No cambiar advertencias en bloqueos ni copiar secretos al diagnóstico. |

La ficha A002 y la tabla de paquetes del [encargo original](ENCARGO_LUNA_AYUDA_CONTEXTUAL.md) asignan estos trabajos a B00. No se amplía el alcance criptográfico.

## Encargo inmediato: cierre B00

Luna debe corregir R5 y terminar los tres requisitos anteriores. Puede dividir este cierre en entregas pequeñas si conviene, indicando qué falta, sin declarar terminado B00 por una entrega parcial. Mantener las regresiones R1–R4 y añadir evidencia de contexto, adaptadores, A002 y preservación de datos. No iniciar B01 hasta recibir el dictamen de Codex.

Archivos propios: modelos/registro/recursos de ayuda, coordinadores y contrato de adaptadores, conexiones acotadas de navegación y validación, pruebas específicas y matriz. Respetar trabajo ajeno, especialmente JOSE/tutorial 25 y documentos del revisor. Sin subagentes, commits, pushes ni cambios del motor o parámetros.

## Secuencia autorizada por el usuario

El usuario ha autorizado a Codex a enviar a Luna las tareas restantes y revisar sus cambios. Los bloques se envían y aceptan uno a uno; la autorización para completar el proyecto no convierte una entrega parcial en aprobada. Toda definición detallada y criterio por caso sigue en el encargo original y en el inventario de UI.

| Orden | Bloque y alcance |
|---|---|
| 0 | Terminar B00: R5, contexto/adaptadores base y A002. |
| 1 | B01: C001 cifrado simétrico y A001 barra global de formatos. |
| 2 | B02: C073 JWE. |
| 3 | B03: C080–C081 PIN claro y protegido. |
| 4 | B04: FPE, archivo, OpenPGP y RSA. |
| 5 | B05: genéricas, Process Designer, conversión y entradas dinámicas. |
| 6 | B06: firmas y MAC. |
| 7 | B07: Key Lab y operaciones de claves simétricas/intercambio. |
| 8 | B08: generadores asimétricos, material, almacenes, PKCS#11 e ICSF. |
| 9 | B09: JOSE restante y COSE. |
| 10 | B10: certificados, CMS/PAdES/ASiC, ASN.1 y CRL. |
| 11 | B11: XML y WS-Security. |
| 12 | B12: Wallet. |
| 13 | B13: PQC, historial, sesiones y configuración. |
| 14 | B14: pagos restantes, suboperativas EMV y comandos dinámicos. |
| 15 | B15: aceptación transversal, cobertura, traducciones, enlaces y empaquetado. |

En cada entrega Codex revisará código, comportamiento y evidencia relevante. Si hay defectos, enviará correcciones del mismo bloque. Cuando un bloque completo quede aprobado, enviará el siguiente a Luna sin requerir que el usuario copie mensajes. El proyecto solo estará terminado cuando se cumplan las condiciones de B15 y toda la cobertura obligatoria esté completa.
