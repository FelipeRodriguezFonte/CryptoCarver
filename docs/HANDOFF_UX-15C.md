# HANDOFF_UX-15C

## Alcance

Se internacionalizaron progresivamente los módulos especializados:

- Post-Quantum (PQC): generación de claves, firma/verificación, KEM educativo y benchmark.
- XML Security / XAdES: firma, inspección, RFC 3161 y verificación.
- WSS Security: firma/verificación SOAP, UsernameToken, cifrado y descifrado.
- JOSE / JWT: JWT, JWS detached, JWK/JWKS, JWE, nested tokens e inspector.

Se reutilizaron `I18nService`, `ModuleI18n` y `ModuleTextCatalog`. No se creó infraestructura adicional. Las vistas ya existentes tenían raíces FXML identificables, por lo que no fue necesario modificar los FXML: se añadieron bindings sobre sus `Accordion`/raíz desde los controladores. `ModuleI18n` sigue siendo el responsable de los textos estáticos y `I18nService` de feedback creado dinámicamente.

## Archivos cambiados

- `src/main/java/com/cryptocarver/ui/ModuleTextCatalog.java`
- `src/main/java/com/cryptocarver/ui/PostQuantumController.java`
- `src/main/java/com/cryptocarver/ui/XMLSignatureController.java`
- `src/main/java/com/cryptocarver/ui/WssSecurityController.java`
- `src/main/java/com/cryptocarver/ui/JOSEController.java`
- `src/main/resources/i18n/messages.properties`
- `src/main/resources/i18n/messages_es.properties`
- `src/test/java/com/cryptocarver/ui/SpecializedI18nTest.java`
- `src/test/java/com/cryptocarver/ui/SpecializedLiveI18nUITest.java`
- `docs/HANDOFF_UX-15C.md`

También se corrigió una inconsistencia previa en `ModuleTextCatalog`: `Algorithm` y `Algorithm:` ya no comparten la misma clave; `module.common.algorithmPlain` conserva el texto sin dos puntos.

No se modificaron Payments/EMV/TR-31, OpenPGP, PAdES, ASiC ni diálogos globales legacy.

## Preservación técnica

- Algoritmos, OID, claims, estándares, aliases, IDs y nombres de protocolo permanecen literales.
- JWT/JWS/JWE, JSON, XML, XAdES, SOAP, PEM, certificados, hashes, bytes, HEX y Base64 no pasan por traducción.
- Los mensajes traducidos usan valores técnicos como argumentos o mantienen el contenido técnico en las áreas de resultado.
- No se tocaron formatos de entrada/salida, recetas, histórico, secretos ni semántica criptográfica.
- Los errores accionables de entrada de XML y WSS tienen claves ES/EN específicas.

## Pruebas y verificaciones

Pasó (`exit 0`):

```text
/opt/homebrew/bin/mvn -q -Dtest=SpecializedI18nTest,SpecializedLiveI18nUITest,I18nServiceTest,ModuleTextCatalogTest,ModernMainControllerFxmlStaticTest,ModernMainControllerI18nFxmlTest test
```

La salida contiene únicamente los avisos esperados de las pruebas de fallback (`missing.ux15c.key`, bundles no disponibles y `still.safe`).

La prueba adicional de cobertura completa de catálogos también pasó (`exit 0`):

```text
/opt/homebrew/bin/mvn -q -Dtest=SpecializedI18nTest test
```

Pasó (`exit 0`) la ejecución focal con regresiones criptográficas relevantes:

```text
/opt/homebrew/bin/mvn -q -Dtest=SpecializedI18nTest,SpecializedLiveI18nUITest,I18nServiceTest,ModuleTextCatalogTest,ModernMainControllerFxmlStaticTest,ModernMainControllerI18nFxmlTest,PostQuantumOperationsTest,PQCBenchmarkTest,XMLSignatureOperationsTest,JoseEcInteroperabilityTest,JwsJsonInteropTest,NestedJwtInteropTest,Wss4jInteropTest,WssEncryptionOperationsTest,WssSecurityOperationsTest,WssUsernameTokenOperationsTest test
```

La salida literal incluye avisos de JavaFX de configuración no modular, diagnósticos esperados de fallback y mensajes de pruebas criptográficas; no hubo fallos ni errores de Maven.

Pasó (`exit 0`) para cada FXML del bloque:

```text
xmllint --noout src/main/resources/fxml/pqc.fxml
xmllint --noout src/main/resources/fxml/xml_security.fxml
xmllint --noout src/main/resources/fxml/wss_security.fxml
xmllint --noout src/main/resources/fxml/jose.fxml
```

Pasó (`exit 0`):

```text
git diff --check
```

Git imprime además el diagnóstico de entorno ya presente:
`error: fsmonitor_ipc__send_query: unspecified error on '.git/fsmonitor--daemon.ipc'`.

## Limitaciones JavaFX

`SpecializedLiveI18nUITest` está marcado como UI opt-in con `-DrunUiTests=true`. En este entorno sin pantalla, las pruebas JavaFX pueden fallar antes de cargar FXML por `Graphics Device initialization failed`, `no suitable pipeline found` o `No toolkit found`, debido al pipeline Prism y a la caché local no escribible. La cobertura sin pantalla valida resolución, fallback, artefactos y XML.

## Deuda para UX-15D

- Completar diálogos globales legacy y feedback secundario que aún conservan textos literales fuera del alcance de estos cuatro módulos.
- Revisar textos generados en ventanas auxiliares de JOSE/WSS/XML que se abren fuera de la raíz FXML principal.
- Migrar Payments/EMV/TR-31, OpenPGP, PAdES y ASiC.
- Ejecutar la revisión visual en una sesión JavaFX con pantalla y comprobar accesibilidad/tooltips de los estados dinámicos.

No se hizo commit.
