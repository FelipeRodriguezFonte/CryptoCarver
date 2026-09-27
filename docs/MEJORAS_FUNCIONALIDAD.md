# Mejoras de funcionalidad — estado contrastado con código y tests

Revisión del 26 de septiembre de 2026. «Hecho» significa que se localizaron una clase y un test que cubre la capacidad indicada. «Parcial» conserva el trabajo pendiente; la presencia de una API sin test específico no acredita por sí sola un perfil completo.

## 1. Validación de revocación real (OCSP/CRL) fuera de XAdES — hecho

`RevocationValidationService` configura fuentes OCSP/CRL locales y en línea de forma explícita (`RevocationValidationServiceTest`). `PadesLtLtaOfflineTest` genera PKI, CRL y OCSP buenos/revocados en el propio test, valida PAdES-T con OCSP local y PAdES-LT/LTA con CRL local, y comprueba que un firmante revocado se marca como tal. También verifica CRL good/revoked en `CertificateGenerator.validateCertificateChain` y en `CmsInspector`; el modo online queda desactivado en estas pruebas. No se atribuye aquí cobertura a XAdES.

## 2. PKCS#11/HSM real — perfiles de proveedor — hecho

`Pkcs11LibraryInventoryService` enumera slots, tokens y mecanismos sin PIN (`Pkcs11LibraryInventoryServiceTest`). `Pkcs11ProfileRepository` guarda perfiles sin credenciales (`Pkcs11ProfileRepositoryTest`). `Pkcs11LibraryDiagnosticService` diagnostica la biblioteca nativa (`Pkcs11LibraryDiagnosticServiceTest`). `XMLSignatureOperations.signXAdESWithPkcs11` conecta el token con XAdES (`SoftHsmIntegrationTest`; requiere el entorno de integración para ejecutarse).

## 3. EMV Option B — hecho

`EMVOperations.deriveICCMasterKeyOptionB` y el selector `deriveICCMasterKey(..., method)` implementan la derivación de EMV Book 2 v4.4, §A1.4.2, con los intermedios SHA-1, decimalización y Y. `AUTO` selecciona B cuando PAN supera 16 dígitos, siguiendo literalmente la cláusula. Aunque el encargo describía el umbral sobre PAN || PSN, §A1.4.2 decide por la longitud de PAN; con PAN de 16 dígitos o menos `AUTO` conserva A incluso si PAN || PSN excede 16.

El vector completo de PAN de 19 dígitos procede de Igor Dubinsky, *Cryptography for Payment Professionals*, apéndice C.5.2, y está corroborado por su test público en `ilya-dubinsky/cfpp` (licencia Unlicense). El libro muestra un sufijo `00` adicional en la entrada de hash; el SHA-1 publicado coincide con BCD `0987654321012345678901`, es decir, PAN rellenado a la izquierda y PSN `01`, sin esos dos ceros extra. Los ejemplos 1 y 2 de decimalización se cotejaron con EMV Book 2 v4.4 §A1.4.2. La norma rellena a la izquierda el PAN impar antes de concatenar PSN; primero se recogen los nibbles decimales de izquierda a derecha y solo si faltan se añaden los nibbles A–F convertidos a 0–5, también desde la izquierda. Nunca faltan suficientes nibbles tras consumir el hash SHA-1 completo para formar 16 dígitos.

El vector del libro presenta la clave con paridad DES impar; la API mantiene la forma cruda de Option A. El test ajusta paridad al comparar con el valor publicado.

La pantalla EMV muestra método e intermedios, y `EMV_ICC_MASTER_KEY` acepta `method` (`AUTO` por defecto). Los flujos que derivan la clave ICC para ARQC/ARPC usan `AUTO`; los vectores existentes de ambos métodos de ARPC y los de ARQC conservan sus valores.

## 4. TR-31 / X9.143 — tablas, diagnóstico y lotes — hecho con límites documentados

`TR31Operations` publica los usos, algoritmos, modos, exportabilidad e identificadores de bloques opcionales documentados y muestra su descripción al analizar cabeceras. La tabla normativa accesible para este cambio es ANSI X9.143:2021; se contrastó con OpenEMV `tr31` (LGPL-2.1), cuya cabecera y comentarios citan cláusulas y tablas concretas. No se atribuyen los códigos ampliados de 2021 a X9.143-2022 sin texto público verificable. Los usos F0–F6 y P2, que aparecen en un listado de IBM sin definición individual completa en las fuentes consultadas, se dejan fuera. La normalización comprueba estructura, cuenta decimal, duplicados, PB al final y las formas documentadas de AL, BI, CT, DA, HM, IK, KC, KP, KS, KV, LB, PB, TC, TS y WP; DA se limita al uso B3. Para FL y PK sólo se dispone de la implementación secundaria citada. No se implementa la longitud extendida de bloques opcionales ni el contenido semántico específico de FL, LB y PB. KS se describe como KSN TDEA-DUKPT conforme a X9.143/ANSI X9.24-3, pero se mantiene permisiva en la normalización por compatibilidad con un ejemplo sintético ya usado por CryptoCarver; al leer se advierte si no mide 8 o 10 bytes hexadecimales.

Las versiones A/B/C validan KBPK TDES de 16/24 bytes; D valida KBPK AES de 16/24/32 bytes. La antigua regla de `validateMatrix` que prohíbe clave envuelta AES con A/B/C se conserva por compatibilidad con pruebas existentes, aunque corresponde al algoritmo de la clave protegida y no al algoritmo de la KBPK. `parseHeader` informa combinaciones que rechaza el validador heredado, bloques decodificados y avisos estructurales; no puede determinar el algoritmo de la KBPK porque éste no está codificado en la cabecera. La prueba `TR31OperationsTest` usa cuatro ejemplos de TR-31:2018, anexo A, publicados por OpenEMV `tr31` (LGPL-2.1): verifica unwrap para A/B/C/D y roundtrip del wrap. El wrap A actual no coincide byte a byte con el bloque de referencia aun con cabecera coincidente y padding determinista; el origen criptográfico del desacuerdo no está aislado. Se conserva el comportamiento de escritura existente.

`BatchOperationCatalog` ofrece «TR-31 → Header JSON», que no requiere claves y queda disponible automáticamente en la API local. `tr31-batch wrap|unwrap` lee la KBPK exclusivamente desde una variable de entorno, usa codecs y runner compartidos, redacta material de clave y continúa tras errores de fila (salida 3). La UI deriva usos, modos y bloques opcionales de las tablas; el análisis muestra valores y avisos.

## 5. PAdES y ASiC — perfiles avanzados — hecho

`PadesLtLtaOfflineTest` acredita Baseline-T con sello de firma RFC 3161, Baseline-LT con DSS, certificados y CRL local, y Baseline-LTA con sello de archivo validado criptográficamente. Para ASiC-S y ASiC-E, `AsicOperations` expone firma CAdES/XAdES en niveles B/T/LT/LTA; T y superiores usan TSA y LT/LTA exigen CRL/OCSP local o revocación en línea explícita. El panel ASiC permite elegir formato, nivel, TSA y evidencia local, con la red desactivada por defecto. Las pruebas offline específicas y los niveles DSS observados documentan el alcance comprobado.

## 6. WSS-Security — integración con Process Designer — parcial

`WssNodeHandler` expone firma y cifrado SOAP como nodos y `ProcessEngine` lo registra (`WssNodeHandlerTest`). La integración de nodos está hecha. Sigue pendiente documentar el recorrido manual de verificación con un cliente SOAP externo.

## 7. Huecos puntuales — hecho para los casos citados

- Carga PQC desde PEM/DER: hecha en `PostQuantumController` (`PostQuantumControllerTest`).
- Previsualización manual de CEK JWE: hecha para los algoritmos admitidos por `JWEManualCekRecovery` (`JweManualCekRecoveryTest`); otros algoritmos siguen fuera de su inventario.
- Conversión JWK OKP Ed25519/Ed448: hecha en `KeyCertificateFormatService` (`KeyCertificateFormatServiceTest`, ida y vuelta JWK/PEM para ambas curvas).

## 8. Batch Runner — catálogo determinista — hecho

`BatchOperationCatalog` ofrece hashes SHA-1, SHA-224, SHA3-256, SHA3-512 y MD5 (identificado como legacy), CRC32/CRC32C, conversiones UTF-8/Hex/Base64/Base64URL/Base32/Base58/Base94 y BCD empaquetado, dígitos AMEX SE, validación PAN, análisis Track 2 y EMV TLV, estado APDU e inspección ASN.1/TLV. Las operaciones reutilizan `HashOperations`, `CodecRegistry`, `DataConverter`, `CheckDigitCalculator`, `PaymentOperations`, `EmvTlv`, `ApduStatus` y `SafeTransformations`; no reciben claves ni PIN. La CLI `batch` y la selección de lotes de la interfaz leen el catálogo automáticamente.

No hay implementación BLAKE2b-256 en `HashOperations`; esa variante no se ofrece. Para BCD, las entradas impares se rellenan con un cero inicial al empaquetar.

`run-process --batch` permite aplicar columnas `nodo.parametro` a procesos guardados, con una ejecución y un resultado por fila; véase `CryptoCarverCli` y los tests del modo lote.

## 9. API REST local — catálogo batch — hecho

`LocalApiServer` publica `GET /v1/operations` y `POST /v1/transform/{operation}` para las operaciones del catálogo batch. Los slugs estables y descripciones proceden de `BatchOperationCatalog`; `/openapi.json` se construye usando ese catálogo. Se mantienen `/v1/sha256` y los dos endpoints Base64URL históricos. El servicio escucha exclusivamente en `127.0.0.1`, limita las peticiones a 1 MiB y devuelve 400 para entradas incorrectas y 404 para slugs desconocidos. No expone operaciones con claves o PIN. La cobertura está en `LocalApiServerTest`.

## 10. Exportación de informes PKI — hecho

`PkiChainReportExporter` genera un informe Markdown con datos X.509, hallazgos de `CertificateLinter` y `EidasCertificateInspector`, ruta de confianza y estado de revocación offline. La interfaz de certificados ofrece «Exportar informe (Markdown)» y la CLI expone `chain-report`. La cobertura está en `PkiChainReportExporterTest`.

La validación PAdES lee el sello de archivo de `DetailedReport`: DSS coloca el documento bajo `Timestamp` con `Type="DOCUMENT_TIMESTAMP"`, no en `SimpleReport.timestampIdList`. El informe local registra `ValidationProcessBasicTimestamp=PASSED` y que la subindicación no está presente; separadamente, `ValidationTimestampQualification=FAILED` con `QUAL_CERT_TRUSTED_LIST_REACHED_ANS` indica que no se alcanzó una lista de confianza cualificada. En el test la TSA encadena al root del truststore y su EKU crítico es `timeStamping`; por ello esa advertencia de lista cualificada no invalida la ruta PKIX local. `PadesLtLtaOfflineTest` verifica el perfil LTA con el ancla y su rechazo cuando falta.

## 11. AES DUKPT — hecho

`AesDukpt` implementa la derivación y operaciones; `PaymentsController` las expone en UI (`AesDukptTest`). La entrada pendiente del roadmap debe actualizarse para no reabrir esta funcionalidad.
