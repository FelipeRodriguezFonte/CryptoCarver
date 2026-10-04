# Authentication firma: fallos observados antes del arreglo

Fase: 1, `AuthenticationSignatureCharacterizationUITest`, base `7aa3519b2eb77ba4edf9604435d3f21365053b2d`.

La ejecución UI de caracterización sobre el controlador sin extraer observó lo siguiente:

- Con `MASKED` y `REDACTED`, tanto la firma creada como la firma alterada durante verificación permanecen legibles en la captura de resultado, se añaden al Clipboard Shelf y aparecen en el visor expandido. Las tres superficies reciben los bytes porque ambos handlers publican `.output(signature)` con clasificación `PUBLIC`.
- Inspector, estado, historial y salida de logs no contienen la firma en esas mismas ejecuciones. El historial fue vaciado entre perfiles para que la ejecución previa `FULL_LAB` no contaminara la observación. La clave privada PEM sintética tampoco aparece en esas superficies.
- El primer borrador de la prueba informó falsamente un fallo de historial porque combinaba mensajes de entrada con secretos y conservaba registros `FULL_LAB` al cambiar de perfil. Se corrigió el detector antes de fijar el digest; el resultado final confirma el historial protegido.
- El mensaje preflight por ausencia de clave se presenta en inglés como “Validation required before execution” y en español como “Validación necesaria antes de ejecutar”. La ayuda es legible en ambos idiomas, aunque la primera frase menciona una firma que se verifica también en el intento de firma. Se registra como texto genérico preexistente y no se cambia en esta extracción.
- La firma inválida produce un error legible en ambos perfiles de idioma. El texto de detalle sigue en inglés también con idioma español; se conserva como comportamiento observado, sin que la prueba dependa de una traducción exacta.

La transcripción de línea base no incluye PEM, firmas, secretos ni bytes de mensaje. Incluye solo los estados de exposición para que pueda fijarse de manera segura:

`3cc974344c23208d6ed8ac63a3d0f5576f4d6f571e951a6d55a1dd6157291cc4`

El defecto de clasificación se corregirá en su commit propio después de este commit de caracterización. La prueba exigirá entonces que la captura de resultado, Shelf y visor expandido queden protegidos bajo ambos perfiles restringidos; el nuevo digest representará el resultado protegido.

## Resultado tras el arreglo de clasificación

Se publicó la firma de `Data Signed` y `Signature Verified` como `SECRET`, dejando que la política común aplique `***MASKED***` o contenido vacío. La captura, Shelf y visor quedan protegidos; el historial continúa enmascarado y el inspector, estado y logs siguen sin contener claves ni firmas. La transcripción protegida fijada por la prueba tiene SHA-256 `892112128b7a2f5d274d3f4c845d1298c3488e6296ac0da48c2fbc6b94d8d62f`. El nuevo digest corresponde al cambio de estado `visible/leaked` → `protected/blocked` y está justificado por el fallo anterior.
