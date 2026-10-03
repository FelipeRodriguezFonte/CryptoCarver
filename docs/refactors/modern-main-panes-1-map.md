# Modern main panes — fase 1

## Inventario previo (2905 líneas)

La navegación resuelve primero los alias en `UiNavigationRegistry` y entrega `Route.section()` canónico al shell. No se cambian ese registro, `NavigationRouter` ni `load*Content`. Los alias no vuelven a clasificarse por su texto visible.

| Módulo | Selección existente | Estado leído / efecto |
| --- | --- | --- |
| Cipher | Symmetric/AES/DES/Padding activa workspace simétrico y vuelve arriba; Format-Preserving, File Cipher, OpenPGP/GPG y Asymmetric/RSA/ECC eligen título parcial, en ese orden | host, controlador Cipher, scroll; catálogo cipher |
| Authentication | Signature/Sign → Signatures; MAC → MAC | host; catálogo authentication; expandir y revelar |
| Payments | ISO 8583/ISO8583, Host Command, DUKPT, CVV, PIN Block Operations, Clear/Encode/Decode, Encrypted/ISO, Generation/IBM/Generate/Verify, en ese orden | host; catálogo payments; expandir y revelar. Destino neutro: Host Command Bank |
| Keys simétricas | Delegación a Keys: incluidos PKCS#11 Profiles, ICSF Batch, ICSF Export/Import, ICSF; resto por título | controlador Keys, sección simétrica, acordeón e incluidos mutuamente excluyentes; catálogo keys; revelar |
| Keys asimétricas | Delegación a Keys por título canónico | controlador Keys y sección asimétrica; catálogo keys; revelar |
| Certificates | Delegación a expandPane: CMS Inspector especial; resto por título | controlador Certificates/acordeón; catálogo certificates; no reveal del shell |
| Generic | NavigationRouter.expandByTitle con sección canónica | host; catálogo generic; expandir y revelar |
| PQC | Delegación a expandAccordionPane por sección canónica | controlador PQC/acordeón; catálogo pqc |
| XML | Delegación a expandAccordionPane por sección canónica | controlador XML/acordeón; catálogo xmlSecurity |
| WSS | Delegación a expandAccordionPane por sección canónica | controlador WSS/acordeón; catálogo wssSecurity |
| EMV | Coincidencia de sección canónica con título | host; catálogo emv; expandir y revelar |

`ModulePaneMatcher` elimina decoraciones y compara inclusión en ambos sentidos. Si no coincide el título visible, traduce las entradas del catálogo del módulo; finalmente busca en todos los catálogos (incluidos FXML con catálogo propio). La primera coincidencia gana: las colisiones y ausencias deben revisarse, no conservarse como contratos correctos.

`moduleAccordion` y `findAccordion` recorren recursivamente el host materializado. Solo los usan las expansiones. `revealExpandedPane` difiere el foco y el scroll con Platform.runLater; lee mainScrollPane y contentContainer en el momento de ejecución. Se moverán junto a las reglas.

## Código muerto y límites

- La segunda comprobación Symmetric/AES/DES/Padding dentro del bucle de Cipher es inalcanzable tras el retorno temprano del workspace. Se conserva durante esta extracción literal.
- `hexToBytes` y `bytesToHex` privados del shell no tienen llamadas en el shell; búsqueda de sus nombres en clases, FXML y tests encuentra conversiones de otras clases, sin llamada/reflexión a estos métodos. Son ajenos a navegación y se conservan fuera de alcance.
- No hay llamadas FXML ni reflexión de tests a los métodos de expansión privados ni a moduleAccordion/findAccordion/revealExpandedPane (búsqueda en src/test y src/main/resources). Los callbacks del router y de restauración de sesiones sí los llaman; se mantienen como delegados.
- Cipher simétrico tiene un workspace deliberado sin panel expandido; no constituye un fallo. Keys incluidos son TitledPane externos al acordeón y deben observarse también.
- Los dos errores históricos de Payments (ISO 8583 y Host Command Bank) ya están corregidos antes de esta fase.

## Caracterización y revisión

Revisión anterior a fijar digests: 189 rutas por idioma (378 filas), con cierre previo de paneles para evitar selecciones heredadas y observación de las secciones visibles e incluidos de Keys.

Fallos reales que **no** son comportamiento a conservar:

| Fallo | Rutas (EN y ES) | Panel previo incorrecto | Destino esperado |
| --- | --- | --- | --- |
| DSA | DSA Key Generation; Generate DSA Key | ECDSA Key Generation | DSA Key Generation |
| EdDSA | EdDSA Key Generation; Generate EdDSA Key | DSA Key Generation | EdDSA Key Generation |

Causa: `ModulePaneMatcher.containsEither` acepta subcadenas en ambos sentidos; ECDSA incluye DSA y EdDSA incluye DSA. Se arreglarán por identidad canónica de panel en el navegador, sin alterar el matcher compartido ni KeysController.

Excepción deliberada `<none>`: AES Encryption, DES/3DES Encryption, Modes & Padding, Symmetric Ciphers, Symmetric Decrypt, Symmetric Encrypt y Symmetric Encryption usan el workspace simétrico de Cipher, que no tiene acordeón visible.

Observación fuera de la selección de panel: el callback EMV llama updateContentHeader("EMV Operations") y sobrescribe el breadcrumb de todas sus rutas con Session Key / EMV Operations. Sus paneles sí son correctos; se conserva y se documenta como límite de esta fase (no se cambia el callback de encabezado).

Los nombres heredados del banco host y su fabricante se normalizan dinámicamente en las transcripciones nuevas. Se recorren las entradas originales del registro, incluidos los alias; la normalización solo afecta el texto guardado. Recurso revisado: `src/test/resources/com/cryptocarver/ui/module-pane-navigation-baseline.tsv`, filas tabuladas idioma/ruta/módulo/sección/panel/breadcrumb, orden estable por nombre original de ruta.

Digests iniciales SHA-256 por idioma (UTF-8, LF final):

- EN: `a8845d4bd744d7ee6e2f6da2ba19692fcfc7836cc70fa479956c7ba8a32a35eb`
- ES: `42cbb38964e7d098b50ab0f1c06fca1b26fb9c35add4e31b9c324c977a8f54ce`

La caracterización preserva provisionalmente los dos errores explícitamente anotados, únicamente para demostrar la extracción sin cambios; las correcciones posteriores actualizarán las cuatro filas por fallo (dos alias × dos idiomas).

## Rutas de sección sin destino de panel del shell

JOSE, COSE y Wallet contienen acordeones internos dentro de sus secciones. Sus rutas del registro tienen `section == null`; el shell llama a `showSection(currentActiveOperation)`, no a ningún método expand*AccordionPane. Se mantienen fuera de la extracción de esta fase y se registran en una transcripción complementaria separada del digest de destinos de panel. No se atribuye a sus acordeones internos el comportamiento de un expander del shell. Se documenta esta distinción para mantener la extracción dentro de los métodos enumerados en el encargo, cubriendo también la lectura literal de todas las rutas a módulos que contienen acordeones.

| Módulo | Reglas existentes / estado | Rutas documentadas fuera del digest |
| --- | --- | --- |
| JOSE | showSection oculta JWT/JWE/JWK/JWA/Inspector y muestra según prefijo JWT, JWE, JWK, JWA o Token Inspector; controlador y secciones VBox, catálogos jose; no selecciona panel interno | JWT (Signed); JWE (Encrypted); JWK (Keys); JWA (Algorithms); Generate JWE; Decrypt JWE; Generate JWT; Validate JWT; Generate Nested JWT; Token Inspector; PEM to JWK; JWK to PEM; JWK Thumbprint; JWKS Rotate Key; Load JWKS File; Import Key (PEM); Import Key (JSON) |
| COSE | showSection oculta Sign1/MAC0/Encrypt0 y muestra por prefijos COSE Sign1/Verify1, COSE MAC0, COSE Encrypt0/Decrypt0; controlador y secciones VBox, catálogo cose; no selecciona panel interno | COSE Sign1; COSE Verify1; COSE MAC0; COSE Verify MAC0; COSE Encrypt0; COSE Decrypt0 |
| Wallet | showSection elige SD-JWT/mdoc/Status List/eIDAS Certificate/Trusted List o Trusted Entity List/CBOR/SCA/AdES, con fallback SD-JWT; controlador y secciones VBox, catálogo wallet; no selecciona panel interno | SD-JWT VC; mdoc / mDL; Status List; eIDAS Certificate Profiles; Trusted List; Trusted Entity List JSON; CBOR Inspector; SCA / OpenID4VP; AdES Validation |

Estas 32 rutas se recorren igualmente con navigateToModule, EN y ES, en `sectionOnlyAccordionModulesAreRecordedSeparately`, con el shell real y restauración de AppSettings. La transcripción complementaria `module-section-navigation-baseline.tsv` tiene 64 filas. Tras cerrar los paneles para aislar cada ruta, las 32 muestran `<none>`: showSection solo revela secciones, no abre un panel interno y Route.section no define qué panel expandir. Se anotan como limitaciones de navegación entre secciones ajenas a los expanders extraídos, no como elecciones de panel correctas. Resolverlas exige ampliar la lógica de los controladores de sección y definir destinos; no se inventa una selección en esta fase.

Antes de fijar el anexo se revisaron sus 64 filas; módulo y breadcrumb siempre están presentes. Digests complementarios, sin cambiar los digests previos de la extracción:

- EN: `d724ed9eea9748381aa32d7012c2553733ef147d09991ea7e2015bb61b41d8af`
- ES: `7029a5c66e7b736b7ac6cac553f9cdb184de1989e24c98e46dce458831e9a17c`

La cobertura combinada es **221 rutas × 2 idiomas = 442 filas**. Se añade un commit complementario de caracterización por esta distinción encontrada en la revisión final. Los otros módulos excluidos (historial, sesiones, Shelf, conversores independientes y Process Designer) no ofrecen destinos en un acordeón del shell. No se han cambiado sus callbacks ni controladores.

## Extracción y verificación

`ModulePaneNavigator` recibe un mapa de Supplier<ModuleHost> para todos los módulos con acordeón, además de proveedores de Keys, Cipher y los controladores a los que ya delegaba el shell, scroll y contentContainer. El constructor solo guarda proveedores; no los evalúa ni captura controladores. Los campos se resuelven en cada navegación y dentro del callback diferido de revelado. El shell conserva todos los métodos expand* como delegados de una línea y sus callbacks de carga/restauración.

Se movieron la clasificación, la búsqueda recursiva y revealExpandedPane. El delegado de reveal del shell se eliminó al quedarse sin llamadas; la búsqueda previa descarta FXML, otras clases y tests. Generic usa su delegado también desde el callback del router. No se modifican NavigationRouter, UiNavigationRegistry, KeysController ni load*Content.

Extracción: ModernMainController pasa de 2905 a 2751 líneas; ModulePaneNavigator tiene inicialmente 235 líneas. Los dos digests iniciales permanecen idénticos tras la extracción. No hubo que adaptar tests de fuente ni reflexión: los puntos consultados por los tests siguen en el shell.

## Corrección DSA

La prueba `dsaRoutesSelectTheirOwnPane` identifica el panel por su control propio dsaKeySizeCombo, no por el matcher de producción. Antes del arreglo falla con DSA → ECDSA (1 fallo, 0 errores). Después verifica ambos alias en EN y ES.

Para la sección canónica DSA Key Generation, el navegador elige por igualdad del título sin decoraciones (o por igualdad con la traducción de la entrada canónica del catálogo Keys). El resto de destinos mantiene la delegación literal. No se amplían reglas contains ni se cambia el matcher compartido.

Solo cambian cuatro filas: el título expandido de DSA Key Generation y Generate DSA Key, en cada idioma; módulo, sección y breadcrumb permanecen iguales.

| SHA-256 | Antes | Después de DSA |
| --- | --- | --- |
| EN | `a8845d4bd744d7ee6e2f6da2ba19692fcfc7836cc70fa479956c7ba8a32a35eb` | `4cd76346b8916a3acd35bdc222301d26d25cc27b53cdf5c270898fd814d4a4ee` |
| ES | `42cbb38964e7d098b50ab0f1c06fca1b26fb9c35add4e31b9c324c977a8f54ce` | `e58cb04e849926b3173a9d3bba09f352f20fc0ec13d9c4a34432f7e59638a995` |

Pendiente: fix EdDSA y resultados separados UI/completos.
