# Fase 7: puertas HCE completadas

Extracción 5e80b48; base sin extraer 0e9a8a9, con privacidad corregida y digest HCE fijado. Guard exclusivo SpecializedFeedbackHeadlessTest,EmvOdaPaneTranslationTest previo a puertas: exit 0. Pasada focalizada de caracterización HCE, contratos HCE y UiStateSnapshotTest: 3 informes / 15 pruebas / 0 fallos, errores u omitidas / exit 0.

| Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| G1 | 451 | 2920 | 0 | 0 | 1 | 0 |
| G2 | 131 | 550 | 0 | 0 | 0 | 0 |
| G3 | 131 | 550 | 0 | 0 | 0 | 0 |

Se borraron los informes antes de cada puerta y se cuentan solo XML nuevos. Comandos exactos:

```sh
mvn -o -q test -Plow-cpu
mvn -o -q test -Plow-cpu -DrunUiTests=true
mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test
```

Las tres pasan limpias; ExpandedViewerLifecycleUITest pasa 3/3 en cada puerta. No se invoca la excepción ni se requieren reintentos o contrastes de base en HCE. Ninguna selección/exclusión de clases en las puertas completas. Las tres caracterizaciones pasan, junto con los once tests de UiStateSnapshot (los diez anteriores y el nuevo dirigido). Ningún test existente depende de guardar LUK en claro.

Logs/manifiesto: target/emv75-hce-cont3-*.log y target/emv75-hce-cont3-results.json. XML confirman Mac OS X / Homebrew Java 25. CI Linux/Java 17 no ejecutado. EMVController final: 783 líneas; original: 988.
