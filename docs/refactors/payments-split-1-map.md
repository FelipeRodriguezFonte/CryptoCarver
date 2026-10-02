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

# Fase 2: generación y verificación de PIN

- `PinGenerationCoordinator`: IBM 3624 (generar y verificar, con ventana de datos de
  validación configurable), generador de offset, generador de PVV y búsqueda de PINs que dan
  un PVV. Caracterizado antes con `PinGenerationCharacterizationUITest`.
- Código muerto eliminado: el panel MAC (`handleGenerateMac`, `handleVerifyMac`,
  `setupMacAlgorithms` y sus campos). `payments.fxml` no tiene esos controles. Los algoritmos
  ISO 9797-1 siguen en el panel MAC de Autenticación; `Iso8583PaymentsPaneTest` lo comprueba ahí.
- `PaymentsController` queda en 1189 líneas.

## Fallos destapados y corregidos

- «Derive PIN from PVV» publicaba en el histórico el informe completo, con la PVK en claro.
  Ahora publica solo los PINs candidatos.
- El generador de offset calculaba primero con la configuración por defecto y fallaba con PANs
  cortos aunque la configuración elegida fuera válida.
- Con una PVK no hexadecimal, el redactor de secretos convertía el error en
  «Error in PIN=[REDACTED] hexadecimal string»; ahora dice qué falló.
- Los errores de posición inicial, longitud y tabla de decimalización señalan su campo.
- Cargar un perfil Secure Messaging desde el menú Laboratorio decía «cargado» sin cargar
  nada (Pagos no tiene ese formulario); ahora lo dice y remite a «Ejecutar y verificar».
- Un bloque PIN en claro con caracteres no hexadecimales da su propio mensaje, no el de
  longitud.

# Fase 3: CVV, DUKPT, comandos de host e ISO 8583

- `CvvCoordinator`: CVV, CVV2, iCVV y dCVV. La verificación pregunta el valor mediante un
  proveedor inyectable (el controlador abre el diálogo), así que ahora tiene test.
- `DukptCoordinator`: inspección TDES y AES con su árbol de derivación, bloque PIN AES DUKPT y
  carga de perfiles de laboratorio.
- `HsmHostCommandCoordinator`: componer y analizar tramas de comandos de host, todo local.
- `Iso8583Coordinator`: analizar y construir mensajes ISO 8583.
- El `initialize` de 40 parámetros, al que solo llamaba `initialize()`, se integra en él.
- `PaymentsController` queda en unas 410 líneas: cableado del FXML, panel de resultados y carga
  de perfiles. Caracterizado antes con `PaymentsToolsCharacterizationUITest`.

## Fallos destapados y corregidos

- Los perfiles DUKPT AES cargaban su clave esperada pero la inspección AES no la comparaba;
  ahora muestra el perfil y la comprobación del vector, como en TDES.
- Mensajes duplicados: «Error in DUKPT Error: …», «Error in AES DUKPT PIN block: Error: …» y
  «DUKPT TDES - DUKPT TDES - Basic PIN profile loaded».
- El resultado de dCVV decía que el código de servicio no se usaba, pero el cálculo sí lo usa.

## Pendiente (en `crypto/`)

- El informe de ISO 8583 lista los campos en orden aleatorio: `Iso8583Operations.Message`
  copia los campos con `Map.copyOf`, que no conserva el orden. El test los ordena.
- Navegación: «ISO 8583 Message Inspector» abría «Encrypted PIN Blocks (ISO)» porque la ruta
  contiene «ISO», y el banco de comandos de host no abría ningún panel. Lo cubre
  `PaymentsNavigationUITest` en inglés y español. La ruta del banco admite también el alias
  neutro «Host Command Bank».
- Traducción: «Clear PIN Blocks» aparecía como «Limpiar bloques PIN»; ahora «Bloques PIN en
  claro».
