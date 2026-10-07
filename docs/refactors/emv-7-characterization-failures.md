# Fase 7: fallo de privacidad previo a extraer

Producción HCE original, con ODA recuperada y aceptada. Dos ejecuciones del test nuevo:

```sh
mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest=EmvHceCharacterizationUITest
```

Cada ejecución: **1 informe / 1 prueba / 1 fallo / 0 errores / 0 omitidas / exit 1**. Primera salida y diagnóstico confirmatorio: target/emv7-characterization.log y target/emv7-characterization-diagnostic.log. Fallo de aserción:

```text
MASKED HCE keys leaked an invented key/cryptogram in history
MASKED HCE MSD leaked an invented key/cryptogram in history
MASKED HCE qVSDC leaked an invented key/cryptogram in history
REDACTED HCE keys leaked an invented key/cryptogram in history
REDACTED HCE MSD leaked an invented key/cryptogram in history
REDACTED HCE qVSDC leaked an invented key/cryptogram in history
```

El diagnóstico conserva únicamente datos de la fixture aislada, con UDK inventada fija y LUK derivado, en target/emv7-history-MASKED.json y target/emv7-history-REDACTED.json. En cada perfil hay tres entradas HCE; todas las recetas ocultan EMVController.hceUdkField con [REDACTED_SECRET], pero EMVController.hceMsdLukField y EMVController.hceQvsdcLukField contienen el mismo LUK de 32 caracteres en claro. Las aserciones sobre las demás superficies no detectaron filtración.

La lista de tokens sensibles de UiStateSnapshot incluye udk, pero no luk. El fallo existe sin extracción HCE; no es una diferencia causada por un coordinador. La reproducción usa resultados/recetas reales del shell y controles FXML. No se usa ningún secreto real. AppSettings, idioma, Shelf y propiedad test.mode se restauran; historial temporal limpiado al cerrar.

**Se detiene HCE antes de fijar SHA-256 y antes de extraer.** El test se conserva como reproducción roja; la caracterización aún no tiene digest fijado y no se declara superada. No se relaja ninguna aserción ni se reduce el conjunto de secretos para ocultar el LUK. No se ejecutan las puertas HCE con este requisito previo incumplido. Corregir la clasificación de esos campos en UiStateSnapshot queda fuera de autorización: el encargo prohíbe modificar ese archivo y cambiar comportamiento. Se mantiene ODA aceptada por la excepción autorizada de GC; esta incidencia es distinta y no está amparada por ella.
