# Revisión B01 — cierre

Fecha: 10 de octubre de 2026. Revisor: Codex. Implementadora: Luna.

**Dictamen: B01 APROBADO.** B00 conserva su aprobación. Se autoriza el comienzo de B02, C073 `op_jose_jwe`, conforme al encargo original.

Turno de Luna revisado: `01a1258a-8457-7563-9a76-3391e6802e45`. Fuentes: encargo, inventario UI, matriz de implementación y revisiones B01 01–03. Este dictamen sustituye el estado pendiente de la revisión 03; no sustituye sus reproducciones ni la rectificación de recuentos históricos.

## Alcance aceptado

C001 `op_sym_ciphers`: explicación ES/EN de operación, campos, opciones y 21 acciones; fuentes de clave, tamaños, modos, padding, IV/nonce, AAD y tag según la variante real. Los controles auxiliares, incluidos menús de plantillas, están clasificados. La gestión completa de A003 sigue asignada a B13 por el encargo; su clasificación y ayuda contextual de C001 están entregadas.

A001 `aux_global_formats`: los 16 perfiles registrados explican el contrato real de cada consumidor, opciones permitidas y motivos de deshabilitación. Se distingue la conversión manual de los codecs y la validación previa de las operaciones. La restauración por ruta y los cambios programáticos actualizan el contexto.

Las correcciones R1–R9 quedan cerradas. R2 ya se confirmó en la revisión 03. La última entrega resuelve R7/Base64URL manual, R8/traducción de la regla de plantillas y R9/campos FXML de los cuatro menús. Volver desde campos o desde A001/A002 recupera las selecciones actuales; no reconstruye valores predeterminados obsoletos. Inspector y ventana compacta conservan idioma, contexto y acción.

## Evidencia de cierre

- Se inspeccionaron las claves ES/EN de `symmetric.template.allowlist` y del contrato Base64URL manual, los cuatro campos `@FXML` y la nueva regresión JavaFX. Convert admite padding y espacios en los ejemplos `QQ==`, `Q Q==`, `QQ` y `Q Q`, produciendo `41`; el codec especializado mantiene su contrato estricto. La ayuda explica esta diferencia.
- Evidencia nueva de Luna: **98 pruebas correctas**, repartidas en 49 UI, 42 de unidad/contratos y 7 de readiness. Se inspeccionaron los XML nuevos y sus fechas en `target/b01-review/r2-r9-verified-reports/{ui,unit,readiness}`; sin fallos, errores ni omisiones.
- Verificación focalizada del revisor: **37 pruebas correctas**, 36 de catálogo/FXML/contratos y la nueva regresión Base64URL de UI. Logs `target/b01-review/review-r7-r9-close-unit.log` y `review-r7-r9-close-ui.log`; XML copiados en `target/b01-review/reviewer-closing-reports/`. Son comprobaciones adicionales sobre clases coincidentes; no se suman a las 98 como pruebas distintas.
- Sonda independiente del revisor: `target/b01-review/B01ClosingProbe.java`, evidencia `context-probe-closing.log`. ES/EN, anchuras 1800 y 900: navegación general/campo/auxiliar, retorno de contexto, visibilidad de controles y ayuda, formatos recordados y sincronización manual/programática. Recorrido AES-256 GCM → CBC → Salsa20: aplicabilidad, reglas visibles y preservación de entradas, clave, IV, tag, AAD y resultado. Todos los controles comprobados pasan.
- La sonda confirma ida y vuelta GCM con AAD, rechazo de tag alterado y de tamaño de clave inválido. No se cambió el comportamiento criptográfico para ajustar la documentación.
- `git diff --check` correcto.

Las ejecuciones fueron secuenciales y con preferencias aisladas. Se contaron únicamente informes nuevos de las clases realmente ejecutadas. La selección de pruebas de unidad evita el filtro exclusivo `ui` del perfil Maven; no se reutilizan XML antiguos como evidencia de ejecución.

## Continuación y límites

No queda deuda obligatoria de B01. Los siguientes casos siguen pendientes según sus bloques originales. Esta aprobación no declara completa la cobertura de 96 operaciones, 24 auxiliares y entradas dinámicas. B15 deberá comprobar la cobertura integral y los recursos empaquetados fuera del checkout.

Luna actualizará la matriz al dictamen aprobado y mantendrá explícitas las rectificaciones de los recuentos históricos. Codex registra el despacho y seguimiento de B02 en `COORDINACION_AYUDA_CONTEXTUAL.md`.
