# Fase 1 — fallos antes de fijar el digest

- En base 4e44749, `JoseJwtCharacterizationUITest` llega al round trip anidado y falla: `MASKED expanded output leaked secret`. La salida publicada por `verifyNestedJWT` no declara clasificación; OperationResult toma PUBLIC y el renderer del visor expandido devuelve el texto claro. REDACTED y la captura Shelf comparten esta clasificación errónea. No se fija el digest de esta transcripción hasta corregirlo.
- Comprobación contra main por identidad de código: este worktree parte exactamente de 4e44749 y aún no se ha cambiado producción; `git show 4e44749:src/main/java/com/cryptocarver/ui/JOSEController.java` contiene el mismo `.output(payloadOut.getText().getBytes(StandardCharsets.UTF_8))`. No se ejecuta main fuera del único worktree autorizado.
- Arreglo acotado previsto: declarar SECRET únicamente en la salida descifrada anidada, usando la API existente. Ningún cambio en crypto/, OperationResult, shell o políticas.
- Error del fixture corregido antes: `JOSEService.verifyJws` solo admite Compact. Para JSON se verifica la firma reconstruyendo el payload mediante verifyDetachedJWS; no era un defecto de producción.
- Privacidad: el fixture usa el renderer real que alimenta el visor expandido y la función real visibleOperationDetails del shell para historial; Shelf se evalúa con ResultPresentationPolicy y se comprueba inalterado. Historial aislado en memoria, settings restaurados. La fase no añade telemetría; revisión de fuentes: estos métodos no envían material a una API de telemetría. Las advertencias públicas se fijan en EN/ES.

## Puerta default tras extracción

2892 tests, 1 fallo, 0 errores, 1 omitido. Único fallo: `SpecializedFeedbackHeadlessTest.specializedValidationFeedbackHasDistinctEnglishAndSpanishKeys`, aserción de presencia de `module.jose.feedback.statusDetachedGenerated` en JOSEController. La clave se ha movido intacta a JoseJwtCoordinator. Se ajusta únicamente el propietario fuente de cuatro claves extraídas (`statusDetachedGenerated`, `statusJwtGenerated`, `statusJwtValidation`, `statusNested`) usando el mapa existente coordinatorKeys. Se mantienen las mismas aserciones de presencia y textos EN/ES distintos; no se relajan umbrales ni se cambian las cuatro pruebas JOSE exigidas. No es un defecto preexistente ni de comportamiento. Se repetirá la misma puerta antes de continuar.

## Revisión del fixture de crit

El primer fixture intentaba generar un header custom con `crit`, que JOSEService rechaza explícitamente. Sus líneas crit_ignore/crit_understood caracterizaban los controles y el token anterior retenido, no la verificación de un token con crit. Se conserva ese digest, se afirma explícitamente el rechazo de generación y se añade una transcripción independiente con un token Nimbus que sí contiene un parámetro crítico inventado. No es un fallo de producción. El nuevo fixture se ejecutará aislado sobre el JOSEController previo a la extracción y sobre el extraído, sin cambiar la rama ni trabajar fuera de este worktree.

El fixture adicional esperaba rojo para crit desconocido; en el código anterior a la extracción el resultado es naranja (firma verificada y finding unsupportedCrit). Se corrige la aserción para verificar el rechazo global de claims y el finding localizado, sin cambiar producción ni la transcripción semántica INVALID.
