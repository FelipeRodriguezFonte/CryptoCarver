# UX-20 — Handoff de consolidación de estado e i18n

## Alcance entregado

- Se añadió `ModuleResetPolicy` como contrato común para módulos especializados.
  `CLEAR` limpia exclusivamente datos locales; `RESET_DEFAULTS` restaura sólo
  selectores/flags seguros. La política conserva el foco cuando el control sigue
  visible y gestionado.
- Se migraron ASN.1, XML/XAdES, WSS, JOSE, CMS Inspector, PAdES, ASiC-S,
  Payments, EMV y Process Designer. Los servicios compartidos de historial,
  Clipboard Shelf, perfiles y secretos no forman parte del grafo local ni son
  tocados por estas acciones.
- Se añadieron acciones FXML diferenciadas `Clear`/`Reset Defaults` y se
  incorporaron sus textos a `ModuleTextCatalog` y a los bundles ES/EN.
- Se localizaron feedback dinámico de reset/limpieza, progreso/cancelación y
  errores/estados principales de XML, WSS, Payments, EMV, CMS y Process
  Designer. Los valores técnicos siguen siendo argumentos sin traducir:
  XML/SOAP/JWT, PEM, certificados, PIN/PAN/KSN, tags EMV, hashes, bytes, IDs y
  mensajes técnicos de las operaciones.
- CMS Inspector pasó a tener binding de módulo y catálogo propio; PAdES/ASiC-S
  ahora enlazan el root completo para traducir también las acciones superiores.

## Pruebas

- `mvn -q -DskipTests compile` — correcto.
- `mvn -q -Dtest=ModuleResetPolicyTest,Ux20HeadlessTest test` — correcto.
- `mvn -q test` — ejecutado; no se observaron fallos de la suite headless.
- `xmllint --noout` sobre los diez FXML modificados — correcto.
- `git -c core.fsmonitor=false diff --check` — correcto.
- `mvn -q -DrunUiTests=true test` — no ejecutable en este entorno por JavaFX.

Las nuevas pruebas headless verifican la diferencia entre `CLEAR` y
`RESET_DEFAULTS`, la preservación de artefactos técnicos/compartidos, fallback
de i18n y la presencia de ambas acciones en FXML/catálogos/bundles.

## Limitación JavaFX

JavaFX 21.0.5 no puede iniciar QuantumRenderer: no hay display/pipeline
utilizable y el runtime no puede escribir/copiar `libprism_sw.dylib` en
`~/.openjfx/cache/21.0.5+1/aarch64/.lock` (`Operation not permitted`). Deben
repetirse las pruebas UI opt-in en macOS/CI con display o pipeline software y
caché JavaFX escribible, incluyendo foco tras `Clear` y cambio ES/EN en XML,
WSS, Payments y EMV.

## Deuda y módulos pendientes

1. Completar la matriz visual con display: foco, expansión de paneles, tamaños
   de diálogos y cambio de idioma en vivo para todas las rutas especializadas.
2. Sustituir progresivamente los textos de feedback todavía muy específicos de
   algunas rutas heredadas de Payments/XML por claves individuales, manteniendo
   sus valores técnicos como argumentos. Los estados principales de UX-20 ya
   usan claves dinámicas y fallback seguro.
3. Auditar visualmente CMS Sign/Verify/Encrypt/Decrypt, TR-31 y módulos
   opcionales (History, Clipboard Shelf, Batch Runner y Compare Results) en la
   misma sesión gráfica.

No se hizo commit. Se conservaron los cambios ajenos que ya estaban presentes
en el worktree.
