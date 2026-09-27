# Mejoras de funcionalidad — estado contrastado con código y tests

Revisión del 26 de septiembre de 2026. «Hecho» significa que se localizaron una clase y un test que cubre la capacidad indicada. «Parcial» conserva el trabajo pendiente; la presencia de una API sin test específico no acredita por sí sola un perfil completo.

## 1. Validación de revocación real (OCSP/CRL) fuera de XAdES — hecho

`RevocationValidationService` configura fuentes OCSP/CRL locales y en línea de forma explícita (`RevocationValidationServiceTest`). `PadesLtLtaOfflineTest` genera PKI, CRL y OCSP buenos/revocados en el propio test, valida PAdES-T con OCSP local y PAdES-LT/LTA con CRL local, y comprueba que un firmante revocado se marca como tal. También verifica CRL good/revoked en `CertificateGenerator.validateCertificateChain` y en `CmsInspector`; el modo online queda desactivado en estas pruebas. No se atribuye aquí cobertura a XAdES.

## 2. PKCS#11/HSM real — perfiles de proveedor — hecho

`Pkcs11LibraryInventoryService` enumera slots, tokens y mecanismos sin PIN (`Pkcs11LibraryInventoryServiceTest`). `Pkcs11ProfileRepository` guarda perfiles sin credenciales (`Pkcs11ProfileRepositoryTest`). `Pkcs11LibraryDiagnosticService` diagnostica la biblioteca nativa (`Pkcs11LibraryDiagnosticServiceTest`). `XMLSignatureOperations.signXAdESWithPkcs11` conecta el token con XAdES (`SoftHsmIntegrationTest`; requiere el entorno de integración para ejecutarse).

## 3. EMV Option B — pendiente

Continúa sin implementación ni vectores públicos incorporados. Mantener el requisito de vectores verificables antes de añadir la derivación.

## 4. TR-31 — cobertura por lotes y matriz de versiones — parcial

`TR31Operations` analiza bloques opcionales y versiones A/B/C/D; `TR31OperationsTest` comprueba análisis, errores, normalización y casos de matriz. Falta acreditar la cobertura completa de cada bloque opcional por versión. `BatchOperationCatalog` no incluye importación/exportación TR-31 por lotes.

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
