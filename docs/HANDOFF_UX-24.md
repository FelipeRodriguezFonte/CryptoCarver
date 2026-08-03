# UX-24 — handoff

## Alcance auditado

Se revisaron únicamente validaciones recuperables en `ASN1Controller`, `CmsInspectorController`, `JOSEController`, `AsicController` y `PadesController`. Las rutas que tienen un campo real de corrección ahora construyen `UserFacingError` mediante `InlineValidationSupport`, que delega en el `StatusReporter` compartido.

## Rutas migradas

- ASN.1: entrada vacía, entrada inválida, DER estricto inválido y fallos de parseo/encode; el destino es `asn1InputArea` o `asn1EncodeInputArea`.
- CMS: entrada CMS ausente, contenido detached ausente y exportación sin informe; los destinos son `cmsInputArea` y `cmsContentArea`.
- JOSE: entradas obligatorias de JWT/JWE/JWS detached/nested, conversión JWK/PEM, thumbprint, inspección vacía y algoritmo de rotación; cada ruta usa el `fieldKey` de su control FXML.
- ASiC-S/ASiC-E: archivos de entrada/salida, payloads, alias PKCS#11, mimetype no compatible y límite de tamaño; se resaltan los campos de ruta, payload o selector correspondientes.
- PAdES: PDF de entrada/salida, PKCS#12, truststore, alias PKCS#11, coordenadas de firma visible, límite de tamaño y exportación de informes sin validación previa.

La validación numérica de firma visible conserva ahora el control exacto que falló: página, X, Y, ancho, alto o texto; ningún error de coordenadas se redirige automáticamente a la página.

Los mensajes mantienen las causas específicas, cambian en vivo entre ES/EN y se redactan antes de presentarse. Los datos técnicos de TR-31, CMS/ASN.1, JOSE, ASiC y PAdES no se transforman.

## Rutas conscientemente excluidas

- Confirmaciones de seguridad e información, incluidos avisos de claves simétricas en JWKS.
- Selectores nativos de archivos/directorios y errores propios de carga del diálogo.
- Excepciones criptográficas profundas o de proveedores, cuando no existe un único campo que el usuario pueda corregir de forma fiable.
- Exportaciones, ediciones y diagnósticos que no tienen una validación previa clara; conservan su comportamiento legacy para no ocultar el resultado técnico.

## Pruebas

- `Ux24HeadlessTest`: claves ES/EN, fallback de i18n, redacción, `fieldKey` de ASN.1/ASiC/PAdES, ausencia de feedback genérico y preservación de key block TR-31, hash y bytes.
- `Ux24HeadlessTest.padesCoordinateValidationKeepsTheSpecificInvalidField`: regresión para los cinco campos numéricos de firma visible.
- `Ux24LegacyValidationLiveUITest` (`@Tag("ui")`, `runUiTests=true`): carga `asn1.fxml` y `ASN1Controller` reales, ejecuta la ruta de entrada vacía y verifica el contrato compartido y `asn1InputArea`.
- `mvn -q -Dtest=Ux24HeadlessTest test`.
- `mvn -q test`.
- `git diff --check`.
- No se modificó FXML; por ello no se requiere `xmllint` en esta iteración.

## Limitación UI/Xvfb

La prueba JavaFX es opt-in y no se ejecutó localmente; requiere `-DrunUiTests=true` con el workflow Xvfb existente. La suite normal de Maven continúa sin hacer obligatorias las pruebas UI.

## Deuda restante

- Las excepciones profundas todavía pueden producir mensajes legacy porque no tienen siempre un control de corrección inequívoco.
- Queda pendiente una primera ejecución remota del workflow Xvfb y ampliar la cobertura UI real a CMS y JOSE.
- Conviene continuar la auditoría de validaciones de exportación y diálogos legacy fuera del alcance limitado de UX-24.

No se ha creado ningún commit ni se ha hecho push.
