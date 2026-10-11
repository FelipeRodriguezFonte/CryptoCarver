# Encargo para Luna: ayuda contextual completa de CryptoCarver

Fecha: 10 de octubre de 2026. Estado: especificación preparada para implementación y revisión; no declara la funcionalidad implementada.

**Responsable de implementación: Luna. Responsable de revisión: Codex, en este chat.** El usuario ha asignado explícitamente estos roles para este encargo. El protocolo anterior docs/LUNA_QA_PROTOCOL.md describe otro reparto de trabajo; no debe impedir que Luna implemente esta tarea. No modificar ese protocolo global como parte de la ayuda.

Este documento desarrolla la [propuesta aceptada](PROPUESTA_AYUDA_CONTEXTUAL.md) y contiene una ficha para cada una de las **96 entradas actuales de OperationRegistry**, más **24 casos auxiliares y suboperativas**. El [inventario de UI](AYUDA_CONTEXTUAL_INVENTARIO_UI.md) identifica los controles y acciones declarados en los 30 FXML revisados.

## 1. Objetivo y alcance del encargo

Incorporar ayuda local y contextual que explique propósito, acciones, campos, opciones, condiciones de uso, resultados y errores de cada operativa. Al cambiar acción, algoritmo, modo, perfil, formato, origen de clave, pestaña o idioma, la ayuda debe describir el contexto vigente.

La entrega completa incluye el catálogo, herramientas auxiliares, acciones internas, operaciones de lote y nodos de Process Designer. Las 96 entradas agrupan más de 96 acciones: contar fichas de navegación no basta para dar por terminada la ayuda.

Las fichas de los apartados siguientes son requisitos de contenido y comportamiento, apoyados en fuentes locales. Cuando una opción se configura en Java, Luna debe obtener su lista del catálogo o coordinador correspondiente. Las combinaciones criptográficas que aquí no tienen una restricción numérica explícita deben comprobarse contra el núcleo antes de redactar una promesa de compatibilidad.

### Alcance permitido de los cambios

- Añadir modelos, recursos, adaptadores y presentación de ayuda.
- Integrarla con navegación, inspector, controles y localización.
- Extraer reglas existentes de aplicabilidad/compatibilidad para que UI, ayuda y validación las compartan, con pruebas del comportamiento conservado.
- Completar fichas españolas e inglesas, ejemplos, referencias locales y su empaquetado.
- Añadir IDs estables a controles que los necesiten y enlazarlos explícitamente.

Los cambios de algoritmo, formato de datos, valores predeterminados, política de bloqueo o reglas criptográficas no forman parte de esta tarea por el hecho de detectar una divergencia. Registrar el hallazgo con un caso reproducible y plantear su corrección como cambio separado. Así Codex puede revisar la ayuda contra un comportamiento conocido.

Hay cambios locales ajenos en el workspace, especialmente JOSE y tutorial 25. Luna debe registrar el estado inicial, respetarlos y trabajar sobre su alcance sin revertir, sobreescribir ni incluirlos como propios. No ejecutar limpieza destructiva. No crear subagentes. No hacer commits, push o publicación salvo instrucción explícita del usuario.

## 2. Contrato de experiencia de usuario

1. Botón «Ayuda de esta operación» junto al título, entrada en menú Ayuda y acceso desde la paleta cuando se integre allí. F1 conserva su función actual de atajos en la primera entrega.
2. Modo de ayuda activable, recordado como preferencia no secreta. Al activarlo se muestran botones ? accesibles junto a campos/configuraciones.
3. El inspector ofrece las vistas Resultado y Ayuda. Consultar ayuda conserva el resultado y las entradas. En ancho reducido se abre la misma vista de ayuda en ventana independiente.
4. Cada campo abre su sección concreta y permite volver a la ficha general. El texto breve contiene información útil aunque no se abra el tutorial.
5. Una pantalla con dos acciones simultáneas permite consultar ambas antes de ejecutar; no deducir el contexto únicamente del último resultado.
6. Cambios programáticos —plantillas, importación, restauración— actualizan igual que los cambios manuales. Pestañas, acordeones y selección de nodo actualizan la acción/variante.
7. La ayuda permanece legible por teclado, con texto accesible y foco visible. Un campo deshabilitado puede seguir teniendo una explicación consultable del motivo.
8. Abrir ayuda es una operación local: no cifra, genera, exporta, firma, conecta con un token ni contacta TSA/servicios remotos.
9. Cerrar ayuda o cambiar ruta no borra los parámetros del usuario. La ayuda de la ruta activa y el resultado histórico seleccionado tienen identidades separadas.
10. Un control oculto que no aplica mantiene la política de layout visible/managed de la UI. La incorporación de ? no debe dejar filas vacías ni desplazar botones de ejecución fuera de su zona.

## 3. Modelo de contenido e integración técnica

Los nombres de clases que siguen son propuestos; se pueden ajustar al estilo del repositorio conservando sus responsabilidades.

| Pieza | Contrato |
|---|---|
| OperationHelpRegistry | Resolver ficha por ID estable de operación/herramienta, acción y variante. Enumerar cobertura y referencias. |
| OperationHelpDefinition | Propósito, contrato de entrada/salida, lista de acciones, campos, opciones, resultados, errores, ejemplos y documentación. |
| ActionHelp / FieldHelp / OptionHelp | Definición individual identificable, traducciones, aplicabilidad y vínculo con reglas. |
| HelpContext | ID de operación, acción, variante, idioma, campo consultado y selecciones públicas necesarias. |
| CapabilityPolicy por familia | Aplicabilidad, obligatoriedad, opciones compatibles y motivos; reutilizar catálogos y reglas existentes. |
| HelpContextSource / adaptador de módulo | Enlazar IDs semánticos con controles y notificar navegación/selección/cambios; liberar suscripciones. |
| ContextualHelpCoordinator y vista | Resolver contenido, gestionar panel/foco, actualizar por idioma y recordar activación. |
| HelpResources | Texto breve localizado y documentación extensa empaquetada con índice por IDs explícitos. |

~~~mermaid
flowchart LR
    A[Operativa y controles activos] --> B[Adaptador del módulo]
    B --> C[Contexto público de ayuda]
    C --> D[Resolución por ID y acción]
    E[Catálogo de fichas ES y EN] --> D
    F[Reglas compartidas de capacidades] --> D
    F --> G[Selectores y validación]
    D --> H[Panel de ayuda y explicación del campo]
~~~

### Identidad y fuentes

- operationId proviene de OperationDescriptor. Las herramientas auxiliares usan helpId explícito; no hace falta inventar una operación criptográfica nueva para darles ayuda.
- actionId y variantId distinguen, por ejemplo, wrap/unwrap/inspect y Compact/General. Los nombres visibles y traducidos no son identidades.
- fieldId describe un parámetro semántico. El adaptador lo relaciona con el fx:id/control real; no buscar controles por promptText ni etiqueta.
- Los encabezados y textos estáticos del anexo no requieren un botón de ayuda por cada repetición; los campos configurables y acciones sí requieren cobertura.
- Los controles sin fx:id necesitan ID o adaptador explícito. Los creados por Java —diálogos, ventanas, formularios de nodo— también deben entrar en el inventario.
- Reutilizar OperationFormatRegistry, JweComposer, PinBlockFormat y NodeParameter. No construir una segunda lista que pueda quedarse desactualizada.
- El servicio LocalHelpService actual está desconectado y busca documentos que el empaquetado no incorpora. Puede adaptarse al índice por ID; no seguir resolviendo fichas por coincidencia parcial de encabezados.

### Contexto y ciclo de vida

HelpContext utiliza únicamente selecciones enumeradas y estados derivados necesarios: algoritmo, perfil, formato, acción, presencia de material o tamaño. No copia texto de claves, PIN, passwords, payloads, tokens, alias privados, rutas o cabeceras libres dentro del contenido de ayuda.

Los adaptadores se enlazan al cargar el módulo diferido y se activan al revelar la sección. Deben evitar listeners duplicados y contexto residual al volver a una pantalla. El coordinador no debe leer campos privados mediante reflexión.

La regla de compatibilidad tiene identidad estable y un motivo traducible. Un error puede enlazar esa regla y campo; no interpretar el texto de una excepción para deducir la ficha.

### Idiomas y fallback

Mantener textos breves en bundles ES/EN; usar claves como help.operation.<id>.purpose, help.action.<id>.<action>, help.field.<id>.<field> y help.rule.<ruleId>. Las opciones conservan su código técnico y traducen la explicación.

El fallback para una ficha incompleta ofrece el resumen conocido y una indicación explícita de detalle aún no disponible; no produce un panel vacío ni finge una definición específica. Una ficha genérica/fallback no cuenta como cobertura completa en los criterios de aceptación.

Los ejemplos y tutoriales locales se enlazan mediante índices estables y relativos. Empaquetar todos los recursos seleccionados y comprobarlos desde classpath/JAR. No depender del directorio docs del checkout en ejecución.

## 4. Contrato editorial obligatorio

Cada ficha de los casos C y A debe entregar:

- Definición sencilla de la operativa y de cada acción.
- Para cada campo: significado, origen del dato, representación, unidad, tamaño/rango, obligatoriedad y condiciones de uso.
- Para cada opción seleccionable: efecto y compatibilidad. Una definición común puede reutilizarse, pero debe añadir la diferencia específica del código seleccionado.
- Campos fijados o ignorados: explicación de por qué no son configurables o no intervienen en esa variante.
- Resultado: qué contiene, representación, clasificación y qué comprobación representa.
- Un ejemplo sintético completo con entradas, parámetros y criterio de resultado.
- Un caso negativo con diagnóstico y campo/regla relacionados. En resultados aleatorios, verificar estructura, tamaño o ida/vuelta.
- Referencia al código/catálogo y tutorial correspondiente, con versión/perfil de documentación cuando aplique.

Los campos enumerados en cada ficha son la especificación semántica base. **Todos los controles adicionales reales del anexo y los creados por Java deben clasificarse y cubrirse también**. No tratar «si está disponible» como permiso para omitir una acción existente: significa comprobar su presencia y registrar la decisión en la matriz de cobertura.

### Vocabulario compartido para las explicaciones

| Concepto | Explicación base que debe concretarse por operación |
|---|---|
| Payload | Datos de entrada que procesa la acción; su representación se decodifica antes del cálculo cuando procede. |
| Hex / Base64 / Base64URL | Representaciones de bytes; no son algoritmos de cifrado y sus alfabetos no son intercambiables. |
| UTF-8 / ASCII / EBCDIC | Codificaciones de texto distintas; indicar cuál usa realmente el campo y la code page EBCDIC. |
| Bits / bytes / dígitos | Unidades distintas: un byte tiene ocho bits y se representa normalmente con dos dígitos Hex. |
| ECB | Modo por bloques sin IV; explicar la advertencia existente y evitar trasladarlo a la palabra ECB de una transformación RSA JCE. |
| CBC | Encadenamiento con IV y relleno cuando corresponde; aclarar alcance de integridad del perfil. |
| CTR | Transformación basada en contador/IV; el panel no configura relleno. |
| CFB / OFB | Modos con estado/IV; explicar parámetros efectivos y ausencia de relleno configurado en esta UI. |
| GCM | Variante autenticada; explicar nonce, AAD y tag, sus tamaños y cómo se entregan al receptor. |
| Padding | Relleno asociado a una construcción concreta; el de AES-KWP, RSA o PIN no es automáticamente el de CBC. |
| IV / nonce / tweak / salt | Parámetros con funciones distintas; explicar generación, reutilización y necesidad según la variante, sin unificarlos por ser bytes. |
| AAD | Datos adicionales que participan en autenticación; formato propio, que puede diferir del payload. |
| Firma / MAC / hash / KCV | Resultados diferentes: clave privada, clave compartida, huella o comprobación corta de clave, respectivamente. |
| Inspección / validación | Leer estructura y comprobar condiciones son acciones distintas; detallar qué comprueba el motor. |
| Confianza | Expectativa/ancla/política aportada; no se deduce solo de un certificado incluido o una cabecera. |
| Compatible / heredado / experimental | Disponibilidad en la implementación y estado del catálogo; no convertir compatibilidad en recomendación general. |

Para reglas numéricas o perfiles externos, citar el validador/fuente revisada. Si Luna necesita incorporar información normativa que no está en el repositorio, debe verificarla en documentación primaria y registrar la referencia; no inventar cumplimiento completo de un estándar.

## 5. Cómo implementar cada caso

Cada caso incluye contenido, opciones, restricciones, comprobación y fuentes. Sus IDs Cnnn son referencias de este encargo; el ID op_* es la identidad estable que utiliza la integración.

La secuencia por caso es: localizar todos sus controles/acciones, resolver las capacidades efectivas, redactar ES/EN, asociar campos y reglas, integrar el contexto, añadir ejemplo y comprobar el criterio indicado. Compartir definiciones está permitido; una descripción repetida sin significado propio no cumple la ficha.


### Índice de las 96 operativas

| Caso | Operativa | ID | Bloque |
|---|---|---|---|
| [C001](#c001) | Symmetric Ciphers | `op_sym_ciphers` | B01 |
| [C002](#c002) | Format-Preserving Encryption | `op_fpe` | B04 |
| [C003](#c003) | File Cipher (Streaming) | `op_sym_file` | B04 |
| [C004](#c004) | OpenPGP (GPG Compatible) | `op_openpgp` | B04 |
| [C005](#c005) | Asymmetric Ciphers | `op_asym_ciphers` | B04 |
| [C006](#c006) | Hashing | `op_gen_hash` | B05 |
| [C007](#c007) | Manual Conversion | `op_gen_manual` | B05 |
| [C008](#c008) | Compressed Hex (2-row) | `op_gen_compressed_hex` | B05 |
| [C009](#c009) | Batch Runner | `op_gen_batch` | B05 |
| [C010](#c010) | Process Designer | `op_gen_process_designer` | B05 |
| [C011](#c011) | File Conversion | `op_gen_file` | B05 |
| [C012](#c012) | Random Number Generator | `op_gen_random` | B05 |
| [C013](#c013) | Check Digits | `op_gen_check_digits` | B05 |
| [C014](#c014) | Modular Arithmetic | `op_gen_mod` | B05 |
| [C015](#c015) | Clipboard Shelf | `op_gen_clipboard` | B05 |
| [C016](#c016) | Key & Certificate Format Workbench | `op_keys_format_workbench` | B05 |
| [C017](#c017) | Crypto Envelope Inspector | `op_gen_envelope_inspector` | B05 |
| [C018](#c018) | SD-JWT VC | `op_wallet_sdjwt` | B12 |
| [C019](#c019) | mdoc / mDL | `op_wallet_mdoc` | B12 |
| [C020](#c020) | Status List | `op_wallet_status_list` | B12 |
| [C021](#c021) | eIDAS Certificate Profiles | `op_wallet_eidas_cert` | B12 |
| [C022](#c022) | Trusted List | `op_wallet_trusted_list` | B12 |
| [C023](#c023) | Trusted Entity List JSON | `op_wallet_trusted_entity_list_json` | B12 |
| [C024](#c024) | CBOR Inspector | `op_wallet_cbor` | B12 |
| [C025](#c025) | SCA / OpenID4VP | `op_wallet_sca` | B12 |
| [C026](#c026) | AdES Validation | `op_wallet_ades` | B12 |
| [C027](#c027) | Digital Signatures | `op_auth_sig` | B06 |
| [C028](#c028) | Message Authentication Codes | `op_auth_mac` | B06 |
| [C029](#c029) | Key Lab | `op_keys_lab` | B07 |
| [C030](#c030) | Key Generation | `op_keys_gen` | B07 |
| [C031](#c031) | Validation & KCV | `op_keys_val` | B07 |
| [C032](#c032) | Key Sharing (XOR Split/Combine) | `op_keys_share` | B07 |
| [C033](#c033) | Key Derivation (KDF) | `op_keys_kdf` | B07 |
| [C034](#c034) | AES Key Wrap | `op_keys_wrap` | B07 |
| [C035](#c035) | TR-31 Key Blocks | `op_keys_tr31` | B07 |
| [C036](#c036) | Atalla Key Block (AKB) | `op_keys_atalla_akb` | B07 |
| [C037](#c037) | RSA Key Exchange | `op_keys_kex_rsa` | B07 |
| [C038](#c038) | TR-34 Key Distribution | `op_keys_tr34` | B07 |
| [C039](#c039) | RSA Key Generation | `op_keys_rsa` | B08 |
| [C040](#c040) | ECDSA Key Generation | `op_keys_ecdsa` | B08 |
| [C041](#c041) | DSA Key Generation | `op_keys_dsa` | B08 |
| [C042](#c042) | EdDSA Key Generation | `op_keys_eddsa` | B08 |
| [C043](#c043) | Compare Public / Private Key | `op_keys_compare` | B08 |
| [C044](#c044) | Key Material Inspector | `op_keys_material` | B08 |
| [C045](#c045) | KeyStore Inspector | `op_keys_store` | B08 |
| [C046](#c046) | PKCS#11 Token | `op_keys_pkcs11` | B08 |
| [C047](#c047) | ICSF / CCA Key Token Analyzer | `op_keys_icsf_token` | B08 |
| [C048](#c048) | ICSF / CCA Batch Analysis | `op_keys_icsf_batch` | B08 |
| [C049](#c049) | ICSF / CCA Key Export / Import | `op_keys_icsf_keywrap` | B08 |
| [C050](#c050) | PQC Key Generation | `op_pqc_gen` | B13 |
| [C051](#c051) | PQC Sign/Verify | `op_pqc_sign` | B13 |
| [C052](#c052) | Sign XML (XAdES) | `op_xml_sign` | B11 |
| [C053](#c053) | Sign SOAP (WSS) | `op_wss_sign` | B11 |
| [C054](#c054) | Add UsernameToken (WSS) | `op_wss_username_add` | B11 |
| [C055](#c055) | Encrypt SOAP Body (WSS) | `op_wss_encrypt` | B11 |
| [C056](#c056) | Decrypt SOAP Body (WSS) | `op_wss_decrypt` | B11 |
| [C057](#c057) | Inspect Signed XML | `op_xml_inspect` | B11 |
| [C058](#c058) | RFC 3161 Timestamp | `op_xml_tsa` | B11 |
| [C059](#c059) | Verify XML (XAdES) | `op_xml_verify` | B11 |
| [C060](#c060) | Verify SOAP (WSS) | `op_wss_verify` | B11 |
| [C061](#c061) | Verify UsernameToken (WSS) | `op_wss_username_verify` | B11 |
| [C062](#c062) | Generate Certificate | `op_cert_gen` | B10 |
| [C063](#c063) | Issue Certificate from CSR | `op_cert_issue` | B10 |
| [C064](#c064) | Parse Certificate | `op_cert_parse` | B10 |
| [C065](#c065) | Compare Certificates | `op_cert_compare` | B10 |
| [C066](#c066) | Validate Certificate | `op_cert_val` | B10 |
| [C067](#c067) | Certificate Chain | `op_cert_chain` | B10 |
| [C068](#c068) | PAdES PDF Signatures | `op_pades` | B10 |
| [C069](#c069) | ASiC-S Containers | `op_asic_s` | B10 |
| [C070](#c070) | CMS/PKCS#7 Operations | `op_cert_cms` | B10 |
| [C071](#c071) | CMS Inspector | `op_cms_inspector` | B10 |
| [C072](#c072) | JWT (Signed) | `op_jose_jwt` | B09 |
| [C073](#c073) | JWE (Encrypted) | `op_jose_jwe` | B02 |
| [C074](#c074) | JWK (Keys) | `op_jose_jwk` | B09 |
| [C075](#c075) | JWA (Algorithms) | `op_jose_jwa` | B09 |
| [C076](#c076) | Token Inspector | `op_jose_insp` | B09 |
| [C077](#c077) | COSE_Sign1 | `op_cose_sign1` | B09 |
| [C078](#c078) | COSE_Mac0 | `op_cose_mac0` | B09 |
| [C079](#c079) | COSE_Encrypt0 | `op_cose_encrypt0` | B09 |
| [C080](#c080) | Clear PIN Blocks | `op_pay_clear_pin` | B03 |
| [C081](#c081) | Encrypted PIN Blocks | `op_pay_enc_pin` | B03 |
| [C082](#c082) | PIN Generation | `op_pay_pin_gen` | B14 |
| [C083](#c083) | CVV Operations | `op_pay_cvv` | B14 |
| [C084](#c084) | DUKPT TDES / AES | `op_pay_dukpt` | B14 |
| [C085](#c085) | EMV TLV Inspector | `op_pay_emv_tlv` | B14 |
| [C086](#c086) | EMV Operations | `op_pay_emv_ops` | B14 |
| [C087](#c087) | EMV Offline Data Authentication | `op_pay_emv_oda` | B14 |
| [C088](#c088) | Thales Variant LMK | `op_pay_thales_lmk` | B14 |
| [C089](#c089) | Thales Key Block | `op_pay_thales_key_block` | B14 |
| [C090](#c090) | payShield Host Command Bank | `op_pay_payshield_host_bank` | B14 |
| [C091](#c091) | ISO 8583 Message Inspector | `op_pay_iso8583` | B14 |
| [C092](#c092) | Decode ASN.1 | `op_asn1_dec` | B10 |
| [C093](#c093) | Encode ASN.1 | `op_asn1_enc` | B10 |
| [C094](#c094) | Recent Operations | `op_hist_recent` | B13 |
| [C095](#c095) | Saved Sessions | `op_hist_saved` | B13 |
| [C096](#c096) | Export History | `op_hist_export` | B13 |

### Cifrado

<a id="c001"></a>

#### C001 — Symmetric Ciphers

**Identidad:** `op_sym_ciphers`. **Bloque:** B01.

**Qué debe explicar:** Cifrar datos con una clave compartida o recuperar los datos al descifrar; explicar si la variante autentica también el contenido.

**Acciones que debe cubrir:** Cifrar, descifrar, generar IV/nonce, seleccionar clave manual o de Key Lab, copiar/reutilizar las partes del resultado.

**Campos y significado:**

- **Algoritmo**: familia y longitud de clave
- **Modo**: transformación aplicable al algoritmo
- **Padding**: relleno y condición de uso
- **Clave**: Hex manual o referencia de Key Lab con material disponible
- **IV/nonce**: bytes Hex, tamaño y uso por variante
- **AAD**: datos adicionales, con la interpretación actual Hex y fallback ASCII en la ruta por bloques
- **Tag**: etiqueta generada al cifrar y requerida al descifrar AEAD
- **Payload y salida**: formatos globales, independientes del formato de clave.

**Modos y opciones:** DES, 3DES y AES-128/192/256; ECB, CBC, CTR, GCM, CFB y OFB como lista actual, verificando su matriz real; Salsa20, ChaCha20, ChaCha20-Poly1305 y XChaCha20-Poly1305 con parámetros propios. ECB prescinde de IV. GCM/CTR/CFB/OFB usan NoPadding.

**Condiciones e implementación:** AES exige 16/24/32 bytes según selección; 3DES acepta 16 o 24 en el núcleo. GCM acepta IV de 8/12/16 bytes y generación de 12; tag de 16. ChaCha y XChaCha no deben heredar reglas de un modo de bloque. Documentar la advertencia de reutilización de nonce sin copiar la clave a la ayuda.

**Comprobación exigida:** Positivo: cifrar y recuperar con AES-GCM, preservando ciphertext, tag, nonce y AAD. Negativos: longitud de clave incorrecta y tag alterado. UI: GCM→CBC→algoritmo de flujo actualiza aplicabilidad y motivos sin borrar entradas.

**Fuentes de integración:** [CipherController.java](../src/main/java/com/cryptocarver/ui/CipherController.java), [SymmetricFieldsPresenter.java](../src/main/java/com/cryptocarver/ui/SymmetricFieldsPresenter.java), [SymmetricCipherCoordinator.java](../src/main/java/com/cryptocarver/ui/SymmetricCipherCoordinator.java), [crypto/SymmetricCipher.java](../src/main/java/com/cryptocarver/crypto/SymmetricCipher.java), [fxml/cipher.fxml](../src/main/resources/fxml/cipher.fxml).

<a id="c002"></a>

#### C002 — Format-Preserving Encryption

**Identidad:** `op_fpe`. **Bloque:** B04.

**Qué debe explicar:** Cifrar conservando longitud y alfabeto de una cadena; diferenciarlo de una conversión o de un simple enmascarado.

**Acciones que debe cubrir:** Cifrar/descifrar según el selector de acción; elegir un alfabeto predefinido o personalizado.

**Campos y significado:**

- **Entrada**: símbolos que pertenecen al alfabeto
- **Clave AES**: Hex de 16/24/32 bytes
- **Algoritmo**: FF1 o FF3_1
- **Alfabeto**: lista ordenada sin símbolos repetidos
- **Tweak**: contexto Hex, con requisito según algoritmo
- **Resultado**: cadena del mismo alfabeto y longitud.

**Modos y opciones:** FF1 y FF3_1; presets Decimal, Alphanumeric, ASCII printable y Custom. Explicar cómo el orden del alfabeto determina el radix.

**Condiciones e implementación:** El código limita radix a 2..256 y símbolos de un byte; dominio mínimo de 1.000.000 de valores; FF3-1 exige tweak de 7 bytes y límite de longitud dependiente del radix. Obtener límites máximos de las constantes de FormatPreservingEncryption, no de un texto duplicado.

**Comprobación exigida:** Positivo: caso decimal aceptado y vuelta al original. Negativos: símbolo ajeno al alfabeto, alfabeto duplicado y tweak FF3-1 de tamaño incorrecto. La ayuda cambia con el algoritmo y con Custom.

**Fuentes de integración:** [FpeCoordinator.java](../src/main/java/com/cryptocarver/ui/FpeCoordinator.java), [crypto/FormatPreservingEncryption.java](../src/main/java/com/cryptocarver/crypto/FormatPreservingEncryption.java), [fxml/cipher.fxml](../src/main/resources/fxml/cipher.fxml).

<a id="c003"></a>

#### C003 — File Cipher (Streaming)

**Identidad:** `op_sym_file`. **Bloque:** B04.

**Qué debe explicar:** Procesar archivos por streaming y explicar qué datos se guardan en el archivo o contenedor de salida y cuáles necesita el descifrado.

**Acciones que debe cubrir:** Cifrar archivo, descifrar archivo y las variantes por registros/líneas disponibles en el panel; elegir rutas y cancelar cuando exista esa acción.

**Campos y significado:**

- **Archivo origen/destino**: contenido leído y ruta creada
- **Perfil**: algoritmo y modo del archivo
- **Clave**: material Hex del tamaño exigido
- **IV/nonce y AAD**: significado y generación por perfil
- **Modo de registros**: delimitación y tratamiento de líneas
- **Codificación del registro**: Hex o Base64URL
- **Charset**: UTF-8 o página EBCDIC elegida
- **Resultado**: archivo y resumen de ejecución.

**Modos y opciones:** AES-256-GCM, AES-256-CTR, AES-256-CBC y ChaCha20-Poly1305 en FileCipherCoordinator. Separar procesamiento del archivo completo de procesamiento por línea.

**Condiciones e implementación:** No trasladar automáticamente formatos ni reglas del payload de la pantalla simétrica. Derivar campos obligatorios y layout de archivo de FileCipherInputs y StreamingCipher. Aclarar el alcance de autenticación por perfil y cómo se recuperan los parámetros guardados.

**Comprobación exigida:** Positivo: ida/vuelta de archivo binario y caso por líneas con charset declarado. Negativos: destino o parámetros inválidos y autenticación fallida cuando aplica. Ayuda explica los campos de registros solo en esa variante.

**Fuentes de integración:** [FileCipherCoordinator.java](../src/main/java/com/cryptocarver/ui/FileCipherCoordinator.java), [FileCipherInputs.java](../src/main/java/com/cryptocarver/ui/FileCipherInputs.java), [crypto/StreamingCipher.java](../src/main/java/com/cryptocarver/crypto/StreamingCipher.java), [fxml/cipher.fxml](../src/main/resources/fxml/cipher.fxml).

<a id="c004"></a>

#### C004 — OpenPGP (GPG Compatible)

**Identidad:** `op_openpgp`. **Bloque:** B04.

**Qué debe explicar:** Crear e inspeccionar claves OpenPGP, cifrar/descifrar mensajes y archivos, y producir o verificar sus diferentes formas de firma.

**Acciones que debe cubrir:** Generar par, importar/exportar/inspeccionar claves; cifrar/descifrar texto y archivo; firma detached, attached y clear-signed con sus verificaciones; inspeccionar firma; probar interoperabilidad con GnuPG si está instalado.

**Campos y significado:**

- **User ID**: identidad textual del par generado
- **Passphrase**: desbloqueo/protección del material secreto
- **Clave pública**: destinatario o verificador
- **Clave secreta**: descifrado o firma
- **Entrada**: texto o archivo según acción
- **Firma detached**: documento separado que necesita el payload original
- **Salida**: armor, mensaje recuperado o informe
- **Estado GnuPG**: disponibilidad local y operación comprobada.

**Modos y opciones:** Las formas de firma tienen acciones independientes; explicar dónde viajan payload y firma. Obtener familias y parámetros de generación del código actual, sin ofrecer variantes que el panel no soporta.

**Condiciones e implementación:** Indicar por acción qué clave y passphrase se utilizan. Guardar clave secreta y limpiar material secreto son acciones distintas. La ausencia de GnuPG afecta la comprobación externa, no debe presentarse como fallo del motor Java.

**Comprobación exigida:** Positivo: mensaje cifrado recuperable y una firma detached verificable. Negativos: passphrase incorrecta y payload modificado. Ayuda de verificar detached requiere entrada y firma separadas.

**Fuentes de integración:** [OpenPgpController.java](../src/main/java/com/cryptocarver/ui/OpenPgpController.java), [crypto/OpenPgpOperations.java](../src/main/java/com/cryptocarver/crypto/OpenPgpOperations.java), [fxml/openpgp.fxml](../src/main/resources/fxml/openpgp.fxml).

<a id="c005"></a>

#### C005 — Asymmetric Ciphers

**Identidad:** `op_asym_ciphers`. **Bloque:** B04.

**Qué debe explicar:** Cifrar con clave pública RSA y descifrar con la privada; explicar el límite de tamaño de mensaje de cada esquema.

**Acciones que debe cubrir:** Cifrar, descifrar, cargar/seleccionar material de clave y consultar los tamaños permitidos para el padding seleccionado.

**Campos y significado:**

- **Clave pública**: PEM/certificado aceptado para cifrar
- **Clave privada**: material privado para descifrar
- **Padding**: esquema RSA, hash OAEP y parámetros efectivos
- **Entrada**: datos decodificados según formato del payload
- **Tamaño máximo**: cálculo con módulo y esquema
- **Salida**: ciphertext o datos recuperados.

**Modos y opciones:** RSA/ECB/PKCS1Padding, OAEPWithSHA-1AndMGF1Padding, OAEPWithSHA-256AndMGF1Padding y NoPadding. El ECB del nombre JCE no debe recibir la explicación de ECB simétrico.

**Condiciones e implementación:** Reutilizar RsaPaddingLimits para los límites. Explicar las advertencias existentes de opciones heredadas/raw y la compatibilidad de clave/algoritmo. La ayuda debe seguir el padding real aplicado por el handler.

**Comprobación exigida:** Positivo: mensaje dentro del límite y vuelta con OAEP. Negativos: mensaje demasiado largo y clave de familia incorrecta. Cambiar padding actualiza el límite sin ejecutar.

**Fuentes de integración:** [AsymmetricCipherCoordinator.java](../src/main/java/com/cryptocarver/ui/AsymmetricCipherCoordinator.java), [RsaPaddingLimits.java](../src/main/java/com/cryptocarver/ui/RsaPaddingLimits.java), [crypto/AsymmetricCipher.java](../src/main/java/com/cryptocarver/crypto/AsymmetricCipher.java), [fxml/cipher.fxml](../src/main/resources/fxml/cipher.fxml).

### Genéricas y herramientas

<a id="c006"></a>

#### C006 — Hashing

**Identidad:** `op_gen_hash`. **Bloque:** B05.

**Qué debe explicar:** Obtener una huella de los bytes de entrada; separar los hashes criptográficos de las sumas CRC ofrecidas en la misma pantalla.

**Acciones que debe cubrir:** Calcular hash/CRC, elegir algoritmo y representación; aplicar o consultar plantillas del panel.

**Campos y significado:**

- **Entrada**: los bytes derivados del formato global
- **Algoritmo**: función de huella o variante CRC
- **Salida**: representación elegida de la huella
- **Tamaño de digest**: bytes/bits efectivos
- **Plantilla**: parámetros y formatos que configura.

**Modos y opciones:** MD5, MD4, WHIRLPOOL, TIGER-192, SHA-1/224/256/384/512, SHA3-256/512 según HashOperations; CRC32 y Crc32Variant añadidos por el coordinador. Explicar el objetivo diferente de CRC.

**Condiciones e implementación:** La misma cadena visible puede representar bytes distintos en Hex o UTF-8. Mostrar esa distinción y las advertencias heredadas del preflight. No exigir una clave.

**Comprobación exigida:** Positivo: SHA-256 de UTF-8 abc con vector conocido del repositorio. Negativo: entrada Hex inválida. Cambiar algoritmo cambia definición y longitud, no presenta CRC como autenticación.

**Fuentes de integración:** [HashingCoordinator.java](../src/main/java/com/cryptocarver/ui/HashingCoordinator.java), [crypto/HashOperations.java](../src/main/java/com/cryptocarver/crypto/HashOperations.java), [fxml/generic.fxml](../src/main/resources/fxml/generic.fxml).

<a id="c007"></a>

#### C007 — Manual Conversion

**Identidad:** `op_gen_manual`. **Bloque:** B05.

**Qué debe explicar:** Transformar representaciones y aplicar las utilidades de codificación del panel, explicando cuál modifica bytes y cuál solo cambia su representación.

**Acciones que debe cubrir:** Convertir formatos; EBCDIC, Base64URL/Base32, URL, endian, comprimir/descomprimir, BCD/COMP-3, desplazamientos y extracción de Hex de trazas; gestionar plantillas.

**Campos y significado:**

- **Entrada/salida**: formatos y datos
- **Dirección EBCDIC**: codificar o decodificar
- **Code page**: tabla concreta EBCDIC
- **Word size**: tamaño de palabra para endian
- **Compresión**: gzip/zlib/deflate
- **Desplazamiento**: número de bits
- **BCD/COMP-3**: dígitos y signo según la transformación
- **Traza**: texto del que se extraen bytes.

**Modos y opciones:** Barra local: UTF-8, Hexadecimal, Base64, Base64URL, Base94, Binary y Decimal. Explicaciones propias para cada botón especializado y para sus formatos independientes.

**Condiciones e implementación:** Cada acción debe identificar cómo interpreta la entrada; no asumir que todos los botones obedecen igual a la barra global. Comprobar límites de palabra y datos compactados en SafeTransformations/DataConverter.

**Comprobación exigida:** Positivo: Hex↔Base64 con mismos bytes y EBCDIC con code page definida. Negativos: Base64 inválido y palabra de tamaño incompatible. Ayuda distingue endian de codificación.

**Fuentes de integración:** [ManualConversionCoordinator.java](../src/main/java/com/cryptocarver/ui/ManualConversionCoordinator.java), [model/SafeTransformations.java](../src/main/java/com/cryptocarver/model/SafeTransformations.java), [fxml/generic.fxml](../src/main/resources/fxml/generic.fxml).

<a id="c008"></a>

#### C008 — Compressed Hex (2-row)

**Identidad:** `op_gen_compressed_hex`. **Bloque:** B05.

**Qué debe explicar:** Reconstruir Hex desde las dos filas de nibbles del host o dividir Hex convencional en esas filas.

**Acciones que debe cubrir:** Combinar las filas alta/baja y separar una entrada Hex; copiar la salida.

**Campos y significado:**

- **Fila alta**: nibble superior de cada byte
- **Fila baja**: nibble inferior
- **Hex convencional**: bytes reconstruidos
- **Separadores**: los tolerados por el parser
- **Resultado**: forma combinada o separada.

**Modos y opciones:** Las dos direcciones del panel. Aclarar que «Compressed» designa la disposición de la herramienta, no compresión de datos.

**Condiciones e implementación:** Explicar igualdad de longitudes y caracteres admitidos según el parser. Conservar el orden posicional de los nibbles.

**Comprobación exigida:** Positivo: dos filas conocidas reconstruyen los bytes esperados. Negativos: filas de distinta longitud y carácter inválido. Cada botón abre la explicación de su dirección.

**Fuentes de integración:** [CompressedHexController.java](../src/main/java/com/cryptocarver/ui/CompressedHexController.java), [fxml/compressed_hex.fxml](../src/main/resources/fxml/compressed_hex.fxml).

<a id="c009"></a>

#### C009 — Batch Runner

**Identidad:** `op_gen_batch`. **Bloque:** B05.

**Qué debe explicar:** Ejecutar una transformación por registro y devolver resultados y errores con su origen; diferenciar el catálogo seguro de cualquier rama criptográfica realmente accesible.

**Acciones que debe cubrir:** Elegir operación, cargar/pegar CSV o JSONL, simulación/dry-run, ejecutar, cancelar y exportar resultados.

**Campos y significado:**

- **Formato de entrada**: estructura CSV/JSON Lines
- **Columna origen/destino**: lectura y escritura por registro
- **Operación**: transformación concreta
- **Opciones crypto**: algoritmo, clave, IV/nonce, AAD, codificación y charset si la rama los usa
- **Stop on error**: política de continuación
- **Compact mode**: representación de resultados
- **Exportación**: formato de archivo
- **Progreso**: registros procesados y errores.

**Modos y opciones:** Usar BatchOperationCatalog.getAvailableOperations como lista efectiva. El coordinador también configura algoritmos para registros; documentarlos solo en las ramas alcanzables, verificando su selección real.

**Condiciones e implementación:** No explicar campos ocultos como obligatorios para una operación stateless. Dry-run, cancelación y stop-on-error tienen resultados diferentes. El inventario debe incluir una subficha por operación del catálogo de lote, enlazada a la definición común cuando corresponda.

**Comprobación exigida:** Positivo: lote de dos registros correctos y uno inválido con continuidad y detención. Negativos: columna ausente y formato mal formado. Revisión verifica que la ayuda coincide con operaciones seleccionables, no con controles preparados pero inaccesibles.

**Fuentes de integración:** [BatchRunnerCoordinator.java](../src/main/java/com/cryptocarver/ui/BatchRunnerCoordinator.java), [model/batch/BatchOperationCatalog.java](../src/main/java/com/cryptocarver/model/batch/BatchOperationCatalog.java), [fxml/generic.fxml](../src/main/resources/fxml/generic.fxml).

<a id="c010"></a>

#### C010 — Process Designer

**Identidad:** `op_gen_process_designer`. **Bloque:** B05.

**Qué debe explicar:** Componer flujos de nodos y conexiones tipadas, validarlos y seguir el resultado de cada paso.

**Acciones que debe cubrir:** Añadir/configurar nodos, conectar, preflight, dry-run, ejecutar/cancelar, guardar/abrir flujo e inspeccionar resultados.

**Campos y significado:**

- **Tipo de nodo**: transformación y contrato de puertos
- **Parámetros**: cada NodeParameter y sus opciones
- **Entrada/salida**: tipos de puertos
- **Conexiones**: origen y destino
- **Condiciones visibles**: dependencias de parámetros
- **Secretos**: valores transitorios marcados sensibles
- **Registro de ejecución**: estado y salida de cada paso.

**Modos y opciones:** NodeCatalog es la fuente de todos los tipos de nodo. Reutilizar helpKey, options y visibleWhen. Cada tipo seleccionable recibe ficha propia o referencia explícita a la operativa base más sus diferencias.

**Condiciones e implementación:** Guardar .cfprocess.json no implica guardar secretos. La ayuda del nodo seleccionado debe tener prioridad dentro de este workspace y no mostrar el contexto de la última operación externa. No implementar otro generador de formularios.

**Comprobación exigida:** Positivo: flujo corto conversión→hash con resultado explicable. Negativos: conexión de tipo incompatible y parámetro requerido ausente. Revisión incluye nodos nuevos/menos comunes y listeners al cambiar selección.

**Fuentes de integración:** [ProcessDesignerController.java](../src/main/java/com/cryptocarver/ui/ProcessDesignerController.java), [NodeInspectorRenderer.java](../src/main/java/com/cryptocarver/ui/NodeInspectorRenderer.java), [model/process/NodeCatalog.java](../src/main/java/com/cryptocarver/model/process/NodeCatalog.java), [model/process/NodeParameter.java](../src/main/java/com/cryptocarver/model/process/NodeParameter.java), [fxml/process_designer.fxml](../src/main/resources/fxml/process_designer.fxml).

<a id="c011"></a>

#### C011 — File Conversion

**Identidad:** `op_gen_file`. **Bloque:** B05.

**Qué debe explicar:** Convertir la representación de archivos y comparar el resultado cuando la pantalla lo permite.

**Acciones que debe cubrir:** Seleccionar archivos origen/destino/comparación y ejecutar conversión.

**Campos y significado:**

- **Origen**: archivo que se lee
- **Destino**: archivo generado
- **Archivo de comparación**: referencia usada por el handler
- **Formato entrada/salida**: representación de los bytes
- **Encoding**: charset aplicable a texto
- **Resultado**: tamaños, estado y comparación.

**Modos y opciones:** Listas efectivas de FileConversionCoordinator y los controles de generic.fxml. Separar binario crudo, texto codificado y representación Hex/Base64 cuando están disponibles.

**Condiciones e implementación:** Explicar qué selector afecta el archivo y cuáles pertenecen a la barra global. Documentar manejo de charset y errores de lectura/escritura del código actual.

**Comprobación exigida:** Positivo: binario→representación→binario y comparación. Negativos: origen ausente y contenido incompatible con formato. La ayuda del destino no confunde ruta con payload.

**Fuentes de integración:** [FileConversionCoordinator.java](../src/main/java/com/cryptocarver/ui/FileConversionCoordinator.java), [fxml/generic.fxml](../src/main/resources/fxml/generic.fxml).

<a id="c012"></a>

#### C012 — Random Number Generator

**Identidad:** `op_gen_random`. **Bloque:** B05.

**Qué debe explicar:** Generar bytes aleatorios y mostrarlos con una representación elegida; distinguir esta función de UUID y de generación de claves con atributos.

**Acciones que debe cubrir:** Generar y representar el resultado; explicar cualquier variante UUID que comparta la sección.

**Campos y significado:**

- **Longitud**: cantidad y unidad esperada por el generador
- **Formato**: cómo mostrar los bytes
- **Resultado**: bytes generados y longitud
- **UUID**: estructura/tipo si la acción está presente.

**Modos y opciones:** Hexadecimal, Decimal, Base64 y Binary en GenericController; las variantes del generador se obtienen del handler.

**Condiciones e implementación:** Los formatos alteran la representación y su número de caracteres, no la cantidad de bytes pedida. Documentar límites reales; no prometer igualdad de dos resultados aleatorios.

**Comprobación exigida:** Positivo: generar longitud conocida y verificar bytes/formato. Negativo: longitud fuera del rango. El ejemplo enseña comprobación estructural.

**Fuentes de integración:** [GenericController.java](../src/main/java/com/cryptocarver/ui/GenericController.java), [fxml/generic.fxml](../src/main/resources/fxml/generic.fxml).

<a id="c013"></a>

#### C013 — Check Digits

**Identidad:** `op_gen_check_digits`. **Bloque:** B05.

**Qué debe explicar:** Calcular un dígito de control o comprobar una secuencia completa; explicar que sirve para detectar ciertos errores de introducción.

**Acciones que debe cubrir:** Calcular y validar; seleccionar algoritmo.

**Campos y significado:**

- **Entrada**: cadena de dígitos o caracteres admitidos por el algoritmo
- **Algoritmo**: regla de cálculo
- **Dígito/salida**: nuevo carácter o veredicto
- **Longitud**: la que exige la variante.

**Modos y opciones:** CheckDigitCalculator.SUPPORTED_ALGORITHMS es la lista efectiva. Luhn, Verhoeff, Damm y cualquier variante adicional del catálogo necesitan explicación propia.

**Condiciones e implementación:** Calcular puede esperar una secuencia sin dígito final; validar espera la secuencia completa. Conservar ceros iniciales y distinguir cadena de número.

**Comprobación exigida:** Positivo: calcular y validar un fixture del algoritmo. Negativos: dígito alterado y carácter no admitido. La ayuda cambia entre calcular y validar.

**Fuentes de integración:** [CheckDigitCoordinator.java](../src/main/java/com/cryptocarver/ui/CheckDigitCoordinator.java), [crypto/CheckDigitCalculator.java](../src/main/java/com/cryptocarver/crypto/CheckDigitCalculator.java), [fxml/generic.fxml](../src/main/resources/fxml/generic.fxml).

<a id="c014"></a>

#### C014 — Modular Arithmetic

**Identidad:** `op_gen_mod`. **Bloque:** B05.

**Qué debe explicar:** Realizar operaciones de enteros y explicar cuándo actúa el módulo y qué significa el resultado.

**Acciones que debe cubrir:** Calcular suma, resta, inverso aditivo, producto, potencia, recíproco modular, GCD, LCM, GCD extendido y XOR Hex/Decimal.

**Campos y significado:**

- **a y b**: operandos y su base
- **m**: módulo en las operaciones que lo usan
- **Operación**: expresión elegida
- **Resultado**: residuo, inverso, máximo divisor o coeficientes según acción.

**Modos y opciones:** Lista completa de ModularArithmeticCoordinator. «Inverse -a» es inverso aditivo; «Reciprocal 1/a» es inverso multiplicativo. XOR Hex y XOR Decimal interpretan bases distintas.

**Condiciones e implementación:** Explicar qué campos no aplican a GCD/LCM o a operaciones unarias. El recíproco modular puede no existir; las condiciones y los signos deben corresponder al handler.

**Comprobación exigida:** Positivo: una potencia modular y GCD extendido con identidad comprobable. Negativos: módulo inválido y recíproco inexistente. Selector cambia campos aplicables y explicación.

**Fuentes de integración:** [ModularArithmeticCoordinator.java](../src/main/java/com/cryptocarver/ui/ModularArithmeticCoordinator.java), [fxml/generic.fxml](../src/main/resources/fxml/generic.fxml).

<a id="c015"></a>

#### C015 — Clipboard Shelf

**Identidad:** `op_gen_clipboard`. **Bloque:** B05.

**Qué debe explicar:** Guardar y reutilizar valores de la sesión con sus metadatos y representaciones.

**Acciones que debe cubrir:** Capturar/copiar, filtrar, enviar a campo o herramienta, limpiar y consultar la política de visibilidad.

**Campos y significado:**

- **Elemento**: valor y procedencia
- **Tipo/formato**: interpretación de bytes/texto
- **Sensibilidad**: clasificación efectiva
- **Filtro**: búsqueda del inventario
- **Destino**: campo al que se envía
- **Caducidad/sesión**: alcance real de conservación.

**Modos y opciones:** Las acciones efectivas de ClipboardShelfController y ClipboardTargetNavigator; explicar paquetes AEAD cuando existan y sus componentes.

**Condiciones e implementación:** La ayuda describe qué se copia, qué se transforma y qué se conserva. No incluir valores reales de la estantería en ejemplos automáticos ni abrir detalles secretos desde el panel de ayuda.

**Comprobación exigida:** Positivo: enviar un ejemplo público a un campo compatible. Negativos: destino incompatible o material no disponible. Ayuda respeta la visibilidad seleccionada.

**Fuentes de integración:** [ClipboardShelfController.java](../src/main/java/com/cryptocarver/ui/ClipboardShelfController.java), [ClipboardTargetNavigator.java](../src/main/java/com/cryptocarver/ui/ClipboardTargetNavigator.java), [fxml/clipboard_shelf.fxml](../src/main/resources/fxml/clipboard_shelf.fxml).

<a id="c016"></a>

#### C016 — Key & Certificate Format Workbench

**Identidad:** `op_keys_format_workbench`. **Bloque:** B05.

**Qué debe explicar:** Identificar y convertir representaciones de claves, certificados y almacenes, mostrando qué material contienen.

**Acciones que debe cubrir:** Cargar/analizar, convertir, validar relación con segundo material, inspeccionar almacén, copiar y enviar salida.

**Campos y significado:**

- **Entrada**: clave/certificado/archivo
- **Password**: descifrado del contenedor cuando procede
- **Formato detectado**: estructura reconocida
- **Convert to**: destinos compatibles
- **Entrada secundaria**: material con el que validar
- **Tipo de almacén**: formato al cargar keystore
- **Tabla de alias**: entradas del contenedor.

**Modos y opciones:** Obtener destinos de KeyCertificateFormatService según material detectado. PEM, DER, JWK y keystores requieren explicaciones distintas; no anunciar todas las conversiones como universales.

**Condiciones e implementación:** Explicar si la salida contiene material público o privado, qué campos se pierden o conservan y qué conversión se rechaza. Detección y validación no son la misma acción.

**Comprobación exigida:** Positivo: clave pública entre dos formatos y certificado reconocido. Negativos: destino incompatible y password incorrecto. Lista y ayuda siguen el material cargado.

**Fuentes de integración:** [KeyCertificateWorkbenchController.java](../src/main/java/com/cryptocarver/ui/KeyCertificateWorkbenchController.java), [service/KeyCertificateFormatService.java](../src/main/java/com/cryptocarver/service/KeyCertificateFormatService.java), [fxml/key_certificate_workbench.fxml](../src/main/resources/fxml/key_certificate_workbench.fxml).

<a id="c017"></a>

#### C017 — Crypto Envelope Inspector

**Identidad:** `op_gen_envelope_inspector`. **Bloque:** B05.

**Qué debe explicar:** Inspeccionar un contenedor criptográfico y, en la acción correspondiente, intentar recuperar la clave envuelta con el material adecuado.

**Acciones que debe cubrir:** Inspeccionar, seleccionar perfil de unwrap, proporcionar clave privada y ejecutar unwrap.

**Campos y significado:**

- **Envelope**: estructura serializada
- **Perfil unwrap**: mecanismo que la interpreta
- **Clave privada**: material requerido para recuperar
- **Informe**: estructura y metadatos públicos
- **Clave recuperada**: resultado sensible del unwrap.

**Modos y opciones:** Perfiles efectivos de CryptoEnvelopeInspectorController y servicios de envelope. Aclarar diferencias entre inspección estructural y recuperación.

**Condiciones e implementación:** Inspeccionar no debe anunciar recuperación ni verificación criptográfica que no se haya realizado. La ayuda no precarga el material sensible recuperado; documentar el alcance exacto de los perfiles.

**Comprobación exigida:** Positivo: envelope de ejemplo identificado y unwrap compatible. Negativos: perfil incompatible y clave incorrecta. Cambiar acción modifica requisitos de clave.

**Fuentes de integración:** [CryptoEnvelopeInspectorController.java](../src/main/java/com/cryptocarver/ui/CryptoEnvelopeInspectorController.java), [fxml/crypto_envelope_inspector.fxml](../src/main/resources/fxml/crypto_envelope_inspector.fxml).

### Wallet

<a id="c018"></a>

#### C018 — SD-JWT VC

**Identidad:** `op_wallet_sdjwt`. **Bloque:** B12.

**Qué debe explicar:** Emitir una credencial con divulgación selectiva, crear una presentación y comprobarla; distinguir lectura estructural de verificación.

**Acciones que debe cubrir:** Emitir, presentar, verificar e inspeccionar.

**Campos y significado:**

- **Algoritmo y clave issuer**: firma de emisión
- **Claims JSON**: contenido inicial
- **Disclosable**: campos que pueden revelarse selectivamente
- **vct**: tipo de credencial
- **Decoys**: entradas de ocultación del conjunto de hashes
- **Reveal**: divulgaciones elegidas
- **Holder key**: firma de key binding cuando se usa
- **Audience/nonce**: contexto esperado de presentación
- **Token y claves de verificación**: materiales que comprueba el verificador.

**Modos y opciones:** Subfichas de emisión, presentación, verificación e inspección. Algoritmos y formato de selección de claims salen de WalletSdJwtCoordinator y SdJwtOperations.

**Condiciones e implementación:** Explicar qué claves son privadas o públicas en cada acción y cuándo se exige key binding. Audience y nonce introducidos al verificar son expectativas externas, no valores automáticamente confiables del token.

**Comprobación exigida:** Positivo: emitir, revelar un subconjunto y verificarlo. Negativos: divulgación alterada y audience/nonce incompatibles. La ayuda de inspección no presenta lectura como credencial válida.

**Fuentes de integración:** [WalletSdJwtCoordinator.java](../src/main/java/com/cryptocarver/ui/WalletSdJwtCoordinator.java), [crypto/SdJwtOperations.java](../src/main/java/com/cryptocarver/crypto/SdJwtOperations.java), [fxml/wallet.fxml](../src/main/resources/fxml/wallet.fxml).

<a id="c019"></a>

#### C019 — mdoc / mDL

**Identidad:** `op_wallet_mdoc`. **Bloque:** B12.

**Qué debe explicar:** Emitir e inspeccionar un documento móvil y comprobar el alcance de su firma y de sus digests.

**Acciones que debe cubrir:** Emitir, verificar e inspeccionar.

**Campos y significado:**

- **docType**: tipo de documento
- **Digest**: función usada para las entradas
- **Issuer key/cert**: firma e identidad del emisor
- **Device key**: clave asociada al dispositivo
- **Claims**: namespaces y elementos del documento
- **Validity**: intervalo o duración que interpreta el handler
- **Documento de verificación**: estructura serializada
- **Issuer verification key**: material de comprobación.

**Modos y opciones:** Algoritmos digest y perfiles expuestos por WalletController/MdocOperations. Mostrar los namespaces y el vínculo con la estructura emitida.

**Condiciones e implementación:** No prometer un protocolo completo de presentación/dispositivo si la acción solo verifica la estructura issuer-signed. La salida debe explicar qué partes se comprobaron y cuáles no.

**Comprobación exigida:** Positivo: emitir y verificar documento de laboratorio. Negativos: digest de elemento alterado y documento mal formado. Ayuda identifica las claves necesarias por acción.

**Fuentes de integración:** [WalletMdocCoordinator.java](../src/main/java/com/cryptocarver/ui/WalletMdocCoordinator.java), [crypto/MdocOperations.java](../src/main/java/com/cryptocarver/crypto/MdocOperations.java), [fxml/wallet.fxml](../src/main/resources/fxml/wallet.fxml).

<a id="c020"></a>

#### C020 — Status List

**Identidad:** `op_wallet_status_list`. **Bloque:** B12.

**Qué debe explicar:** Construir una lista de estados compacta, firmarla y resolver el estado de una posición.

**Acciones que debe cubrir:** Emitir, resolver por índice y describir la lista.

**Campos y significado:**

- **Bits por estado**: capacidad de valores de cada entrada
- **Statuses**: estados de la lista
- **URI**: identificador/publicación referenciada
- **Algoritmo y clave**: firma del token
- **Token**: lista que se consulta
- **Index**: posición a resolver
- **Verify key**: comprobación de firma cuando el handler la aplica.

**Modos y opciones:** Anchos de estado admitidos por StatusListOperations y algoritmos del panel. Diferenciar descripción del contenido de consulta de una posición concreta.

**Condiciones e implementación:** Explicar valores admisibles según bits e índices válidos. La presencia de una URI no implica que la aplicación publique o descargue automáticamente la lista.

**Comprobación exigida:** Positivo: lista con varios estados y resolución de una posición. Negativos: índice fuera de rango y estado no representable. La ayuda mantiene la unidad y base del índice.

**Fuentes de integración:** [WalletStatusListCoordinator.java](../src/main/java/com/cryptocarver/ui/WalletStatusListCoordinator.java), [crypto/StatusListOperations.java](../src/main/java/com/cryptocarver/crypto/StatusListOperations.java), [fxml/wallet.fxml](../src/main/resources/fxml/wallet.fxml).

<a id="c021"></a>

#### C021 — eIDAS Certificate Profiles

**Identidad:** `op_wallet_eidas_cert`. **Bloque:** B12.

**Qué debe explicar:** Leer un certificado frente a los perfiles eIDAS/wallet implementados y explicar los hallazgos.

**Acciones que debe cubrir:** Inspeccionar perfil y consultar su informe.

**Campos y significado:**

- **Certificado**: material X.509 aceptado
- **Perfil detectado/evaluado**: condiciones analizadas
- **Extensiones**: usos, políticas y declaraciones leídas
- **Hallazgos**: evidencia y limitación del análisis.

**Modos y opciones:** Los perfiles disponibles del servicio utilizado por WalletTrustCoordinator; explicar cada etiqueta que aparece en su resultado.

**Condiciones e implementación:** No convertir coincidencias de extensiones o perfil en una garantía de confianza, cualificación o vigencia. Señalar qué proviene del certificado y qué se deduce del análisis.

**Comprobación exigida:** Positivo: certificado fixture con extensiones conocidas. Negativo: material que no es certificado. El texto de ayuda y los hallazgos corresponden al perfil realmente analizado.

**Fuentes de integración:** [WalletTrustCoordinator.java](../src/main/java/com/cryptocarver/ui/WalletTrustCoordinator.java), [fxml/wallet.fxml](../src/main/resources/fxml/wallet.fxml).

<a id="c022"></a>

#### C022 — Trusted List

**Identidad:** `op_wallet_trusted_list`. **Bloque:** B12.

**Qué debe explicar:** Leer una Trusted List XML, verificar su firma con el material indicado y buscar un certificado dentro de la lista.

**Acciones que debe cubrir:** Inspeccionar, verificar y buscar certificado.

**Campos y significado:**

- **XML de lista**: documento que se procesa
- **Certificado de firma**: material para verificación
- **Certificado de búsqueda**: objeto a localizar
- **Servicio/estado/fecha**: datos de la entrada encontrada
- **Informe**: comprobaciones y coincidencias.

**Modos y opciones:** Acciones separadas: inspección de estructura, firma y búsqueda. Las expectativas de confianza se derivan del handler y no de la palabra trusted en el título.

**Condiciones e implementación:** Documentar la diferencia entre lista bien formada, firma correcta y servicio aceptado en una fecha/contexto. Indicar si la verificación utiliza un certificado aportado o uno extraído.

**Comprobación exigida:** Positivo: lista fixture inspeccionada y certificado localizado. Negativos: XML mal formado, firma alterada o certificado sin coincidencia. Ayuda distingue cada veredicto.

**Fuentes de integración:** [WalletTrustCoordinator.java](../src/main/java/com/cryptocarver/ui/WalletTrustCoordinator.java), [fxml/wallet.fxml](../src/main/resources/fxml/wallet.fxml).

<a id="c023"></a>

#### C023 — Trusted Entity List JSON

**Identidad:** `op_wallet_trusted_entity_list_json`. **Bloque:** B12.

**Qué debe explicar:** Analizar una lista JSON de entidades confiables y explicar el alcance de la comprobación de su firmante y búsqueda.

**Acciones que debe cubrir:** Inspeccionar lista con certificado de firmante y certificado de búsqueda cuando se suministran.

**Campos y significado:**

- **Lista JSON**: estructura de entidades/servicios
- **Signer certificate**: material de comprobación
- **Search certificate**: entidad a localizar
- **Entradas y hallazgos**: información extraída y comprobaciones efectivas.

**Modos y opciones:** Los tipos y perfiles aceptados por el servicio llamado desde handleTrustedEntityListJsonInspect. No trasladar parámetros XML a esta pantalla.

**Condiciones e implementación:** Explicar por separado parseo, firma y confianza. Los certificados opcionales deben aparecer como tales si el handler permite omitirlos.

**Comprobación exigida:** Positivo: lista JSON de ejemplo con entrada conocida. Negativos: JSON incompatible y certificado de búsqueda ausente en la lista. La ayuda no anuncia validaciones que se omitieron.

**Fuentes de integración:** [WalletTrustCoordinator.java](../src/main/java/com/cryptocarver/ui/WalletTrustCoordinator.java), [fxml/wallet.fxml](../src/main/resources/fxml/wallet.fxml).

<a id="c024"></a>

#### C024 — CBOR Inspector

**Identidad:** `op_wallet_cbor`. **Bloque:** B12.

**Qué debe explicar:** Explorar datos CBOR y sus representaciones, incluida la decodificación anidada que el inspector soporte.

**Acciones que debe cubrir:** Inspeccionar, convertir CBOR a JSON y construir CBOR desde JSON.

**Campos y significado:**

- **Entrada CBOR**: representación admitida de bytes
- **JSON**: estructura usada por las conversiones
- **Árbol/diagnóstico**: tipos y valores
- **Tags**: semántica del tag reconocido, incluido tag 24
- **Salida**: representación CBOR/JSON.

**Modos y opciones:** Las tres acciones tienen entradas y salidas diferentes. Explicar pérdida o cambio de tipos cuando una representación JSON no conserva todas las características CBOR.

**Condiciones e implementación:** Validación estructural no comprueba firma de COSE ni confianza de un documento. Resolver formatos desde WalletCborCoordinator.

**Comprobación exigida:** Positivo: fixture con mapa y elemento etiquetado. Negativo: CBOR truncado. La ayuda muestra el formato esperado al cambiar de dirección.

**Fuentes de integración:** [WalletCborCoordinator.java](../src/main/java/com/cryptocarver/ui/WalletCborCoordinator.java), [fxml/wallet.fxml](../src/main/resources/fxml/wallet.fxml).

<a id="c025"></a>

#### C025 — SCA / OpenID4VP

**Identidad:** `op_wallet_sca`. **Bloque:** B12.

**Qué debe explicar:** Construir/verificar datos de vinculación dinámica SCA y analizar solicitudes OpenID4VP dentro del alcance del laboratorio.

**Acciones que debe cubrir:** Construir SCA, verificar SCA e inspeccionar solicitud OpenID4VP.

**Campos y significado:**

- **Tipo SCA**: estructura de transacción
- **Credential IDs**: credenciales referenciadas
- **Payload/entry/presentation**: objetos que consume la variante
- **Transaction data**: datos vinculados
- **Issuer/holder keys**: emisión y prueba de posesión
- **Audience/nonce**: destinatario y reto
- **Response mode**: modalidad declarada
- **OID4VP request/key**: solicitud y material opcional para su análisis.

**Modos y opciones:** Tipos definidos por Ts12ScaOperations y modos realmente expuestos. Dividir la ficha entre SCA y la acción de inspección OID4VP.

**Condiciones e implementación:** Explicar el dato exacto que queda vinculado y los requisitos de comprobación. No presentar la inspección local como un flujo de autenticación completo con un proveedor remoto.

**Comprobación exigida:** Positivo: construcción/verificación fixture SCA. Negativos: transacción alterada y nonce esperado incompatible. Para OID4VP, solicitud malformed produce diagnóstico de formato.

**Fuentes de integración:** [WalletScaCoordinator.java](../src/main/java/com/cryptocarver/ui/WalletScaCoordinator.java), [crypto/Ts12ScaOperations.java](../src/main/java/com/cryptocarver/crypto/Ts12ScaOperations.java), [fxml/wallet.fxml](../src/main/resources/fxml/wallet.fxml).

<a id="c026"></a>

#### C026 — AdES Validation

**Identidad:** `op_wallet_ades`. **Bloque:** B12.

**Qué debe explicar:** Validar un artefacto AdES y explicar el informe y el alcance de las evidencias disponibles.

**Acciones que debe cubrir:** Validar y generar informe ETSI.

**Campos y significado:**

- **File name**: nombre/tipo que ayuda a seleccionar el formato
- **Documento**: contenido o representación consumida por el handler
- **Informe**: resultado global y subcomprobaciones
- **Evidencia disponible**: certificados, tiempo y revocación usados por el validador.

**Modos y opciones:** Formatos XAdES, PAdES, CAdES y ASiC alcanzables desde AdesValidationOperations. Explicar formato, nivel declarado y nivel comprobado por separado.

**Condiciones e implementación:** Cada estado del informe necesita explicación de significado y límites; una ausencia de evidencia no debe explicarse como una firma necesariamente falsa. Reutilizar la semántica del resultado del motor.

**Comprobación exigida:** Positivo: artefacto fixture con informe esperado. Negativos: documento inválido y formato que no puede resolverse. La ficha explica el estado indeterminado cuando exista.

**Fuentes de integración:** [WalletScaCoordinator.java](../src/main/java/com/cryptocarver/ui/WalletScaCoordinator.java), [crypto/AdesValidationOperations.java](../src/main/java/com/cryptocarver/crypto/AdesValidationOperations.java), [fxml/wallet.fxml](../src/main/resources/fxml/wallet.fxml).

### Autenticación

<a id="c027"></a>

#### C027 — Digital Signatures

**Identidad:** `op_auth_sig`. **Bloque:** B06.

**Qué debe explicar:** Firmar bytes con una clave privada y verificar la firma con la pública o certificado correspondiente.

**Acciones que debe cubrir:** Firmar, verificar, seleccionar origen de clave y aplicar plantillas.

**Campos y significado:**

- **Algoritmo**: familia y hash, incluyendo parámetros del esquema
- **Payload**: bytes según formato seleccionado
- **Clave privada**: material de firma
- **Clave pública/certificado**: material de verificación
- **Origen/alias**: referencia de Key Lab o fuente disponible
- **Firma a verificar**: valor Hex/Base64 leído por el handler
- **Resultado**: firma o veredicto.

**Modos y opciones:** Ed25519; RSA-SHA1/SHA256/SHA512-PKCS1; RSA-SHA256/SHA384/SHA512-PSS; ECDSA-SHA256/SHA384/SHA512 según SignatureOperations. Explicar RSA-PSS frente a PKCS1 y familia de clave compatible.

**Condiciones e implementación:** Generar una firma y validarla son acciones distintas. No presentar firma correcta como validación automática de confianza del certificado. Explicar la codificación de firma efectiva y el papel del payload original.

**Comprobación exigida:** Positivo: firma y verificación con una familia soportada. Negativos: payload alterado y clave de familia incompatible. La ayuda del campo clave se adapta a firmar/verificar.

**Fuentes de integración:** [AuthenticationSignatureCoordinator.java](../src/main/java/com/cryptocarver/ui/AuthenticationSignatureCoordinator.java), [AuthenticationKeyCoordinator.java](../src/main/java/com/cryptocarver/ui/AuthenticationKeyCoordinator.java), [crypto/SignatureOperations.java](../src/main/java/com/cryptocarver/crypto/SignatureOperations.java), [fxml/authentication.fxml](../src/main/resources/fxml/authentication.fxml).

<a id="c028"></a>

#### C028 — Message Authentication Codes

**Identidad:** `op_auth_mac`. **Bloque:** B06.

**Qué debe explicar:** Calcular o comprobar un autenticador con una clave compartida y parámetros concretos.

**Acciones que debe cubrir:** Generar MAC, verificar MAC y seleccionar clave manual o de Key Lab.

**Campos y significado:**

- **Algoritmo**: construcción MAC y función subyacente
- **Clave**: Hex o referencia
- **Payload**: bytes que se autentican
- **IV/padding/truncamiento**: solo si están expuestos y usados por la variante
- **MAC esperado**: Hex/Base64 para verificación
- **Resultado**: valor o veredicto.

**Modos y opciones:** HMAC-SHA1/256/384/512; CMAC AES/3DES; CBC-MAC; ISO-9797-1 ALG1/2/4/6; ANSI-X9.9/X9.19, AS2805.4.1 y Retail MAC según MACOperations. Explicar equivalencias únicamente donde el código las declare.

**Condiciones e implementación:** Longitud de clave, relleno y tamaño de MAC varían según algoritmo. Reutilizar los validadores del núcleo. Explicar qué campos de salida son valores completos y cuáles están truncados si esa opción existe.

**Comprobación exigida:** Positivo: vector conocido del algoritmo seleccionado y verificación. Negativos: MAC alterado y clave de tamaño incorrecto. Elegir otro algoritmo actualiza restricciones.

**Fuentes de integración:** [AuthenticationMacCoordinator.java](../src/main/java/com/cryptocarver/ui/AuthenticationMacCoordinator.java), [crypto/MACOperations.java](../src/main/java/com/cryptocarver/crypto/MACOperations.java), [fxml/authentication.fxml](../src/main/resources/fxml/authentication.fxml).

### Claves

<a id="c029"></a>

#### C029 — Key Lab

**Identidad:** `op_keys_lab`. **Bloque:** B07.

**Qué debe explicar:** Gestionar el inventario de claves del laboratorio y conocer si una entrada contiene material utilizable o solo metadatos.

**Acciones que debe cubrir:** Crear/generar, importar, inspeccionar, editar metadatos, seleccionar, exportar y eliminar según las acciones del panel.

**Campos y significado:**

- **Alias/ID**: referencia estable de la entrada
- **Tipo/algoritmo/tamaño**: propiedades de clave
- **Uso**: operaciones permitidas
- **Material**: bytes disponibles o metadata-only
- **KCV**: comprobación corta calculada
- **Política de exportación**: restricciones del inventario
- **Resultado**: estado de la entrada.

**Modos y opciones:** Tipos soportados por KeyLabCoordinator y SimulatedHsmProvider. La ayuda distingue referencia, bytes y metadatos.

**Condiciones e implementación:** Describir qué datos se conservan y qué operaciones requieren material. No llamar HSM físico al proveedor simulado. Abrir ayuda no debe generar/exportar/eliminar claves.

**Comprobación exigida:** Positivo: entrada sintética utilizable en una operación compatible. Negativo: referencia metadata-only que no puede cifrar. Ayuda explica el motivo sin mostrar la clave.

**Fuentes de integración:** [KeyLabCoordinator.java](../src/main/java/com/cryptocarver/ui/KeyLabCoordinator.java), [crypto/hsm/SimulatedHsmProvider.java](../src/main/java/com/cryptocarver/crypto/hsm/SimulatedHsmProvider.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c030"></a>

#### C030 — Key Generation

**Identidad:** `op_keys_gen`. **Bloque:** B07.

**Qué debe explicar:** Generar una clave simétrica del tipo y longitud seleccionados, y explicar sus representaciones y controles asociados.

**Acciones que debe cubrir:** Generar, copiar/guardar o enviar al inventario cuando esas acciones estén disponibles.

**Campos y significado:**

- **Tipo**: DES, 3DES de dos/tres componentes o AES
- **Resultado de clave**: bytes sensibles generados
- **KCV**: identificador corto con su método
- **Paridad**: tratamiento DES cuando corresponda
- **Destino**: registro o formato de exportación.

**Modos y opciones:** DES, 3DES-2KEY, 3DES-3KEY, AES-128, AES-192 y AES-256. Explicar longitud almacenada y efectiva donde el resultado las diferencie.

**Condiciones e implementación:** Tomar de KeyOperations los tamaños y comportamiento de paridad. No confundir KCV con una firma o prueba completa de igualdad. Los ejemplos generados se comprueban por estructura.

**Comprobación exigida:** Positivo: AES de longitud elegida y KCV coherente. Negativo: selección no soportada en la política pura. La ayuda de 3DES diferencia dos y tres componentes.

**Fuentes de integración:** [SymmetricKeyCoordinator.java](../src/main/java/com/cryptocarver/ui/SymmetricKeyCoordinator.java), [crypto/KeyOperations.java](../src/main/java/com/cryptocarver/crypto/KeyOperations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c031"></a>

#### C031 — Validation & KCV

**Identidad:** `op_keys_val`. **Bloque:** B07.

**Qué debe explicar:** Comprobar estructura, longitud y propiedades de una clave, y calcular su Key Check Value.

**Acciones que debe cubrir:** Validar, calcular KCV y comprobar/ajustar paridad si el panel ofrece esa acción.

**Campos y significado:**

- **Clave**: material Hex
- **Tipo**: algoritmo que determina los tamaños
- **Método KCV**: procedimiento empleado
- **Longitud KCV**: bytes/dígitos devueltos
- **Paridad**: regla aplicable a DES
- **Informe**: tamaño, degeneración y hallazgos comprobados.

**Modos y opciones:** Los métodos y longitudes de KCV que utiliza el panel; no deducirlos de una referencia genérica a AES o DES. Explicar cuándo la paridad no aplica.

**Condiciones e implementación:** Un KCV corto no prueba unicidad ni recupera la clave. Documentar resultados de claves DES/3DES que colapsan según la comprobación existente.

**Comprobación exigida:** Positivo: fixture con KCV conocido. Negativos: Hex inválido y longitud incompatible. Cambiar método/longitud actualiza la explicación del resultado.

**Fuentes de integración:** [SymmetricKeyCoordinator.java](../src/main/java/com/cryptocarver/ui/SymmetricKeyCoordinator.java), [crypto/KeyOperations.java](../src/main/java/com/cryptocarver/crypto/KeyOperations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c032"></a>

#### C032 — Key Sharing (XOR Split/Combine)

**Identidad:** `op_keys_share`. **Bloque:** B07.

**Qué debe explicar:** Dividir una clave en componentes XOR o reconstruirla combinando todos los componentes requeridos.

**Acciones que debe cubrir:** Dividir y combinar; seleccionar cantidad de componentes.

**Campos y significado:**

- **Clave original**: bytes a dividir
- **Cantidad**: número de componentes
- **Componentes**: secuencias Hex de igual longitud
- **Tipo/paridad**: tratamiento si se aplica
- **Resultado**: componentes o clave reconstruida
- **KCV**: comprobación de la reconstrucción.

**Modos y opciones:** Cantidad configurada por el panel: 2, 3, 4 o 5. La combinación XOR de este módulo no debe presentarse como un esquema general de umbral t-de-n.

**Condiciones e implementación:** Explicar que los componentes deben tener longitud compatible y el conjunto requerido por esta construcción. La ayuda trata originales y componentes como sensibles.

**Comprobación exigida:** Positivo: dividir y reconstruir una clave sintética. Negativos: componente de distinta longitud y dato mal formado. La ficha de combinar no pide una clave original.

**Fuentes de integración:** [SymmetricKeyCoordinator.java](../src/main/java/com/cryptocarver/ui/SymmetricKeyCoordinator.java), [crypto/KeyOperations.java](../src/main/java/com/cryptocarver/crypto/KeyOperations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c033"></a>

#### C033 — Key Derivation (KDF)

**Identidad:** `op_keys_kdf`. **Bloque:** B07.

**Qué debe explicar:** Derivar material de clave a partir de un secreto y contexto; explicar cada función sin confundir sus parámetros.

**Acciones que debe cubrir:** Derivar y generar salt si la acción está presente; cambiar algoritmo y formatos independientes.

**Campos y significado:**

- **Input key material**: secreto/contraseña con su propio formato
- **Salt**: datos de diversificación con formato local
- **Info/context**: información de aplicación con formato local
- **Iterations**: coste o parámetro interpretado por la KDF
- **Output length**: cantidad y unidad del resultado
- **Algoritmo**: construcción
- **Resultado**: bytes sensibles derivados.

**Modos y opciones:** HKDF-SHA1/256/512, NIST-800-108-SHA256, X9.63-SHA256, PBKDF2-SHA1/256/512, SCrypt y Argon2id. Formatos locales UTF-8/Hex/Base64 para input, salt e info.

**Condiciones e implementación:** Por cada algoritmo identificar campos visibles, obligatorios e ignorados y parámetros fijados internamente. No describir Iterations como el mismo coste en PBKDF2, scrypt y Argon2. Derivar límites de KdfKeyWrapLogic.

**Comprobación exigida:** Positivo: vector reproducible de HKDF y PBKDF2. Negativos: formato mal formado y longitud/coste inválido. Cambio de algoritmo y restauración actualizan visibilidad, ayuda y validación.

**Fuentes de integración:** [KdfKeyWrapCoordinator.java](../src/main/java/com/cryptocarver/ui/KdfKeyWrapCoordinator.java), [KdfKeyWrapLogic.java](../src/main/java/com/cryptocarver/ui/KdfKeyWrapLogic.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c034"></a>

#### C034 — AES Key Wrap

**Identidad:** `op_keys_wrap`. **Bloque:** B07.

**Qué debe explicar:** Proteger una clave con una KEK o recuperar y comprobar la integridad de la clave envuelta.

**Acciones que debe cubrir:** Wrap y unwrap, seleccionados por el control del panel.

**Campos y significado:**

- **KEK**: clave AES que protege el material
- **Data**: clave en claro al wrap o bytes envueltos al unwrap
- **Modo**: RFC 3394 o RFC 5649
- **Unwrap**: dirección
- **Resultado**: material protegido o recuperado.

**Modos y opciones:** RFC 3394 - AES Key Wrap y RFC 5649 - AES Key Wrap with Padding. Explicar diferencias de tamaño/admisibilidad de datos y el relleno interno de KWP.

**Condiciones e implementación:** Obtener restricciones y comprobación de integridad de KeyWrapOperations. No transferir padding de cifrado CBC al selector de esta operación.

**Comprobación exigida:** Positivo: vector conocido y recuperación. Negativos: bloque alterado y longitud no admitida por 3394. Cambiar a 5649 modifica restricciones explicadas.

**Fuentes de integración:** [KdfKeyWrapCoordinator.java](../src/main/java/com/cryptocarver/ui/KdfKeyWrapCoordinator.java), [crypto/KeyWrapOperations.java](../src/main/java/com/cryptocarver/crypto/KeyWrapOperations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c035"></a>

#### C035 — TR-31 Key Blocks

**Identidad:** `op_keys_tr31`. **Bloque:** B07.

**Qué debe explicar:** Crear, importar e inspeccionar bloques de clave TR-31 con su cabecera y protección.

**Acciones que debe cubrir:** Exportar/wrap, importar/unwrap, analizar cabecera, gestionar bloques opcionales y copiar resultados.

**Campos y significado:**

- **KBPK**: clave protectora
- **Key to wrap**: clave transportada
- **Versión**: formato/protección del bloque
- **Usage**: finalidad declarada
- **Algorithm**: algoritmo de la clave transportada
- **Mode of use**: permisos declarados
- **Exportability**: atributo E/N/S del bloque
- **Optional blocks**: metadatos adicionales
- **Key block**: bloque completo de importación
- **Key length**: significado real del campo en la importación.

**Modos y opciones:** Versiones, usos, algoritmos y modos de Tr31Coordinator/Tr31Logic/TR31Operations. Las opciones visibles y la generación real deben tener la misma cobertura documentada.

**Condiciones e implementación:** Separar atributos del bloque de la clave protectora. Explicar la comprobación de integridad al importar y el alcance del análisis sin KBPK. No considerar un atributo de cabecera como una custodia física.

**Comprobación exigida:** Positivo: bloque de una versión implementada recupera la clave y atributos. Negativos: KBPK incorrecta y cabecera/código incompatible. Cada opción y bloque opcional tiene definición específica.

**Fuentes de integración:** [Tr31Coordinator.java](../src/main/java/com/cryptocarver/ui/Tr31Coordinator.java), [Tr31Logic.java](../src/main/java/com/cryptocarver/ui/Tr31Logic.java), [crypto/TR31Operations.java](../src/main/java/com/cryptocarver/crypto/TR31Operations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c036"></a>

#### C036 — Atalla Key Block (AKB)

**Identidad:** `op_keys_atalla_akb`. **Bloque:** B07.

**Qué debe explicar:** Construir, interpretar y recuperar un Atalla Key Block y explicar su cabecera de opciones.

**Acciones que debe cubrir:** Elegir plantilla/opciones de cabecera, wrap, unwrap y analizar.

**Campos y significado:**

- **Template**: conjunto de opciones de cabecera
- **Opciones 0..7**: significado de cada posición
- **Header**: bytes/códigos construidos
- **Meaning**: interpretación
- **MFK**: clave protectora
- **Key**: material a envolver
- **AKB**: bloque completo
- **Resultado**: clave o informe.

**Modos y opciones:** Plantillas y valores definidos por AtallaAkbHeader. Reutilizar su catálogo para explicar los códigos y las combinaciones.

**Condiciones e implementación:** Tratar parsing, autenticación y recuperación como pasos distintos. Vincular cada posición de cabecera con su descripción; no sustituirla por una explicación genérica de TR-31.

**Comprobación exigida:** Positivo: plantilla produce cabecera esperada y wrap/unwrap funciona. Negativos: opción incompatible y bloque alterado. La ayuda sigue las ocho posiciones.

**Fuentes de integración:** [PaymentKeyBlockCoordinator.java](../src/main/java/com/cryptocarver/ui/PaymentKeyBlockCoordinator.java), [crypto/AtallaAkbOperations.java](../src/main/java/com/cryptocarver/crypto/AtallaAkbOperations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c037"></a>

#### C037 — RSA Key Exchange

**Identidad:** `op_keys_kex_rsa`. **Bloque:** B07.

**Qué debe explicar:** Transportar una clave simétrica usando RSA y el contenedor seleccionado; explicar el contenido y las expectativas del receptor.

**Acciones que debe cubrir:** Exportar/envolver e importar/recuperar.

**Campos y significado:**

- **Recipient PEM**: pública/certificado del receptor
- **Key to wrap**: clave simétrica Hex
- **Perfil**: Raw OAEP/JWE/CMS
- **Include envelope**: envoltorio adicional del laboratorio
- **kid y key version**: metadatos del envelope cuando aplica
- **Private key**: recuperación
- **Wrapped data**: objeto recibido
- **Resultado**: contenedor o clave.

**Modos y opciones:** Raw OAEP, JWE Compact y CMS EnvelopedData. Obtener parámetros efectivos OAEP y de contenedor de RsaKeyExchangeLogic.

**Condiciones e implementación:** Esta ruta transporta claves; no debe explicarse como distribución TR-34 autenticada. Los metadatos opcionales deben indicar si son protegidos y comprobados por la implementación.

**Comprobación exigida:** Positivo: recuperación con cada perfil implementado. Negativos: contenedor de perfil diferente y clave privada incorrecta. Fields de envelope se explican solo al activarlo.

**Fuentes de integración:** [RsaKeyExchangeCoordinator.java](../src/main/java/com/cryptocarver/ui/RsaKeyExchangeCoordinator.java), [RsaKeyExchangeLogic.java](../src/main/java/com/cryptocarver/ui/RsaKeyExchangeLogic.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c038"></a>

#### C038 — TR-34 Key Distribution

**Identidad:** `op_keys_tr34`. **Bloque:** B07.

**Qué debe explicar:** Distribuir material de clave en el flujo de laboratorio que firma y envuelve, explicando identidades y comprobaciones.

**Acciones que debe cubrir:** Distribuir, recibir/verificar, generar/usar reto o binding nonce cuando proceda.

**Campos y significado:**

- **Sender private key/certificate**: firma e identidad emisor
- **Receiver certificate/private key**: transporte y recuperación
- **Key to distribute**: material sensible
- **Key ID**: identificador
- **Binding nonce**: vínculo de la distribución
- **Expected sender certificate**: expectativa del receptor
- **Challenge nonce**: reto esperado
- **Include envelope**: contenedor adicional.

**Modos y opciones:** Variantes expuestas por Tr34Logic y TR34Operations; diferenciar el flujo implementado de cualquier perfil externo no soportado.

**Condiciones e implementación:** Explicar verificación de remitente, destinatario y nonce según el código. Omitir un valor esperado no debe presentarse como si se hubiese comprobado.

**Comprobación exigida:** Positivo: distribución/recepción fixture con identidades y nonce conocidos. Negativos: emisor esperado diferente, reto incompatible y firma alterada. Ayuda de recepción define expectativas.

**Fuentes de integración:** [Tr34Coordinator.java](../src/main/java/com/cryptocarver/ui/Tr34Coordinator.java), [Tr34Logic.java](../src/main/java/com/cryptocarver/ui/Tr34Logic.java), [crypto/TR34Operations.java](../src/main/java/com/cryptocarver/crypto/TR34Operations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c039"></a>

#### C039 — RSA Key Generation

**Identidad:** `op_keys_rsa`. **Bloque:** B08.

**Qué debe explicar:** Generar un par RSA y explicar tamaño, claves pública/privada y formatos entregados.

**Acciones que debe cubrir:** Generar y copiar/exportar o registrar las claves según el panel.

**Campos y significado:**

- **Key size**: bits del módulo RSA
- **Pública**: parte compartible para las operaciones compatibles
- **Privada**: material sensible
- **Formato**: contenedor de cada salida
- **Resultado/tamaño**: datos del par generado.

**Modos y opciones:** Tamaños de AsymmetricKeyOperations.RSA_KEY_SIZES. Cada tamaño seleccionable debe quedar descrito sin confundir bits con caracteres PEM.

**Condiciones e implementación:** La ayuda no modifica el tamaño ni genera claves al abrirse. Diferenciar este generador de generación de certificado y de transporte RSA.

**Comprobación exigida:** Positivo: tamaño seleccionado produce par compatible. Negativo: tamaño no soportado en la política. Ayuda explica formatos de ambas salidas.

**Fuentes de integración:** [AsymmetricKeyGenerationCoordinator.java](../src/main/java/com/cryptocarver/ui/AsymmetricKeyGenerationCoordinator.java), [crypto/AsymmetricKeyOperations.java](../src/main/java/com/cryptocarver/crypto/AsymmetricKeyOperations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c040"></a>

#### C040 — ECDSA Key Generation

**Identidad:** `op_keys_ecdsa`. **Bloque:** B08.

**Qué debe explicar:** Generar un par EC en la curva seleccionada y explicar la relación entre curva, tamaño y uso.

**Acciones que debe cubrir:** Generar con curva nombrada; cubrir además parámetros personalizados u otras pestañas que estén accesibles.

**Campos y significado:**

- **Curve**: nombre y dominio de parámetros
- **Parámetros custom**: significado de p/a/b/G/n/h si el panel los expone
- **Pública/privada**: salidas del par
- **Formato**: representación utilizada.

**Modos y opciones:** Curvas de AsymmetricKeyOperations.ECDSA_FP_NAMED_CURVES y variantes efectivamente visibles. F2m y custom reciben subfichas si existen, sin suponer la misma lista que JOSE.

**Condiciones e implementación:** El tamaño de curva y el hash de una futura firma son opciones distintas. Explicar validación de parámetros custom si aplica y límites de formatos.

**Comprobación exigida:** Positivo: par en curva soportada. Negativos: curva no admitida y parámetro custom inválido cuando existe. Cambiar variante actualiza campos requeridos.

**Fuentes de integración:** [AsymmetricKeyGenerationCoordinator.java](../src/main/java/com/cryptocarver/ui/AsymmetricKeyGenerationCoordinator.java), [crypto/AsymmetricKeyOperations.java](../src/main/java/com/cryptocarver/crypto/AsymmetricKeyOperations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c041"></a>

#### C041 — DSA Key Generation

**Identidad:** `op_keys_dsa`. **Bloque:** B08.

**Qué debe explicar:** Generar un par DSA con el conjunto de tamaños ofrecido y conocer los formatos de sus componentes.

**Acciones que debe cubrir:** Generar y consultar/copiar salidas.

**Campos y significado:**

- **Tamaño/conjunto**: parámetros L/N o representación usada por el selector
- **Pública/privada**: partes del par
- **Parámetros del dominio**: los que devuelve la implementación
- **Formato**: contenedor de exportación.

**Modos y opciones:** Valores de AsymmetricKeyOperations.DSA_KEY_SIZES. Explicar el selector real sin sustituirlo por tamaños RSA.

**Condiciones e implementación:** Documentar parámetros fijados por el generador y los límites de interoperabilidad que el código exponga. No ofrecer DSA como firma disponible en una pantalla que no la incluya.

**Comprobación exigida:** Positivo: conjunto seleccionado produce par válido. Negativo: conjunto no soportado. La ayuda de generación no anuncia acciones de firma ausentes.

**Fuentes de integración:** [AsymmetricKeyGenerationCoordinator.java](../src/main/java/com/cryptocarver/ui/AsymmetricKeyGenerationCoordinator.java), [crypto/AsymmetricKeyOperations.java](../src/main/java/com/cryptocarver/crypto/AsymmetricKeyOperations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c042"></a>

#### C042 — EdDSA Key Generation

**Identidad:** `op_keys_eddsa`. **Bloque:** B08.

**Qué debe explicar:** Generar el par EdDSA que soporta este panel y explicar su uso y sus formatos.

**Acciones que debe cubrir:** Generar, copiar/exportar y registrar cuando corresponda.

**Campos y significado:**

- **Familia/curva efectiva**: parámetro del generador
- **Pública**: clave de verificación
- **Privada**: material para firma
- **Formato**: estructura de cada salida.

**Modos y opciones:** El coordinador expone generación Ed25519. No añadir Ed448 o selectores de tamaño porque estén disponibles en otras bibliotecas.

**Condiciones e implementación:** Presentar parámetros fijados como información. Explicar diferencias entre semilla, material privado serializado y clave pública solo si el formato utilizado las expone.

**Comprobación exigida:** Positivo: par generado puede usarse en Ed25519 del laboratorio. Negativo: formato importado incompatible en la operación que lo consume.

**Fuentes de integración:** [AsymmetricKeyGenerationCoordinator.java](../src/main/java/com/cryptocarver/ui/AsymmetricKeyGenerationCoordinator.java), [crypto/AsymmetricKeyOperations.java](../src/main/java/com/cryptocarver/crypto/AsymmetricKeyOperations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c043"></a>

#### C043 — Compare Public / Private Key

**Identidad:** `op_keys_compare`. **Bloque:** B08.

**Qué debe explicar:** Comprobar si una clave pública y una privada pertenecen al mismo par.

**Acciones que debe cubrir:** Comparar ambos materiales e inspeccionar el resultado.

**Campos y significado:**

- **Pública**: clave/certificado aceptado
- **Privada**: material de la otra parte
- **Algoritmo detectado**: familia comparada
- **Veredicto**: relación entre los materiales
- **Diagnóstico**: parte que no se pudo interpretar.

**Modos y opciones:** Formatos y familias aceptados por la comparación de KeyStoreCoordinator. Explicar si la comprobación compara parámetros o realiza una prueba criptográfica.

**Condiciones e implementación:** Comparación de par no equivale a validación de cadena del certificado ni confirma identidad de un tercero. No reflejar la privada en la ayuda.

**Comprobación exigida:** Positivo: par relacionado. Negativos: dos pares distintos y material de familias diferentes. Ayuda define el significado del veredicto.

**Fuentes de integración:** [KeyStoreCoordinator.java](../src/main/java/com/cryptocarver/ui/KeyStoreCoordinator.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c044"></a>

#### C044 — Key Material Inspector

**Identidad:** `op_keys_material`. **Bloque:** B08.

**Qué debe explicar:** Reconocer estructura y propiedades de material de clave sin ejecutar una operación de cifrado.

**Acciones que debe cubrir:** Inspeccionar material y consultar su informe.

**Campos y significado:**

- **Entrada**: PEM/DER/JWK u otros formatos realmente detectados
- **Tipo**: pública/privada/secreta
- **Algoritmo/tamaño**: propiedades extraídas
- **Huella**: resumen identificativo
- **Protección**: estado del contenedor
- **Informe**: detección y límites.

**Modos y opciones:** Formatos reconocidos por el servicio de material utilizado por KeyStoreCoordinator. Explicar detección frente a descifrado de material protegido.

**Condiciones e implementación:** Una estructura reconocida puede no ser utilizable en todas las operaciones. Describir los datos detectados y el motivo de falta de material cuando aplica.

**Comprobación exigida:** Positivo: material conocido correctamente identificado. Negativos: estructura truncada y texto sin formato reconocido. La ayuda no presenta propiedades inferidas como comprobaciones realizadas.

**Fuentes de integración:** [KeyStoreCoordinator.java](../src/main/java/com/cryptocarver/ui/KeyStoreCoordinator.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c045"></a>

#### C045 — KeyStore Inspector

**Identidad:** `op_keys_store`. **Bloque:** B08.

**Qué debe explicar:** Inspeccionar un almacén y sus alias, certificados y tipos de entradas; explicar la extracción de material cuando se permite.

**Acciones que debe cubrir:** Elegir archivo/tipo, inspeccionar, guardar/cargar perfil y activar extracción explícita cuando existe.

**Campos y significado:**

- **Path/type**: archivo y formato JKS/PKCS12 soportado
- **Password**: apertura
- **Alias**: entrada seleccionada
- **Perfil/nombre**: preferencias guardadas
- **Unsafe extract**: acción y material que habilita
- **Informe**: contenido y atributos de las entradas.

**Modos y opciones:** Tipos de almacén y operaciones aceptados por el servicio. Explicar certificados, entradas de clave y cadenas de forma distinta.

**Condiciones e implementación:** El perfil no debe guardar password ni claves. La ayuda debe describir la opción de extracción sin activarla ni mostrar el material.

**Comprobación exigida:** Positivo: keystore fixture con alias conocidos. Negativos: password incorrecto y formato incompatible. La ayuda explica qué cambia al activar extracción.

**Fuentes de integración:** [KeyStoreCoordinator.java](../src/main/java/com/cryptocarver/ui/KeyStoreCoordinator.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c046"></a>

#### C046 — PKCS#11 Token

**Identidad:** `op_keys_pkcs11`. **Bloque:** B08.

**Qué debe explicar:** Conectar con un token de laboratorio e inspeccionar/usar sus objetos mediante el proveedor PKCS#11.

**Acciones que debe cubrir:** Conectar/desconectar, inventario, seleccionar alias, firmar/verificar, mostrar certificados, producir JWT/CMS y wrap/unwrap según las capacidades disponibles.

**Campos y significado:**

- **Nombre/library/slot**: perfil y selección del token
- **PIN**: autenticación
- **Alias**: objeto residente
- **Signature algorithm/data/signature**: operación de firma
- **JWT payload/algorithm**: token firmado
- **CMS data/detached**: contenedor
- **Wrapping/unwrapping key/transformation**: transporte soportado por token
- **Unwrap type/algorithm**: objeto a recuperar.

**Modos y opciones:** Opciones reales del proveedor y del token conectado. Distinguir capacidades configuradas de mecanismos disponibles y explicar controles deshabilitados.

**Condiciones e implementación:** Documentar dónde se realiza la operación y qué material sale del token. Referencias residentes no equivalen a bytes exportables. No incluir PIN ni parámetros secretos en persistencia de ayuda.

**Comprobación exigida:** Positivo: token de prueba con inventario y una operación soportada. Negativos: biblioteca/slot inválido, login fallido o mecanismo no soportado. Si el entorno no tiene token, registrar la limitación de pruebas.

**Fuentes de integración:** [KeyStoreCoordinator.java](../src/main/java/com/cryptocarver/ui/KeyStoreCoordinator.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml), [fxml/pkcs11_profiles.fxml](../src/main/resources/fxml/pkcs11_profiles.fxml).

<a id="c047"></a>

#### C047 — ICSF / CCA Key Token Analyzer

**Identidad:** `op_keys_icsf_token`. **Bloque:** B08.

**Qué debe explicar:** Leer un token nativo ICSF/CCA y explicar estructura, material protegido y hallazgos de procedencia.

**Acciones que debe cubrir:** Analizar Hex, cargar binario, cargar ejemplos y exportar informe.

**Campos y significado:**

- **Token**: bytes completos
- **Procedencia**: crudo de dataset, Key Record Read o inferencia según selector
- **Familia/versión**: formato leído
- **CV/TVV/MKVP**: atributos o comprobaciones presentes
- **Material**: estado protegido/público
- **Hallazgos**: evidencia y alcance.

**Modos y opciones:** Formatos reconocidos por el analizador, no perfiles TR-31. La procedencia cambia cómo se interpretan campos ausentes.

**Condiciones e implementación:** La ayuda no afirma que el analizador descifra claves protegidas. Explicar ausencia, no aplicabilidad y valor cero por separado. El informe exportado puede contener el token íntegro.

**Comprobación exigida:** Positivo: cada ejemplo integrado corresponde a su familia. Negativos: token truncado y versión no reconocida. Cambiar procedencia actualiza interpretación y ayuda.

**Fuentes de integración:** [IcsfTokenController.java](../src/main/java/com/cryptocarver/ui/IcsfTokenController.java), [fxml/icsf_token.fxml](../src/main/resources/fxml/icsf_token.fxml).

<a id="c048"></a>

#### C048 — ICSF / CCA Batch Analysis

**Identidad:** `op_keys_icsf_batch`. **Bloque:** B08.

**Qué debe explicar:** Analizar varios tokens, normalizar el inventario y explicar estadísticas y hallazgos de cada registro.

**Acciones que debe cubrir:** Analizar lote, elegir detección/formato, cargar ejemplo y exportar texto/CSV.

**Campos y significado:**

- **Entrada**: bloques o líneas de tokens
- **Formato**: detección automática o forma forzada
- **Procedencia**: contexto de lectura
- **Registros**: normalización por token
- **Estadísticas**: dimensión y denominador
- **Hallazgos**: código, gravedad y evidencia
- **Exportación**: contenido incluido.

**Modos y opciones:** Un token por línea, dos filas por token o bloque Hex apilado, según el parser actual. Cada modo tiene ejemplo de delimitación.

**Condiciones e implementación:** Distinguir token inválido de campo no aplicable y ausencia esperada. Explicar cómo un fallo afecta el resto del lote y los recuentos. No sumar categorías de forma que cambien los denominadores.

**Comprobación exigida:** Positivo: lote integrado de formatos mixtos con recuentos conocidos. Negativo: un token defectuoso entre válidos. Ayuda explica cada modo y cada estadística mostrada.

**Fuentes de integración:** [IcsfBatchController.java](../src/main/java/com/cryptocarver/ui/IcsfBatchController.java), [fxml/icsf_batch.fxml](../src/main/resources/fxml/icsf_batch.fxml).

<a id="c049"></a>

#### C049 — ICSF / CCA Key Export / Import

**Identidad:** `op_keys_icsf_keywrap`. **Bloque:** B08.

**Qué debe explicar:** Reproducir exportación/importación nativa CCA en claro y analizar/resolver los esquemas implementados.

**Acciones que debe cubrir:** Exportar, importar, inspeccionar y resolver; copiar token/clave y guardar informe.

**Campos y significado:**

- **Key/KEK**: material de transporte
- **Type/CV**: uso y control vector
- **Variant/mode**: esquema elegido
- **RN**: dato usado por la variante
- **Host version/NOCV**: condiciones del verbo simulado
- **Input token**: objeto a importar/inspeccionar
- **Expected key/KCV**: criterio de resolución
- **Resultado**: token o material recuperado.

**Modos y opciones:** Listas y reglas de IcsfKeyWrapController y su servicio. Explicar los cuatro tabs y la diferencia entre elección de esquema y búsqueda por expectativas.

**Condiciones e implementación:** Esta herramienta reproduce cálculos con material en claro, sin conexión a host. Inspeccionar no es importar. Resolver requiere explicar lo que se prueba y cómo se decide una coincidencia; no adivinar un esquema único sin evidencia.

**Comprobación exigida:** Positivo: exportar/importar fixture y resolver con expectativa conocida. Negativos: CV/esquema incompatible y KEK incorrecta. Cada tab ofrece campos y salida propios.

**Fuentes de integración:** [IcsfKeyWrapController.java](../src/main/java/com/cryptocarver/ui/IcsfKeyWrapController.java), [fxml/icsf_keywrap.fxml](../src/main/resources/fxml/icsf_keywrap.fxml).

### Postcuántica

<a id="c050"></a>

#### C050 — PQC Key Generation

**Identidad:** `op_pqc_gen`. **Bloque:** B13.

**Qué debe explicar:** Generar/importar pares de claves postcuánticos y explicar familia, conjunto de parámetros y tamaños.

**Acciones que debe cubrir:** Generar, importar, inspeccionar y exportar pública/privada.

**Campos y significado:**

- **Algorithm/parameter set**: identidad completa de la variante
- **Pública/privada**: materiales y formatos
- **Imported keys**: estructura que acepta el importador
- **Tamaños**: bytes de cada clave
- **Estado del par**: disponibilidad para firma o KEM.

**Modos y opciones:** ML-KEM-512/768/1024; ML-DSA-44/65/87; SLH-DSA-SHA2-128s/128f/192s/192f/256s/256f. Encabezados separadores del combo no son algoritmos.

**Condiciones e implementación:** Explicar KEM frente a firma: ML-KEM no debe describirse como generador de firmas. El conjunto de parámetros debe coincidir con la operación que consume el par.

**Comprobación exigida:** Positivo: par de una familia y exportación/importación compatible. Negativos: encabezado seleccionado y mezcla de parámetros. Ayuda adapta usos y tamaños por familia.

**Fuentes de integración:** [PostQuantumKeyCoordinator.java](../src/main/java/com/cryptocarver/ui/PostQuantumKeyCoordinator.java), [crypto/PostQuantumOperations.java](../src/main/java/com/cryptocarver/crypto/PostQuantumOperations.java), [fxml/pqc.fxml](../src/main/resources/fxml/pqc.fxml).

<a id="c051"></a>

#### C051 — PQC Sign/Verify

**Identidad:** `op_pqc_sign`. **Bloque:** B13.

**Qué debe explicar:** Firmar/verificar mensajes con ML-DSA o SLH-DSA y el par correspondiente.

**Acciones que debe cubrir:** Firmar y verificar.

**Campos y significado:**

- **Algoritmo**: familia/conjunto
- **Mensaje**: datos en el formato leído por el coordinador
- **Clave**: par importado/generado y parte utilizada
- **Firma**: valor a verificar
- **Resultado**: firma o veredicto y tamaños.

**Modos y opciones:** ML-DSA-44/65/87 y las seis variantes SLH-DSA-SHA2 disponibles. Explicar identidad completa y no simplificarla a «PQC».

**Condiciones e implementación:** Reutilizar areAlgorithmsCompatible para coherencia entre clave y selección. La pantalla KEM tiene ficha auxiliar separada.

**Comprobación exigida:** Positivo: firmar/verificar mensaje con conjunto coincidente. Negativos: mensaje alterado y conjunto incompatible. Cambiar selector actualiza compatibilidad y explicación.

**Fuentes de integración:** [PostQuantumSignatureCoordinator.java](../src/main/java/com/cryptocarver/ui/PostQuantumSignatureCoordinator.java), [crypto/PostQuantumOperations.java](../src/main/java/com/cryptocarver/crypto/PostQuantumOperations.java), [fxml/pqc.fxml](../src/main/resources/fxml/pqc.fxml).

### Seguridad XML y SOAP

<a id="c052"></a>

#### C052 — Sign XML (XAdES)

**Identidad:** `op_xml_sign`. **Bloque:** B11.

**Qué debe explicar:** Firmar un documento XML con el empaquetado XAdES y nivel seleccionados.

**Acciones que debe cubrir:** Elegir documento, origen de clave y alias, firmar, guardar y configurar TSA/perfiles.

**Campos y significado:**

- **Input path**: XML original
- **Origen local/PKCS11**: lugar de la operación
- **Key path/password/alias**: selección de clave y cadena
- **Level**: nivel XAdES solicitado
- **Packaging**: relación entre XML y firma
- **TSA URL/auth**: solicitud temporal cuando el nivel la necesita
- **Salida**: XML/contenedor firmado.

**Modos y opciones:** XAdES-BASELINE-B/T/LT/LTA; ENVELOPED, ENVELOPING y DETACHED. TSA auth NONE/BASIC/BEARER. Obtener compatibilidades efectivas del motor.

**Condiciones e implementación:** Explicar qué evidencia requiere cada nivel y qué se conserva según packaging. Configurar TSA no garantiza que se haya obtenido un sello. La ayuda local no debe contactar servicios.

**Comprobación exigida:** Positivo: firma local de fixture en packaging soportado. Negativos: clave ausente y nivel que necesita evidencia no disponible. Cambio de origen/nivel actualiza los campos.

**Fuentes de integración:** [XmlSignatureSigningCoordinator.java](../src/main/java/com/cryptocarver/ui/XmlSignatureSigningCoordinator.java), [XmlSignatureKeyMaterialCoordinator.java](../src/main/java/com/cryptocarver/ui/XmlSignatureKeyMaterialCoordinator.java), [crypto/XMLSignatureOperations.java](../src/main/java/com/cryptocarver/crypto/XMLSignatureOperations.java), [fxml/xml_security.fxml](../src/main/resources/fxml/xml_security.fxml).

<a id="c053"></a>

#### C053 — Sign SOAP (WSS)

**Identidad:** `op_wss_sign`. **Bloque:** B11.

**Qué debe explicar:** Firmar las partes SOAP que selecciona el perfil WS-Security del laboratorio.

**Acciones que debe cubrir:** Seleccionar SOAP, keystore/alias y algoritmo, configurar timestamp, firmar y guardar.

**Campos y significado:**

- **SOAP input**: documento a proteger
- **Keystore/password/private-key password/alias**: material firmante
- **Algorithm**: familia y hash
- **Timestamp enabled/validity/signed**: creación, duración y protección temporal
- **Output/report**: SOAP y referencias firmadas.

**Modos y opciones:** RSA_SHA256/384/512 y ECDSA_SHA256/384/512 de WssSignatureAlgorithm. Diferenciar timestamp SOAP de sello de tiempo RFC 3161.

**Condiciones e implementación:** Explicar qué elementos se firman realmente, qué certificado se incluye y cómo interviene el timestamp. Compatibilidad de algoritmo con clave según núcleo.

**Comprobación exigida:** Positivo: SOAP fixture con referencias y timestamp esperados. Negativos: XML que no es SOAP y algoritmo incompatible con clave. La ayuda muestra las partes protegidas.

**Fuentes de integración:** [WssSecurityController.java](../src/main/java/com/cryptocarver/ui/WssSecurityController.java), [crypto/WssSecurityOperations.java](../src/main/java/com/cryptocarver/crypto/WssSecurityOperations.java), [fxml/wss_security.fxml](../src/main/resources/fxml/wss_security.fxml).

<a id="c054"></a>

#### C054 — Add UsernameToken (WSS)

**Identidad:** `op_wss_username_add`. **Bloque:** B11.

**Qué debe explicar:** Añadir un UsernameToken a SOAP y explicar los datos de autenticación que contiene.

**Acciones que debe cubrir:** Crear token y guardar SOAP.

**Campos y significado:**

- **SOAP**: documento original
- **Username**: identidad
- **Password**: secreto usado al construir
- **Password type**: Text o Digest
- **Nonce**: dato de diversificación y su formato
- **Created**: instante asociado
- **Opciones temporales**: duración y controles disponibles.

**Modos y opciones:** PasswordText y PasswordDigest. Explicar exactamente qué se incluye y qué se calcula en cada tipo según WssUsernameTokenOperations.

**Condiciones e implementación:** El password digest no es firma XML de todo el SOAP. La generación de nonce/fecha debe explicarse si es automática; la ayuda no los regenera.

**Comprobación exigida:** Positivo: token integrado que puede verificar la operación hermana. Negativos: SOAP mal formado y datos obligatorios ausentes. Selector cambia explicación de password.

**Fuentes de integración:** [WssSecurityController.java](../src/main/java/com/cryptocarver/ui/WssSecurityController.java), [crypto/WssUsernameTokenOperations.java](../src/main/java/com/cryptocarver/crypto/WssUsernameTokenOperations.java), [fxml/wss_security.fxml](../src/main/resources/fxml/wss_security.fxml).

<a id="c055"></a>

#### C055 — Encrypt SOAP Body (WSS)

**Identidad:** `op_wss_encrypt`. **Bloque:** B11.

**Qué debe explicar:** Cifrar el cuerpo SOAP y transportar la clave de contenido mediante la clave del destinatario.

**Acciones que debe cubrir:** Seleccionar SOAP/certificado, elegir cifrado y transporte, cifrar y guardar.

**Campos y significado:**

- **SOAP input**: cuerpo que se cifra
- **Recipient certificate**: clave pública receptora
- **Data algorithm**: cifrado del contenido
- **Key transport**: protección de la clave
- **Output/report**: documento XML Encryption y detalles.

**Modos y opciones:** AES-128/256-GCM y AES-128/256-CBC; RSA-OAEP SHA-256 y RSA-OAEP SHA-1 legacy. Separar algoritmo de datos del de transporte.

**Condiciones e implementación:** Explicar las diferencias de autenticación por variante usando el atributo del catálogo. Firmar y cifrar son acciones distintas y pueden cubrir partes diferentes.

**Comprobación exigida:** Positivo: SOAP cifrado recuperable por receptor. Negativos: certificado de familia incompatible y SOAP inválido. Las dos selecciones tienen ayuda independiente.

**Fuentes de integración:** [WssSecurityController.java](../src/main/java/com/cryptocarver/ui/WssSecurityController.java), [crypto/WssEncryptionOperations.java](../src/main/java/com/cryptocarver/crypto/WssEncryptionOperations.java), [fxml/wss_security.fxml](../src/main/resources/fxml/wss_security.fxml).

<a id="c056"></a>

#### C056 — Decrypt SOAP Body (WSS)

**Identidad:** `op_wss_decrypt`. **Bloque:** B11.

**Qué debe explicar:** Recuperar el cuerpo SOAP cifrado con la clave privada del destinatario.

**Acciones que debe cubrir:** Seleccionar SOAP/keystore, descifrar y guardar.

**Campos y significado:**

- **Encrypted SOAP**: estructura recibida
- **Keystore path/password**: apertura del almacén
- **Private key password**: acceso al material si difiere
- **Recipient key**: selección que resuelve el handler
- **Output/report**: SOAP recuperado y resultado.

**Modos y opciones:** Algoritmos reconocidos del mensaje y soportados por WssEncryptionOperations. No pedir al usuario que elija parámetros que la acción lee del XML.

**Condiciones e implementación:** Descifrar no implica validar una firma SOAP ni confianza de un certificado. Explicar cómo se resuelve la clave y los errores de integridad/formato.

**Comprobación exigida:** Positivo: recuperación del fixture cifrado. Negativos: clave receptora distinta y contenido alterado en variante autenticada. Ayuda distingue passwords.

**Fuentes de integración:** [WssSecurityController.java](../src/main/java/com/cryptocarver/ui/WssSecurityController.java), [crypto/WssEncryptionOperations.java](../src/main/java/com/cryptocarver/crypto/WssEncryptionOperations.java), [fxml/wss_security.fxml](../src/main/resources/fxml/wss_security.fxml).

<a id="c057"></a>

#### C057 — Inspect Signed XML

**Identidad:** `op_xml_inspect`. **Bloque:** B11.

**Qué debe explicar:** Mostrar estructura y propiedades de una firma XML sin confundir la lectura con su validación.

**Acciones que debe cubrir:** Inspeccionar XML firmado y consultar informe.

**Campos y significado:**

- **Signed XML**: documento que se analiza
- **Signatures/references**: objetos localizados
- **Algorithms**: identificadores leídos
- **Certificates/levels**: datos presentes
- **Findings**: lectura y límites.

**Modos y opciones:** Las estructuras reconocidas por handleInspectSignedXML. Explicar cada sección del informe usando los datos que el motor devuelve.

**Condiciones e implementación:** Una firma localizada puede ser incorrecta. El nivel declarado o inferido de estructura no equivale a un nivel validado.

**Comprobación exigida:** Positivo: XML firmado con referencias conocidas. Negativos: XML mal formado y documento sin firma. El estado de ausencia se explica distinto del de firma inválida.

**Fuentes de integración:** [XmlSignatureSigningCoordinator.java](../src/main/java/com/cryptocarver/ui/XmlSignatureSigningCoordinator.java), [crypto/XMLSignatureOperations.java](../src/main/java/com/cryptocarver/crypto/XMLSignatureOperations.java), [fxml/xml_security.fxml](../src/main/resources/fxml/xml_security.fxml).

<a id="c058"></a>

#### C058 — RFC 3161 Timestamp

**Identidad:** `op_xml_tsa`. **Bloque:** B11.

**Qué debe explicar:** Solicitar, inspeccionar y validar un token RFC 3161 vinculado a una huella de datos.

**Acciones que debe cubrir:** Seleccionar archivo/hash/TSA, solicitar y guardar token; cargar token, inspeccionar y validar con truststore.

**Campos y significado:**

- **File**: bytes cuyo imprint se calcula
- **Hash**: SHA-256/384/512
- **TSA URL/auth**: destino y autenticación
- **Token**: respuesta a inspeccionar/validar
- **Truststore/password**: anclas usadas
- **Informe**: imprint, tiempo, firma y confianza comprobados.

**Modos y opciones:** Solicitar, inspeccionar y validar son subfichas independientes. Auth NONE/BASIC/BEARER si se comparte configuración del módulo.

**Condiciones e implementación:** Explicar qué petición usa red y qué análisis es local. Abrir ayuda no prueba una TSA. Validez temporal, coincidencia de imprint y confianza del firmante requieren explicaciones separadas.

**Comprobación exigida:** Positivo: token fixture con imprint conocido, sin depender de una TSA pública para tests. Negativos: archivo que no coincide y token alterado. Solicitud se prueba con entorno controlado.

**Fuentes de integración:** [XmlSignatureTimestampCoordinator.java](../src/main/java/com/cryptocarver/ui/XmlSignatureTimestampCoordinator.java), [fxml/xml_security.fxml](../src/main/resources/fxml/xml_security.fxml).

<a id="c059"></a>

#### C059 — Verify XML (XAdES)

**Identidad:** `op_xml_verify`. **Bloque:** B11.

**Qué debe explicar:** Comprobar una firma XML y explicar integridad, referencias y confianza según las evidencias aportadas.

**Acciones que debe cubrir:** Verificar XML y seleccionar perfil/truststore.

**Campos y significado:**

- **Signed XML**: artefacto recibido
- **Truststore/profile/password**: anclas de comprobación
- **Detached content**: original si la variante lo necesita
- **Report**: comprobación criptográfica, referencias, cadena y evidencia temporal.

**Modos y opciones:** Packaging y niveles que soporta el motor. Los parámetros leídos del documento no son selectores configurables de esta acción.

**Condiciones e implementación:** El informe debe separar resultado criptográfico de confianza y disponibilidad de evidencia. Aclarar el alcance de recursos externos según el verificador actual.

**Comprobación exigida:** Positivo: fixture firmado verificado con ancla adecuada. Negativos: contenido alterado y ancla diferente. La ayuda explica estados parciales/indeterminados.

**Fuentes de integración:** [XmlSignatureSigningCoordinator.java](../src/main/java/com/cryptocarver/ui/XmlSignatureSigningCoordinator.java), [crypto/XMLSignatureOperations.java](../src/main/java/com/cryptocarver/crypto/XMLSignatureOperations.java), [fxml/xml_security.fxml](../src/main/resources/fxml/xml_security.fxml).

<a id="c060"></a>

#### C060 — Verify SOAP (WSS)

**Identidad:** `op_wss_verify`. **Bloque:** B11.

**Qué debe explicar:** Verificar firmas WS-Security y explicar las partes SOAP y restricciones temporales que se han comprobado.

**Acciones que debe cubrir:** Cargar SOAP, aportar truststore/política temporal y verificar.

**Campos y significado:**

- **SOAP signed**: documento recibido
- **Truststore/password**: anclas
- **Timestamp policy**: ventana/condición temporal de la pantalla
- **Report**: firmas, referencias, certificado y checks temporales.

**Modos y opciones:** Algoritmos de firma permitidos por WssSecurityOperations; parámetros temporales realmente expuestos.

**Condiciones e implementación:** Una firma correcta en una parte SOAP no cubre automáticamente todo el mensaje. La ayuda debe nombrar las referencias comprobadas y la política de timestamp aplicada.

**Comprobación exigida:** Positivo: fixture con partes y tiempo compatibles. Negativos: Body alterado y timestamp fuera de la condición configurada. Ayuda explica cuál comprobación falló.

**Fuentes de integración:** [WssSecurityController.java](../src/main/java/com/cryptocarver/ui/WssSecurityController.java), [crypto/WssSecurityOperations.java](../src/main/java/com/cryptocarver/crypto/WssSecurityOperations.java), [fxml/wss_security.fxml](../src/main/resources/fxml/wss_security.fxml).

<a id="c061"></a>

#### C061 — Verify UsernameToken (WSS)

**Identidad:** `op_wss_username_verify`. **Bloque:** B11.

**Qué debe explicar:** Comprobar UsernameToken y sus condiciones temporales con la identidad y secreto esperados.

**Acciones que debe cubrir:** Verificar token en SOAP.

**Campos y significado:**

- **SOAP token**: mensaje recibido
- **Expected username/password**: expectativas de autenticación
- **Max age/skew**: límites temporales si están expuestos
- **Report**: tipo, digest y condiciones verificadas.

**Modos y opciones:** PasswordText y PasswordDigest leídos del token. La ayuda explica qué comprobaciones cambian entre tipos.

**Condiciones e implementación:** Indicar si se valida nonce/Created y si existe protección de repetición persistente; no atribuirla por el mero hecho de verificar un digest. Usar la semántica del motor.

**Comprobación exigida:** Positivo: token generado por la operación hermana con expectativas correctas. Negativos: password/usuario distinto y token caducado según la política.

**Fuentes de integración:** [WssSecurityController.java](../src/main/java/com/cryptocarver/ui/WssSecurityController.java), [crypto/WssUsernameTokenOperations.java](../src/main/java/com/cryptocarver/crypto/WssUsernameTokenOperations.java), [fxml/wss_security.fxml](../src/main/resources/fxml/wss_security.fxml).

### Certificados y contenedores

<a id="c062"></a>

#### C062 — Generate Certificate

**Identidad:** `op_cert_gen`. **Bloque:** B10.

**Qué debe explicar:** Generar un certificado de laboratorio o una CSR con identidad y clave seleccionadas.

**Acciones que debe cubrir:** Generar certificado y generar CSR; escoger clave nueva/local o alias PKCS#11.

**Campos y significado:**

- **CN/O/OU/L/ST/C/email**: componentes del sujeto
- **SAN DNS/IP**: identidades alternativas
- **Validity**: duración y unidad del campo
- **Key type/source**: par nuevo, PEM del área utilizada o alias token
- **Signature algorithm**: firma del certificado/CSR
- **Root CA**: perfil y extensiones CA
- **Output**: certificado/CSR y material que el handler entregue.

**Modos y opciones:** RSA-2048/4096, ECDSA-P256/P384, Local PEM (Parse Area), PKCS#11 Active Alias. Algoritmos efectivos deben seguir la clave; explicar diferencias certificado autofirmado/CSR.

**Condiciones e implementación:** Identidad, uso CA y SAN tienen significados distintos. Crear una CSR no emite un certificado confiable. La ayuda no debe anunciar campos privados como parte pública de la CSR.

**Comprobación exigida:** Positivo: CSR verificable y certificado con sujeto/SAN esperados. Negativos: identidad requerida ausente y clave incompatible. Cambiar source/root CA actualiza explicación.

**Fuentes de integración:** [CertificateCoordinator.java](../src/main/java/com/cryptocarver/ui/CertificateCoordinator.java), [crypto/CertificateAuthorityOperations.java](../src/main/java/com/cryptocarver/crypto/CertificateAuthorityOperations.java), [fxml/certificates.fxml](../src/main/resources/fxml/certificates.fxml).

<a id="c063"></a>

#### C063 — Issue Certificate from CSR

**Identidad:** `op_cert_issue`. **Bloque:** B10.

**Qué debe explicar:** Emitir un certificado a partir de una CSR usando la identidad y clave de una CA.

**Acciones que debe cubrir:** Emitir con perfil seleccionado.

**Campos y significado:**

- **CSR**: solicitud y prueba de posesión
- **CA certificate**: identidad del emisor
- **CA private key**: firma de emisión
- **Validity**: intervalo/duración
- **Signature algorithm**: algoritmo de emisión
- **Issuance profile**: conjunto de extensiones/usos
- **Path length**: límite CA cuando aplica
- **Output**: certificado emitido.

**Modos y opciones:** Perfiles de CertificateAuthorityOperations.IssuanceProfile. Mostrar definición de cada perfil y dependencia de path length.

**Condiciones e implementación:** La ayuda explica verificación de CSR y correspondencia CA/clave según núcleo. No describir el perfil leaf como si path length fuese obligatorio para todos.

**Comprobación exigida:** Positivo: CSR fixture emitida con perfil y extensiones esperadas. Negativos: CSR alterada y CA privada que no corresponde al certificado.

**Fuentes de integración:** [CertificateCoordinator.java](../src/main/java/com/cryptocarver/ui/CertificateCoordinator.java), [crypto/CertificateAuthorityOperations.java](../src/main/java/com/cryptocarver/crypto/CertificateAuthorityOperations.java), [fxml/certificates.fxml](../src/main/resources/fxml/certificates.fxml).

<a id="c064"></a>

#### C064 — Parse Certificate

**Identidad:** `op_cert_parse`. **Bloque:** B10.

**Qué debe explicar:** Leer un certificado y explicar sus campos sin confundirlos con una validación de confianza.

**Acciones que debe cubrir:** Parsear e inspeccionar.

**Campos y significado:**

- **Certificate**: PEM/representación aceptada
- **Subject/issuer**: identidad y emisor
- **Serial**: identificador
- **Validity**: fechas declaradas
- **Public key**: familia/tamaño
- **Extensions**: usos, SAN, políticas y restricciones
- **Fingerprint**: huella calculada
- **Output**: informe de lectura.

**Modos y opciones:** Formatos realmente aceptados por handleParseCertificate y su servicio. No aplicar formato global del payload a PEM si esa acción lo ignora.

**Condiciones e implementación:** Explicar datos declarados y campos ausentes; parser correcto no significa certificado confiable o vigente.

**Comprobación exigida:** Positivo: fixture con campos conocidos. Negativos: PEM truncado y clave pública presentada como certificado. Ayuda distingue subject de issuer.

**Fuentes de integración:** [CertificateCoordinator.java](../src/main/java/com/cryptocarver/ui/CertificateCoordinator.java), [fxml/certificates.fxml](../src/main/resources/fxml/certificates.fxml).

<a id="c065"></a>

#### C065 — Compare Certificates

**Identidad:** `op_cert_compare`. **Bloque:** B10.

**Qué debe explicar:** Comparar dos certificados y explicar igualdades/diferencias de identidad, clave y extensiones.

**Acciones que debe cubrir:** Comparar y consultar el informe.

**Campos y significado:**

- **Left/right certificate**: objetos comparados
- **Fingerprints**: igualdad de bytes
- **Public keys**: igualdad de clave
- **Subject/issuer/serial/validity/extensions**: diferencias de propiedades
- **Verdict**: dimensión de comparación.

**Modos y opciones:** Dimensiones implementadas por la comparación. Explicar que igualdad de sujeto y de certificado no son equivalentes.

**Condiciones e implementación:** No inferir relación de cadena porque dos certificados compartan algún campo. Informar de forma explícita qué se compara.

**Comprobación exigida:** Positivo: mismo certificado y dos certificados con misma clave/datos diferentes. Negativo: uno de los inputs inválido. Ayuda explica cada resultado de comparación.

**Fuentes de integración:** [CertificateCoordinator.java](../src/main/java/com/cryptocarver/ui/CertificateCoordinator.java), [fxml/certificates.fxml](../src/main/resources/fxml/certificates.fxml).

<a id="c066"></a>

#### C066 — Validate Certificate

**Identidad:** `op_cert_val`. **Bloque:** B10.

**Qué debe explicar:** Comprobar un certificado con el emisor aportado y explicar el alcance exacto de esa validación.

**Acciones que debe cubrir:** Validar certificado.

**Campos y significado:**

- **Certificate**: objeto objetivo
- **Issuer certificate**: material del emisor
- **Validity/signature checks**: condiciones implementadas
- **Report**: resultados individuales.

**Modos y opciones:** Comprobaciones de handleValidateCertificate. La validación de cadena completa es una operativa separada.

**Condiciones e implementación:** Especificar si el handler comprueba firma, fechas y relación issuer; no prometer revocación, uso o cadena si no los evalúa.

**Comprobación exigida:** Positivo: hijo y emisor compatibles. Negativos: emisor distinto y certificado temporalmente inválido cuando el check exista. El texto de ayuda refleja el alcance.

**Fuentes de integración:** [CertificateCoordinator.java](../src/main/java/com/cryptocarver/ui/CertificateCoordinator.java), [fxml/certificates.fxml](../src/main/resources/fxml/certificates.fxml).

<a id="c067"></a>

#### C067 — Certificate Chain

**Identidad:** `op_cert_chain`. **Bloque:** B10.

**Qué debe explicar:** Comprobar una cadena con anclas de confianza aportadas y explicar su construcción y resultado.

**Acciones que debe cubrir:** Validar cadena.

**Campos y significado:**

- **Chain**: conjunto de certificados y orden esperado
- **Trust anchors**: certificados confiados
- **Validation time**: instante que usa el motor si es configurable
- **Report**: ruta construida y comprobaciones.

**Modos y opciones:** Reglas de CertificateChainCoordinator y servicio de cadena. Aclarar cómo se suministran varias entradas y cuáles son anclas.

**Condiciones e implementación:** No presentar un certificado incluido en el conjunto como confiable solo por estar presente. Explicar el tratamiento de cadenas incompletas y anclas.

**Comprobación exigida:** Positivo: cadena fixture hasta ancla aportada. Negativos: intermedio ausente y ancla ajena. Ayuda explica el papel distinto de Chain y Trust anchors.

**Fuentes de integración:** [CertificateChainCoordinator.java](../src/main/java/com/cryptocarver/ui/CertificateChainCoordinator.java), [fxml/certificates.fxml](../src/main/resources/fxml/certificates.fxml).

<a id="c068"></a>

#### C068 — PAdES PDF Signatures

**Identidad:** `op_pades`. **Bloque:** B10.

**Qué debe explicar:** Firmar, inspeccionar y validar PDF con un perfil PAdES concreto y evidencias opcionales.

**Acciones que debe cubrir:** Elegir PDF/destino, firmar, inspeccionar, validar y guardar informes.

**Campos y significado:**

- **PDF input/output**: archivos leídos/creados
- **Source local/PKCS11**: clave firmante
- **PKCS12/password/alias**: selección local/token
- **Profile**: Baseline-B/T/LT/LTA
- **TSA**: timestamp requerido por perfil
- **Truststore**: anclas para validación
- **CRL evidence**: material aportado
- **Visible signature**: apariencia, página y posición si se habilita
- **Report**: firma y validación.

**Modos y opciones:** Baseline-B, T, LT y LTA. La firma visible describe apariencia del PDF, no añade confianza por sí misma.

**Condiciones e implementación:** Explicar las evidencias y operaciones externas necesarias por perfil y cómo se comunican fallos. Diferenciar inspección y validación; mantener el contenido PDF fuera del contexto de ayuda.

**Comprobación exigida:** Positivo: firma local y validación del fixture con evidencia disponible. Negativos: PDF inválido y perfil temporal sin evidencia. UI verifica campos de apariencia al activar/desactivar.

**Fuentes de integración:** [PadesController.java](../src/main/java/com/cryptocarver/ui/PadesController.java), [crypto/PadesOperations.java](../src/main/java/com/cryptocarver/crypto/PadesOperations.java), [fxml/pades.fxml](../src/main/resources/fxml/pades.fxml).

<a id="c069"></a>

#### C069 — ASiC-S Containers

**Identidad:** `op_asic_s`. **Bloque:** B10.

**Qué debe explicar:** Crear e inspeccionar contenedores ASiC con documentos y firmas; cubrir ASiC-E además de ASiC-S si se ofrece en la misma pantalla.

**Acciones que debe cubrir:** Crear S, seleccionar varios payloads y crear E, inspeccionar y elegir fuente/perfil.

**Campos y significado:**

- **Payload path(s)**: documentos incluidos
- **Output path**: contenedor
- **Format**: CAdES/XAdES
- **Level**: perfil baseline
- **Key source/PKCS12/password/alias**: firma
- **TSA**: evidencia temporal
- **Truststore/revocation files**: validación disponible
- **Inventory/report**: entradas, firmas y hallazgos.

**Modos y opciones:** Formatos CAdES/XAdES y niveles ofrecidos por AsicController; ASiC-S y ASiC-E tienen subfichas propias y distinta cardinalidad de payload.

**Condiciones e implementación:** Explicar qué entrada del ZIP representa cada documento y qué firmas los cubren. No tratar contenedor legible como firma válida. Perfiles y material deben coincidir con la generación real.

**Comprobación exigida:** Positivo: S con un payload y E con varios en las combinaciones implementadas. Negativos: payload ausente y contenedor mal formado. Ayuda identifica diferencias S/E.

**Fuentes de integración:** [AsicController.java](../src/main/java/com/cryptocarver/ui/AsicController.java), [crypto/AsicOperations.java](../src/main/java/com/cryptocarver/crypto/AsicOperations.java), [fxml/asic.fxml](../src/main/resources/fxml/asic.fxml).

<a id="c070"></a>

#### C070 — CMS/PKCS#7 Operations

**Identidad:** `op_cert_cms`. **Bloque:** B10.

**Qué debe explicar:** Crear/verificar firmas CMS/CAdES y cifrar/descifrar contenidos CMS.

**Acciones que debe cubrir:** Firmar, verificar, cifrar, descifrar y upgrade CAdES LT cuando se ofrece.

**Campos y significado:**

- **Input**: datos u objeto CMS según acción
- **Detached**: separación del payload
- **CAdES BES/T**: atributos y evidencia solicitada
- **TSA URL**: sello temporal
- **Sign cert/key/source/alias**: firmante
- **Verify data**: contenido original detached
- **Recipient certificate**: cifrado
- **Private key**: descifrado
- **Online revocation**: política externa de validación
- **Output**: contenedor o informe.

**Modos y opciones:** SignedData, EnvelopedData y perfiles CAdES de la pantalla. Local/PKCS11 tiene dependencias por acción; detached obliga a explicar la entrega del contenido separado.

**Condiciones e implementación:** La ayuda debe describir el formato de entrada que usa cada botón. Upgrade LT es una acción diferente de firmar BES y requiere evidencias concretas.

**Comprobación exigida:** Positivo: detached con contenido externo verificado y cifrado recuperable. Negativos: payload detached distinto y receptor incorrecto. Ayuda no oculta la opción de revocación online.

**Fuentes de integración:** [CmsCoordinator.java](../src/main/java/com/cryptocarver/ui/CmsCoordinator.java), [CmsLogic.java](../src/main/java/com/cryptocarver/ui/CmsLogic.java), [crypto/CMSOperations.java](../src/main/java/com/cryptocarver/crypto/CMSOperations.java), [fxml/certificates.fxml](../src/main/resources/fxml/certificates.fxml).

<a id="c071"></a>

#### C071 — CMS Inspector

**Identidad:** `op_cms_inspector`. **Bloque:** B10.

**Qué debe explicar:** Inspeccionar estructura CMS y validar SignedData con contenido y confianza aportados.

**Acciones que debe cubrir:** Cargar CMS/content/truststore, inspeccionar, validar y exportar informe.

**Campos y significado:**

- **CMS input**: bytes codificados o archivo
- **Detached content**: original separado
- **Detached toggle**: condición de suministro
- **Truststore/password**: anclas
- **Structure**: objetos/firmantes/certificados
- **Validation report**: firmas y confianza comprobadas.

**Modos y opciones:** Tipos CMS reconocidos y validación SignedData soportada; explicar qué tipos solo se inspeccionan.

**Condiciones e implementación:** No prometer descifrado de EnvelopedData desde la acción de inspección. Reutilizar diagnóstico cuando falta el contenido detached.

**Comprobación exigida:** Positivo: SignedData fixture inspeccionado y validado. Negativos: detached sin contenido y estructura truncada. Cada estado del informe tiene definición.

**Fuentes de integración:** [CmsInspectorController.java](../src/main/java/com/cryptocarver/ui/CmsInspectorController.java), [fxml/cms_inspector.fxml](../src/main/resources/fxml/cms_inspector.fxml).

### JOSE

<a id="c072"></a>

#### C072 — JWT (Signed)

**Identidad:** `op_jose_jwt`. **Bloque:** B09.

**Qué debe explicar:** Construir y validar JWT/JWS firmados, incluyendo serializaciones y variantes de payload externo.

**Acciones que debe cubrir:** Generar JWT, validar/decodificar, JWS detached, segundo firmante en General JSON y Nested sign+encrypt en su sección.

**Campos y significado:**

- **Algoritmo**: firma/MAC y familia compatible
- **Key/secret format**: material y lectura explícita del secreto
- **Payload/claims builder**: JSON y claims iss/aud/sub/iat/nbf/exp/jti disponibles
- **Protected header**: metadatos firmados
- **Serialization/signer 2**: estructura y mult firma
- **Validate key/allowed algorithms**: confianza y lista permitida
- **Expected issuer/audience/type/cty/nonce**: expectativas
- **Clock skew**: tolerancia temporal
- **crit/embedded-key/RFC9068/hash-binding**: opciones avanzadas con su efecto
- **Detached payload**: contenido externo original.

**Modos y opciones:** JWS HS256/384/512, RS256/384/512, ES256/ES256K/ES384/ES512, PS256/384/512, EdDSA y none de la lista actual. Compact/Flattened/General según el composer; none se explica con su política explícita.

**Condiciones e implementación:** Cada ajuste avanzado del inventario necesita definición propia, valor predeterminado y efecto. Decodificar no valida. La confianza en una clave de cabecera y omitir crit son decisiones explícitas, con el alcance del código. Nested hereda ayuda JWE pero mantiene las dos capas.

**Comprobación exigida:** Positivo: token con expectativas y firma compatibles; detached verificable; General con firmantes presentes. Negativos: algoritmo no permitido, claim esperado distinto y payload externo alterado. La ayuda cubre todos los controles Advanced Validation.

**Fuentes de integración:** [JoseJwtCoordinator.java](../src/main/java/com/cryptocarver/ui/JoseJwtCoordinator.java), [JoseInitializationCoordinator.java](../src/main/java/com/cryptocarver/ui/JoseInitializationCoordinator.java), [fxml/jose.fxml](../src/main/resources/fxml/jose.fxml).

<a id="c073"></a>

#### C073 — JWE (Encrypted)

**Identidad:** `op_jose_jwe`. **Bloque:** B02.

**Qué debe explicar:** Cifrar un payload con un algoritmo de gestión de claves y otro de cifrado del contenido, y recuperar un JWE recibido.

**Acciones que debe cubrir:** Cifrar, descifrar, elegir serialización/destinatarios e inspeccionar el desglose visual.

**Campos y significado:**

- **alg**: gestión de clave
- **enc**: cifrado de contenido
- **Key input/secret encoding**: pública, JWK, secreto o password según alg
- **Serialization**: Compact/Flattened/General JSON
- **kid/typ/cty**: identificadores de cabecera
- **apu/apv**: contexto de participantes para ECDH
- **Custom header**: JSON adicional con nombres reservados
- **Compression**: transformación previa si se activa
- **PBES2 iterations**: coste de derivación
- **AAD**: dato externo autenticado
- **Decrypt key**: privada/secreto/password
- **Breakdown**: encrypted_key/IV/ciphertext/tag/cabecera.

**Modos y opciones:** RSA1_5 y RSA-OAEP SHA-1/256/384/512; ECDH-ES y +A128/192/256KW; A128/192/256KW y GCMKW; PBES2-HS256+A128KW, HS384+A192KW, HS512+A256KW; dir. enc: A128/192/256GCM y A128CBC-HS256/A192CBC-HS384/A256CBC-HS512.

**Condiciones e implementación:** Usar JweComposer. AAD externo requiere JSON. General con múltiples destinatarios lee alg por JWK y kid/apu/apv por destinatario. dir depende del tamaño de la CEK requerida por enc. Diferenciar IV/tag del contenido frente a IV/tag de GCMKW. No mostrar CEK privada automáticamente.

**Comprobación exigida:** Positivo: caso RSA, dir y General multidestinatario de los fixtures. Negativos: AAD externo con Compact, tamaño de secreto incompatible y tag alterado. Help cambia con alg/enc/serialización.

**Fuentes de integración:** [JoseJweCoordinator.java](../src/main/java/com/cryptocarver/ui/JoseJweCoordinator.java), [crypto/JweComposer.java](../src/main/java/com/cryptocarver/crypto/JweComposer.java), [fxml/jose.fxml](../src/main/resources/fxml/jose.fxml).

<a id="c074"></a>

#### C074 — JWK (Keys)

**Identidad:** `op_jose_jwk`. **Bloque:** B09.

**Qué debe explicar:** Representar claves como JWK, gestionar JWKS y explicar los metadatos de uso sin confundirlos con material criptográfico.

**Acciones que debe cubrir:** PEM→JWK, JWK→PEM, secret→oct, inspeccionar metadatos, thumbprint, cargar/guardar JWKS, añadir/rotar/eliminar y exportar conjunto público.

**Campos y significado:**

- **Key type**: RSA/EC/OKP/oct disponibles
- **Curve**: variante compatible
- **Secret encoding**: lectura del material oct
- **Input**: PEM/JWK/secreto según acción
- **kid**: identificador
- **use**: uso declarado
- **key_ops**: operaciones declaradas
- **JWKS**: conjunto de claves
- **Rotation algorithm**: tipo de clave nueva
- **Output**: representación pública/privada.

**Modos y opciones:** Curvas y políticas de JoseJwkPolicy/JoseJwkCoordinator. Las claves oct no se convierten a una clave pública PEM como RSA/EC.

**Condiciones e implementación:** Explicar qué propiedades son material y cuáles son metadatos. Exportar público debe retirar privados. Thumbprint usa el subconjunto canónico de propiedades que implementa el método, no todo el JSON.

**Comprobación exigida:** Positivo: RSA pública PEM↔JWK y oct con formato explícito; exportación JWKS pública. Negativos: curva/tipo incompatible y formato oct incorrecto. Visibilidad privada se conserva al abrir ayuda.

**Fuentes de integración:** [JoseJwkCoordinator.java](../src/main/java/com/cryptocarver/ui/JoseJwkCoordinator.java), [crypto/JoseJwkPolicy.java](../src/main/java/com/cryptocarver/crypto/JoseJwkPolicy.java), [crypto/JoseKeyMaterial.java](../src/main/java/com/cryptocarver/crypto/JoseKeyMaterial.java), [fxml/jose.fxml](../src/main/resources/fxml/jose.fxml).

<a id="c075"></a>

#### C075 — JWA (Algorithms)

**Identidad:** `op_jose_jwa`. **Bloque:** B09.

**Qué debe explicar:** Consultar algoritmos JOSE y su relación con las operaciones soportadas.

**Acciones que debe cubrir:** Consultar catálogo/tablas y seleccionar información de una familia cuando la UI lo permita.

**Campos y significado:**

- **Nombre de algoritmo**: código JOSE
- **Familia**: firma, gestión de clave o cifrado
- **Tipo/tamaño de clave**: requisitos
- **Estado/capacidad**: compatibilidad en esta aplicación
- **Referencia**: origen de la definición.

**Modos y opciones:** Separar JWS alg, JWE alg y JWE enc. Usar catálogos compartidos con los composers; cada código tiene definición propia o familia común más diferencias de tamaño/hash.

**Condiciones e implementación:** No anunciar disponibilidad porque una biblioteca conozca el nombre. El catálogo de ayuda refleja las acciones alcanzables en CryptoCarver.

**Comprobación exigida:** Positivo: cada opción de JWT/JWE encuentra explicación JWA compatible. Negativo: algoritmo conocido pero no soportado aparece como tal si se lista.

**Fuentes de integración:** [JOSEController.java](../src/main/java/com/cryptocarver/ui/JOSEController.java), [JoseInitializationCoordinator.java](../src/main/java/com/cryptocarver/ui/JoseInitializationCoordinator.java), [crypto/JweComposer.java](../src/main/java/com/cryptocarver/crypto/JweComposer.java), [fxml/jose.fxml](../src/main/resources/fxml/jose.fxml).

<a id="c076"></a>

#### C076 — Token Inspector

**Identidad:** `op_jose_insp`. **Bloque:** B09.

**Qué debe explicar:** Descomponer tokens y mostrar cabeceras/payload/capas con el alcance de lectura del inspector.

**Acciones que debe cubrir:** Inspeccionar y consultar detalle de segmentos/capas.

**Campos y significado:**

- **Token**: entrada compacta o JSON reconocida
- **Header**: parámetros declarados
- **Payload**: contenido visible si existe
- **Signature/encrypted parts**: componentes estructurales
- **Findings**: parseo y limitaciones.

**Modos y opciones:** JWT/JWS/JWE/Nested y serializaciones reconocidas por JoseInspectorCoordinator. Para contenido cifrado explicar qué puede leerse sin clave.

**Condiciones e implementación:** Inspección no da un veredicto de firma ni confianza si no se realizó esa operación. Decodificación Base64URL y descifrado requieren explicaciones diferentes.

**Comprobación exigida:** Positivo: token fixture con partes conocidas. Negativos: número de segmentos inválido y JSON incompatible. Ayuda identifica la capa consultada.

**Fuentes de integración:** [JoseInspectorCoordinator.java](../src/main/java/com/cryptocarver/ui/JoseInspectorCoordinator.java), [fxml/jose.fxml](../src/main/resources/fxml/jose.fxml).

### COSE

<a id="c077"></a>

#### C077 — COSE_Sign1

**Identidad:** `op_cose_sign1`. **Bloque:** B09.

**Qué debe explicar:** Firmar y verificar una estructura CBOR COSE de un solo firmante.

**Acciones que debe cubrir:** Sign1 y Verify1.

**Campos y significado:**

- **Algorithm**: identificador COSE de firma
- **Payload**: datos que se firman
- **Private/public key**: parte requerida según acción
- **External AAD**: contexto que participa en la firma
- **Encoded COSE**: objeto para verificar
- **Format**: representación de los bytes
- **Report**: estructura y resultado.

**Modos y opciones:** ES256/384/512, PS256/384/512 y EDDSA del panel. Explicar cabeceras protegidas/no protegidas según la estructura generada.

**Condiciones e implementación:** Usar COSEOperations para formatos y parámetros reales. External AAD debe coincidir al verificar; no confundir CBOR con el algoritmo de firma.

**Comprobación exigida:** Positivo: Sign1 verificable con AAD conocido. Negativos: payload/AAD alterado y clave incompatible. Ayuda cambia de privada a pública por acción.

**Fuentes de integración:** [COSEController.java](../src/main/java/com/cryptocarver/ui/COSEController.java), [crypto/COSEOperations.java](../src/main/java/com/cryptocarver/crypto/COSEOperations.java), [fxml/cose.fxml](../src/main/resources/fxml/cose.fxml).

<a id="c078"></a>

#### C078 — COSE_Mac0

**Identidad:** `op_cose_mac0`. **Bloque:** B09.

**Qué debe explicar:** Calcular y verificar una estructura COSE autenticada con una clave compartida.

**Acciones que debe cubrir:** Generar clave, Mac0 y Verify Mac0.

**Campos y significado:**

- **Algorithm**: MAC seleccionado
- **Secret key**: bytes del secreto
- **Payload**: datos autenticados
- **External AAD**: contexto
- **COSE input**: objeto recibido
- **Result**: estructura o veredicto.

**Modos y opciones:** HS256, HS384 y HS512 del panel. Obtener longitudes y codificación de clave del handler.

**Condiciones e implementación:** La generación de clave es una acción; consultar ayuda no la ejecuta. Explicar qué se autentica en la estructura y qué dato externo debe repetirse.

**Comprobación exigida:** Positivo: Mac0 de fixture verificado. Negativos: secreto/AAD incorrectos y objeto mal formado.

**Fuentes de integración:** [COSEController.java](../src/main/java/com/cryptocarver/ui/COSEController.java), [crypto/COSEOperations.java](../src/main/java/com/cryptocarver/crypto/COSEOperations.java), [fxml/cose.fxml](../src/main/resources/fxml/cose.fxml).

<a id="c079"></a>

#### C079 — COSE_Encrypt0

**Identidad:** `op_cose_encrypt0`. **Bloque:** B09.

**Qué debe explicar:** Cifrar/descifrar una estructura COSE con una clave simétrica y autenticación del contenido.

**Acciones que debe cubrir:** Generar clave, Encrypt0 y Decrypt0.

**Campos y significado:**

- **Algorithm**: tamaño AES-GCM
- **Secret key**: material compatible
- **Payload**: texto/bytes interpretados por el handler
- **IV/nonce**: valor usado/guardado en la estructura
- **External AAD**: datos externos
- **COSE input/output**: objeto serializado
- **Report**: resultado.

**Modos y opciones:** A128GCM, A192GCM y A256GCM. Diferenciar algoritmo COSE y representación de entrada/salida.

**Condiciones e implementación:** Explicar si el nonce se genera o se aporta y dónde se conserva. La ayuda del descifrado distingue datos del objeto y contexto externo requerido.

**Comprobación exigida:** Positivo: Encrypt0→Decrypt0 con AAD. Negativos: tag alterado y secreto de tamaño incompatible. Selección del algoritmo actualiza tamaño.

**Fuentes de integración:** [COSEController.java](../src/main/java/com/cryptocarver/ui/COSEController.java), [crypto/COSEOperations.java](../src/main/java/com/cryptocarver/crypto/COSEOperations.java), [fxml/cose.fxml](../src/main/resources/fxml/cose.fxml).

### Pagos y EMV

<a id="c080"></a>

#### C080 — Clear PIN Blocks

**Identidad:** `op_pay_clear_pin`. **Bloque:** B03.

**Qué debe explicar:** Construir o interpretar un bloque PIN en claro con el formato y datos necesarios.

**Acciones que debe cubrir:** Codificar/encode, decodificar/decode y seleccionar formato/relleno.

**Campos y significado:**

- **PIN**: dígitos y longitud
- **PAN**: número de cuenta requerido por el formato
- **Format**: construcción del bloque
- **Padding**: relleno configurable cuando se permite
- **Clear block**: bytes Hex que se interpretan
- **Result**: bloque o PIN recuperado.

**Modos y opciones:** Todos los valores de PinBlockFormat.displayNames, incluidos ISO0/1/2/3/4 y formatos heredados. Cada formato utiliza usesPan, paddingOptions y defaultPadding como fuente.

**Condiciones e implementación:** Explicar qué formatos usan PAN y qué parte toma la implementación. Distinguir relleno fijo, aleatorio y configurable. No describir bloque en claro como dato cifrado.

**Comprobación exigida:** Positivo: fixture por familia/formato y decodificación consistente. Negativos: PIN inválido, PAN requerido ausente y relleno no permitido. La ayuda cambia por formato y dirección.

**Fuentes de integración:** [PinBlockCoordinator.java](../src/main/java/com/cryptocarver/ui/PinBlockCoordinator.java), [crypto/PinBlockFormat.java](../src/main/java/com/cryptocarver/crypto/PinBlockFormat.java), [fxml/payments.fxml](../src/main/resources/fxml/payments.fxml).

<a id="c081"></a>

#### C081 — Encrypted PIN Blocks

**Identidad:** `op_pay_enc_pin`. **Bloque:** B03.

**Qué debe explicar:** Proteger/recuperar un bloque PIN y explicar las diferencias entre TDES y el tratamiento ISO-4 con AES.

**Acciones que debe cubrir:** Codificar/proteger y descifrar/decodificar.

**Campos y significado:**

- **Format**: formato PIN
- **PIN/PAN**: datos de construcción
- **Key**: clave de protección compatible
- **Encrypted block**: material recibido
- **Resultado**: bloque protegido o PIN
- **Estado protected**: indica si se suministró clave.

**Modos y opciones:** TDES en formatos soportados por esa rama y AES para ISO-4. Usar el algoritmo de la ruta real, no el selector global simétrico.

**Condiciones e implementación:** El handler actual puede producir bloque sin protección si no se aporta clave en la codificación: la ayuda debe explicar ese estado y no etiquetarlo cifrado. Cualquier cambio de ese comportamiento se separa del encargo de ayuda.

**Comprobación exigida:** Positivo: protección/recuperación de fixture TDES e ISO-4. Negativos: tamaño de clave incompatible y bloque mal formado. Caso sin clave verifica explicación de estado.

**Fuentes de integración:** [PinBlockCoordinator.java](../src/main/java/com/cryptocarver/ui/PinBlockCoordinator.java), [crypto/PaymentOperations.java](../src/main/java/com/cryptocarver/crypto/PaymentOperations.java), [fxml/payments.fxml](../src/main/resources/fxml/payments.fxml).

<a id="c082"></a>

#### C082 — PIN Generation

**Identidad:** `op_pay_pin_gen`. **Bloque:** B14.

**Qué debe explicar:** Calcular o comprobar valores PIN según los métodos IBM 3624 y PVV implementados.

**Acciones que debe cubrir:** Generar PIN IBM 3624, verificar PIN, calcular offset, calcular PVV y derivar PIN para PVV cuando esa acción se ofrece.

**Campos y significado:**

- **PVK**: clave del método
- **PAN**: datos de validación
- **Decimalization/conversion table**: mapeo usado por IBM
- **Start/length/pad**: selección y completado de datos
- **Offset**: ajuste respecto al PIN natural
- **PIN**: candidato o elegido
- **Key index**: índice PVV
- **Target PVV**: objetivo para la acción de derivación
- **Result**: PIN/offset/PVV/veredicto.

**Modos y opciones:** Subficha independiente por acción de PinGenerationCoordinator. No reutilizar el significado de offset como si fuera índice PVV.

**Condiciones e implementación:** Explicar bases, unidades, rango y posición inicial según el código, especialmente start/length. La acción de derivación debe indicar lo que busca y sus límites.

**Comprobación exigida:** Positivo: fixtures IBM y PVV con resultados conocidos. Negativos: tabla de decimalización inválida y PVK/PIN incorrectos. Ayuda cambia por subpanel.

**Fuentes de integración:** [PinGenerationCoordinator.java](../src/main/java/com/cryptocarver/ui/PinGenerationCoordinator.java), [crypto/PaymentOperations.java](../src/main/java/com/cryptocarver/crypto/PaymentOperations.java), [fxml/payments.fxml](../src/main/resources/fxml/payments.fxml).

<a id="c083"></a>

#### C083 — CVV Operations

**Identidad:** `op_pay_cvv`. **Bloque:** B14.

**Qué debe explicar:** Calcular los valores de verificación de tarjeta que implementa el laboratorio y explicar qué datos utiliza cada variante.

**Acciones que debe cubrir:** Generar CVV/CVV2/iCVV/dCVV y verificar si la pantalla ofrece la acción.

**Campos y significado:**

- **CVK A/B**: componentes de clave
- **PAN**: número de cuenta
- **Expiry**: formato exacto de fecha que lee el método
- **Service code**: dato usado/modificado por la variante
- **Type**: selección de CVV
- **ATC**: contador para dCVV cuando aplica
- **Result**: dígitos de verificación.

**Modos y opciones:** CVV (Magnetic Stripe), CVV2 (Card Printed), iCVV (Chip) y dCVV (Dynamic). Explicar campos ignorados o fijados por la variante con su valor efectivo.

**Condiciones e implementación:** Explicar fechas y longitudes desde PaymentOperations. No asumir el mismo service code para las cuatro acciones. PAN y claves no forman parte del contexto persistido de ayuda.

**Comprobación exigida:** Positivo: fixture de cada variante. Negativos: fecha de formato incorrecto, longitud de CVK inválida y ATC ausente para la variante que lo requiera.

**Fuentes de integración:** [CvvCoordinator.java](../src/main/java/com/cryptocarver/ui/CvvCoordinator.java), [crypto/PaymentOperations.java](../src/main/java/com/cryptocarver/crypto/PaymentOperations.java), [fxml/payments.fxml](../src/main/resources/fxml/payments.fxml).

<a id="c084"></a>

#### C084 — DUKPT TDES / AES

**Identidad:** `op_pay_dukpt`. **Bloque:** B14.

**Qué debe explicar:** Derivar claves por transacción desde BDK y KSN y explicar usos/contadores de TDES y AES.

**Acciones que debe cubrir:** Derivar e inspeccionar KSN; seleccionar uso/tipo de clave; cifrar/descifrar bloque PIN en la rama AES.

**Campos y significado:**

- **BDK**: clave base
- **KSN**: serial y contador de transacción
- **Scheme**: TDES o AES
- **TDES usage**: variante PIN/MAC request/MAC response/data
- **AES usage**: función de la clave derivada
- **AES key type**: longitud de salida
- **AES PIN block/operation**: datos y dirección
- **Result**: claves/valores y metadatos.

**Modos y opciones:** TDES con KSN de 10 bytes y AES con KSN de 12. Usos AES: datos encrypt/decrypt, PIN, MAC generation/verification/both, key encryption y key derivation; tipos AES-128/192/256.

**Condiciones e implementación:** Separar tamaño de BDK, KSN y clave derivada. Explicar contador/ID con el parser. No presentar todos los usos como equivalentes ni usar un KSN del otro esquema.

**Comprobación exigida:** Positivo: vectores independientes TDES y AES del repositorio. Negativos: KSN del esquema incorrecto y uso/tipo incompatible. Cambio TDES↔AES actualiza visibilidad y ayuda.

**Fuentes de integración:** [DukptCoordinator.java](../src/main/java/com/cryptocarver/ui/DukptCoordinator.java), [fxml/payments.fxml](../src/main/resources/fxml/payments.fxml).

<a id="c085"></a>

#### C085 — EMV TLV Inspector

**Identidad:** `op_pay_emv_tlv`. **Bloque:** B14.

**Qué debe explicar:** Leer TLV EMV y explicar tags, longitudes, valores y estructuras anidadas.

**Acciones que debe cubrir:** Parsear/inspeccionar y consultar tag/valor; cargar ejemplos cuando exista.

**Campos y significado:**

- **TLV input**: bytes Hex
- **Tag**: identificador
- **Length**: bytes declarados
- **Value**: contenido
- **Constructed flag**: estructura anidada
- **Decoded fields**: interpretación por tag
- **Result**: árbol/informe.

**Modos y opciones:** Tags y decodificaciones del catálogo EmvTlv y EMVController. Aclarar que valores no reconocidos pueden conservarse como bytes.

**Condiciones e implementación:** Explicar offsets/longitudes y truncamiento. Leer un ARQC como valor TLV no verifica su autenticidad.

**Comprobación exigida:** Positivo: fixture anidado con longitudes conocidas. Negativos: longitud que excede entrada y tag truncado. Ayuda distingue bytes crudos e interpretación.

**Fuentes de integración:** [EMVController.java](../src/main/java/com/cryptocarver/ui/EMVController.java), [crypto/EmvTlv.java](../src/main/java/com/cryptocarver/crypto/EmvTlv.java), [fxml/emv.fxml](../src/main/resources/fxml/emv.fxml).

<a id="c086"></a>

#### C086 — EMV Operations

**Identidad:** `op_pay_emv_ops`. **Bloque:** B14.

**Qué debe explicar:** Reunir las operaciones EMV de claves y criptogramas, cada una con sus parámetros y perfiles propios.

**Acciones que debe cubrir:** Derivación de sesión, generar/verificar ARQC, ARPC, issuer script, Visa HCE, Mastercard Data Storage, ICC Dynamic Number, CAP y Track 2.

**Campos y significado:**

- **Datos comunes**: PAN/PSN, contadores, claves de distinto uso y datos de transacción
- **Cada subpanel**: campos descritos individualmente en los casos auxiliares EMV del apartado siguiente
- **Salidas**: clave/criptograma/datos/informe según acción.

**Modos y opciones:** No basta una ayuda genérica «EMV». Cada acción del anexo recibe actionId propio y la ficha auxiliar correspondiente.

**Condiciones e implementación:** Importes, fechas, códigos de país/moneda y bytes Hex deben indicar unidades y codificación. Datos introducidos en campos separados y bloque terminal raw pueden obedecer ramas distintas.

**Comprobación exigida:** Positivo/negativo: aplicar los casos auxiliares EMV. Revisión considera incompleta esta entrada si solo explica un criptograma o comparte descripciones que no corresponden a los subpaneles.

**Fuentes de integración:** [EmvSessionKeyCoordinator.java](../src/main/java/com/cryptocarver/ui/EmvSessionKeyCoordinator.java), [EmvArqcCoordinator.java](../src/main/java/com/cryptocarver/ui/EmvArqcCoordinator.java), [EmvArpcCoordinator.java](../src/main/java/com/cryptocarver/ui/EmvArpcCoordinator.java), [EmvSecureMessagingCoordinator.java](../src/main/java/com/cryptocarver/ui/EmvSecureMessagingCoordinator.java), [EmvHceCoordinator.java](../src/main/java/com/cryptocarver/ui/EmvHceCoordinator.java), [EmvTrack2Coordinator.java](../src/main/java/com/cryptocarver/ui/EmvTrack2Coordinator.java), [EMVController.java](../src/main/java/com/cryptocarver/ui/EMVController.java), [fxml/emv.fxml](../src/main/resources/fxml/emv.fxml).

<a id="c087"></a>

#### C087 — EMV Offline Data Authentication

**Identidad:** `op_pay_emv_oda`. **Bloque:** B14.

**Qué debe explicar:** Recuperar la cadena de certificados EMV y comprobar SDA/DDA/CDA según los datos aportados.

**Acciones que debe cubrir:** Recuperar clave issuer/ICC, verificar SDA, DDA y CDA y cargar ejemplos.

**Campos y significado:**

- **CA modulus/exponent**: ancla RSA
- **Issuer cert/remainder/exponent**: recuperación issuer
- **ICC cert/remainder/exponent**: recuperación ICC
- **PAN**: comprobación asociada
- **SSAD/static data**: SDA
- **SDAD/terminal data**: DDA
- **CID/transaction data**: CDA
- **Result**: claves recuperadas y comprobaciones.

**Modos y opciones:** SDA, DDA y CDA con dependencias de sus estructuras. Cada subacción debe explicar qué parte de cadena necesita.

**Condiciones e implementación:** Certificados EMV recuperables tienen estructura propia y no son certificados X.509 PEM. Explicar fragmentos/remainder y qué hash/firma verifica cada acción.

**Comprobación exigida:** Positivo: fixtures ODA independientes del repositorio. Negativos: remainder incorrecto, datos estáticos alterados y SDAD incompatible. Ayuda identifica el paso que necesita cada campo.

**Fuentes de integración:** [EmvOdaCoordinator.java](../src/main/java/com/cryptocarver/ui/EmvOdaCoordinator.java), [crypto/EmvOdaOperations.java](../src/main/java/com/cryptocarver/crypto/EmvOdaOperations.java), [fxml/emv.fxml](../src/main/resources/fxml/emv.fxml).

<a id="c088"></a>

#### C088 — Thales Variant LMK

**Identidad:** `op_pay_thales_lmk`. **Bloque:** B14.

**Qué debe explicar:** Proteger/recuperar claves bajo una variante LMK de laboratorio y explicar esquema y tipo de clave.

**Acciones que debe cubrir:** Wrap, unwrap y comprobar KCV cuando la acción lo utiliza.

**Campos y significado:**

- **LMK**: material protector
- **Key type code**: variante/uso
- **Scheme**: representación U/T/Z
- **Clear key**: clave a proteger
- **Cryptogram**: material a recuperar
- **Check value**: expectativa/comprobación
- **Component**: tratamiento por componentes
- **Result**: bloque/clave/KCV.

**Modos y opciones:** Esquemas U, T y Z del panel y tipos de ThalesLmkOperations. Explicar prefijo/esquema separado del código de uso.

**Condiciones e implementación:** No decir que se conectó a un payShield. El cálculo utiliza claves en claro del laboratorio. Diferenciar el resultado de un Thales Key Block.

**Comprobación exigida:** Positivo: fixture wrap/unwrap por esquema soportado. Negativos: código desconocido y LMK de tamaño inválido. Ayuda explica component y KCV esperado.

**Fuentes de integración:** [PaymentKeyBlockCoordinator.java](../src/main/java/com/cryptocarver/ui/PaymentKeyBlockCoordinator.java), [crypto/ThalesLmkOperations.java](../src/main/java/com/cryptocarver/crypto/ThalesLmkOperations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c089"></a>

#### C089 — Thales Key Block

**Identidad:** `op_pay_thales_key_block`. **Bloque:** B14.

**Qué debe explicar:** Interpretar y, donde se ofrece, proteger/recuperar un Thales Key Block y explicar sus atributos.

**Acciones que debe cubrir:** Analizar y operaciones wrap/unwrap que estén disponibles en el panel actual.

**Campos y significado:**

- **Key block**: bloque con cabecera/cuerpo
- **Version/length**: formato y longitud declarada
- **Usage/algorithm/mode/exportability**: atributos
- **Optional blocks**: metadatos
- **LMK/KBPK**: material requerido por acciones de protección
- **Result**: parsing/hallazgos o clave recuperada.

**Modos y opciones:** VersionId, keyUsages y UsageRule de ThalesKeyBlockOperations. La cabecera puede restringir combinación algoritmo/modo.

**Condiciones e implementación:** El descriptor del catálogo menciona lectura, pero el núcleo tiene operaciones adicionales: documentar solo las acciones alcanzables en UI y distinguir su alcance. Un bloque parseado no está autenticado por ese hecho.

**Comprobación exigida:** Positivo: bloque fixture con atributos y opcionales conocidos. Negativos: longitud declarada incoherente y combinación de cabecera incompatible. Si hay unwrap, incluir bloque alterado.

**Fuentes de integración:** [PaymentKeyBlockCoordinator.java](../src/main/java/com/cryptocarver/ui/PaymentKeyBlockCoordinator.java), [crypto/ThalesKeyBlockOperations.java](../src/main/java/com/cryptocarver/crypto/ThalesKeyBlockOperations.java), [fxml/keys.fxml](../src/main/resources/fxml/keys.fxml).

<a id="c090"></a>

#### C090 — payShield Host Command Bank

**Identidad:** `op_pay_payshield_host_bank`. **Bloque:** B14.

**Qué debe explicar:** Construir y analizar tramas de comandos/respuestas payShield sin envío de red.

**Acciones que debe cubrir:** Componer comando y analizar trama capturada.

**Campos y significado:**

- **Header**: prefijo de protocolo
- **Command code**: código de operación
- **Body**: campos del comando
- **Trailer**: terminación
- **Header length**: longitud de parsing
- **TCP prefix**: prefijo de longitud cuando aplica
- **Captured frame**: trama a analizar
- **Result**: campos/estado/hallazgos.

**Modos y opciones:** Códigos y estructura del catálogo utilizado por HsmHostCommandCoordinator. Cada comando soportado necesita explicación de sus campos, no solo del Body genérico.

**Condiciones e implementación:** Explicar composición frente a ejecución: generar una trama no la envía a un HSM. Longitudes y delimitadores siguen el parser, incluidos los bytes que cuenta TCP prefix.

**Comprobación exigida:** Positivo: trama fixture compuesta y analizada con campos conocidos. Negativos: longitud prefijada incoherente y comando desconocido. Ayuda del código muestra subcampos soportados.

**Fuentes de integración:** [HsmHostCommandCoordinator.java](../src/main/java/com/cryptocarver/ui/HsmHostCommandCoordinator.java), [fxml/payments.fxml](../src/main/resources/fxml/payments.fxml).

<a id="c091"></a>

#### C091 — ISO 8583 Message Inspector

**Identidad:** `op_pay_iso8583`. **Bloque:** B14.

**Qué debe explicar:** Interpretar/construir mensajes ISO 8583 con un perfil y codificaciones explícitas.

**Acciones que debe cubrir:** Parse, build y consultar informe de MTI/bitmaps/data elements.

**Campos y significado:**

- **Profile**: diccionario 1987 o 1993
- **Bitmap encoding**: Binary o Hexadecimal ASCII
- **Length encoding**: ASCII o BCD
- **Message**: bytes/estructura de construcción según acción
- **MTI**: versión/clase/función/origen
- **Data elements**: definición y longitud
- **Report**: offsets/valores/diagnósticos.

**Modos y opciones:** ISO 8583:1987/:1993; bitmap Binary/Hex ASCII; indicadores de longitud ASCII/BCD. Cada Data Element conocido usa la definición del diccionario del perfil.

**Condiciones e implementación:** No presentar el diccionario del laboratorio como todos los perfiles de red posibles. Campos no definidos y mensajes truncados deben explicarse sin adivinar longitud.

**Comprobación exigida:** Positivo: fixture independiente con LLVAR/LLLVAR y valores comprobados. Negativos: truncamiento, perfil incorrecto y DE no definido. El ejemplo de ayuda debe cubrir algo más que el bitmap.

**Fuentes de integración:** [Iso8583Coordinator.java](../src/main/java/com/cryptocarver/ui/Iso8583Coordinator.java), [crypto/iso8583/Iso8583Operations.java](../src/main/java/com/cryptocarver/crypto/iso8583/Iso8583Operations.java), [fxml/payments.fxml](../src/main/resources/fxml/payments.fxml).

### ASN.1

<a id="c092"></a>

#### C092 — Decode ASN.1

**Identidad:** `op_asn1_dec`. **Bloque:** B10.

**Qué debe explicar:** Decodificar bytes ASN.1, explorar su árbol y consultar el diagnóstico de canonicidad DER.

**Acciones que debe cubrir:** Decodificar, elegir tipo/detección y examinar elementos.

**Campos y significado:**

- **Input**: bytes en formato local
- **Input format**: Hexadecimal/Base64/Base64 PEM
- **Type**: Auto-detect o tipo esperado
- **Tree**: tags/longitudes/valores
- **Details**: tipo y tamaño detectados
- **DER report**: condición de codificación canónica.

**Modos y opciones:** Opciones de asn1TypeCombo en ASN1Controller. Distinguir ASN.1 como descripción de datos, BER/DER como reglas de codificación y PEM como envoltura textual.

**Condiciones e implementación:** Parsear una estructura no implica validar una firma/certificado. Un dato BER legible puede no ser DER canónico; usar el diagnóstico real.

**Comprobación exigida:** Positivo: fixture DER de varios elementos. Negativos: entrada truncada y ejemplo no canónico. Ayuda explica los estados del informe.

**Fuentes de integración:** [ASN1Controller.java](../src/main/java/com/cryptocarver/ui/ASN1Controller.java), [fxml/asn1.fxml](../src/main/resources/fxml/asn1.fxml).

<a id="c093"></a>

#### C093 — Encode ASN.1

**Identidad:** `op_asn1_enc`. **Bloque:** B10.

**Qué debe explicar:** Construir el valor ASN.1 del tipo seleccionado y serializarlo según la operación del panel.

**Acciones que debe cubrir:** Elegir tipo/representación y codificar.

**Campos y significado:**

- **Type**: UTF8String y demás primitivas disponibles
- **Input format**: Text/Hex/Base64 cuando aplica
- **Value**: contenido interpretado según tipo
- **Result**: bytes DER/representación
- **OID/Integer/Boolean**: sintaxis específica de cada tipo disponible.

**Modos y opciones:** Todos los tipos de asn1EncodeTypeCombo. Cada uno tiene restricciones y campos/formatos fijados por el listener.

**Condiciones e implementación:** La ayuda explica por qué un formato queda fijado/deshabilitado al cambiar tipo. No mostrar la misma definición para cadena, OCTET STRING, BIT STRING, OID y entero.

**Comprobación exigida:** Positivo: fixture por grupo de tipos y decodificación independiente. Negativos: OID inválido y formato incompatible. Cambio manual/programático actualiza ayuda.

**Fuentes de integración:** [ASN1Controller.java](../src/main/java/com/cryptocarver/ui/ASN1Controller.java), [fxml/asn1.fxml](../src/main/resources/fxml/asn1.fxml).

### Historial

<a id="c094"></a>

#### C094 — Recent Operations

**Identidad:** `op_hist_recent`. **Bloque:** B13.

**Qué debe explicar:** Consultar ejecuciones recientes y distinguir inspección de resultados de restauración de una operativa.

**Acciones que debe cubrir:** Filtrar/seleccionar, ver datos, reabrir receta, comparar y añadir a sesión cuando existe.

**Campos y significado:**

- **Filtros**: criterio de búsqueda
- **Registro**: operación y estado guardados
- **Workspace/recipe**: ruta y configuración
- **Input/output/details**: datos bajo política de visibilidad
- **Reopen**: restauración explícita
- **Compare**: registros seleccionados.

**Modos y opciones:** Acciones de HistoryController y ShellHistoryCoordinator. Selección de ejecución reciente para inspección no implica restaurar datos automáticamente.

**Condiciones e implementación:** Explicar qué se ha conservado y qué secreto se omite. La ayuda sigue el workspace actual; una ficha de registro histórico debe identificar su operación explícitamente.

**Comprobación exigida:** Positivo: inspeccionar y reabrir registro con configuración segura. Negativos: material omitido o ruta no resoluble. Ayuda explica la diferencia entre ver y reabrir.

**Fuentes de integración:** [HistoryController.java](../src/main/java/com/cryptocarver/ui/HistoryController.java), [ShellHistoryCoordinator.java](../src/main/java/com/cryptocarver/ui/ShellHistoryCoordinator.java), [fxml/history.fxml](../src/main/resources/fxml/history.fxml).

<a id="c095"></a>

#### C095 — Saved Sessions

**Identidad:** `op_hist_saved`. **Bloque:** B13.

**Qué debe explicar:** Gestionar sesiones guardadas, recetas y la secuencia de operaciones que contienen.

**Acciones que debe cubrir:** Guardar/abrir, seleccionar, restaurar configuración y gestionar registros según la interfaz.

**Campos y significado:**

- **Session name/path**: identificación del archivo
- **Steps/recipes**: contenido conservado
- **Visibility**: política de datos
- **Missing material**: entradas no recuperables
- **Restore**: partes de configuración que aplica.

**Modos y opciones:** Formatos reales de SavedSessionsCoordinator y SaveSessionCoordinator. Diferenciar sesión, receta segura y screen configuration.

**Condiciones e implementación:** Guardar sesión no implica que todos los secretos se hayan conservado. Abrir ayuda no reabre ni ejecuta los pasos.

**Comprobación exigida:** Positivo: sesión de ejemplo con varios pasos y recuperación de configuración. Negativos: archivo incompatible y material no conservado. Ayuda muestra alcance de restauración.

**Fuentes de integración:** [SavedSessionsCoordinator.java](../src/main/java/com/cryptocarver/ui/SavedSessionsCoordinator.java), [SaveSessionCoordinator.java](../src/main/java/com/cryptocarver/ui/SaveSessionCoordinator.java), [fxml/main-view-modern.fxml](../src/main/resources/fxml/main-view-modern.fxml).

<a id="c096"></a>

#### C096 — Export History

**Identidad:** `op_hist_export`. **Bloque:** B13.

**Qué debe explicar:** Exportar historial o registro de sesión y explicar formato y política de datos de la salida.

**Acciones que debe cubrir:** Seleccionar formato/destino y exportar.

**Campos y significado:**

- **Scope**: registros o sesión exportados
- **Format**: estructura de archivo
- **Path**: destino
- **Visibility/profile**: valores completos/enmascarados/redactados que permite el código
- **Contents**: columnas, detalles y payloads realmente incluidos.

**Modos y opciones:** Formatos efectivos de HistoryCoordinator/SessionTrailCoordinator. Mantener sus diferencias de contenido en fichas separadas cuando comparten la acción.

**Condiciones e implementación:** Describir qué datos se escriben y sus omisiones. La ayuda no debe relajar la política de visibilidad al mostrar un ejemplo ni dar por hecho que toda exportación es segura.

**Comprobación exigida:** Positivo: fixture exportado con perfil redactado y columnas esperadas. Negativo: destino no escribible. Comprobar que la documentación coincide con los datos exportados.

**Fuentes de integración:** [HistoryCoordinator.java](../src/main/java/com/cryptocarver/ui/HistoryCoordinator.java), [SessionTrailCoordinator.java](../src/main/java/com/cryptocarver/ui/SessionTrailCoordinator.java), [fxml/history.fxml](../src/main/resources/fxml/history.fxml), [fxml/main-view-modern.fxml](../src/main/resources/fxml/main-view-modern.fxml).

## 6. Casos auxiliares y suboperativas

Estas fichas amplían las entradas del catálogo y cubren ventanas, acciones o subpaneles con contexto propio. Mantener su identidad aunque reutilicen definiciones de una ficha C.

<a id="a001"></a>

### A001 — Barra global de formatos

**Identidad de ayuda:** `aux_global_formats`. **Bloque:** B01.

**Qué debe explicar:** Explicar qué selector transforma el payload y cuál representa la salida.

**Acciones:** Seleccionar formato de entrada/salida y consultar contrato de la ruta.

**Campos y significado:**

- **Input format**: decodificación del payload
- **Output format**: representación del resultado
- **Contract**: tipo de entrada/salida
- **Disabled**: operación con controles propios
- **Remembered selection**: preferencia por ruta.

**Opciones:** Perfiles de OperationFormatRegistry, no una lista universal.

**Condiciones e implementación:** Claves, IV, tags, certificados, JWK y formatos locales tienen interpretación propia. El fallback no permite inventar formatos. Explicar formatos deshabilitados cuando la ruta usa estructuras/archivos.

**Comprobación exigida:** Hashing→JWE→Hashing conserva contrato y ayuda correctos; restauración cambia selección sin listeners duplicados.

**Fuentes:** [NavigationChromeCoordinator.java](../src/main/java/com/cryptocarver/ui/NavigationChromeCoordinator.java), [model/OperationFormatRegistry.java](../src/main/java/com/cryptocarver/model/OperationFormatRegistry.java), [fxml/main-view-modern.fxml](../src/main/resources/fxml/main-view-modern.fxml).

<a id="a002"></a>

### A002 — Validación previa y errores

**Identidad de ayuda:** `aux_preflight_error`. **Bloque:** B00.

**Qué debe explicar:** Explicar qué impide ejecutar, qué falta y qué es una advertencia.

**Acciones:** Consultar detalle, ir a campo, abrir explicación de regla y copiar diagnóstico redactado.

**Campos y significado:**

- **Status**: READY/INCOMPLETE/BLOCKED/WARNING según modelo
- **Check**: regla evaluada
- **Target field**: control afectado
- **Remedy**: corrección
- **Details**: contenido redactado.

**Opciones:** Estados reales de PreflightReport/UserFacingError y banners.

**Condiciones e implementación:** La explicación se relaciona por regla y campo, no por coincidencia de texto. No convertir una advertencia existente en bloqueo nuevo como parte de este encargo.

**Comprobación exigida:** Entrada inválida enfoca su campo y abre la regla correcta en ES/EN; un mensaje sin regla usa explicación general explícita.

**Fuentes:** [ReadinessPanelCoordinator.java](../src/main/java/com/cryptocarver/ui/ReadinessPanelCoordinator.java), [InlineErrorPresenter.java](../src/main/java/com/cryptocarver/ui/InlineErrorPresenter.java), [UserFacingErrorMapper.java](../src/main/java/com/cryptocarver/ui/UserFacingErrorMapper.java), [fxml/main-view-modern.fxml](../src/main/resources/fxml/main-view-modern.fxml).

<a id="a003"></a>

### A003 — Plantillas, recetas y configuración de pantalla

**Identidad de ayuda:** `aux_templates_configuration`. **Bloque:** B13.

**Qué debe explicar:** Explicar qué parámetros reutiliza una plantilla/receta y qué falta aportar después.

**Acciones:** Aplicar, guardar, importar/exportar y restaurar configuración.

**Campos y significado:**

- **Template/recipe**: conjunto de parámetros
- **Name/version**: identificación
- **Module/route**: ámbito
- **Omitted material**: secretos no guardados
- **Restore**: parámetros que realmente se aplican.

**Opciones:** SafeTemplateUIHelper, RecipeUIHelper y ScreenConfigurationCoordinator; distinguir sus formatos y políticas.

**Condiciones e implementación:** No explicar aplicar plantilla como ejecución. La ayuda se actualiza tras cambios programáticos de controles y muestra el material que debe reintroducirse sin solicitar secretos dentro del panel.

**Comprobación exigida:** Aplicar plantilla cambia selección y ficha; importación incompatible se diagnostica sin mezclar rutas. Revisar ausencia de secretos en el fichero.

**Fuentes:** [SafeTemplateUIHelper.java](../src/main/java/com/cryptocarver/ui/SafeTemplateUIHelper.java), [RecipeUIHelper.java](../src/main/java/com/cryptocarver/ui/RecipeUIHelper.java), [ScreenConfigurationCoordinator.java](../src/main/java/com/cryptocarver/ui/ScreenConfigurationCoordinator.java).

<a id="a004"></a>

### A004 — Inspector de bytes

**Identidad de ayuda:** `aux_byte_inspector`. **Bloque:** B05.

**Qué debe explicar:** Ver los mismos bytes en varias representaciones e identificar tamaños/offsets.

**Acciones:** Abrir datos, seleccionar representación, buscar/copiar y comparar cuando exista.

**Campos y significado:**

- **Bytes source**: contenido público elegido
- **Representation**: Hex/Base64/Base64URL/texto y formatos disponibles
- **Offset/length**: posición y tamaño
- **Charset**: lectura textual
- **Selection**: intervalo y exportación.

**Opciones:** Opciones efectivas de ByteInspectorWindow.

**Condiciones e implementación:** Representar bytes como texto puede no producir caracteres legibles; no implica descifrado. Respetar clasificación de material al abrir desde resultados.

**Comprobación exigida:** Fixture público muestra longitud y representación coherentes; material oculto no se revela al abrir ayuda.

**Fuentes:** [ByteInspectorWindow.java](../src/main/java/com/cryptocarver/ui/ByteInspectorWindow.java).

<a id="a005"></a>

### A005 — Conversor Epoch

**Identidad de ayuda:** `aux_epoch`. **Bloque:** B05.

**Qué debe explicar:** Convertir entre un valor temporal y fecha/hora con unidad y zona explícitas.

**Acciones:** Convertir en ambas direcciones y usar ahora si existe.

**Campos y significado:**

- **Epoch value**: número temporal
- **Unit**: segundos/milisegundos que admite el diálogo
- **Date/time**: fecha legible
- **Zone**: zona aplicada
- **Result**: instante y representación.

**Opciones:** Opciones del diálogo creado por UtilityToolsCoordinator.

**Condiciones e implementación:** Documentar unidad, zona y límites de parsing reales; no usar automáticamente la zona del sistema para describir otra configurada.

**Comprobación exigida:** Valor fixture produce fecha esperada y vuelta; valor inválido o fuera de rango recibe explicación.

**Fuentes:** [UtilityToolsCoordinator.java](../src/main/java/com/cryptocarver/ui/UtilityToolsCoordinator.java).

<a id="a006"></a>

### A006 — Formateador JSON

**Identidad de ayuda:** `aux_json`. **Bloque:** B05.

**Qué debe explicar:** Dar formato a un JSON y explicar el diagnóstico estructural.

**Acciones:** Formatear/compactar u otras acciones realmente presentes.

**Campos y significado:**

- **Input**: documento JSON
- **Formatting**: sangría o modo de presentación
- **Output**: mismo contenido serializado
- **Error position**: punto que el parser puede señalar.

**Opciones:** Acciones del diálogo y JsonTextFormatter.

**Condiciones e implementación:** Dar formato no valida un esquema de negocio ni firma un documento. Explicar preservación y tratamiento de números/orden según el formateador.

**Comprobación exigida:** JSON fixture mantiene valores; JSON inválido ofrece diagnóstico. Ayuda no presenta éxito como validación de JWK/JWT.

**Fuentes:** [UtilityToolsCoordinator.java](../src/main/java/com/cryptocarver/ui/UtilityToolsCoordinator.java), [util/JsonTextFormatter.java](../src/main/java/com/cryptocarver/util/JsonTextFormatter.java).

<a id="a007"></a>

### A007 — Comparador de resultados

**Identidad de ayuda:** `aux_compare_results`. **Bloque:** B13.

**Qué debe explicar:** Contrastar dos resultados y explicar representación, tamaño y diferencias.

**Acciones:** Seleccionar izquierda/derecha, comparar y consultar diferencias.

**Campos y significado:**

- **Left/right result**: objetos comparados
- **Format/type**: interpretación
- **Length**: tamaños
- **Difference**: posición y valor
- **Metadata**: procedencia pública cuando se muestra.

**Opciones:** Opciones de CompareResultsController.

**Condiciones e implementación:** Explicar si se comparan bytes o texto renderizado; dos formatos diferentes pueden contener los mismos bytes. Respetar visibilidad de datos seleccionados.

**Comprobación exigida:** Mismos bytes en representaciones distintas y diferencia real tienen veredictos correctos; selección incompleta tiene explicación.

**Fuentes:** [CompareResultsController.java](../src/main/java/com/cryptocarver/ui/CompareResultsController.java), [fxml/compare_results.fxml](../src/main/resources/fxml/compare_results.fxml).

<a id="a008"></a>

### A008 — Análisis de archivo cifrado

**Identidad de ayuda:** `aux_encrypted_file_analysis`. **Bloque:** B04.

**Qué debe explicar:** Diagnosticar un archivo, contenedor o representación usando hipótesis y parámetros de laboratorio.

**Acciones:** Seleccionar archivo, configurar análisis, ejecutar, promover hipótesis y exportar informe si está disponible.

**Campos y significado:**

- **File/representation**: datos analizados
- **Algorithm/mode/padding**: hipótesis
- **Key/IV/tag/AAD**: material requerido por intento
- **Expected result**: criterio de comprobación si existe
- **Candidates/report**: hipótesis, pruebas y límites.

**Opciones:** Opciones de CipherController y EncryptedFileAnalyzer; referencia al tutorial 24.

**Condiciones e implementación:** Una pista de formato no confirma algoritmo. Ayuda debe distinguir candidato, descifrado sin verificación y resultado autenticado; no prometer recuperación sin material.

**Comprobación exigida:** Fixture del tutorial con análisis verificable y prueba de clave incorrecta; ayuda de cada hipótesis conserva parámetros propios.

**Fuentes:** [CipherController.java](../src/main/java/com/cryptocarver/ui/CipherController.java), [EncryptedFileAnalyzer.java](../src/main/java/com/cryptocarver/ui/EncryptedFileAnalyzer.java), [fxml/cipher.fxml](../src/main/resources/fxml/cipher.fxml).

<a id="a009"></a>

### A009 — Perfiles PKCS#11

**Identidad de ayuda:** `aux_pkcs11_profiles`. **Bloque:** B08.

**Qué debe explicar:** Configurar conexión y consultar diagnóstico/inventario del token.

**Acciones:** Crear/guardar/eliminar perfil, diagnosticar, consultar inventario y cancelar.

**Campos y significado:**

- **Name**: referencia del perfil
- **Library**: módulo local
- **Slot**: identificador/índice según configuración
- **Pin**: login efímero
- **Diagnostics**: disponibilidad y mecanismos
- **Inventory**: objetos y atributos.

**Opciones:** Opciones y persistencia de Pkcs11ProfilesController/Presenter.

**Condiciones e implementación:** Documentar diferencia entre slot e índice si se ofrece y entre guardar perfil y abrir sesión. Diagnosticar consulta el proveedor, abrir ayuda es local.

**Comprobación exigida:** Perfil de prueba diagnostica correctamente; biblioteca ausente y slot inválido muestran causa. El perfil guardado no incluye PIN.

**Fuentes:** [Pkcs11ProfilesController.java](../src/main/java/com/cryptocarver/ui/Pkcs11ProfilesController.java), [Pkcs11ProfilesPresenter.java](../src/main/java/com/cryptocarver/ui/Pkcs11ProfilesPresenter.java), [fxml/pkcs11_profiles.fxml](../src/main/resources/fxml/pkcs11_profiles.fxml).

<a id="a010"></a>

### A010 — Generar CRL y revocar certificados

**Identidad de ayuda:** `aux_crl`. **Bloque:** B10.

**Qué debe explicar:** Crear una lista firmada de revocación o incorporar una entrada de revocación.

**Acciones:** Generar CRL y revocar/actualizar CRL existente.

**Campos y significado:**

- **Issuer cert/key**: identidad y firma de la lista
- **Existing CRL**: lista base
- **Serial**: certificado a marcar
- **Reason**: motivo de revocación
- **Result**: CRL serializada y resumen.

**Opciones:** Razones y formato de serial del catálogo usado por CertificateCoordinator.

**Condiciones e implementación:** Serial de revocación y número de CRL son conceptos distintos. Revocar en la lista local no publica la CRL en un servicio remoto.

**Comprobación exigida:** CRL fixture nueva y actualizada contienen entradas esperadas; clave issuer incompatible y serial mal formado reciben diagnóstico.

**Fuentes:** [CertificateCoordinator.java](../src/main/java/com/cryptocarver/ui/CertificateCoordinator.java), [crypto/CertificateAuthorityOperations.java](../src/main/java/com/cryptocarver/crypto/CertificateAuthorityOperations.java), [fxml/certificates.fxml](../src/main/resources/fxml/certificates.fxml).

<a id="a011"></a>

### A011 — Generador UUID

**Identidad de ayuda:** `aux_uuid`. **Bloque:** B05.

**Qué debe explicar:** Crear un identificador UUID con la variante implementada y explicar su estructura.

**Acciones:** Generar UUID, copiar y consultar sus componentes si se muestran.

**Campos y significado:**

- **Variant/version**: tipo generado
- **Identifier**: representación canónica
- **Components**: versión/variante y otros datos que el panel decodifica.

**Opciones:** Opciones del generador real en GenericController.

**Condiciones e implementación:** UUID es una herramienta separada de bytes aleatorios y de claves; no confundir sus bits totales con fortaleza criptográfica.

**Comprobación exigida:** UUID generado tiene formato/versión esperados; la ayuda explica solo la variante realmente usada.

**Fuentes:** [GenericController.java](../src/main/java/com/cryptocarver/ui/GenericController.java), [fxml/generic.fxml](../src/main/resources/fxml/generic.fxml).

<a id="a012"></a>

### A012 — ML-KEM: encapsular y decapsular

**Identidad de ayuda:** `aux_pqc_kem`. **Bloque:** B13.

**Qué debe explicar:** Obtener un secreto compartido mediante encapsulación y recuperarlo con la privada correspondiente.

**Acciones:** Encapsular y decapsular.

**Campos y significado:**

- **Algorithm**: ML-KEM-512/768/1024
- **Public/private pair**: material del panel compartido
- **Encapsulation/ciphertext**: objeto transmitido
- **Bob secret**: secreto obtenido al encapsular
- **Alice secret**: secreto obtenido al decapsular
- **Match status**: comparación efectiva.

**Opciones:** Conjuntos ML-KEM de PostQuantumOperations.

**Condiciones e implementación:** El ciphertext de KEM no es el cifrado de un mensaje arbitrario. La decapsulación de datos alterados puede producir otro secreto según el mecanismo; no exigir un error que el motor no promete.

**Comprobación exigida:** Fixture y encapsulación/decapsulación producen secreto coincidente; incompatibilidad de familia/parámetro se diagnostica. Material alterado se evalúa con el comportamiento real.

**Fuentes:** [PostQuantumKemCoordinator.java](../src/main/java/com/cryptocarver/ui/PostQuantumKemCoordinator.java), [crypto/PostQuantumOperations.java](../src/main/java/com/cryptocarver/crypto/PostQuantumOperations.java), [fxml/pqc.fxml](../src/main/resources/fxml/pqc.fxml).

<a id="a013"></a>

### A013 — Benchmark PQC

**Identidad de ayuda:** `aux_pqc_benchmark`. **Bloque:** B13.

**Qué debe explicar:** Medir las operaciones que implementa el benchmark y explicar alcance de las cifras.

**Acciones:** Elegir algoritmo y ejecutar medición.

**Campos y significado:**

- **Algorithm**: familia y parámetros
- **Iterations**: repeticiones si son configurables
- **Times**: unidades y operación medida
- **Sizes**: material producido
- **Result**: resumen y contexto de ejecución.

**Opciones:** Algoritmos configurados en PostQuantumBenchmarkCoordinator.

**Condiciones e implementación:** Medición local no demuestra seguridad ni permite prometer rendimiento universal. Explicar inicialización/warmup y medidas exactamente como se calculan.

**Comprobación exigida:** Ejecución pequeña produce unidades y operaciones coherentes; familia inválida no se mide. Help no ejecuta benchmark.

**Fuentes:** [PostQuantumBenchmarkCoordinator.java](../src/main/java/com/cryptocarver/ui/PostQuantumBenchmarkCoordinator.java), [fxml/pqc.fxml](../src/main/resources/fxml/pqc.fxml).

<a id="a014"></a>

### A014 — EMV: derivación de claves

**Identidad de ayuda:** `aux_emv_session`. **Bloque:** B14.

**Qué debe explicar:** Obtener clave ICC y clave de sesión con PAN/PSN y datos de transacción del perfil.

**Acciones:** Derivar sesión y las claves SMI/SMC disponibles.

**Campos y significado:**

- **IMK**: clave maestra
- **PAN/PSN**: datos de diversificación
- **ICC method**: variante de derivación
- **ATC**: contador Hex
- **MK SMI/SMC y UDK**: claves de usos distintos
- **AC/command number**: contexto de script.

**Opciones:** Opciones reales de ICC method y esquema de script.

**Condiciones e implementación:** No intercambiar clave ICC, sesión y claves de MAC/cifrado. Explicar si los campos PAN/PSN se concatenan y sus bases según handler.

**Comprobación exigida:** Vector de derivación conocido; longitud de IMK/ATC o método incompatible se rechaza. Cada salida está nombrada por uso.

**Fuentes:** [EmvSessionKeyCoordinator.java](../src/main/java/com/cryptocarver/ui/EmvSessionKeyCoordinator.java), [crypto/EMVOperations.java](../src/main/java/com/cryptocarver/crypto/EMVOperations.java), [fxml/emv.fxml](../src/main/resources/fxml/emv.fxml).

<a id="a015"></a>

### A015 — EMV: generar y verificar ARQC

**Identidad de ayuda:** `aux_emv_arqc`. **Bloque:** B14.

**Qué debe explicar:** Calcular o comprobar un criptograma sobre el bloque exacto de datos de transacción.

**Acciones:** Generar/verificar y elegir método de padding.

**Campos y significado:**

- **Session key**: clave de criptograma
- **Amount/amount other**: importes y representación
- **Country/currency**: códigos
- **TVR/date/type/UN/ATC**: datos de terminal
- **Raw transaction data**: bytes completos alternativos
- **ICC data**: segmento adicional
- **Padding**: regla aplicada
- **Expected ARQC**: valor a comprobar si la acción lo lee.

**Opciones:** Datos estructurados frente a raw según la rama del coordinador; rellenos expuestos.

**Condiciones e implementación:** Describir orden, tamaño y fuente efectiva del bloque sin afirmar que sirve para todos los perfiles EMV. Indicar qué entrada tiene prioridad si raw y campos separados están presentes.

**Comprobación exigida:** Vector ARQC con bloque exacto comprobado; alterar un dato cambia el resultado y verificación falla. Help explica bytes que intervienen.

**Fuentes:** [EmvArqcCoordinator.java](../src/main/java/com/cryptocarver/ui/EmvArqcCoordinator.java), [crypto/EMVOperations.java](../src/main/java/com/cryptocarver/crypto/EMVOperations.java), [fxml/emv.fxml](../src/main/resources/fxml/emv.fxml).

<a id="a016"></a>

### A016 — EMV: ARPC

**Identidad de ayuda:** `aux_emv_arpc`. **Bloque:** B14.

**Qué debe explicar:** Crear el criptograma de respuesta con el método seleccionado.

**Acciones:** Generar ARPC y consultar la relación con ARQC.

**Campos y significado:**

- **Session key**: clave del perfil
- **ARQC**: criptograma de solicitud
- **ARC**: código de respuesta cuando aplica
- **CSU**: datos de respuesta para el método
- **Proprietary auth data**: datos adicionales cuando se consumen
- **Method**: estructura y algoritmo.

**Opciones:** Métodos ofrecidos por arpcMethodCombo y EmvArpcCoordinator.

**Condiciones e implementación:** ARC y CSU tienen tipos y tamaños diferentes. La ayuda debe indicar qué campos se ignoran o necesitan para cada método, y comprobar si proprietary auth data es realmente utilizado.

**Comprobación exigida:** Vectores por método con resultado conocido; tamaño ARQC/CSU incompatible se diagnostica. Cambiar método actualiza ayuda.

**Fuentes:** [EmvArpcCoordinator.java](../src/main/java/com/cryptocarver/ui/EmvArpcCoordinator.java), [crypto/EMVOperations.java](../src/main/java/com/cryptocarver/crypto/EMVOperations.java), [fxml/emv.fxml](../src/main/resources/fxml/emv.fxml).

<a id="a017"></a>

### A017 — EMV: issuer script y secure messaging

**Identidad de ayuda:** `aux_emv_scripts`. **Bloque:** B14.

**Qué debe explicar:** Derivar claves de script, proteger datos/PIN y generar MAC del comando.

**Acciones:** Derivar claves, cifrar PIN y generar MAC; cargar ejemplo.

**Campos y significado:**

- **Scheme**: perfil de mensajería
- **MK/UDK SMI/SMC**: claves de autenticación/cifrado
- **PAN sequence/ATC/AC/command number**: diversificación
- **SK MAC/ENC**: claves de sesión
- **Header/data**: partes del comando
- **PIN/UDK A**: datos usados por protección PIN.

**Opciones:** Esquemas de EmvSecureMessaging y sus diferencias.

**Condiciones e implementación:** Cada botón utiliza un subconjunto de campos. Explicar si SK se aporta o deriva y qué bytes del comando quedan autenticados. No aplicar un único perfil a todos los scripts.

**Comprobación exigida:** Fixture por esquema alcanzable; comando/PIN inválido y clave de tamaño incorrecto dan diagnóstico específico.

**Fuentes:** [EmvSecureMessagingCoordinator.java](../src/main/java/com/cryptocarver/ui/EmvSecureMessagingCoordinator.java), [EmvSessionKeyCoordinator.java](../src/main/java/com/cryptocarver/ui/EmvSessionKeyCoordinator.java), [crypto/EmvSecureMessaging.java](../src/main/java/com/cryptocarver/crypto/EmvSecureMessaging.java), [fxml/emv.fxml](../src/main/resources/fxml/emv.fxml).

<a id="a018"></a>

### A018 — Visa HCE

**Identidad de ayuda:** `aux_emv_hce`. **Bloque:** B14.

**Qué debe explicar:** Derivar claves limitadas y construir los criptogramas MSD/qVSDC implementados.

**Acciones:** Derivar LUK, calcular MSD y qVSDC.

**Campos y significado:**

- **UDK**: clave base
- **Year/hours/counter**: contexto LUK y unidades reales
- **MSD/qVSDC LUK**: clave del perfil
- **ATC/device type**: contexto MSD
- **Amount/other/country/TVR/currency/date/type/UN/AIP/CVR**: bloque qVSDC
- **Result**: claves/criptogramas.

**Opciones:** Ramas MSD y qVSDC de VisaHceOperations.

**Condiciones e implementación:** Explicar campos por rama y formato de datos temporales/contador. No confundir LUK con UDK ni su generación con emisión de una tarjeta.

**Comprobación exigida:** Fixtures HCE reproducibles por rama; datos de longitud/formato inválidos. Ayuda no mezcla ATC MSD y ATC qVSDC.

**Fuentes:** [EmvHceCoordinator.java](../src/main/java/com/cryptocarver/ui/EmvHceCoordinator.java), [crypto/VisaHceOperations.java](../src/main/java/com/cryptocarver/crypto/VisaHceOperations.java), [fxml/emv.fxml](../src/main/resources/fxml/emv.fxml).

<a id="a019"></a>

### A019 — Mastercard Data Storage

**Identidad de ayuda:** `aux_emv_ds`. **Bloque:** B14.

**Qué debe explicar:** Calcular DSPK, digest OWHF2 y summary con los datos que usa cada acción.

**Acciones:** Derivar DSPK, digest y summary.

**Campos y significado:**

- **DS ID/operator ID**: identidades de diversificación
- **Input**: dato del digest
- **Summary1**: resumen previo
- **Amount/currency**: contexto
- **RCP/GAC**: parámetros de acción
- **DS UN/UN**: números impredecibles del contexto
- **Result**: clave/digest/resumen.

**Opciones:** Las tres acciones de MastercardDataStorage usadas por EMVController.

**Condiciones e implementación:** No reutilizar descripción de MAC genérico para OWHF2/summary. Documentar tamaños Hex y representación decimal de importes según el handler.

**Comprobación exigida:** Fixtures por acción y campo alterado; longitud incompatible se rechaza. Ficha cambia al consultar DSPK/digest/summary.

**Fuentes:** [EMVController.java](../src/main/java/com/cryptocarver/ui/EMVController.java), [crypto/MastercardDataStorage.java](../src/main/java/com/cryptocarver/crypto/MastercardDataStorage.java), [fxml/emv.fxml](../src/main/resources/fxml/emv.fxml).

<a id="a020"></a>

### A020 — ICC Dynamic Number

**Identidad de ayuda:** `aux_emv_dynamic`. **Bloque:** B14.

**Qué debe explicar:** Calcular el número dinámico con clave, PAN y contexto de transacción.

**Acciones:** Calcular y cargar ejemplo.

**Campos y significado:**

- **MK**: clave base
- **PAN**: diversificación
- **ATC**: contador
- **UN**: dato impredecible
- **Result**: número dinámico y longitud.

**Opciones:** Perfil implementado por MastercardIccDynamicNumber.

**Condiciones e implementación:** Explicar la clave de sesión interna y el formato de entrada. No confundir resultado con un ARQC o con aleatoriedad independiente del contexto.

**Comprobación exigida:** Fixture del ejemplo produce resultado conocido; tamaño de ATC/UN inválido se explica.

**Fuentes:** [EMVController.java](../src/main/java/com/cryptocarver/ui/EMVController.java), [crypto/MastercardIccDynamicNumber.java](../src/main/java/com/cryptocarver/crypto/MastercardIccDynamicNumber.java), [fxml/emv.fxml](../src/main/resources/fxml/emv.fxml).

<a id="a021"></a>

### A021 — CAP Token

**Identidad de ayuda:** `aux_emv_cap`. **Bloque:** B14.

**Qué debe explicar:** Construir datos CAP, aplicar la selección/compresión de bits y obtener el token del perfil implementado.

**Acciones:** Calcular y cargar ejemplo.

**Campos y significado:**

- **IPB**: patrón de selección
- **IAF**: flag usado
- **PAN sequence/CID/ATC/AC/IAD**: datos originales
- **Token data**: concatenación efectiva
- **Padded IPB/compressed bits**: pasos de extracción
- **Token**: representación final.

**Opciones:** Perfil de capToken en MastercardIccDynamicNumber.

**Condiciones e implementación:** Definir cada acrónimo con la semántica del motor y explicar orden/posición de bits. Un token calculado localmente no implica autenticación ante un banco.

**Comprobación exigida:** Fixture compara token data, IPB, bits comprimidos y token; IPB/dato inválido produce diagnóstico.

**Fuentes:** [EMVController.java](../src/main/java/com/cryptocarver/ui/EMVController.java), [crypto/MastercardIccDynamicNumber.java](../src/main/java/com/cryptocarver/crypto/MastercardIccDynamicNumber.java), [fxml/emv.fxml](../src/main/resources/fxml/emv.fxml).

<a id="a022"></a>

### A022 — Track 2

**Identidad de ayuda:** `aux_emv_track2`. **Bloque:** B14.

**Qué debe explicar:** Construir o leer datos Track 2 y explicar sus campos y separadores.

**Acciones:** Encode y decode.

**Campos y significado:**

- **PAN**: número de cuenta
- **Expiry**: formato real de fecha
- **Service code**: tres dígitos y significado del campo
- **Discretionary data**: contenido del perfil
- **Track 2 input**: cadena completa
- **Result**: cadena o desglose.

**Opciones:** Codificación/separadores admitidos por EmvTrack2Coordinator y PaymentOperations.

**Condiciones e implementación:** No convertir una cadena construida en dato verificado. Definir completado/terminador solo si el handler los implementa.

**Comprobación exigida:** Encode/decode con fixture conocido; terminador/longitud o dato incompatible recibe diagnóstico.

**Fuentes:** [EmvTrack2Coordinator.java](../src/main/java/com/cryptocarver/ui/EmvTrack2Coordinator.java), [crypto/PaymentOperations.java](../src/main/java/com/cryptocarver/crypto/PaymentOperations.java), [fxml/emv.fxml](../src/main/resources/fxml/emv.fxml).

<a id="a023"></a>

### A023 — JWS detached

**Identidad de ayuda:** `aux_jws_detached`. **Bloque:** B09.

**Qué debe explicar:** Firmar/verificar una estructura cuyo payload se aporta separadamente.

**Acciones:** Generar y verificar detached, seleccionar codificación/serialización/opciones de cabecera.

**Campos y significado:**

- **Payload external**: bytes originales
- **Algorithm/key/secret format**: firma
- **Serialization**: forma JWS
- **Header b64/crit**: tratamiento del payload si se ofrece
- **Signature object**: estructura separada
- **Verification payload/key**: expectativa.

**Opciones:** Opciones efectivas de JoseJwtCoordinator, incluido payload no codificado cuando esté soportado.

**Condiciones e implementación:** El JSON del token no contiene necesariamente el payload. Definir qué bytes firma y cómo se entrega crit en la variante seleccionada.

**Comprobación exigida:** Fixture detached verificable y payload alterado rechazado; cambios de encoding no deben validar otro contenido.

**Fuentes:** [JoseJwtCoordinator.java](../src/main/java/com/cryptocarver/ui/JoseJwtCoordinator.java), [fxml/jose.fxml](../src/main/resources/fxml/jose.fxml).

<a id="a024"></a>

### A024 — Nested JWT

**Identidad de ayuda:** `aux_nested`. **Bloque:** B09.

**Qué debe explicar:** Firmar y después cifrar, explicando ambas capas y el orden de comprobación.

**Acciones:** Generar y descifrar/verificar según las acciones de la sección.

**Campos y significado:**

- **Signing alg/key/secret format**: JWS interno
- **Key management/content encryption/recipient material**: JWE externo
- **Header/claims**: datos de cada capa
- **Output**: token anidado
- **Verification expectations**: políticas de capa interna.

**Opciones:** Intersección de algoritmos realmente soportados por la composición nested, no todas las opciones de las pantallas independientes.

**Condiciones e implementación:** El secreto HMAC y una clave dir pueden tener lectura/tamaño distintos. Descifrar JWE no valida por sí solo el JWS interno.

**Comprobación exigida:** Fixture nested conserva y verifica ambas capas; destinatario incorrecto o firma interna alterada se distinguen.

**Fuentes:** [JoseJwtCoordinator.java](../src/main/java/com/cryptocarver/ui/JoseJwtCoordinator.java), [JoseJweCoordinator.java](../src/main/java/com/cryptocarver/ui/JoseJweCoordinator.java), [fxml/jose.fxml](../src/main/resources/fxml/jose.fxml).

## 7. Operaciones dinámicas: catálogo de lote, nodos y comandos

La cobertura de las 120 fichas de este documento se amplía con las entradas dinámicas realmente seleccionables. Luna debe producir una matriz de implementación que enumere:

| Inventario | Unidad de cobertura y criterio |
|---|---|
| BatchOperationCatalog | Cada operación seleccionable: entrada/columnas, transformación, salida, limitaciones y ejemplo. Puede enlazar a una ficha C con sus diferencias de procesamiento por registro. |
| NodeCatalog | Cada tipo de nodo: propósito, puertos, parámetros, opciones, condiciones, salida y diagnóstico. Reutilizar NodeParameter.helpKey, sensible y visibleWhen; registrar las claves de ayuda ausentes. |
| Banco de comandos payShield | Cada comando soportado: código, body dividido en campos, longitud/formato y significado de respuesta. No prometer soporte para comandos que el catálogo no conoce. |
| Diccionarios ISO 8583 / TLV | Cada elemento/tag que ya tenga definición: mostrarla desde el diccionario; campos desconocidos se describen como bytes no interpretados. |
| Diálogos y ventanas construidos en Java | Añadir sus acciones/campos a la matriz; el anexo FXML por sí solo no los recoge. |

No copiar manualmente todas las listas dinámicas en el controlador principal. La revisión debe poder detectar automáticamente nuevas entradas sin ayuda; las diferencias específicas de cada nodo/operación no pueden quedar tapadas por un fallback genérico.

## 8. Paquetes de implementación para Luna

Cada bloque debe terminar con una entrega pequeña y verificable. Codex revisará esa entrega contra este encargo antes de darla por aceptada. Cuando un bloque contenga demasiadas pantallas para una entrega, Luna puede dividirlo en subbloques conservando IDs y cobertura; no reducir alcance ni marcarlo completo antes de cubrir sus casos.

| Bloque | Alcance | Condición para revisión |
|---|---|---|
| B00 | Modelo, recursos, coordinador, panel y adaptadores base; activación, idioma y lifecycle. | Ayuda local navegable con ejemplo mínimo, sin cambiar motor ni datos. Infraestructura lista para pilotos. |
| B01 | Cifrado simétrico C y barra global A. | Campos, modos y cambio de algoritmo completos; pruebas de compatibilidad y estado. |
| B02 | JWE C. | alg/enc/serializaciones y claves/AAD/multidestinatario completos. |
| B03 | PIN claro y protegido C. | Formatos, PAN, relleno, TDES/AES y estado sin clave explicados. |
| B04 | FPE, archivo, OpenPGP y RSA. | Todas las acciones y parámetros específicos de esas pantallas. |
| B05 | Genéricas, Process Designer y herramientas de conversión/bytes/UUID. | Doce fichas Generic más operaciones dinámicas de lote/nodos y auxiliares asignadas. |
| B06 | Firmas y MAC. | Generar/verificar, orígenes de clave y todos los algoritmos del catálogo. |
| B07 | Key Lab, generación/validación/XOR/KDF/wrap/TR-31/AKB/RSA KEX/TR-34. | Diez fichas Keys y reglas específicas por acción. |
| B08 | Generadores asimétricos, material/comparación/almacenes/PKCS11/ICSF. | Once fichas Keys, perfiles y suboperativas de token. |
| B09 | JOSE restante y COSE. | JWT/JWK/JWA/inspector, detached/nested y tres COSE. |
| B10 | Certificados, CMS/PAdES/ASiC, ASN.1 y CRL. | Doce fichas C y CRL; niveles y alcance de confianza explícitos. |
| B11 | XML y WS-Security. | Diez fichas, temporalidad/TSA, autenticación y cifrado SOAP. |
| B12 | Wallet. | Nueve fichas y sus acciones, expectativas y límites. |
| B13 | PQC e historial/sesiones/configuración/comparación. | Cinco fichas C, KEM/benchmark y auxiliares. |
| B14 | Pagos restantes y suboperativas EMV. | Diez fichas Payments restantes, nueve subcasos EMV y comandos/diccionarios dinámicos. |
| B15 | Revisión transversal, cobertura, traducciones, empaquetado y enlaces. | Todas las filas completas; comprobación desde aplicación distribuida. |

B00–B03 validan el diseño. Después pueden abordarse familias con independencia funcional, pero Luna trabaja sin subagentes y presenta cambios por bloque. No ejecutar varias instancias de Maven simultáneamente.

### Archivos esperados por responsabilidad

- Infraestructura: modelos/servicios de ayuda nuevos, vista común, coordinador y tests.
- Shell: cambios acotados en main-view-modern.fxml, navegación/localización y AppSettings para preferencia no secreta.
- Familia: adaptador y conexiones en controller/coordinadores/FXML correspondientes; recursos de texto de esa familia.
- Documentación: fichas, ejemplos, índice y matriz de cobertura.
- Packaging: configuración de recursos y prueba desde el artefacto empaquetado.

No trasladar la nueva lógica al ModernMainController como un gran switch por operación. Los archivos fuente de cada caso permiten localizar dónde enlazar; no obligan a modificar todos ellos.

## 9. Matriz de aceptación para la revisión de Codex

Luna entregará un inventario por operación/acción/campo/opción con columnas: ID, control/fuente, ES, EN, regla, ejemplo, prueba/evidencia, estado y limitación. Los estados son PENDIENTE, PARCIAL y COMPLETO. Un requisito sin evidencia verificable permanece pendiente o parcial.

| Criterio | Evidencia exigida |
|---|---|
| Cobertura de catálogo | 96 IDs op_* resueltos y ninguna entrada nueva sin ficha; acciones internas inventariadas y vinculadas. |
| Cobertura auxiliar | 24 fichas A y las herramientas realmente accesibles adicionales que aparezcan al integrar. |
| Cobertura dinámica | Todas las entradas seleccionables de lote, nodos y comandos tienen definición específica o referencia con diferencias. |
| Campos/opciones | Cada control configurable tiene explicación propia o referencia común explícita; todas sus opciones tienen efecto y condiciones. |
| Coherencia | Mismas capacidades en ayuda, selector, validación y handler; divergencias documentadas como defectos separados. |
| Contexto | Ruta, acción, pestaña, algoritmo, fuente y cambios programáticos actualizan ayuda correctamente. |
| Lifecycle | Navegar repetidas veces y cargar módulos diferidos no duplica listeners ni conserva contexto anterior. |
| Idiomas | ES/EN completos; cambio de idioma actualiza la vista abierta; códigos técnicos conservados. |
| Interacción | Teclado, foco, etiquetas accesibles, ventana estrecha y explicación de controles deshabilitados comprobados. |
| Datos | Activar/consultar ayuda no modifica campos, resultados ni clasificaciones; no ejecuta operaciones. |
| Secretos | Prueba con valores centinela en clave/PIN/password/payload confirma que no entran en contexto, texto de ayuda, preferencias o exportación de ayuda. |
| Resultados | Cada resultado y estado tienen definición de alcance; inspección, validación y confianza diferenciadas. |
| Ejemplos | Cada acción tiene datos sintéticos y comprobación positiva/negativa pertinente, o referencia explícita a fixture reutilizable. |
| Recursos | Enlaces/documentos/imagenes seleccionados existen y funcionan desde classpath y JAR fuera del checkout. |
| Mantenimiento | La aparición de una opción/operación nueva se detecta en cobertura sin ocultarse por fallback. |

### Verificación prevista

Crear tests de política/cobertura, recursos/idiomas y recorridos de UI representativos. Los nombres pueden ajustarse, conservando su finalidad:

- OperationHelpRegistryTest: IDs, acciones, campos y cobertura dinámica; detectar entradas sin definición.
- ContextualHelpPolicyTest: dependencias y compatibilidad de los tres pilotos y familias extraídas.
- ContextualHelpResourcesTest: bundles, índices, ejemplos y recursos locales accesibles.
- ContextualHelpUITest: abrir/cerrar, navegar, cambiar selección/idioma, aplicar plantilla, volver al módulo y usar ventana estrecha.
- Pruebas de preservación y secreto: eventos de ayuda no mutan entradas ni transportan valores centinela.

Comando de tests unitarios específico cuando esas clases existan: mvn -o -q -Dtest=OperationHelpRegistryTest,ContextualHelpPolicyTest,ContextualHelpResourcesTest -DrunUiTests=false test.

Para recorridos de UI, usar una sesión gráfica y seleccionar los tests de ayuda y los existentes del módulo afectado. Si cambia una regla criptográfica extraída, ejecutar también sus tests del núcleo.

En B15 ejecutar una única validación integrada apropiada —mvn -o -q package con sesión gráfica, o la alternativa documentada si no existe pantalla— y comprobar recursos desde el JAR resultante. El modo offline requiere dependencias en caché; un fallo de caché no demuestra un fallo de la funcionalidad.

No repetir todas las pruebas criptográficas por cada cambio de texto. Ampliar la verificación cuando haya cambios de reglas, fallos o riesgos sin resolver. Estas comprobaciones están prescritas para la implementación futura; no se han ejecutado para crear este documento.

## 10. Entrega de Luna y método de revisión

Cada entrega debe contener:

1. Bloque y casos/acciones completados, con matriz actualizada.
2. Resumen de comportamiento resultante y archivos propios modificados.
3. Evidencia de campos/opciones y capturas de los recorridos relevantes.
4. Tests/comandos realmente ejecutados y resultado.
5. Hallazgos del código actual, limitaciones y casos pendientes.
6. Instrucción concreta para reproducir el piloto o flujo.
7. Ausencia de cambios ajenos atribuidos a la entrega.

Codex inspeccionará el diff del bloque, trazará requisitos a código/FXML/recursos y comprobará los casos de comportamiento. Aprovechará pruebas suficientes aportadas por Luna y ejecutará comprobaciones adicionales donde falte evidencia, especialmente cambios programáticos, lifecycle, reglas compartidas, visibilidad y contenido empaquetado.

El resultado de revisión será APROBADO, APROBADO CON SEGUIMIENTO o REQUIERE CORRECCIÓN, con hallazgos reproducibles y corrección mínima. Un problema de cobertura requerido no se clasifica como seguimiento para dar por terminado el bloque.

La revisión corresponde a las entregas que se traigan a este chat. Esta especificación no crea una automatización de seguimiento ni inicia por sí misma la implementación.

### Prompt inicial listo para derivar

> Implementa el bloque B00 de docs/ENCARGO_LUNA_AYUDA_CONTEXTUAL.md en CryptoCarver. Lee también docs/PROPUESTA_AYUDA_CONTEXTUAL.md y docs/AYUDA_CONTEXTUAL_INVENTARIO_UI.md. Para este encargo tú eres la implementadora y Codex el revisor; el reparto anterior de LUNA_QA_PROTOCOL.md no aplica. Respeta los cambios locales ajenos, no crees subagentes y no hagas commits ni pushes. Entrega infraestructura de ayuda local, modelo por ID/acción/campo, vista común accesible, activación y localización, sin cambiar cálculos ni parámetros. Presenta una entrega acotada con matriz, archivos, evidencia y pruebas reales para revisión de Codex. Conserva el alcance completo de los casos para los bloques siguientes.

## 11. Alcance de la preparación de este encargo

La preparación incluye revisión estática, extracción del inventario FXML, fichas por ID y comprobación de referencias locales. No es una auditoría normativa de todos los estándares ni una prueba exhaustiva de las combinaciones de la aplicación.

Las etiquetas inferidas y el estado editable de un control en FXML no prueban su uso en ejecución; Luna debe resolverlo con el controller y handler. Las listas existentes tampoco prueban todas sus combinaciones. Estos límites están incluidos como requisitos de implementación y revisión, no como compatibilidades supuestas.

Los documentos son entregables de preparación. La funcionalidad y los tests indicados siguen pendientes de implementación.

