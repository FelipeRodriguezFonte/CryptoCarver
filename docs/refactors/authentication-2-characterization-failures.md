# Authentication fase 2: fallos de caracterización previos al arreglo

Se ejecutó la nueva prueba contra el comportamiento previo a cualquier cambio de producción:

`mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest=AuthenticationMacCharacterizationUITest`

Resultado: 1 prueba, 1 fallo de caracterización y 0 errores. La prueba confirmó que las validaciones de clave ausente y longitud AES incorrecta muestran mensajes legibles en EN y ES; el MAC alterado también muestra un error legible en ambas preferencias. El log de stdout/stderr no contiene ni la clave inventada ni los bytes MAC.

Defecto reproducido:

- MAC Generated publica el output con clasificación predeterminada PUBLIC y duplica el valor completo en el detalle público Output.
- MAC Verified publica tanto el MAC válido como el incorrecto con clasificación PUBLIC.
- En MASKED y REDACTED, los valores MAC se ven en el visor de resultado, historial, Shelf y visor expandido; el MAC generado también aparece en el inspector. El estado no filtró los valores.
- El fallo se reprodujo para los tres perfiles y con datos inventados. La transcripción del ensayo previo no se fija como digest protegido porque representa el estado defectuoso; la prueba fijará el SHA-256 cuando el fallo esté reparado.

No se ajustaron umbrales de CI ni se modificó ningún test preexistente.

## Arreglo caracterizado

En el commit de arreglo, ambos resultados MAC clasifican el output como SECRET y se elimina el detalle público duplicado Output. La prueba volvió a pasar y confirmó que MASKED/REDACTED no filtran los valores MAC en resultado, inspector, historial, Shelf, barra, visor expandido ni logs. El transcript protegido tiene SHA-256 `95e2692fb07d42fca1f02df27fda47240d1cdb0120120ed8e153031880dad598`; el nuevo digest corresponde al cambio observado de valores expuestos a superficies protegidas.
