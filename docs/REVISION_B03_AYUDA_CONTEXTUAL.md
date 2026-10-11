# Revisión B03 — C080/C081

Fecha: 10 de octubre de 2026. Codex revisa el turno de Luna `01a125e1-f26d-7902-98e5-1a0e1bd0f024`.

**Dictamen: REQUIERE CORRECCIÓN.** B00–B02 mantienen aprobación. No iniciar B04.

Se contrastaron encargo, inventario, matriz, catálogo PIN, referencias ES/EN, adaptador, shell, renderer, PaymentsController, PinBlockCoordinator y pruebas. El motor criptográfico permanece intacto. Las referencias describen las particularidades actuales de ISO-4, PAN por formato y salida sin clave, y los indicadores de clave no contienen material sensible.

## R1 — acción/campo de una operativa PIN se restaura en la otra

P2. El shell usa un único `savedPinBlockHelpContext` y restaura acción/campo en navegación y acceso general sin comprobar `operationId`. C080 y C081 tienen catálogos de acciones/campos diferentes.

Reproducción con producción, ES/EN, 1800/900: en Clear PIN abrir `?` del formato de decodificación y navegar a Encrypted PIN. Ayuda conserva `op_pay_enc_pin:decode:format_decode`; ese campo no existe en C081 y desaparece su explicación. En sentido inverso, abrir clave de protección C081 y navegar a Clear PIN produce `op_pay_clear_pin:protect:encrypt_key`; ni acción ni campo existen en C080, por lo que la ficha queda sin detalle seleccionado. El acceso general reproduce la misma mezcla.

Criterio: contexto guardado por ID de operación o restauración solo si el ID coincide y la acción/campo pertenece al catálogo destino. Mantener memoria legítima al volver a la misma ruta; republicar selecciones actuales. Cubrir ambos sentidos, distintas acciones/campos, modo activo/inactivo, retorno A001/A002, campo→general, teclado y cierre/reapertura compacta, con preservación/privacidad. No reducir el alcance de ninguna ruta para evitar la mezcla.

## R2 — opciones de relleno ajenas a C081

P2. `renderPinBlockApplicability` presenta `paddingOptions()` como opciones elegibles también en la ruta protegida. Pero C081 no tiene selector de relleno y su handler llama `PaymentOperations.encodePinBlock(pin, pan, format)`, usando el comportamiento predeterminado, no el selector de C080.

Reproducción ES/EN, 1800/900: C081, formato VISA-2, ayuda de proteger. Aparece «elige un dígito decimal 0–9 o RANDOM_DECIMAL» y toda la lista. No hay control que permita esa elección y esta ruta utiliza el valor predeterminado 5. Lo mismo afecta a las familias con relleno configurable del catálogo claro.

Criterio: en C080 explicar opciones/default/habilitación del selector claro. En C081 explicar únicamente el relleno efectivo de esa ruta y que no es configurable allí. El relleno de C080 nunca modifica C081. La ayuda de decodificación no debe sugerir que cambiar relleno reinterpreta el bloque. Añadir casos VISA-2, ECI-2/3 y Docutel, incluidos cambios del selector claro discordante.

## R3 — textos ES de C081 reutilizan significados de C080

P2. En español, `help.field.op_pay_enc_pin.result.description` explica únicamente campo/bloque claro o PIN; `encode_action.description` omite protección; `decode_action.description` dice interpretar el bloque claro. Sus equivalentes EN ya explican cifrado/descifrado y ausencia de clave correctamente.

Criterio: actualizar ES y comprobar coherencia de ambos idiomas en ficha y referencia: resultado protegido con clave válida, salida clara sin clave, clave independiente de decode, descifrado seguido de recuperación del PIN y ruta AES/TDES/DES efectiva. No modificar el comportamiento actual ni afirmar que presencia de clave demuestra validez/protección exitosa.

## Evidencia y rectificación de recuentos

La entrega informó 53 pruebas nuevas. Sus seis XML archivados en `target/b03-review/` contienen 53 resultados, pero **41 pertenecen a ejecuciones antiguas**: LegacyPinBlockFormatTest (8), PaymentOperationsLegacyPinBlockTest (5) y PaymentControlValuesTest (28), fechadas 08:55. Las cinco de catálogo son del 15:10 y las siete UI del 15:27. El argumento `-DrunUiTests=true` activa el filtro de grupo UI y no ejecuta los tests no etiquetados aunque se enumeren. Copiar todos los XML existentes no constituye evidencia de ejecución nueva.

El revisor ejecutó unidad/catálogo por separado: **46 pruebas correctas** a las 15:33:46, sin fallos, errores ni omisiones. Log `target/b03-review/reviewer-unit.log`; XML y resumen fechados `target/b03-review/reviewer-unit-reports/`. Así se revalidan efectivamente las 41 criptográficas y cinco de catálogo. Con las siete UI recientes de Luna hay 53 pruebas distintas comprobadas, pero no 53 nuevas ejecutadas por el comando de su matriz. No sumar dos ejecuciones de las cinco de catálogo como pruebas distintas.

Luna debe corregir atribución, comandos y recuentos en matriz. En adelante copiar/listar solo clases realmente ejecutadas e informes posteriores al inicio de esa ejecución; desactivar el perfil UI para suites mixtas o separar UI/unidad. La evidencia criptográfica nueva del revisor puede reutilizarse si no cambia el motor: no repetir las 41 por rutina.

Sonda independiente `target/b03-review/B03Probe.java`, `reviewer-probe.log`: confirma R1/R2 en los cuatro escenarios ES/EN y anchuras. Los fallos de contexto/editoriales no son fallos del cifrado. La regresión UI de Luna no comprueba el contexto inmediatamente tras navegar entre rutas antes de pulsar otro `?`, lo que oculta R1. Debe ampliar ese caso y las regresiones necesarias de auxiliares/teclado. No contar un fallback como ficha completa.

Las referencias locales están empaquetadas por ID; sus fuentes del código se inspeccionaron. La aceptación completa fuera del checkout queda para B15. Codex escribe dictámenes/coordinación; Luna corrige aplicación, recursos, pruebas, inventario y matriz, respetando cambios locales ajenos.
