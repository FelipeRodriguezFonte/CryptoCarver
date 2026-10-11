# Revisión B02 — cierre C073 JWE

Fecha: 10 de octubre de 2026. Revisor: Codex. Turno revisado: `01a125c6-9629-7a40-a6cd-6a7462e4edd3`.

**Dictamen: B02 APROBADO.** R1–R3 de la primera revisión quedan cerrados. B00 y B01 mantienen aprobación; procede B03, C080/C081, sin cambiar el alcance del encargo.

## Correcciones verificadas

R1: el acordeón notifica un seguimiento separado de la apertura explícita. `trackJweHelpAction` no abre vistas y respeta modo/ruta activos. Se retiraron los handlers de clic indiscriminado de los paneles. La sonda de producción confirma en ES/EN, 1800/900, que expandir descifrado con modo desactivado no crea ventana ni selecciona la pestaña Ayuda. El acceso explícito mantiene su funcionamiento.

R2: el renderer limita las reglas de selectores/AAD del generador a cifrado, serialización y destinatarios. Descifrado y desglose no presentan esas selecciones como condiciones del token recibido. Se inspeccionaron la explicación del token y la independencia del formato de secreto. No hay parsing de tokens/claves como efecto de consultar ayuda. La sonda confirma ausencia de la falsa restricción de AAD Compact en los cuatro escenarios y preservación de dos JWE sintéticos válidos, Compact y Flattened, discordantes con el generador.

R3: recursos ES/EN y referencias locales concretan PBES2, entero mínimo 1000, valor predeterminado/vacío 600000 y validación previa actual aunque alg no use PBES2. Enumeran los ocho nombres reservados y ejemplos aceptado/rechazados, rechazo de kid/apu/apv globales en General multidestinatario, formatos de material y decodificación real de secretos. Se retiró la promesa de presencia del token que el adaptador no publicaba. Los textos se contrastaron con `JoseKeyMaterial`, `JweComposer` y `JoseJweCoordinator`, sin cambiar esas reglas.

## Alcance y evidencia

La entrega cubre las 11 acciones de C073, campos de cifrado/descifrado/desglose, opciones reales de alg/enc/serialización/formatos/compresión, y referencias locales ES/EN. La sonda recorre los **33 botones locales** de ayuda, comprueba traducciones y preservación de entradas/resultados. También verifica las 241 referencias de claves de catálogo (incluidas repeticiones por acción) en cada bundle. La protección de CEK aceptada en el primer informe se conserva. Los controles compartidos y las familias JWK/JWKS/nested pendientes están clasificados; no se atribuyen a B02.

Se inspeccionó la regresión ampliada `JoseJweContextualHelpUITest`: modo activo/inactivo, cambios manuales/programáticos, idiomas, ambos tamaños, sentinelas, snapshot de controles, acción por teclado, retorno de formatos, cierre/reapertura compacta y navegación/reactivación. La regresión compartida B00/B01 aporta la cobertura auxiliar y lifecycle común. Se inspeccionaron también catálogo, contratos y pruebas del compositor: positivos RSA/dir/General multidestinatario y negativos AAD Compact, longitud de secreto y tag alterado.

Evidencia conservada en `target/b02-review/closing-reports/summary.json` y XML correspondientes:

- **93 pruebas de unidad/catálogo/FXML de Luna**, XML del 14:37:18–14:37:19 Europe/Madrid; cero fallos, errores u omisiones.
- **2 caracterizaciones JWE de Luna**, XML del 14:38:37; correctas.
- **1 regresión contextual JWE reejecutada por el revisor**, XML del 14:52:49; correcta. Su ejecución reemplaza el XML de esa misma prueba de Luna, sin contarlo dos veces. Log: `target/b02-review/reviewer-correction-ui.log`.
- **14 regresiones compartidas B00/B01 de Luna**, XML del 14:35:58; correctas, informadas separadamente del bloque.

Así se confirma la suite focalizada de **96 pruebas distintas de B02**, más las 14 compartidas, sin duplicar ejecuciones de la misma clase ni sumar XML históricos. Preferencias aisladas; comprobaciones secuenciales, sin Maven paralelo.

Sonda independiente del revisor: `target/b02-review/B02Probe.java`, resultados `probe-correction.log` y `probe-closing.log`. FXML de producción, ES/EN, 1800/900; R1/R2, todas las consultas locales, preservación y tokens válidos discordantes. Terminación correcta, sin aserciones fallidas. No se contabiliza como JUnit. `git diff --check` correcto. Los enlaces RFC oficiales se comprobaron en la primera revisión; recursos fuera del checkout siguen asignados a B15.

## Continuación

No queda deuda obligatoria de B02. Luna actualizará el estado aprobado en la matriz al comenzar B03. La cobertura completa de 96 operaciones, 24 auxiliares y entradas dinámicas, y la aceptación integral del empaquetado, siguen pendientes de los bloques siguientes. Codex conserva la responsabilidad de dictámenes y coordinación; Luna modifica aplicación, pruebas, recursos, inventario y matriz.
