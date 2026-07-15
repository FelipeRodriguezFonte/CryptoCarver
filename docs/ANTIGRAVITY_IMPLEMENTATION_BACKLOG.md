# CryptoCarver — backlog de implementación para Antigravity

**Base de trabajo:** CryptoCarver 2.3.0  
**Fecha:** 14 de julio de 2026  
**Naturaleza del producto:** aplicación de escritorio JavaFX para laboratorio, formación, desarrollo y diagnóstico criptográfico. No es un HSM ni un producto de custodia de producción.

Este documento es el contrato funcional y técnico para implementar la siguiente evolución. Antigravity debe trabajar por bloques completos, sin reescribir funcionalidades existentes salvo que una tarea lo pida expresamente. Tras cada dos bloques cerrados se hará una revisión externa con Codex.

## 1. Reglas de trabajo obligatorias

### 1.1 Restricciones funcionales

- Mantener el nombre público **CryptoCarver**. El paquete Java interno `com.cryptoforge` se conserva por compatibilidad y no se renombra en este ciclo.
- No usar claves, certificados, TSA ni endpoints de producción en pruebas automáticas.
- La aplicación puede mostrar secretos porque es una herramienta de laboratorio, pero cada vista que lo haga debe etiquetarlo como tal y ofrecer un modo redactado cuando se persista o exporte información.
- Ninguna operación debe aplicar conversiones de texto, charset, EBCDIC, compresión o canonicalización de forma implícita.
- Una inspección estructural no debe presentarse como una validación criptográfica o de confianza. Ejemplo: el inspector XAdES no equivale a `Verify XML`.
- Mantener compatibilidad macOS y Windows; no introducir rutas, comandos ni APIs exclusivas de macOS en la lógica común.

### 1.2 Convenciones técnicas

- Java 17 (`maven.compiler.release=17`), JavaFX 21 y Maven.
- Todo cambio funcional debe incluir pruebas JUnit 5: un caso válido, dos fallos relevantes y, cuando aplique, un round-trip o vector independiente.
- Usar `OperationResult` y `StatusReporter` para actualizar inspector, estado e histórico de manera coherente en módulos nuevos.
- Usar `apply_patch` para editar archivos. No modificar ni borrar cambios ajenos del árbol de trabajo.
- No añadir dependencias sin justificarlo en la PR/resumen y sin actualizar `pom.xml` y las notas de versión.
- Evitar que la UI ejecute criptografía costosa en el hilo JavaFX. Usar tareas asíncronas y devolver el resultado al hilo de interfaz.

### 1.3 Comandos de verificación

Ejecutar al menos estos comandos antes de declarar un bloque terminado:

```bash
/opt/homebrew/bin/mvn -q test
xmllint --noout src/main/resources/fxml/main-view-modern.fxml
```

La prueba `LocalApiServerTest` abre un puerto loopback; si el sandbox lo bloquea, debe ejecutarse fuera del aislamiento. Para una comprobación del empaquetado:

```bash
/opt/homebrew/bin/mvn -q package
java -jar target/cryptocarver-2.3.0.jar --cli sha256 abc
```

No se debe usar `mvn clean` durante una iteración ordinaria: el directorio de trabajo contiene artefactos y cambios locales que no pertenecen necesariamente a Antigravity.

## 2. Estado base confirmado (no reimplementar)

Estas capacidades existen en 2.3.0 y deben preservarse mediante regresión:

| Área | Capacidades ya disponibles |
|---|---|
| Conversión/bytes | Base64URL, Base32/58, EBCDIC explícito, BCD/COMP-3, compresión, Byte Inspector independiente, diff/XOR/estadísticas y herramientas de archivo en streaming. |
| Simétrica | AES/3DES, AEAD, AES Key Wrap RFC 3394/5649, GMAC, Poly1305, KDF HKDF/NIST/X9.63 y cifrado de archivos por streaming. |
| Claves/PKI | Inspector de material/keystores, KCV, comparación de claves, CSR/SAN, CA raíz/intermedia de laboratorio, emisión desde CSR, linter y diagnóstico básico de cadenas. |
| XAdES/RFC 3161 | XAdES B/T/LT/LTA, TSA configurable/perfiles, timestamp RFC 3161 de fichero, inspector XML firmado y validación con truststore. |
| PQC | ML-KEM, ML-DSA, SLH-DSA y alias BC heredados; PEM; KEM; firma/verificación; compatibilidad de conjuntos de parámetros. |
| Pagos | TR-31 A/B/C/D con bloques opcionales autenticados, TDES/AES DUKPT, EMV TLV/CDOL/DDOL, ARQC/ARPC, PIN/CVV/PVV/IBM 3624. |
| Automatización | Recetas JSON con variables, Batch Runner CSV/JSON Lines, CLI local y API loopback limitada a transformaciones sin claves. |

### Hallazgos que deben conservarse

- El DOL (*Data Object List*) es una plantilla EMV que describe `tag + tamaño`. El constructor actual muestra campos aportados, rellenos a cero y valores no solicitados.
- ARQC usa el mismo método de padding en verificación que en la generación. No volver a forzar el método 2.
- Los bloques opcionales TR-31 amplían la cabecera autenticada; el motor no puede volver a asumir una cabecera fija de 16 caracteres.
- Los alias `Kyber512`/`ML-KEM-512` y `Dilithium3`/`ML-DSA-65` son equivalentes; parámetros distintos no lo son.

## 3. Orden de ejecución recomendado

Trabajar en este orden. Cada identificador es un bloque implementable y verificable; no empezar un bloque de prioridad menor si su dependencia no está cerrada.

| Orden | ID | Bloque | Dependencias |
|---:|---|---|---|
| 1 | CF-01 | Smoke tests FXML, navegación y handlers | Ninguna |
| 2 | CF-02 | Registro central de operaciones y catálogo de estado | CF-01 |
| 3 | CF-03 | `CodecRegistry` y contratos de bytes/formato | CF-01 |
| 4 | CF-04 | Histórico estructurado y perfiles de secreto | CF-02 |
| 5 | CF-05 | Extracción modular de UI (FXML/controladores) | CF-02, CF-03 |
| 6 | CF-06 | Detección de charset y streaming de archivos grandes | CF-03 |
| 7 | CF-07 | Modelo unificado `KeyMaterial` y proveedor HSM simulado | CF-04 |
| 8 | CF-08 | XAdES/TSA: confianza de token y autenticación en memoria | CF-07 |
| 9 | CF-09 | PQC estandarizado: KAT, benchmark e importación automática | CF-07 |
| 10 | CF-10 | Pagos: perfiles reproducibles y vectores | CF-04 |
| 11 | CF-11 | JOSE y ASN.1 avanzados | CF-03, CF-07 |
| 12 | CF-12 | Automatización segura ampliada | CF-02, CF-03, CF-04 |
| 13 | CF-13 | Release engineering multiplataforma | CF-01 a CF-12 según alcance elegido |

## 4. Bloques detallados

## CF-01 — Smoke tests de interfaz, navegación y handlers

**Objetivo:** detectar roturas de FXML/controlador antes de que el usuario las encuentre al navegar.

**Punto de partida:** `src/main/resources/fxml/main-view-modern.fxml`, `ModernMainController`, `SidePanel`, `NavigationRail`.

**Implementación:**

1. Crear una prueba que inicialice el toolkit JavaFX una única vez y cargue `main-view-modern.fxml` con `FXMLLoader`.
2. Comprobar que cada `fx:id` relevante se inyecta y que no hay `LoadException`.
3. Añadir una tabla de rutas esperadas: cada elemento lateral debe seleccionar el contenedor correcto y, cuando corresponda, expandir el `TitledPane` esperado.
4. Ejecutar smoke tests de handlers sin abrir diálogos nativos ni red: Byte Inspector, conversión, PQC, XAdES inspector, EMV TLV, TR-31 parser, histórico y recetas.
5. Si la plataforma CI no dispone de display, documentar y aplicar un perfil headless JavaFX compatible; no desactivar los tests silenciosamente.

**Criterios de aceptación:**

- `mvn test` falla si un atributo `onAction` referencia un método inexistente.
- El FXML moderno se carga desde una ruta con espacios.
- No hay excepciones al seleccionar todas las rutas principales de navegación.
- Los smoke tests no llaman TSA, OCSP, CRL ni diálogos de fichero.

**Pruebas mínimas:** FXML válido; handler inexistente detectado con fixture; navegación de Keys, Payments, XML Security, Post-Quantum, Generic y Byte Inspector.

## CF-02 — `OperationRegistry` e inventario visible de operaciones

**Objetivo:** eliminar strings duplicados entre Rail, SidePanel y `ModernMainController`, y mostrar si una capacidad es estable, experimental o incompleta.

**Implementación:**

1. Crear `com.cryptoforge.model.OperationDescriptor` con: `id` estable, título, categoría, subtítulo, icono lógico, estado (`STABLE`, `EXPERIMENTAL`, `PLANNED`), riesgo de secretos y ruta de navegación.
2. Crear `OperationRegistry` inmutable que exponga descriptores por categoría y búsqueda por id/título/alias/estándar.
3. Migrar progresivamente SidePanel y rutas del controlador a ids de registro. Mantener temporalmente adaptadores para textos históricos existentes.
4. Añadir en la UI un distintivo discreto para operaciones experimentales y un texto de ayuda para las planificadas. Las planificadas no deben aparentar ser ejecutables.
5. Generar un inventario Markdown desde el registro, con estado y módulo; guardarlo en `docs/OPERATIONS_CATALOG.md`.

**No hacer:** no convertir todo el controlador principal en una sola iteración; migrar un módulo por bloque y mantener la navegación actual funcionando.

**Criterios de aceptación:**

- Una operación visible tiene un `id` único y un estado.
- La búsqueda encuentra por `XAdES`, `RFC 3161`, `ML-KEM`, `TR-31`, `EMV` y `EBCDIC`.
- El catálogo documentado se genera desde datos de registro, no se mantiene manualmente.

## CF-03 — `CodecRegistry` y contratos de representación

**Objetivo:** que cada módulo declare de forma inequívoca qué recibe y qué entrega: bytes, texto, Hex, Base64, Base64URL, decimal, PEM, etc.

**Implementación:**

1. Crear `ByteFormat` y un `CodecRegistry` con codecs puros y estrictos para Hex, Base64, Base64URL, UTF-8, ASCII, binario, decimal y los charsets ya soportados.
2. Definir un contrato `decode(String, ByteFormat) -> byte[]` y `encode(byte[], ByteFormat) -> String`; los errores deben incluir formato, posición y causa cuando sea posible.
3. Migrar primero Manual Conversion, Byte Inspector, CLI y Batch Runner; conservar adaptadores de `DataConverter` durante la transición.
4. Separar los charsets (interpretación de bytes) de los formatos de transporte. EBCDIC debe continuar activándose explícitamente, nunca por heurística automática.
5. Añadir tests de equivalencia GUI/CLI/Batch para los codecs migrados.

**Criterios de aceptación:**

- Una misma entrada Hex produce exactamente los mismos bytes en GUI, CLI y Batch.
- Base64URL nunca acepta silenciosamente padding/alfabeto inválido salvo que el modo lo documente.
- Los errores de UTF-8 malformado y Hex impar son deterministas y testeados.

## CF-04 — Histórico estructurado, recetas y perfiles de secretos

**Objetivo:** sustituir el JSON plano difícil de leer por datos estructurados, repetibles y exportables de forma segura.

**Implementación:**

1. Introducir `SecretVisibility` (`FULL_LAB`, `MASKED`, `REDACTED`) en `AppSettings` y aplicarlo a histórico, inspector, recetas y exportes.
2. Representar cada detalle de operación como campo tipado: nombre, valor, clasificación (`PUBLIC`, `SENSITIVE`, `SECRET`), multilinea y formato sugerido.
3. Cambiar la vista de detalle del histórico a tabla clave/valor con área expandible para PEM/XML/JSON/hex largos; conservar export JSON.
4. Permitir comparar dos operaciones del histórico mostrando solo diferencias de parámetros y metadatos. No comparar secretos en modo redactado.
5. Añadir rotación configurable por número de entradas/tamaño y limpieza explícita de histórico/sesiones.

**Criterios de aceptación:**

- Una receta exportada en modo redactado no contiene contraseñas, KBPK, BDK, claves privadas ni secretos KEM.
- En modo laboratorio el usuario ve una advertencia persistente antes de exportar secretos.
- Reabrir una receta nunca ejecuta la operación automáticamente.

## CF-05 — Extracción modular de interfaz

**Objetivo:** reducir el tamaño y acoplamiento de `ModernMainController` sin alterar el comportamiento.

**Implementación por submódulos:**

1. Extraer primero **Post-Quantum** y **XML Security** a FXML propios con controladores ya existentes (`PostQuantumController`, `XMLSignatureController`).
2. Extraer después **Payments/EMV** y **Keys/PKI**.
3. El controlador principal debe quedar limitado a shell, navegación, cabecera, inspector común, histórico y carga de módulos.
4. Cada módulo expone una interfaz pequeña para: inicialización, navegación a operación y publicación de `OperationResult`.
5. Mantener ids de FXML estables o actualizar tests CF-01 en la misma PR.

**Criterios de aceptación:**

- La carga de un módulo no necesita conocer campos privados de otro.
- No hay reflexión para comunicar módulos nuevos con el controlador principal.
- Todas las rutas existentes de SidePanel continúan funcionando.

## CF-06 — Charset orientativo y procesamiento real de archivos grandes

**Objetivo:** completar la fase avanzada de bytes sin cargar ficheros enteros en memoria.

**Implementación:**

1. Implementar detector orientativo de charset con puntuación por UTF-8 válido, distribución de controles, ASCII imprimible, Latin-1/Windows-1252 y codepages EBCDIC. El resultado debe incluir un porcentaje orientativo y el aviso “no concluyente”.
2. Muestra máximo configurable (por defecto 256 KiB) para la detección y vista previa.
3. Extender hash, MAC, cifrado y conversión por streaming hasta 1 GiB con buffer configurable, progreso, cancelación y escritura atómica en fichero temporal + rename.
4. Definir límites de memoria y tamaño visibles; no leer el fichero entero para calcular un hash, comparar o convertir.
5. Añadir pruebas con streams sintéticos y un fichero temporal de al menos 64 MiB; la prueba de 1 GiB puede ser perfil de integración desactivado por defecto.

**Criterios de aceptación:**

- Cancelar deja claro si no se generó salida o elimina el temporal.
- La comparación identifica el primer offset distinto sin cargar los dos archivos completos.
- La detección EBCDIC no cambia ninguna conversión a menos que el usuario la active.

## CF-07 — `KeyMaterial` y proveedor HSM simulado

**Objetivo:** reutilizar claves entre módulos sin serializaciones ambiguas y preparar operaciones no exportables de laboratorio.

**Implementación:**

1. Crear modelo `KeyMaterial` con id/fingerprint, tipo, algoritmo, tamaño, formato, usos, exportabilidad y referencia a material en memoria/fichero/keystore.
2. Implementar adaptadores para `SecretKey`, `PrivateKey`, `PublicKey`, certificado X.509, PEM, PKCS#12/JKS/JCEKS y JWK donde ya existe soporte.
3. Crear `SimulatedHsmProvider`: almacén local de laboratorio con claves marcadas no exportables. Debe permitir firmar/verificar/MAC/cifrar según uso, pero rechazar exportación de clave.
4. Añadir selector de proveedor para operaciones de claves; no añadir PKCS#11 real en este bloque.
5. Añadir borrado lógico de referencias sensibles al cerrar sesión y botón “Clear lab key cache”. Documentar que Java no garantiza zeroization absoluta.

**Criterios de aceptación:**

- Una clave RSA/EC/Ed25519 o simétrica puede seleccionarse sin pegar PEM de nuevo en al menos dos módulos.
- Una clave no exportable no aparece en export, histórico redactado ni recetas.
- Las operaciones rechazadas indican si el problema es uso de clave, algoritmo o exportabilidad.

## CF-08 — XAdES y TSA: confianza del token y credenciales efímeras

**Objetivo:** pasar de inspeccionar un token RFC 3161 a distinguir criptografía, cadena y confianza.

**Implementación:**

1. Ampliar `TsaDiagnostics.TokenInspection` con algoritmo de firma, vigencia de certificado TSA, EKU de timeStamping, cadena embebida y huellas de certificados.
2. Añadir validación opcional de cadena TSA contra truststore seleccionado; el informe debe separar: parseo del token, coincidencia de imprint, firma CMS, cadena de confianza y revocación.
3. Añadir autenticación HTTP Basic y Bearer **solo en memoria** para perfiles TSA. No persistir password/token en `AppSettings`, histórico, receta ni logs.
4. Añadir timeouts conectable/lectura configurables y límite de tamaño de respuesta TSA.
5. Para XAdES Verify, publicar Simple Report, Detailed Report y ETSI report como archivos elegidos por el usuario, con advertencia si contiene datos personales.

**Criterios de aceptación:**

- Un token con cadena no confiable no se etiqueta como TSA confiable aunque su imprint coincida.
- Las credenciales no aparecen en el export de perfiles ni en el texto de errores.
- Las pruebas usan TSA simulada/local o fixtures, no una TSA pública.

## CF-09 — PQC estandarizado: KAT, benchmark e importación automática

**Objetivo:** que el módulo sea útil para evaluar migración PQC sin confundir nombres o parámetros.

**Implementación:**

1. Mostrar ML-KEM/ML-DSA/SLH-DSA como nombres primarios; los nombres Kyber/Dilithium/SPHINCS+ se presentan únicamente como alias BC.
2. Implementar detección automática de algoritmo/parámetro desde SubjectPublicKeyInfo/PKCS#8 al importar PEM; si no se reconoce, mostrar OID y no adivinar.
3. Añadir KAT de fuente oficial compatible con la versión concreta de Bouncy Castle. Incluir fuente, versión y licencia de los vectores en `docs/pqc/`.
4. Añadir benchmark cancelable: keygen, encapsulación/decapsulación o firma/verificación, tamaño de claves/ciphertext/firma, iteraciones, media y percentiles simples. No medir en el hilo UI.
5. Crear demostración didáctica KEM: Alice/Bob, clave pública, encapsulación, secreto compartido, decapsulación y comparación en tiempo constante; indicar que no es un protocolo completo.

**Criterios de aceptación:**

- No se permite firmar con ML-DSA-44 usando una clave ML-DSA-65.
- Importar una PEM válida selecciona automáticamente el conjunto compatible o solicita elección explícita si el proveedor solo aporta familia.
- KAT y benchmark no dependen de red.

## CF-10 — Pagos, EMV y TR-31: perfiles y vectores reproducibles

**Objetivo:** transformar funciones sueltas en escenarios de laboratorio repetibles.

**Implementación:**

1. Crear `PaymentProfile` versionado para supuestos EMV/ARQC, DUKPT, TR-31 y PIN. Debe declarar explícitamente esquema, padding, formato, currency/country, CVN, algoritmo y longitudes.
2. Exponer una matriz TR-31 de versión/algoritmo/uso/modo/exportabilidad. Debe bloquear combinaciones imposibles y advertir las legacy, no solo permitirlas.
3. Añadir visualización de árbol de derivación DUKPT: BDK → initial key → pasos de contador → clave de trabajo; separar TDES y AES DUKPT.
4. Completar EMV Option B solo si existe vector legal y perfil claro. No etiquetar un cálculo genérico como Option B sin vector.
5. Añadir secure messaging/script MAC como perfil explícito con datos de ejemplo ficticios.
6. Preparar por cada operación de pago dos vectores positivos y dos negativos, cargables desde la UI como ejemplos de laboratorio.

**Criterios de aceptación:**

- Un perfil guardado reproduce los mismos bytes de entrada, salida y parámetros.
- Los defaults visibles no son heurísticas silenciosas; el informe indica los valores inyectados.
- TR-31 con bloques opcionales sigue verificando MAC tras exportar/importar.

## CF-11 — JOSE y ASN.1 avanzados

**Objetivo:** completar serializaciones anunciadas y fortalecer análisis de estructuras binarias.

**JOSE:**

1. Añadir JWS JSON Serialization, múltiples firmas, payload detached y `b64=false`, con una advertencia explícita sobre interoperabilidad.
2. Completar JWE con RSA-OAEP, AES-KW, ECDH-ES y PBES2 solo donde Nimbus lo soporte y con validación de algoritmo/clase de clave.
3. Añadir nested JWT “sign then encrypt” y proceso inverso como receta visual; no asumir que es el único orden posible.
4. Implementar validación temporal configurable (`exp`, `nbf`, `iat`, clock skew) y perfiles OAuth/OIDC de laboratorio.
5. JWKS: selección determinista por `kid`, thumbprint RFC 7638, rotación y diagnóstico de `alg` confuso.

**ASN.1:**

1. Añadir modo DER estricto frente a BER permisivo con informe de la primera violación.
2. Editor de árbol que re-encode, compare el hex original y no permita editar longitudes inválidas sin recalcular.
3. Soportar al menos PKCS#1/#8/#10/#7, X.509, CRL, OCSP y TSTInfo con navegación por offset.
4. Permitir registro OID adicional desde fichero JSON validado y exportación árbol a JSON/Markdown.

**Criterios de aceptación:** interop JOSE contra Nimbus/otra librería; ASN.1 truncado/malformado nunca produce excepción no controlada; no anunciar un algoritmo ausente.

## CF-12 — Automatización segura ampliada

**Objetivo:** ampliar recetas/batch/CLI sin exponer operaciones que requieran secretos o red de forma accidental.

**Implementación:**

1. Extender `SafeTransformations` con transformaciones deterministas y sin claves: codecs, hash, HMAC solo con variable explícita y opt-in, BER-TLV/EMV TLV inspect, ASN.1 inspect y compresión.
2. Añadir mapeo de columnas Batch a parámetros, previsualización de diez filas, límite de concurrencia y orden de resultados estable.
3. Añadir recetas encadenadas con tipos explícitos (`bytes`, `text`, `file-reference`) y validación de compatibilidad entre pasos.
4. CLI: códigos de salida documentados, `--json` estable, `--version`, `--help` y mensajes de error en stderr.
5. API loopback: conservar desactivada por defecto; añadir rate limit, CORS denegado por defecto, límite de tamaño por endpoint y OpenAPI estático solo para endpoints seguros.

**Criterios de aceptación:**

- GUI, CLI y Batch producen los mismos bytes para una receta equivalente.
- Cancelar batch no deja archivo final parcial.
- La API no expone endpoints de claves, cifrado, firma, TSA ni acceso a ficheros.

## CF-13 — Release engineering y distribución

**Objetivo:** convertir una versión verificada en entregables repetibles para macOS, Windows y Linux.

**Implementación:**

1. Centralizar versión Maven en una propiedad y hacer que scripts, About/diagnóstico, JAR y `jpackage` la consuman sin valores literales duplicados.
2. Generar checksum SHA-256 para cada JAR/instalador y un SBOM CycloneDX/SPDX.
3. Añadir perfiles de empaquetado para macOS (`.app`/`.dmg`), Windows (`.msi` o `.exe`) y Linux (`.deb`/`.rpm` o `app-image`) con runtime incluido cuando sea posible.
4. Añadir `--version` a CLI y versión/runtime/arquitectura al diagnóstico de GUI.
5. Crear checklist de release: tests, smoke visual, artefactos, checksums, changelog, notas, downgrade/migración y plataformas probadas.

**Criterios de aceptación:**

- No hay nombres de JAR o versiones hard-coded fuera de una única fuente de verdad.
- Un usuario sin Maven puede ejecutar un paquete nativo en al menos macOS y Windows de laboratorio.
- Cada artefacto tiene checksum y origen trazable.

## 5. Protocolo de handoff y verificación con Codex

Antigravity debe parar y entregar un informe al terminar **dos bloques** o antes si detecta un riesgo de compatibilidad. El informe debe contener exactamente:

```markdown
## Handoff CF-XX / CF-YY

### Alcance terminado
- ...

### Archivos modificados o añadidos
- ruta — propósito

### Decisiones y compatibilidad
- decisión, alternativa descartada y motivo

### Verificación ejecutada
- comando — resultado
- tests añadidos — qué demuestran
- recorrido manual UI — pasos y resultado

### Riesgos / pendiente
- ...

### Cómo revisar
1. ...
2. ...
```

El usuario traerá dicho handoff a Codex para una revisión. Codex comprobará: coherencia con este documento, cambios no autorizados, calidad de pruebas, errores de seguridad, FXML/UI, regresiones y cumplimiento de criterios de aceptación. Antigravity no debe iniciar el siguiente par de bloques hasta recibir el resultado de esa revisión.

## 6. Definition of Done por bloque

Un bloque solo está terminado si:

- Implementa todos los puntos del alcance, o documenta explícitamente cuál se ha pospuesto y por qué.
- Compila con Java 17 y pasa pruebas completas.
- Añade pruebas positivas, negativas y de round-trip/vector cuando proceda.
- Actualiza UI, inspector, histórico y documentación si el usuario puede invocar la función desde la interfaz.
- No deja mensajes de depuración, secretos en logs ni excepciones no controladas.
- Explica las limitaciones de laboratorio y no promete cumplimiento o confianza no verificados.
- Entrega el handoff del apartado anterior.

## 7. Fuera de alcance de este ciclo

- Integrar una HSM o token PKCS#11 real sin un entorno de laboratorio, biblioteca nativa y vector de interoperabilidad acordados.
- Declarar conformidad PSD2, eIDAS, PCI DSS, EMVCo o certificación de seguridad.
- Exponer operaciones con claves mediante API HTTP.
- Añadir algoritmos legacy sin un caso de uso y vector de prueba concreto.
- Convertir automáticamente firmas PQC en XAdES/CMS/JOSE de producción antes de que el perfil e interoperabilidad estén maduros.
