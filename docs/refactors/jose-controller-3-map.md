# JOSEController — fase 3: JWK/JWKS

Base: `1608` líneas y `103` métodos. Inventario reproducible con `python3 docs/refactors/jose_method_map.py`.

Destino: `JoseJwkCoordinator`, con `record View`, proveedores vivos de controles y `Supplier<StatusReporter>`. No recibe ni captura el controlador. La API pública de `JOSEController` y los controles FXML se conservan.

| Método | Estado UI / datos vivos | Efectos y límites |
|---|---|---|
| `generateNewJWK` (83 líneas) | Argumentos `alg`, `use`; no lee controles | Generación RSA/EC/OKP/oct mediante Nimbus/JCA/BC; UUID aleatorio; contiene material privado/oct. No publicar ni registrar el JWK. |
| `addToJWKSet` (21) | `currentJson`, `newKey` | Combina JSON JWKS; nueva clave aleatoria; validación/parsing Nimbus. |
| `exportPublicJWKS` (4) | JSON argumento | Retira mitades privadas mediante Nimbus y serializa JWKS pública. |
| `handleRotateKey` (36) | Lee `jwksRotateAlgoCombo`, `jwkKeyOpsField`, `jwksArea`; modifica `jwksArea` | Tiene confirmación modal para claves simétricas vía `LabPrompt.JWKS_SECRET`, genera y concatena clave, publica status localizado. El diálogo y su orden permanecen en el controlador; lógica de mutación se delega. |
| `handleNewJWKS` (5) | Modifica `jwksArea` | Inicializa JSON vacío. Delegado de una línea. |
| `handleExportPublicJWKS` (11) | Lee `jwksArea` | Presenta diálogo JavaFX. Se conserva como cableado de vista. |
| `handleLoadJWKS` (16) | Selector de fichero, escribe `jwksArea` | Selector/modal queda en controlador; lectura y status del éxito se delegan. |
| `asymmetricJwk` (46), `pem` (5), `withKeyId` (7) | Argumentos solamente | Conversión PEM/JWK para RSA, EC y OKP. No publica ni conserva claves. Helpers sin estado. |
| `convertPemToJwk` (23/overload), `convertJwkToPem` (86) | Lee argumentos; escribe `jwkOutputArea` | Conversión de clave privada/pública/simétrica; errores mostrados en el área sin publicar ni registrar material. API se mantiene por delegados. |
| `calculateThumbprint` (10) | Lee argumento JWK/PEM; escribe `jwkOutputArea` | RFC 7638; thumbprint público, errores en área. |
| `handlePemToJwk`, `handleJwkToPem`, `handleCalculateThumbprint` | Leen `jwkInputArea`, combo tipo/uso y `jwkKeyIdField`/`jwkKeyOpsField`; validan; el coordinador escribe resultado | Validación localizada delegable; no abre diálogo. |
| `handleInspectJwkMetadata` | Lee `jwkInputArea`, escribe `jwkOutputArea` | Lee `use`, `key_ops` y metadatos; no cambia el JWK. Puede compartir propietario por afinidad, no se incluye en la petición de extracción. |

`use` y `key_ops` se aplican a la clave recién generada mediante `JoseJwkPolicy.withMetadata` y al convertir PEM con el overload existente. La carga de JWKS solo lee texto y lo asigna; sin caché de claves ni estado independiente. `AppSettings` solo interviene para la confirmación existente de JWKS simétricas (`LabPrompt`); el `StatusReporter` es proveedor vivo desde `JoseCoordinatorSupport`. Ningún método de esta fase modifica `crypto/`, `pom.xml`, `OperationResult`, `StatusReporter` ni telemetría.

Lógica de generación/conversión/huella/JWKS pura en cuanto a controles; handlers de archivos y diálogos siguen como cableado. La conversión muestra explícitamente el material solicitado solo en `jwkOutputArea`; no se publica a historial/Shelf/status. No se traslada la confirmación de publicación simétrica ni cambia su texto o su orden.

Caracterización requerida antes de extraer: RSA, EC P-256/P-384/P-521/secp256k1, OKP Ed25519/Ed448/X25519/X448 y oct; conversión JWK↔PEM, thumbprint, rotación JWKS, importación JSON/PEM, errores legibles EN/ES, FULL_LAB/MASKED/REDACTED. Transcripción SHA-256 portable: claves inventadas y valores aleatorios normalizados; no registrar PEM privado/JWK oct ni mensajes de proveedores. Fixture `JoseCharacterizationSupport` restaura preferencias y Shelf.

Caracterización de generación/conversión fijada antes de extraer: `315e6810e2bc477b3f4de37ff4bc6c1b81acc057f1b4a5bbebb5f3e8a88c9d1a`. Se normalizan UUID, claves y datos oct; el digest contiene únicamente algoritmos, curvas, tipos de clave, flags y resultados de ida/vuelta. Incluye error legible para PEM/JWK inválido en EN/ES. La firma actual `generateNewJWK(alg,use)` genera Ed25519 para EdDSA y no permite seleccionar Ed448; Ed448 se caracteriza por importación PEM. La UI no enumera un algoritmo/selector de curva Ed448; no se cambia el contrato ni se crea un algoritmo JOSE no estándar.

Fase 4 queda fuera de este mapa. `initialize` y `showSection` permanecen por evaluar separadamente: el inicializador se enlaza a `ModuleI18n`, registra listener débil retenido por el controlador, adjunta listeners a combos y rellena controles en orden; por tanto, mover el método entero no es seguro. Solo se extraerá la porción pura de poblar combos/listas si se preserva exactamente el orden y la instalación de listeners. No se moverán showSection ni listeners sin evidencia que descarte dependencia de orden.
