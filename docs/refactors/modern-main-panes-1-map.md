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

Pendiente: transcripción exhaustiva EN/ES anterior a extracción, anomalías, SHA-256 y cobertura de alias.

## Extracción y verificación

Pendiente: proveedores, líneas finales, digests y resultados separados UI/completos.
