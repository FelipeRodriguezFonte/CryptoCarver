# PaymentsController, fase 1: bloques PIN

`ui/PaymentsController.java` tenía 2473 líneas. Esta fase saca los bloques PIN en claro
y cifrados.

## Qué entra

- Paneles «Clear PIN Blocks» y «Encrypted PIN Blocks (ISO)» de `payments.fxml`.
- Entradas desde el FXML: `handleEncodePinBlock`, `handleDecodePinBlock`,
  `handleEncodeEncryptedPinBlock` y `handleDecodeEncryptedPinBlock`.
- Auxiliares: listas de formatos, selector de relleno según formato (`setupPinBlockFormats`,
  `setupPaddingSelection`, `updatePaddingSelection`) e `isIso4`.
- Estado compartido con el resto del módulo: `maskPan` (también lo usa CVV) pasa a `PanMask`;
  `loadProfile` sigue en el controlador y elige formatos en los mismos combos con
  `selectPinFormat`; el panel de resultados enlaza `pinBlockResultArea` y `encResultArea`.

## Código muerto encontrado

Sin llamadores en FXML, otras clases ni tests:

- `handleTranslatePinBlock` y sus campos `pinTrans*`.
- `initializeAdvancedFeatures`, el único que asignaba esos campos y los de PVV y pistas, con
  `handleGeneratePVV`, `handleVerifyPVV`, `handleEncodeTrack1`, `handleEncodeTrack2`,
  `handleParseTrackData` y sus campos `pvv*` y `track*`. El panel EMV tiene su propio
  `handleEncodeTrack2`.
- `setPinBlockPaddingCombo`: el combo de relleno ya llega inyectado del FXML.

Se elimina en un commit aparte. Queda para otra fase el MAC (`handleGenerateMac`,
`handleVerifyMac`, `setupMacAlgorithms`), que recibe siempre controles nulos.

## Caracterización

`PinBlockCharacterizationUITest` carga `payments.fxml` real y fija con SHA-256 la ida y
vuelta de todos los formatos en claro, los errores de entrada, la ida y vuelta cifrada (TDES y
AES para el formato 4) y los errores del panel cifrado. Los formatos con relleno aleatorio se
fijan por longitud y PIN recuperado, no por bytes.

## Resultado

`PaymentsController` pasa de 2473 a 1733 líneas: 398 de código muerto y el resto a
`PinBlockCoordinator` (y `PanMask`). `PaymentsValidationHeadlessTest`, que busca las claves de
traducción en el código, ahora lee también el coordinador y ya no exige las claves que solo
usaba el código muerto.

## Fallos destapados

- Con una clave de longitud no válida, el panel cifrado publicaba el bloque PIN en claro como
  operación correcta marcada «TDES ECB», y lo añadía al histórico. Ahora muestra el error en el
  campo de clave y no publica nada.
- «Usar como entrada» copiaba el informe completo del resultado en el campo de bloque PIN a
  decodificar. Ahora pasa solo el bloque que produjo la última codificación (en claro o cifrada).
- Pendiente, menor: un bloque en claro de 16 caracteres con letras no hexadecimales da el mensaje
  de longitud incorrecta («Current length: 16»).
