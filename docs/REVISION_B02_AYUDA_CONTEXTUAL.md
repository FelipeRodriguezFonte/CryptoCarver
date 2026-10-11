# Revisión B02 — C073 JWE

Fecha: 10 de octubre de 2026. Revisor: Codex. Turno revisado: `01a125a6-8565-7eb3-81fe-50ca61f610e5`.

**Dictamen: REQUIERE CORRECCIÓN.** B00 y B01 siguen aprobados; B03 no comienza todavía.

Se contrastaron encargo C073, inventario UI, matriz, catálogo, recursos ES/EN, adaptador, renderer, integración del shell, coordinador JWE y pruebas. La entrega incorpora 11 acciones, controles por campo y opciones derivadas de las listas actuales. La eliminación de la recuperación/visualización automática de CEK cumple la condición explícita de C073; no cambia el cifrado ni el descifrado del contenido. Se conserva el componente de recuperación manual y sus pruebas fuera de la consulta de ayuda.

## R1 — el acordeón abre ayuda con el modo desactivado

Prioridad P2. `JOSEController.installJweContextHelpButtons` y `bindJweActionPane` notifican expansión y clics del panel a `showJweHelpContext`. Ese método siempre llama a `showCurrentHelpContext`, sin distinguir seguimiento de contexto de solicitud explícita de ayuda.

Reproducción confirmada con FXML de producción en ES/EN y anchura 900: modo contextual desactivado, navegar a JWE y expandir descifrado. Antes no hay ventana de ayuda; después aparece una ventana compacta. La sonda registra `after_expand_mode_off_window=true`. En escritorio también puede seleccionar Ayuda sin petición explícita. Desplegar un panel debe actualizar el contexto sin abrir una ventana ni cambiar la pestaña seleccionada con modo desactivado. Los botones `?` y el acceso explícito de ayuda sí deben abrirla. Evitar handlers de clic que reaccionen indiscriminadamente a todo el contenido del panel.

Criterio: regresión ES/EN, escritorio/compacto, modo desactivado/activado, expansión y clic de cabecera frente a solicitud explícita, sin pérdida de acción ni campos.

## R2 — descifrado hereda restricciones del formulario de cifrado

Prioridad P2. `ContextualHelpCoordinator.renderJweApplicability` aplica las selecciones de cifrado a todas las acciones. El adaptador publica `alg`, `enc`, serialización y AAD del generador también al consultar descifrado. La regresión actual incluso espera esa selección en acción decrypt.

Reproducción ES/EN, anchuras 1800/900: dejar generador en `dir`/`A256GCM`/Compact y AAD no vacío; abrir `?` de la clave privada de descifrado. La ayuda muestra que el AAD actual es incompatible y el cifrado se rechaza, aunque se consulta un campo de descifrado y el token puede usar otra serialización/algoritmo. La sonda registra `decrypt_help_contains_encrypt_rule=true` en los cuatro escenarios. Tampoco corresponde presentar el tamaño de CEK de enc del generador como condición del token recibido.

Criterio: contextualizar las reglas por acción/campo. En descifrado explicar que alg/enc/serialización proceden del token y que el selector local pertinente es el formato del secreto. No analizar tokens ni secretos como efecto de consultar ayuda, ni usar el último resultado para inventar contexto. Los selectores del generador pueden mantenerse almacenados de forma segura, pero no presentarse como condiciones actuales del descifrado/desglose. Verificar dos tokens distintos con los selectores del generador deliberadamente discordantes.

## R3 — detalles editoriales obligatorios todavía indeterminados

Prioridad P2. El campo PBES2 dice «mínimo actual»/«rango aceptado», sin explicar qué valores admite. El coordinador actual usa 600000 por defecto/vacío y rechaza valores no enteros o inferiores a 1000; ese control se valida antes de generar incluso con alg no PBES2. Explicar el comportamiento existente y cuándo el algoritmo realmente usa el valor, sin modificarlo ni atribuir estos números a un requisito normativo universal.

Cabecera personalizada: enumerar los nombres realmente reservados `alg`, `enc`, `zip`, `epk`, `iv`, `tag`, `p2s`, `p2c`, con ejemplo JSON permitido/rechazado. En General multidestinatario explicar que `kid`/`apu`/`apv` globales no vacíos se rechazan y deben proceder de cada JWK; no basta decir dónde se obtienen. Explicar tipos admitidos de material (PEM/certificado/JWK/JWKS según ruta) y el contrato del formato del secreto: UTF-8 conserva el texto, Hex admite espacios y prefijo 0x, Base64/Base64URL elimina espacios. Contrastar con `JoseKeyMaterial`, sin trasladar reglas de otros codecs. La ficha de entrada afirma ofrecer presencia/vacío, pero el adaptador no publica presencia de `jweInputArea`: corregir esa promesa o implementar solo el indicador booleano seguro.

Criterio: textos ES/EN concretos, coherentes en ficha y referencia, ejemplos sintéticos y pruebas que contrasten límites/formato con consumidores actuales. No parámetros nuevos ni cambios de política.

## Evidencia y límites

Los nueve XML nuevos de Luna se inspeccionaron y copiaron a `target/b02-review/luna-reports/`: **95 pruebas correctas**, 92 unidad/catálogo/FXML y 3 UI, sin fallos, errores ni omisiones. Fechas de modificación 14:12:53–14:13:06 Europe/Madrid. Incluyen positivos RSA/dir/General y negativos de Compact/AAD, longitud de secreto y tag alterado. No se reejecutaron esas suites sin causa ni se sumaron informes antiguos.

Sonda independiente: `target/b02-review/B02Probe.java`, `probe.log`; FXML de producción, preferencias aisladas, ES/EN y anchuras 1800/900. Confirma R1/R2. No se contabiliza como una prueba JUnit. `git diff --check` correcto. Los tres enlaces oficiales RFC 7516/7518/7519 de las referencias locales responden; la prueba integral del empaquetado fuera del checkout sigue pendiente de B15.

La prueba contextual nueva comprueba selectores y ausencia de sentinelas en HelpContext, pero no comprueba que todas las entradas/resultados conserven su valor tras consultar ayuda. Añadir evidencia directa de preservación, teclado, creación/reapertura compacta y liberación/reactivación del adaptador al corregir R1/R2; mantener las regresiones compartidas B00/B01 afectadas. No dar cobertura completa por la mera presencia de claves de traducción.

Luna debe limitar sus ediciones a aplicación, pruebas, recursos, inventario y matriz; el registro de coordinación y los informes pertenecen al revisor. Su actualización del snapshot de coordinación en esta entrega queda sustituida por el registro del dictamen y despacho de corrección.
