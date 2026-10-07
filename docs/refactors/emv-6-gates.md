# Fase 6: puertas y retirada

| Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| G1 | 450 | 2918 | 0 | 0 | 1 | 0 |
| G2 | 130 | 548 | 0 | 0 | 0 | 0 |
| G3 | 130 | 548 | 3 | 0 | 0 | 1 |
| G3-expanded-rerun | 1 | 3 | 3 | 0 | 0 | 1 |

G3 no pasa limpia: fallan los tres tests de ExpandedViewerLifecycleUITest por `Closed UI fixture is still strongly reachable` (Stage sigue alcanzable). Se borraron los informes y se reejecutó **solo esa clase** con las mismas opciones de G3 y `-Dtest=ExpandedViewerLifecycleUITest`: vuelve a fallar, 3/3. No se modifica ese test ni sus aserciones. No se comprobó una ejecución de main limpio ni otro JDK; la causa permanece sin atribuir.

Se detiene el encargo y se retira íntegramente la extracción 06dd269 mediante revert --no-commit, incluido EmvOdaCoordinator y todos los cambios de esa fase en EMVController. El controlador queda byte a byte como en 0808b3c (fase 5 validada). Se conservan mapa, caracterización anterior a la extracción y evidencias. No se inicia la fase 7/HCE. Esta retirada y la documentación se guardan juntas en el commit del paso d.

Logs y manifiestos: target/emv75-phase6-G*.log y target/emv75-phase6-gate-results.json. Cada recuento procede exclusivamente de XML frescos, también la reejecución.

## Continuación posterior

Se recuperó ODA en e9e3944. La nueva G1 falla solo en los tres tests GC: 450 informes / 2918 pruebas / 3 fallos / 0 errores / 1 omitida / exit 1. Clase aislada con opciones G1: 1/3/3/0/0/1 con extracción y en base 45e5a1b, mismos fallos. La excepción autorizada cubre únicamente G2/G3; no se ejecutaron estas ni HCE, y se retiró nuevamente ODA. Ver el registro completo en emv-75-report.md.

## Segunda continuación: ODA aceptada

Recuperación solo en código, idéntica a 06dd269, en 626ee3b. La excepción del usuario ahora cubre G1/G2/G3. Los únicos fallos de cada puerta son exactamente los tres tests GC de ExpandedViewerLifecycleUITest, mismos métodos y diagnóstico, reproducidos por la clase aislada con las opciones correspondientes en base 45e5a1b. Se conserva el exit 1 de Maven; aceptación por excepción, no puertas limpias.

| Ejecución | Commit | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| G1 | `626ee3b` | 450 | 2918 | 3 | 0 | 1 | 1 |
| G1-gc-extracted | `626ee3b` | 1 | 3 | 3 | 0 | 0 | 1 |
| G1-gc-base | `45e5a1b` | 1 | 3 | 3 | 0 | 0 | 1 |
| G2 | `626ee3b` | 130 | 548 | 3 | 0 | 0 | 1 |
| G2-gc-extracted | `626ee3b` | 1 | 3 | 3 | 0 | 0 | 1 |
| G2-gc-base | `45e5a1b` | 1 | 3 | 3 | 0 | 0 | 1 |
| G3 | `626ee3b` | 130 | 548 | 3 | 0 | 0 | 1 |
| G3-gc-extracted | `626ee3b` | 1 | 3 | 3 | 0 | 0 | 1 |
| G3-gc-base | `45e5a1b` | 1 | 3 | 3 | 0 | 0 | 1 |

Guard exclusivo previo: SpecializedFeedbackHeadlessTest,EmvOdaPaneTranslationTest, exit 0. Informes borrados antes de cada ejecución. Logs/manifiesto: target/emv75-oda-cont2-*.log y target/emv75-oda-cont2-results.json. Puertas completas sin -Dtest; selección solo para reintento/contraste aislado. Ningún cambio al test GC ni a tests existentes. Controlador: 810 líneas. ODA permanece extraída y se permite comenzar HCE.
