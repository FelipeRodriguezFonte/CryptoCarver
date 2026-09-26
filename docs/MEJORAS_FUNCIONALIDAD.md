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

## 5. PAdES y ASiC — perfiles avanzados — parcial

`PadesLtLtaOfflineTest` acredita Baseline-T con sello de firma RFC 3161, Baseline-LT con DSS, certificados y CRL local, y Baseline-LTA con sello de archivo validado criptográficamente. Prueba también Baseline-T/LT/LTA mediante conexión de token PKCS#12. `AsicOperations` implementa contenedores ASiC con CAdES; siguen pendientes XAdES dentro del contenedor, revocación y LTV, por lo que el punto permanece parcial.

## 6. WSS-Security — integración con Process Designer — parcial

`WssNodeHandler` expone firma y cifrado SOAP como nodos y `ProcessEngine` lo registra (`WssNodeHandlerTest`). La integración de nodos está hecha. Sigue pendiente documentar el recorrido manual de verificación con un cliente SOAP externo.

## 7. Huecos puntuales — hecho para los casos citados

- Carga PQC desde PEM/DER: hecha en `PostQuantumController` (`PostQuantumControllerTest`).
- Previsualización manual de CEK JWE: hecha para los algoritmos admitidos por `JWEManualCekRecovery` (`JweManualCekRecoveryTest`); otros algoritmos siguen fuera de su inventario.
- Conversión JWK OKP Ed25519/Ed448: hecha en `KeyCertificateFormatService` (`KeyCertificateFormatServiceTest`, ida y vuelta JWK/PEM para ambas curvas).

## 8. Batch Runner — catálogo limitado — parcial

`BatchOperationCatalog` ya incluye hashes, conversiones de formatos y dígitos de control, con cobertura en `BatchOperationCatalogTest`; la antigua descripción «solo SHA-256 y Base64URL» está desfasada. Queda ampliar el catálogo a otras operaciones deterministas aptas para lote.

`run-process --batch` permite aplicar columnas `nodo.parametro` a procesos guardados, con una ejecución y un resultado por fila; véase `CryptoCarverCli` y los tests del modo lote.

## 9. API REST local — pendiente de ampliación

`LocalApiServer` continúa limitado a `/v1/sha256` y a codificación/decodificación Base64URL (`LocalApiServerTest`). No cubre todavía el catálogo batch; conserva el acceso por loopback.

## 10. Exportación de informes PKI — hecho

`PkiChainReportExporter` genera un informe Markdown con datos X.509, hallazgos de `CertificateLinter` y `EidasCertificateInspector`, ruta de confianza y estado de revocación offline. La interfaz de certificados ofrece «Exportar informe (Markdown)» y la CLI expone `chain-report`. La cobertura está en `PkiChainReportExporterTest`.

La validación PAdES lee el sello de archivo de `DetailedReport`: DSS coloca el documento bajo `Timestamp` con `Type="DOCUMENT_TIMESTAMP"`, no en `SimpleReport.timestampIdList`. El informe local registra `ValidationProcessBasicTimestamp=PASSED` y que la subindicación no está presente; separadamente, `ValidationTimestampQualification=FAILED` con `QUAL_CERT_TRUSTED_LIST_REACHED_ANS` indica que no se alcanzó una lista de confianza cualificada. En el test la TSA encadena al root del truststore y su EKU crítico es `timeStamping`; por ello esa advertencia de lista cualificada no invalida la ruta PKIX local. `PadesLtLtaOfflineTest` verifica el perfil LTA con el ancla y su rechazo cuando falta.

## 11. AES DUKPT — hecho

`AesDukpt` implementa la derivación y operaciones; `PaymentsController` las expone en UI (`AesDukptTest`). La entrada pendiente del roadmap debe actualizarse para no reabrir esta funcionalidad.
