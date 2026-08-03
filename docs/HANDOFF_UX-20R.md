# Handoff UX-20R

## Alcance

UX-20R cierra la consolidación de estado de módulos de UX-20 manteniendo la política común `Clear` / `Reset Defaults` y corrigiendo el feedback accionable de Payments.

- `Reset Defaults` restaura sólo selectores y flags seguros; conserva entradas, resultados locales, foco, historial, Clipboard Shelf, perfiles, keystores y secretos compartidos.
- `Clear` elimina únicamente datos y resultados contenidos en el módulo mediante la política común; no alcanza artefactos compartidos.
- Los errores de Payments ya no colapsan en `module.payments.error.required` cuando existe una causa concreta. Se cubren PIN/PAN, longitud/formato de PIN block, CVK A/B, PAN, expiración, service code, ATC para dCVV, MAC, traducción de PIN, PVV y Track.
- Las claves nuevas están en `ModuleTextCatalog` y en los bundles ES/EN. Los argumentos técnicos (PAN, CVK, ATC, PVV, Track, bytes y hexadecimal) permanecen sin traducir.

No se modificaron operaciones criptográficas ni los artefactos técnicos producidos por ellas.

## Pruebas

- `ModuleResetPolicyTest`: diferencia `Clear` frente a `Reset Defaults` y verifica preservación de estado compartido y fallback i18n.
- `PaymentsValidationHeadlessTest`: comprueba que cada validación Payments mínima conserva un mensaje distinguible, accionable y localizado en EN/ES, y que el controlador usa la clave específica.
- `Ux20RPaymentsLiveUITest`: prueba opt-in (`-DrunUiTests=true`) un PAN inválido en EN/ES, `Reset Defaults` con datos y foco preservados, y `Clear` eliminando el dato local.
- `mvn -q test`
- `xmllint --noout` sobre los FXML modificados.
- `git diff --check`

## Limitación JavaFX

Las pruebas UI se mantienen opt-in y no se deshabilitan. En entornos sin display o pipeline JavaFX disponible pueden fallar antes de ejecutar la aserción con errores de inicialización de Prism/toolkit; la validación headless sigue siendo ejecutable y cubre los contratos deterministas.

## Deuda pendiente

- Ejecutar la batería UI opt-in en un runner con display/pipeline JavaFX y registrar el resultado.
- Completar la revisión de textos legacy fuera del flujo Payments que aún pertenezcan a módulos no incluidos en UX-20R.
- Migrar progresivamente mensajes técnicos de operaciones avanzadas de Payments a claves parametrizadas sin alterar sus valores técnicos.

## Ejecución reproducible de UI en Linux

Se añadió `.github/workflows/ui-tests.yml`, con el job `javafx-ui` y el nombre
visible `JavaFX UI suite (Xvfb)`. Se dispara en `push`, `pull_request` y
`workflow_dispatch`; sólo tiene permiso `contents: read`, no usa secretos,
despliegues ni publicación. El workflow conserva separada la batería
headless: `mvn test` no activa las pruebas JavaFX.

El paso de ejecución llama a `bash scripts/run-ui-tests.sh`, que mantiene el
runner reutilizable para Linux aunque se invoque fuera de GitHub Actions.

Dependencias del runner:

- Java compatible con `maven.compiler.release` (17 o superior) y Maven.
- `xvfb-run`/Xvfb.
- Bibliotecas X11/GTK/Mesa requeridas por JavaFX: `libgtk-3-0`, `libgbm1`,
  `libx11-xcb1`, `libxtst6`, `libxrender1`, `libxxf86vm1`, `libgl1` y
  `libgl1-mesa-dri`.

Comandos reproducibles en Linux:

```bash
sudo apt-get update
sudo apt-get install -y xvfb libgtk-3-0 libgbm1 libx11-xcb1 libxtst6 libxrender1 libxxf86vm1 libgl1 libgl1-mesa-dri
bash scripts/run-ui-tests.sh
```

El runner equivale a:

```bash
xvfb-run --auto-servernum --server-args="-screen 0 1920x1080x24" mvn -q -DrunUiTests=true test
```

Para una prueba focalizada se pueden añadir argumentos Maven, por ejemplo:

```bash
bash scripts/run-ui-tests.sh -Dtest=Ux20RPaymentsLiveUITest
```

La validación local de esta entrega detectó Maven y la caché JavaFX, pero no
`xvfb-run` en macOS; por ello no se ejecutó la suite gráfica localmente. La
limitación ambiental queda acotada al display virtual ausente y no se oculta
ni se convierte en una dependencia de la suite headless. El resultado del job
remoto queda pendiente hasta que GitHub Actions ejecute el workflow.

## Bloque de auditoría especializado (sin Payments)

Auditoría aplicada a `ASN1Controller`, `AsicController`, `CmsInspectorController`, `EMVController`, `JOSEController`, `PadesController`, `ProcessDesignerController`, `WssSecurityController` y `XMLSignatureController`.

- Se localizaron validaciones de campos requeridos, formatos DOL/DER, precondiciones ARQC/ARPC/Track 2, carga de KeyStore/PKCS#11, selección de alias, archivos de entrada/salida, perfiles TSA, tokens RFC 3161, formatos PEM/JSON JWK y preflight AAD/IV.
- Se migraron sus mensajes accionables y estados relevantes a claves parametrizadas ES/EN.
- Los detalles dinámicos de excepciones se conservan como argumentos técnicos dentro de mensajes localizados; no se alteran XML, SOAP, JOSE, ASN.1, EMV, ASiC, PAdES ni valores criptográficos.
- Payments queda fuera de este bloque y no se modificó.

Regresiones añadidas: `SpecializedFeedbackHeadlessTest`, con comprobación de claves EN/ES, distinción de traducciones, uso por controlador y ausencia del feedback genérico antiguo en EMV/WSS/XML.

Pruebas adicionales ejecutadas: compilación Maven y batería headless focalizada. La limitación UI JavaFX descrita arriba permanece pendiente de un runner con display/pipeline.
