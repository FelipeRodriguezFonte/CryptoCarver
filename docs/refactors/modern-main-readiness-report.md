# Encargo 35: readiness y paso guiado

## Cambios y commits

- `8fdfb09` — límite JVM de Maven y Surefire.
- `1cafb5d` — inventario de preparación y paso guiado.
- `7fddb5a` — caracterización previa del FXML de producción.
- `888f00f` — políticas puras y `ReadinessPanelCoordinator`.
- Commit de esta documentación — comando de suite con menor prioridad y resultados.

No se hizo push.

## CPU y suite

La suite inicial, antes del límite de CPU, terminó correctamente en 297,17 s reales. La referencia de informes previa era 2.383 tests con 1 omitido.

Con `ActiveProcessorCount=2`, la suite tardó 455,28 s y el fork de Surefire terminó con `Java heap space` durante `ModernMainControllerUITest.testResultSummaryNeutralAndSuccessStates`. El aumento observado antes del fallo fue del 53,2 % sobre la base; por ese umbral se ajustó `ActiveProcessorCount` a 3, tal como se pidió. La ejecución con 3 quedó incompleta y se interrumpió a 622,90 s; por eso no hay un tiempo total ni un conteo final válido de la suite con el límite aplicado. No se omitieron ni desactivaron tests.

Los perfiles Maven `ui-tests` y `integration-tests` solo añaden grupos y propiedades de prueba; ninguno declara `argLine`. Ambos heredan el `argLine` principal. No hay `parallel` ni `threadCount` en Surefire.

Se añadieron 11 tests: 7 de caracterización FXML, 2 unitarios de `PreflightPolicy` y 2 unitarios de `GuidedStepPolicy`. La ejecución dirigida pasó con 11 tests nuevos (0 fallos, 0 errores) y 2 tests UI preexistentes (0 fallos, 0 errores). La suite completa posterior no terminó, de modo que el total de suite que se puede confirmar sigue siendo el dato base de 2.383 con 1 omitido; no se presenta como resultado posterior.

La instantánea de estilo previa a la extracción, con `user.home` limpio y el código anterior a la extracción, agotó el heap de 1 GB recorriendo sus 40 combinaciones de pantalla/tema, incluso solicitando GC tras cerrar cada escena. No se generó el archivo de salida, por lo que no existe un `diff` válido antes/después. No se hicieron cambios a FXML ni CSS. La captura posterior queda sin verificar por la misma limitación de memoria.

No se hizo prueba manual.

## Tamaño y separación

`ModernMainController` tenía 3.651 líneas antes y tiene 3.342 después: 309 líneas menos. `ReadinessPanelCoordinator` mantiene las vistas y los estados de preflight/paso guiado; los controladores de módulo se leen mediante proveedores. `PreflightPolicy` y `GuidedStepPolicy` no dependen de JavaFX y tienen tests propios.

La prueba dirigida de caracterización cargó `/fxml/main-view-modern.fxml` mediante `Fxml.loader`, con `user.home=target/test-home`. Su salida fue:

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```

Comando de suite con menor prioridad:

```bash
nice -n 19 mvn -o -q test
```
