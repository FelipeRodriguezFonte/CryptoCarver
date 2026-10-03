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

## Extracción y verificación

`ModulePaneNavigator` recibe un mapa de Supplier<ModuleHost> para todos los módulos con acordeón, además de proveedores de Keys, Cipher y los controladores a los que ya delegaba el shell, scroll y contentContainer. El constructor solo guarda proveedores; no los evalúa ni captura controladores. Los campos se resuelven en cada navegación y dentro del callback diferido de revelado. El shell conserva todos los métodos expand* como delegados de una línea y sus callbacks de carga/restauración.

Se movieron la clasificación, la búsqueda recursiva y revealExpandedPane. El delegado de reveal del shell se eliminó al quedarse sin llamadas; la búsqueda previa descarta FXML, otras clases y tests. Generic usa su delegado también desde el callback del router. No se modifican NavigationRouter, UiNavigationRegistry, KeysController ni load*Content.

Extracción: ModernMainController pasa de 2905 a 2751 líneas; ModulePaneNavigator tiene inicialmente 235 líneas. Los dos digests iniciales permanecen idénticos tras la extracción. No hubo que adaptar tests de fuente ni reflexión: los puntos consultados por los tests siguen en el shell.

Pendiente: fixes DSA/EdDSA y resultados separados UI/completos.
