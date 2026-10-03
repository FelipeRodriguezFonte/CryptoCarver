# Encargo 57: informe final

Rama codex/ui-minor-fixes desde main `1ec7e77332fb987867c236251636b7d42b71ce6f`. [CI de base](https://github.com/FelipeRodriguezFonte/CryptoCarver/actions/runs/37145329351): UX quality gates, JavaFX UI y PKCS#11 inventory integration success.

## Causas y arreglos

- A: isError buscaba Invalid y otras subcadenas también en el nombre del perfil. Los perfiles de escenarios negativos son datos válidos para cargar; su nombre describe lo que fallará al ejecutar. Se reconoce la envoltura informativa de carga localizada antes de clasificar el resto del texto. Se mantienen los mensajes y la detección de errores reales. [Mapa A](ui-status-profile-severity-map.md).
- B: EMVController.loadProfile rellenaba los controles y escribía el nombre solo en consola. El status visible conservaba el aviso genérico de navegación. Ahora publica el nombre con la misma clave localizada que DukptCoordinator y PaymentsController. [Mapa B](ui-emv-profile-status-map.md).
- C: el panel Manual Conversion soportaba Base94, pero el registro del contrato de la barra lo omitía; selectIfSupported borraba ese valor. Además, el contrato no llegaba al controller en su primera carga perezosa. Se corrige la lista y la entrega del contrato. La elección de salida se guarda por operación en AppSettings, con validación de formatos admitidos y guard durante el cambio de contrato. Se conservan las preferencias de otras operaciones; no se guarda el input ni contenido de formularios. [Mapa C](ui-toolbar-output-formats-map.md).

## Reproducción previa y commits

Pase previo a cualquier arreglo: 12 casos, 10 fallos, 0 errores/omisiones. A: 2 fallos de carga y 2 errores reales correctamente clasificados; B: 2 fallos; C: 6 fallos (Base94, primera apertura y persistencia, EN/ES). Se anotó cada fallo en su mapa antes de modificar producción. El commit 2c31870 contiene solo mapas y tests; `git diff main 2c31870 -- src/main` es vacío. Por tanto, las reproducciones usaron exactamente la producción de main 1ec7e77, no una implementación intermedia. No quedaron fallos en los gates finales que requiriesen otra comprobación contra main.

Un commit por arreglo: A 323f887; B 51d4302; C 48be8be. El commit inicial guarda la reproducción y el último guarda este informe. Ningún test existente se modificó. Los tests nuevos comparten una fixture FXML con Stage real, configuración aislada y restauración de AppSettings/Shelf, locale, cierre del shell y Scene. Los tres tests se parametrizan en EN y ES; C recorre los siete formatos en ambas direcciones y recrea el shell leyendo el archivo de AppSettings.

## Suites independientes

Entorno: macOS, Temurin 21.0.8, JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home. Ejecuciones secuenciales, informes separados y sin cambios de código entre gates:

| Suite | Comando | Tests | Clases | Fallos | Errores | Omitidos | Exit |
|---|---|---:|---:|---:|---:|---:|---:|
| UI | `mvn -o -q test -Plow-cpu -DrunUiTests=true -Dgroups=ui` | 484 | 97 | 0 | 0 | 0 | 0 |
| Completa | `mvn -o -q test -Plow-cpu` | 2827 | 411 | 0 | 0 | 1 | 0 |

ProfileStatusSeverityUITest (4), EmvProfileStatusUITest (2) y ToolbarOutputFormatsUITest (6) pasan en ambas suites. La única omisión es Pkcs11SessionEncapsulationTest.testSoftHsmUpdateCertificateChain. Informes locales: target/ui-minor-ui-reports y target/surefire-reports. Pase dirigido C: 16 pruebas, sin fallos/errores/omisiones, incluida caracterización existente de navegación, conversión y arranque. Pase dirigido A: 4; B: 2; ambos verdes.

## Archivos y líneas

| Producción | Antes | Después | Delta |
|---|---:|---:|---:|
| src/main/java/com/cryptocarver/ui/StatusBarPresenter.java | 62 | 72 | +10 |
| src/main/java/com/cryptocarver/ui/EMVController.java | 1468 | 1469 | +1 |
| src/main/java/com/cryptocarver/model/OperationFormatRegistry.java | 128 | 128 | 0 (dos listas corregidas) |
| src/main/java/com/cryptocarver/ui/ModernMainController.java | 2278 | 2283 | +5 |
| src/main/java/com/cryptocarver/ui/NavigationChromeCoordinator.java | 154 | 164 | +10 |
| src/main/java/com/cryptocarver/model/AppSettings.java | 352 | 371 | +19 |

Producción: seis archivos, +45 líneas netas. Tests nuevos: UiMinorFixesFixture.java (96), ProfileStatusSeverityUITest.java (48), EmvProfileStatusUITest.java (27), ToolbarOutputFormatsUITest.java (77). Documentación nueva: los tres mapas y este informe. Total: 14 archivos del encargo.

Diff contra main sin errores de whitespace. Sin cambios en crypto/, imágenes, .local.md ni secretos reales. Rama limpia tras el commit final; cinco commits en total.
