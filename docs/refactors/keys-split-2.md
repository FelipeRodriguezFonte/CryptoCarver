# KeysController: extracción de la fase 2

Base `main` / `6f86a9b`. Implementación directa con Sol en `luna/split-keys-2`, sin push y sin subagentes. Se extraen generación asimétrica, KDF/key wrap, CMS y validación de cadenas. La fachada conserva los contratos públicos y los handlers FXML; las operaciones y sus informes mantienen el comportamiento caracterizado antes de mover producción.

## Commits

| Commit | Contenido |
|---|---|
| `a19a2e6` | Mapa previo de métodos, controles, contratos y dependencias |
| `5b79e42` | Caracterización previa ejecutada con producción intacta |
| `c4e2b4c` | Generación asimétrica: coordinador, informes puros y tests |
| `c04060a` | KDF/key wrap: coordinador, validación/procesamiento puro y tests |
| `a17ff26` | CMS: coordinador, procesamiento local/informes puros y tests |
| `79ca7a7` | Cadena: coordinador, parsing/validación/informes puros y tests |
| commit de este informe | Resultados finales y mapa actualizado de fase 3 |

## Caracterización previa

[Métodos, campos y contratos antes de la extracción](keys-split-2-map.md). [Salida y condiciones de la caracterización previa](keys-split-2-characterization.md).

```text
Maven exit 0
KeysSplit2CharacterizationUITest: tests=31, failures=0, errors=0, skipped=0, time=23.223 s
KeyDerivationTest: tests=4, failures=0, errors=0, skipped=0, time=0.003 s
KeyWrapOperationsTest: tests=3, failures=0, errors=0, skipped=0, time=0.002 s
ComputedStyleSnapshotTool: tests=1, failures=0, errors=0, skipped=0, time=11.628 s
```

Se cargó el FXML de producción con `Fxml.loader`, bajo `test.mode`, `user.home=target/test-home` y navegación perezosa. RSA usa el mínimo ofrecido por la pantalla. La espera comprueba el executor en IDLE y el resultado publicado después de vaciar la cola FX; no usa pausas fijas. Los cambios de AppSettings se restauran. Cada caso cierra su Stage, desconecta la escena, llama shutdown y suelta referencias.

Los 31 casos cubren las cuatro generaciones, PEM/inspector/histórico/Shelf, los diez KDF, KW/KWP y KEK incorrecta, firma/verificación y cifrado/descifrado CMS, certificado asociado a otra clave, cadena completa/incompleta/caducada, configuración portable, redacción de secretos e idioma. La generación asimétrica no inserta entradas en Key Lab en el comportamiento original; no hay selector DER/JWK en estas pantallas. CMS verifica con el certificado incluido: el negativo incrusta un certificado cuya clave no firma los datos. La cadena incompleta omite la raíz; PKIX no exige que el PEM esté ordenado.

Los vectores preexistentes de crypto se reutilizan mediante KdfWrapTestVectors. Los oráculos adicionales y el límite del caso Argon2id están descritos en la caracterización: este último llama directamente a BouncyCastle y no constituye una validación independiente de esa biblioteca.

La misma caracterización de 31 casos pasó después de cada uno de los cuatro bloques. Las pruebas puras nuevas suman 32 invocaciones:

| Clase nueva | Casos | Comportamientos |
|---|---:|---|
| AsymmetricKeyGenerationLogicTest | 4 | Informes y reimportación PEM de RSA, DSA, EC y Ed25519 |
| KdfKeyWrapLogicTest | 15 | Diez vectores KDF, KW/KWP, errores de longitud/formato y KEK |
| CmsLogicTest | 8 | CMS/CAdES-BES, contenido encapsulado/separado, envelope, firma inválida y evidencia local |
| CertificateChainLogicTest | 5 | Cadena completa/incompleta/caducada y errores de entrada |

No se ajustaron tests existentes por reflexión o por el traslado de código. Los únicos existentes modificados son KeyDerivationTest y KeyWrapOperationsTest: sus literales se movieron a un fixture compartido, conservando valores y aserciones. En los tests nuevos de CMS se corrigieron dos expectativas durante la implementación: el nombre original del perfil es `CMS / PKCS#7`, y una firma con certificado distinto devuelve INVALID en vez de lanzar una excepción. La caracterización previa ya aceptaba ese comportamiento.

## Estilo y recortes

```text
ComputedStyleSnapshotTool, tema theme-light.css, ocho rutas y recorrido de tabs
Antes:   7430 líneas
Después: 7430 líneas
diff: exit 0, 0 líneas
ClippedTextAuditTool, locale es, tema light: TOTAL = 0
```

Las ocho rutas cubren los cuatro bloques: RSA, DSA, ECDSA, EdDSA, KDF, AES Key Wrap, CMS y cadena, incluyendo tabs de claves públicas/privadas y firma/cifrado CMS. La auditoría añade también el diálogo informativo. No se modificaron las herramientas, sus exclusiones ni las hojas de estilo. Los dos intentos iniciales de auditoría rechazaron nombres de rutas antes de inspeccionar pantallas: la herramienta acepta el primer alias ordenado de cada ruta. Se repitió con los aliases válidos y el resultado fue cero.

Comando de instantánea (antes con `styles-before.txt`, después con `styles-after.txt`):

```sh
mvn -o -q test -Dtest=ComputedStyleSnapshotTool \
  -DstyleSnapshotTheme=theme-light.css \
  '-DstyleSnapshotRoute=RSA Key Generation,ECDSA Key Generation,EdDSA Key Generation,DSA Key Generation,Key Derivation (KDF),AES Key Wrap,CMS Sign,Validate Chain' \
  -DstyleSnapshotTabs=true -DstyleSnapshotOut=target/keys-split-2/styles-after.txt
diff target/keys-split-2/styles-before.txt target/keys-split-2/styles-after.txt
```

Comando de auditoría:

```sh
mvn -o -q test -Dtest=ClippedTextAuditTool \
  -DclippedTextAuditOut=target/keys-split-2/clipped \
  -DclippedTextAuditLocale=es -DclippedTextAuditTheme=light \
  '-DclippedTextAuditRoutes=Generate RSA Key|ECDSA Key Generation|EdDSA Key Generation|DSA Key Generation|Derive Key|AES Key Wrap|CMS Decrypt|Certificate Chain'
```

Artefactos locales ignorados: `target/keys-split-2/styles-{before,after}.txt`, `styles.diff`, `clipped/es-light.txt` y logs de cada ejecución. No se generaron ni subieron imágenes. No se hizo prueba manual.

## Suite completa

```text
mvn -o -q test
Maven exit 0
Tests: 2643; failures: 0; errors: 0; skipped: 1
Clases: 381
Tiempo observado de la ejecución: ~287 s (4 min 47 s)
Suma de tiempos de clases de Surefire: 248.077 s
Java heap space / OutOfMemoryError: 0
```

La base tenía 2580 tests; se añaden 63 (31 UI y 32 puros). El omitido es Pkcs11SessionEncapsulationTest por el prerrequisito HSM. Solo se contabilizan los 381 XML generados después de iniciar esta ejecución; los reportes anteriores de las herramientas opt-in se excluyen. La caracterización también pasó dentro de la suite (31 casos, 21.668 s).

La suite completa se ejecutó una sola vez, con un único Maven activo y el heap de Surefire de 2 GB, sin low-cpu ni cambios en pom/.mvn. Tras la suite se retiró un import comodín sin uso del coordinador KDF y se comprobó compilación con `mvn -o -q -DskipTests compile`; no cambió comportamiento ni se repitió la suite.

## Distribución del código y contratos

| Clase | Líneas |
|---|---:|
| KeysController antes | 6061 |
| KeysController después | 4727 |
| AsymmetricKeyGenerationCoordinator | 357 |
| KdfKeyWrapCoordinator | 429 |
| CmsCoordinator | 436 |
| CertificateChainCoordinator | 64 |
| AsymmetricKeyGenerationLogic | 52 |
| KdfKeyWrapLogic | 318 |
| CmsLogic | 157 |
| CertificateChainLogic | 77 |
| KeysCoordinatorSupport | 27 |
| KeysInputValidation | 15 |

La fachada pierde 1334 líneas (22,0 %). Los 287 campos inyectados conservan nombres, tipos y orden. KDF/asimétrica reciben una vista mediante Supplier, y los callbacks resuelven el StatusReporter actual sin almacenar controladores de módulos. CMS/cadena conservan una vista de los controles entregados por sus contratos públicos: los campos FXML oficiales siguen en CertificatesController y UiStateSnapshot los visita allí. No hay dependencias nuevas de controladores incluidos.

El par y los summaries asimétricos compartidos permanecen en KeysController para export/Shelf/generación de certificados de fase 3; el coordinador actualiza ese estado mediante callback. Los tres MaterialFieldBadge de KDF pasan al coordinador. La lógica pura no importa JavaFX ni conoce FXML, controladores o AppSettings. En CMS quedan en el coordinador los efectos de PKCS#11, TSA/red, ajustes, executor y selección/lectura de ficheros; la evidencia seleccionada se interpreta en lógica pura. Se conserva el tratamiento original de `trustAnchorArea` como entrada CRL en el inicializador clásico.

No se modificó producción en crypto, FXML, CSS, pom.xml ni .mvn. El soporte común solo agrupa callbacks de shell; no introduce un servicio global ni duplica estado de módulos.

## Mapa resumido de fase 3

Rangos actuales de KeysController tras la extracción:

| Área pendiente | Localización | Campos y dependencias |
|---|---|---|
| Cableado, navegación, inicialización y servicios de shell | 226–1045, más contratos 1696–1988 | Controles FXML en cabecera; AppSettings/i18n, HsmProvider y callbacks |
| PKCS#11, material de claves, comparación y almacenes | 1047–1694 | Sesiones, perfiles, signing/wrap/JWT/CMS del token; controles externos e inspectores |
| Generación simétrica, componentes, KCV y validación | 1990–2680 | Material y summaries simétricos, badges, executor e histórico |
| Generación/CSR/parsing y validación individual de certificados; emisión y CRL | 2722–3040 y 1739–1988 | Par asimétrico compartido, controles de CertificatesController y validación |
| Fachadas de fase 1 | 3043–3217 | 39 campos FXML; coordinadores TR-31, intercambio RSA y TR-34 |
| Fachadas de fase 2 | 2693–2719, 3222–3338; inicializadores 1696–1730 y 1830 | Controles de generación/KDF aún inyectados; contratos CMS/cadena conservados |
| Auxiliares globales, clear, export, resúmenes, Shelf y Key Lab | 3342–4378 | current*Summary, KeyMaterial, persistencia, navegación y redacción |
| Variant LMK, Key Block LMK y Atalla | 4381–4727 | 26 campos FXML y sincronización de cabecera Atalla |

El próximo reparto debe atender propiedad y estado compartido, no solo rangos: los controles de bloques posteriores siguen declarados al principio, y los contratos de certificados reciben controles externos. Los rangos anteriores se solapan cuando un contrato inicializa un área cuya ejecución está más abajo.
