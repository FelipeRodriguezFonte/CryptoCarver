# Mejoras de funcionalidad — estado contrastado con código y tests

Revisión del 26 de septiembre de 2026. «Hecho» significa que se localizaron una clase y un test que cubre la capacidad indicada. «Parcial» conserva el trabajo pendiente; la presencia de una API sin test específico no acredita por sí sola un perfil completo.

## 1. Validación de revocación real (OCSP/CRL) fuera de XAdES — parcial

`RevocationValidationService` configura fuentes OCSP/CRL locales y en línea de forma explícita (`RevocationValidationServiceTest`). `PadesOperations` ya ofrece validación con revocación en línea y exige evidencia local o consulta en línea para firmar LT/LTA. `PadesOperationsTest` cubre Baseline-B y la validación sin revocación, pero no demuestra una firma LT/LTA con evidencia; por ello no se da por cerrada esa parte. Sigue pendiente acreditar con tests la integración equivalente en el diagnóstico de cadenas y CMS.

## 2. PKCS#11/HSM real — perfiles de proveedor — hecho

`Pkcs11LibraryInventoryService` enumera slots, tokens y mecanismos sin PIN (`Pkcs11LibraryInventoryServiceTest`). `Pkcs11ProfileRepository` guarda perfiles sin credenciales (`Pkcs11ProfileRepositoryTest`). `Pkcs11LibraryDiagnosticService` diagnostica la biblioteca nativa (`Pkcs11LibraryDiagnosticServiceTest`). `XMLSignatureOperations.signXAdESWithPkcs11` conecta el token con XAdES (`SoftHsmIntegrationTest`; requiere el entorno de integración para ejecutarse).

## 3. EMV Option B — pendiente

Continúa sin implementación ni vectores públicos incorporados. Mantener el requisito de vectores verificables antes de añadir la derivación.

## 4. TR-31 — cobertura por lotes y matriz de versiones — parcial

`TR31Operations` analiza bloques opcionales y versiones A/B/C/D; `TR31OperationsTest` comprueba análisis, errores, normalización y casos de matriz. Falta acreditar la cobertura completa de cada bloque opcional por versión. `BatchOperationCatalog` no incluye importación/exportación TR-31 por lotes.

## 5. PAdES y ASiC — perfiles avanzados — parcial

`PadesOperations` contiene Baseline-LT/LTA con evidencia de revocación local o en línea y sello de archivo para LTA, pero falta un test específico de generación y validación LT/LTA. `AsicOperations` implementa contenedores ASiC con CAdES; siguen pendientes XAdES dentro del contenedor, revocación y LTV. `PadesOperationsTest` cubre Baseline-B; por ello no se marca el punto completo como hecho.

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

## 10. Exportación de informes PKI — pendiente

Hay diagnósticos en `EidasCertificateInspector` (`EidasCertificateInspectorTest`) e informes específicos de CMS en `CmsInspectionReport` (`CmsInspectionReportTest`), pero no se ha localizado un exportador Markdown/PDF del diagnóstico de cadena completo.

## 11. AES DUKPT — hecho

`AesDukpt` implementa la derivación y operaciones; `PaymentsController` las expone en UI (`AesDukptTest`). La entrada pendiente del roadmap debe actualizarse para no reabrir esta funcionalidad.
