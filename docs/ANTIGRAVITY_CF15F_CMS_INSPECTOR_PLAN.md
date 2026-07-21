# CF-15F — Inspector CMS/PKCS#7 y validación explicable

## Objetivo

Añadir una herramienta de **inspección y validación explicable de CMS/PKCS#7**. Debe permitir abrir datos Base64, DER o PEM/armored CMS y explicar qué contiene antes de intentar firmar, descifrar o confiar en él.

Este bloque se limita a CMS/PKCS#7. **No debe presentarse como CAdES, eIDAS ni validación de confianza completa** si no se implementan los perfiles y políticas correspondientes.

## Alcance funcional

### 1. Servicio puro: `CmsInspector`

Crear `com.cryptoforge.crypto.CmsInspector` (o un nombre equivalente coherente) sin dependencia JavaFX.

Entrada:

- `byte[]` CMS DER.
- Helpers de frontera para decodificar Base64/PEM, con mensajes claros si el artefacto no es CMS válido.
- Para `SignedData` detached, aceptar opcionalmente los bytes originales para poder evaluar la integridad/firma; no inventar una validación cuando falten.

Salida: modelo inmutable tipado, por ejemplo `CmsInspectionReport`, que incluya:

- Tipo CMS detectado: `SignedData`, `EnvelopedData`, `AuthenticatedData`, `CompressedData` u otro OID no soportado/identificado.
- OID de content type y nombre legible cuando se conozca.
- Estado de contenido: encapsulado, detached, no aplicable o desconocido. Incluir tamaño únicamente si el contenido está disponible.
- Algoritmos de digest, firma, cifrado de contenido y transporte/acuerdo de clave cuando existan.
- Firmantes: SID (issuer/serial o SKI), algoritmo, atributos firmados y no firmados (OID, nombre conocido, resumen seguro), y certificado coincidente si está incluido.
- Destinatarios: tipo de recipient info, identificador seguro (issuer/serial o SKI) y algoritmo de transporte/acuerdo; nunca claves ni secretos.
- Certificados incluidos: Subject, Issuer, serial, vigencia, algoritmo de clave, huella SHA-256, KeyUsage/EKU de forma resumida. No emitir PEM completo por defecto.
- Advertencias tipadas: certificados ausentes, firma detached sin datos aportados, algoritmo no reconocido, atributo malformado, contenido no soportado, etc.

El parser debe ser defensivo: límite explícito de tamaño de entrada de laboratorio (por ejemplo 16 MiB), colecciones acotadas razonablemente y errores convertidos en mensajes de dominio. No usar `toString()` de objetos de BC como informe principal.

### 2. Validación explicable de `SignedData`

Sin reescribir `CMSOperations.verifySignedData` de golpe, crear una capa que produzca pasos de validación independientes:

1. **Estructura CMS:** parseable y tipo esperado.
2. **Contenido:** encapsulado o detached; si es detached y no hay bytes, resultado `NOT_EVALUATED`.
3. **Firma/integridad:** `VALID`, `INVALID`, `NOT_EVALUATED` o `ERROR` por cada firmante.
4. **Certificado del firmante:** presente/no presente, coincidencia de SID y vigencia a la hora actual; esto no es trust chain.
5. **Cadena de confianza:** sólo si el usuario facilita un truststore/perfil. Si no hay truststore, `NOT_EVALUATED`, nunca `TRUSTED`.

Representar cada paso con estado estable: `VALID`, `INVALID`, `NOT_EVALUATED`, `WARNING`, `ERROR`. El texto visual no sustituye el estado estructurado.

La validación de truststore debe utilizar `PKIX` de Java y explicar el fallo resumido. No descargar AIA/OCSP/CRL, no acceder a red y no afirmar LTV.

### 3. Interfaz moderna

Añadir dentro de **Keys → CMS Operations** una pestaña o panel claramente llamado **Inspect / Validate CMS**. No modificar ni romper los flujos existentes Sign/Verify/EnvelopedData.

Controles mínimos:

- Área de entrada CMS y selector `Base64 | PEM/armor | DER file`.
- Botón **Load CMS file…** con `FileChooser`; límite de 16 MiB y sin persistir rutas sensibles.
- Área opcional de datos detached (texto/Base64 según selector ya visible) y checkbox/selector que indique explícitamente que se aporta contenido original.
- Truststore opcional: ruta + selector de fichero + contraseña no persistida. Dejar claro que se usa sólo para validar confianza, no para descifrar.
- Botones separados: **Inspect CMS** y **Validate SignedData**.
- Tabla o vista estructurada: resumen, firmantes, destinatarios, certificados, atributos y pasos de validación. Los valores largos deben abrirse con el visor expandido existente; no añadir otro popup ad-hoc.
- Exportación de un informe JSON/Markdown sin datos secretos. El informe puede incluir certificados públicos y huellas, pero no claves, contraseñas ni el contenido completo encapsulado por defecto.

Publicación:

- El controlador debe publicar una única `OperationResult` mediante `StatusReporter.publish(...)` por operación exitosa.
- Historial e inspector guardan sólo resumen y detalles públicos. Entrada/salida completa sólo bajo la política de visibilidad de laboratorio ya existente; nunca contraseña de truststore.

Arquitectura:

- Si el panel queda grande, extraer a `cms_inspector.fxml` y `CmsInspectorController` mediante `<fx:include>`, siguiendo ASN.1/JOSE/OpenPGP.
- No ampliar `ModernMainController` con lógica criptográfica ni con delegados innecesarios.
- Añadir el registro de operación al `OperationRegistry` y regenerar `docs/OPERATIONS_CATALOG.md`.

## Pruebas obligatorias

### Servicio

Crear `CmsInspectorTest` independiente de JavaFX con fixtures construidos localmente mediante Bouncy Castle:

- `SignedData` encapsulado firmado: tipo, firmante, certificado, algoritmo y firma `VALID`.
- `SignedData` detached sin contenido: estructura válida y firma `NOT_EVALUATED`.
- `SignedData` detached con contenido correcto: firma `VALID`; contenido alterado: `INVALID`.
- CMS con certificado no incluido: advertencia explícita y sin falso `VALID` de certificado.
- `EnvelopedData` para uno y dos destinatarios: lista de destinatarios y algoritmo de contenido; el inspector no intenta descifrar.
- DER corrupto/no CMS y payload superior al límite: fallo controlado.
- Truststore local temporal: cadena válida produce `VALID`; truststore sin CA produce `INVALID`/`ERROR` explicado; nunca resultado `TRUSTED` cuando pasos de firma/certificado fallan.

No usar TSA, internet, GnuPG ni SoftHSM en estas pruebas.

### FXML/UI

- Añadir el FXML nuevo (si se extrae) a `ModernMainControllerFxmlStaticTest`.
- Añadir una prueba de carga/inyección específica que compruebe controles y handlers principales.
- Una prueba de controlador con `StatusReporter` falso debe confirmar una publicación atómica tras inspección correcta y que la contraseña de truststore no aparece en `OperationResult`, historial ni mensajes de error.

### Verificación

Ejecutar y pegar resultados reales:

```bash
mvn -q -Dtest=CmsInspectorTest,CMSOperationsTest,ModernMainControllerFxmlStaticTest test
mvn -q -DrunUiTests=true -Dtest=ModernMainControllerUITest#<nuevo_test> test
xmllint --noout src/main/resources/fxml/main-view-modern.fxml [src/main/resources/fxml/cms_inspector.fxml]
git diff --check
```

Ejecutar `mvn -q test` completo antes del handoff. Si UI global falla por el timeout conocido de JavaFX, documentar el comando, el fallo literal y demostrar los tests aislados; no ocultarlo ni cambiar timeouts indiscriminadamente.

## Fuera de alcance

- CAdES-BES/EPES/T/LT/LTA, validación eIDAS o afirmaciones de conformidad.
- Descarga de OCSP, CRL, AIA o cualquier llamada de red.
- Descifrado automático dentro del inspector.
- Persistencia de contraseña de truststore o contenidos/secretos en histórico.
- Refactor amplio de todos los flujos CMS existentes.

## Criterios de aceptación

1. Un CMS SignedData o EnvelopedData válido se inspecciona sin claves privadas y muestra datos estructurados públicos.
2. La pantalla diferencia de forma inequívoca `VALID`, `INVALID` y `NOT_EVALUATED`; un detached sin datos no se marca como válido.
3. La confianza PKIX sólo se indica como válida cuando se suministra truststore y todos los pasos previos lo permiten.
4. Ninguna contraseña, clave privada ni contenido completo se incluye en informes/histórico por defecto.
5. El FXML moderno arranca y los flujos Sign/Verify/EnvelopedData existentes continúan funcionando.
6. Todos los tests obligatorios pasan y el catálogo de operaciones queda regenerado.

## Entrega requerida de Antigravity

No continuar a otro bloque sin entregar `docs/HANDOFF_CF15F.md` con:

- alcance terminado y elementos explícitamente pospuestos;
- archivos modificados/nuevos y motivo;
- decisiones de modelo/compatibilidad;
- resultados literales de pruebas y recorrido manual;
- riesgos conocidos, especialmente diferencias entre validez criptográfica y confianza PKIX;
- instrucciones de revisión paso a paso para Codex.
