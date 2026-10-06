# Encargo 73 — JOSE: informe final

## Resultado

Las cuatro fases están implementadas y sus doce puertas finales pasan limpias. Se trabajó exclusivamente en `/Users/feliperodriguezfonte/dev/CryptoCarver-jose-5`, rama `codex/jose-capabilities-2`, desde `5f6dfcbe89011bfe808f12fd4c1b4fa5dd92d7aa`. No hubo merge, push ni cambios de rama. Antes de los comandos se comprobó directorio y rama; Maven se ejecutó de uno en uno.

Ejecución local: macOS aarch64, Maven 3.9.11 y **JDK 25 según `mvn -version`**. Se ejecutaron las opciones del CI; no se afirma una ejecución remota en Linux/Java 17. Los digests excluyen material generado, fechas, series, huellas, rutas, tiempos, salidas aleatorias, mensajes de proveedores y órdenes de iteración sin definir. Las razones PKIX se convierten a códigos propios estables.

## Estado por fase y capacidad

| Fase | Capacidad | Estado | Evidencia / motivo |
|---|---|---|---|
| 1 | JWK RSA privado, incluidos d/p/q/dp/dq/qi | hecha | Conversión real, resultado SECRET y capturas protegidas en MASKED/REDACTED |
| 1 | JWK oct, campo k | hecha | Material sintético; Copy/Shelf/Expand y detalles públicos comprobados |
| 1 | PEM privado obtenido de JWK | hecha | Clasificación SECRET, errores normalizados y perfiles comprobados |
| 1 | JWKS privado/simétrico; generación, rotación y exportación pública | hecha | Editor protegido, memoria del coordinador y exportación pública operativa |
| 1 | Inspector privado, cambio FULL_LAB a perfil restringido | hecha | Copy Report/Expand Report vuelven a consultar el perfil |
| 1 | Recetas nuevas de historial JWKS | hecha | Campo interno jwksSecretArea se redacta sin modificar UiStateSnapshot |
| 1 | Reapertura de recetas JWKS con el identificador anterior | parcial | Compatibilidad interna documentada abajo; hay que volver a pegar el JWKS |
| 2 | Selector Ed25519/Ed448/X25519/X448 y uso sig/enc | hecha | Sincronización curva/algoritmo; se preservan variantes ECDH-ES+KW |
| 2 | Ed448 JWT/JWS e interoperabilidad JDK | hecha | JDK verifica JOSE y JOSE verifica firma JDK |
| 2 | Ed448 rotación JWKS y JWK↔PEM | hecha | Ambos kid verifican; thumbprint conservado en conversión |
| 3 | PKIX contra una o varias anclas PEM y fecha | hecha | PKI inventada raíz/intermedia/hoja; fecha fija y actual por defecto |
| 3 | Diagnósticos de cadena, firmas, vigencia, CA/longitud y uso | hecha | Casos válidos y negativos, firmas por eslabón y hacia el ancla |
| 3 | Clave de hoja y x5t/x5t#S256 | hecha | Coincidencias y discrepancias comprobadas por separado |
| 3 | Clave x5c explícita con cadena inválida/sin anclas | hecha | Desactivada por defecto; firma del token posible con aviso público no modal EN/ES |
| 3 | Política sin red y revocación desactivada | hecha | CertPathValidator con camino local; sin Builder, descarga, OCSP ni CRL remotas; resultado DISABLED |
| 4 | Cabeceras propias, kid/typ/cty/x5c/x5t/x5t#S256/jwk pública | hecha | Helper compartido con normal; integridad y roundtrip comprobados |
| 4 | b64=false y crit=[b64] | hecha | Casilla de composición; alg/b64/crit siguen reservadas en JSON |
| 4 | Compact, Flattened y General JSON detached | hecha | Payload externo, verificación y selección JWKS por kid |
| 4 | Captura del editor de cabecera y JWK privada embebida | hecha | Editor conservador SECRET; privada/oct rechazadas sin material en error ni captura |

No se saltó ninguna capacidad nueva solicitada por dependencias o ficheros prohibidos.

## Fase 1: fugas reproducidas antes del arreglo

**Sí había fuga.** Los tests dedicados se ejecutaron con JavaFX, FXML JOSE y las clases reales de captura/presentación del shell.

1. JWK JSON privado se clasificaba PUBLIC por el fallback de `jwkOutputArea`: el test obtuvo `ShelfBlocked=false`. Copy y Shelf resolvían el JSON crudo. Las operaciones tampoco publicaban un resultado clasificado.
2. El inspector conservaba un informe privado al cambiar FULL_LAB a MASKED. Falló `changing profile must protect previously rendered reports`; Copy Report y Expand Report consumían ese texto.
3. La receta de historial conservaba JWKS privado tras cambiar el perfil. Falló `history recipe retained private JWKS after profile change`: el nombre jwksArea no activaba la política de campo secreto.

Cada fuga tiene su test en rojo y su commit de arreglo independiente. Se caracterizan RSA CRT, oct k, PEM y JWKS privado/simétrico en EN/ES y los tres perfiles. Copy completo/selección, acceso nativo Copy/Cut/contexto, Expand, Add to Shelf, receta de historial, presentación del historial/inspector, barra de estado y ausencia de material en detalles públicos de telemetría están cubiertos. FULL_LAB conserva el material y las operaciones actuales; MASKED/REDACTED protegen las capturas. Se usan ResultCaptureCoordinator, ResultViewerCoordinator, ResultPresentationPolicy y OperationInspectorPresenter reales.

Los fixtures guardan/restauran idioma y perfil de AppSettings, comprueban que el Shelf no cambie y vacían su historial local. No modifican el historial del usuario.

## Vectores y respaldo

| Capacidad | Respaldo |
|---|---|
| Privacidad JWK/JWKS | Inputs inventados con sintaxis de [RFC 7517 A.2/A.3](https://www.rfc-editor.org/rfc/rfc7517.html#appendix-A) y tratamiento del material privado de [§9.2](https://www.rfc-editor.org/rfc/rfc7517.html#section-9.2) |
| OKP/Ed25519/XDH existentes | [RFC 8037 §§2–3 y apéndice A](https://www.rfc-editor.org/rfc/rfc8037.html#appendix-A): EdDsaJwsTest conserva A.1/A.4 y JoseCapabilitiesTest conserva XDH A.6/A.7; puertas generales limpias |
| Ed448 | No hay vector JOSE oficial Ed448 en ese apéndice. Ida y vuelta, firma JWT/JWS, rotación y Signature Ed448 del JDK en ambos sentidos |
| x5c PKIX | [RFC 7515 §4.1.6–4.1.8](https://www.rfc-editor.org/rfc/rfc7515.html#section-4.1.6), PKI inventada propia y [PKIXParameters Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/cert/PKIXParameters.html); fechas y restricciones negativas controladas |
| JWS detached | Vector exacto [RFC 7797 §4.2](https://www.rfc-editor.org/rfc/rfc7797.html#section-4.2), control §4.1 y clave pública del ejemplo RFC 7515 A.1; Mac del JDK independiente; [RFC 7515 apéndice F](https://www.rfc-editor.org/rfc/rfc7515.html#appendix-F) |

PKIX informa formación, firmas por eslabón, autofirma de raíz, firma hacia el ancla, vigencia, CA/longitud, uso de firma de hoja, clave verificadora, ambas huellas y confianza PKIX. Los detalles son públicos. Una cadena inválida no bloquea la opción explícita de verificar con la clave de x5c: el resultado añade `Security warning` y el aviso aparece en findings, sin modal.

## Textos añadidos (EN / ES)

Claves al final de los bundles base/en/es, con paridad comprobada. Se incluyen avisos, errores y etiquetas nuevos para dejar los textos completos.

| Clave | EN | ES |
|---|---|---|
| `module.jose.jwkPrivateHidden` | Private JWK material is hidden by the visibility profile. Copy, Expand and Shelf are protected. | El material privado de JWK está oculto por el perfil de visibilidad. Copy, Expand y Shelf están protegidos. |
| `module.jose.jwkResultReady` | JWK result ready. | Resultado de JWK preparado. |
| `module.jose.jwkInvalidMaterial` | Invalid or unsupported key material. | Material de clave no válido o no compatible. |
| `module.jose.okpCurve` | OKP curve: | Curva OKP: |
| `module.jose.x5cAnchors` | x5c trust anchors (PEM): | Anclas de confianza x5c (PEM): |
| `module.jose.x5cDate` | Certificate validation date (ISO-8601, empty = now): | Fecha de validación de certificados (ISO-8601, vacío = actual): |
| `module.jose.x5cDateInvalid` | Enter a valid ISO-8601 certificate validation date. | Introduce una fecha ISO-8601 válida para los certificados. |
| `module.jose.warning.x5cUntrusted` | Warning: x5c has no valid PKIX trust or certificate binding. Explicit header-key verification is enabled. Revocation is disabled; no network access. | Aviso: x5c no tiene confianza PKIX o vinculación de certificado válida. Está habilitada la verificación explícita con la clave de cabecera. La revocación está desactivada; sin acceso a la red. |
| `module.jose.detachedB64Title` | JWS unencoded payload (b64=false) | Payload JWS sin codificar (b64=false) |
| `module.jose.detachedB64Warning` | Warning: b64=false signs the exact external payload bytes. Preserve the payload without changes. | Aviso: b64=false firma los bytes exactos del payload externo. Conserva el payload sin cambios. |
| `module.jose.detachedHeaderInvalid` | Invalid protected header. alg, b64 and crit are reserved; embedded jwk must be public. | Cabecera protegida no válida. alg, b64 y crit están reservadas; la jwk embebida debe ser pública. |
| `module.jose.detachedHeaderCaptureHidden` | Header editor capture is disabled by this visibility profile. Public headers remain available in the generated token. | La captura del editor de cabecera está desactivada por este perfil de visibilidad. Las cabeceras públicas siguen disponibles en el token generado. |

## Digests completos verificados

Todas las suites de esta tabla aparecen en los informes de la última puerta UI, con tests ejecutados y cero omitidos/fallos/errores. Los hashes existentes se mantuvieron. Las nuevas transcripciones se fijaron después de pasar las aserciones funcionales y se volvieron a verificar en las puertas.

| Suite / transcripción | SHA-256 | Estado |
|---|---|---|
| JoseCapabilitiesCharacterizationUITest | `bc36301eed279b7a7c7a83ac29214da2dcd1fc9e79eb3a52e034261dea5e64db` | verificado |
| JoseDetachedHeadersCharacterizationUITest | `325d384b8f4f546853aabf90b2fa0307f054ec61c3f87752db2edff1ae31693a` | verificado |
| JoseInspectorCharacterizationUITest | `89c07b84cea4a9a478881fd369a1b47796a4d7a5d2008165cddf341518c261da` | verificado |
| JoseInspectorPrivacyCharacterizationUITest | `c49261434dbb4b22f6fd78dff1d4782037eafeecb7e236cb109d4e754eeede72` | verificado |
| JoseJweCharacterizationUITest (1) | `898aa2dc9da06352c580966e0a54dee58d8db86d94ea2bcc3febe5beb7d42401` | verificado |
| JoseJweCharacterizationUITest (2) | `784d6a61e011dee39d8c2c5f6798bd4a29484f2fbc86d8f041d4e39ed69272fd` | verificado |
| JoseJwkCharacterizationUITest | `315e6810e2bc477b3f4de37ff4bc6c1b81acc057f1b4a5bbebb5f3e8a88c9d1a` | verificado |
| JoseJwkPrivacyCharacterizationUITest | `3bd9891728349fad323eee284024eeaa03bf7a8548efe970a0bd41968d6d344c` | verificado |
| JoseJwtCharacterizationUITest (1) | `526ed1435a5950126aae7065abbaeee3b6c9bb81c8cee77c3a4841e7e599537e` | verificado |
| JoseJwtCharacterizationUITest (2) | `0776e413f48a43f8d7afa8f0e4dadee462ef2aae969b4fb41d71e268fc7b499a` | verificado |
| JoseOkpCharacterizationUITest | `3794f33085d21f997300f67535bdd7e731e7729e0573e82141fc16af1adc76b1` | verificado |
| JosePkixCharacterizationUITest | `838c59482ac00cae7c56211e3f4d4029058c7aee35bd8c27819ab2b2fa8fad64` | verificado |

## Las tres puertas por fase

Comandos exactos, en este orden, cada fase por separado:

1. `mvn -o -q test -Plow-cpu`
2. `mvn -o -q test -Plow-cpu -DrunUiTests=true`
3. `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test`

Se borró target/surefire-reports antes de cada puerta y se contaron solamente sus XML TEST. Las ejecuciones dirigidas/en rojo y la puerta preliminar antes del arreglo de coherencia de fase 2 están anotadas en los mapas, fuera de estos recuentos finales. No fue necesario aplicar la excepción de ExpandedViewerLifecycleUITest.

| Fase | Puerta | Informes | Tests | Fallos | Errores | Omitidos | Resultado |
|---|---|---:|---:|---:|---:|---:|---|
| 1 | 1 | 440 | 2899 | 0 | 0 | 1 | limpia |
| 1 | 2 | 122 | 538 | 0 | 0 | 0 | limpia |
| 1 | 3 | 122 | 538 | 0 | 0 | 0 | limpia |
| 2 | 1 | 441 | 2900 | 0 | 0 | 1 | limpia |
| 2 | 2 | 123 | 539 | 0 | 0 | 0 | limpia |
| 2 | 3 | 123 | 539 | 0 | 0 | 0 | limpia |
| 3 | 1 | 443 | 2906 | 0 | 0 | 1 | limpia |
| 3 | 2 | 124 | 540 | 0 | 0 | 0 | limpia |
| 3 | 3 | 124 | 540 | 0 | 0 | 0 | limpia |
| 4 | 1 | 445 | 2911 | 0 | 0 | 1 | limpia |
| 4 | 2 | 125 | 541 | 0 | 0 | 0 | limpia |
| 4 | 3 | 125 | 541 | 0 | 0 | 0 | limpia |

Los logs y JSON de recuento locales están en target/jose-cap-{4,5,6,7}-gate-{1,2,3}.*; target está ignorado. Los recuentos duraderos están también en los mapas 4, 5, 6 y 7.

## Alcance e higiene

- crypto/ tocado: **JOSEService.java, JoseJwkPolicy.java y la clase nueva JoseX5cValidation.java**. Todos autorizados. CertificateGenerator solo se llama para parsear certificados; no se modifica. CmsInspector no se modifica.
- pom.xml sin cambios ni dependencias nuevas. ModernMainController, UiStateSnapshot, StatusReporter y OperationResult intactos.
- JOSEController: **1063 → 1055 líneas**, firmas de métodos públicos comparadas con la base y sin cambios. Los cambios de lógica están en coordinadores; el controlador mantiene campos y delegados.
- Tests existentes: JoseJwkCharacterizationUITest cambia solo tres búsquedas de namespace por el id jwksSecretArea; conserva aserciones y digest. El test de privacidad se adapta al View ampliado en fase 2, también sin cambiar su digest. No se relaja ningún umbral.
- Comandos del job quality-gates de .github/workflows/ui-tests.yml ejecutados tras los cambios de fuente: **estilos en línea FXML 0/0**, **emojis 325/325**. Rangos contados idénticos al job; sin nuevos emojis ni style="...".
- Sin imágenes, .local.md, certificados/claves de producción ni modificaciones fuera del worktree autorizado.

## Límites y compatibilidad

- Las recetas antiguas con la clave interna JOSEController.jwksArea requieren volver a pegar ese JWKS. El cambio a jwksSecretArea es necesario para redactar las recetas nuevas con la política existente; se documentó en el mapa 4 y no se modificó el modelo prohibido para migrarlas. Los contenidos y operaciones actuales en FULL_LAB se conservan.
- General JSON detached conserva la verificación de una firma del algoritmo seleccionado; no representa una política de exigir todas las firmas. Se comprueban ambos algoritmos individualmente en el test de dos firmas.
- alg=none conserva la limitación de cabeceras personalizadas del compositor normal. Las JWK embebidas deben ser públicas: privado/oct se rechazan. El editor nuevo se protege conservadoramente en perfiles restringidos; la cabecera pública sigue disponible en el token generado.
- La cadena se suministra en orden hoja→emisores; no se buscan intermediarias ni se descargan x5u/jku. Revocación desactivada por el alcance solicitado.

## Rama y commits

Rama `codex/jose-capabilities-2`, limpia antes de añadir este informe. El commit de cierre `docs(jose): close capability 73 with verified report` contiene únicamente este documento; tras él se comprueba de nuevo git status --porcelain vacío. No hay merge ni push.

Commits de trabajo/verificación desde la base, en orden:

```text
f4587946 docs(jose): map JWK privacy characterization
e09a0427 test(jose): reproduce unclassified private JWK capture
3d856c91 fix(jose): protect private JWK results and JWKS editor capture
d606ecc4 test(jose): reproduce private inspector report capture
d4a70efd fix(jose): protect private inspector reports during capture
b9e78c67 test(jose): reproduce private JWKS history recipe capture
c8c28b68 fix(jose): redact the JWKS editor in history recipes
f77bfde5 docs(jose): record verified JWK privacy gates
e9300d4b docs(jose): map OKP curve generation and Ed448 coverage
314cc853 test(jose): reproduce missing OKP curve selection
25311e29 feat(jose): select OKP curves and verify Ed448 interoperability
029f932d fix(jose): keep OKP curve and rotation algorithm coherent
f9bf51fc docs(jose): record verified OKP and Ed448 gates
52c28fe6 docs(jose): map offline x5c PKIX validation
d2327bd9 test(jose): reproduce missing PKIX controls and trust result
8e0c4817 feat(jose): validate x5c paths offline with explicit trust warnings
a9883948 docs(jose): record verified offline PKIX gates
4df0e51d docs(jose): map protected headers for detached JWS
f3b8f379 test(jose): reproduce missing detached protected header editing
ced3d4cd feat(jose): edit detached protected headers with privacy safeguards
478453fd docs(jose): record verified detached header gates
```

El commit de cierre se identifica por su título arriba; su SHA no se incrusta en su propio contenido. La lista completa, incluido ese commit, se obtiene con `git log --oneline --reverse 5f6dfcb..HEAD`.
