# Segunda revisión B04 — cierre R1–R5

Fecha: 10 de octubre de 2026. Entrega: `01a12648-c7e5-7973-a308-3beade42c51b`. Revisor: Codex.

**Dictamen: REQUIERE CORRECCIÓN. R1, R2 y R4 cerrados. R3 y R5 parcialmente corregidos; B04 completo permanece pendiente. B05 no comienza.**

## Evidencia nueva

Se revisaron matriz, inventario, cambios de integración, pruebas y recursos contra el encargo y el primer dictamen. Los XML de `target/b04-review/correction/unit-closure/` contienen **22 pruebas**: catálogo 7, límites RSA 5, FPE 8, contrato FXML 2. Los de `ui-final/` contienen **19**: regresión compartida 14, Cipher 3 y OpenPGP 2. Todos sin fallos, errores ni omisiones; corresponden a las ejecuciones declaradas 14:59:52–15:00:02 y 15:01:38–15:02:19 UTC. No se suman a las suites iniciales ni se cuentan las repeticiones como pruebas distintas.

No se repitieron suites Maven que ya aportaban evidencia pertinente. Se volvió a ejecutar la sonda del primer dictamen y después se amplió para cubrir los dos contratos pendientes. Ambas terminaron correctamente, usando preferencias temporales, clases compiladas de la entrega y ES/EN a 1800/900. Resultados: `target/b04-review/reviewer/correction-probe.log` y `correction-expanded-probe.log`; fuente ampliada `B04ExpandedProbe.java` en el mismo directorio. Las claves RSA de la sonda son pares sintéticos temporales, sin lectura de archivos ni ejecución del cifrado.

## Correcciones aceptadas

- **R1:** las entradas B04 del recurso base tienen contraparte propia en ES. La prueba de catálogo carga el recurso propio directamente, sin aceptar claves del padre. La sonda muestra los textos FPE traducidos en ambas vistas.
- **R2:** la carga real del shell instala **41 bindings OpenPGP** mediante referencias FXML, sin dependencia de CSS previo. IDs de acción/campo válidos y específicos; User ID tiene consulta propia. La prueba nueva inspeccionada recorre los botones en ES/EN y 1800/900, navegación sin duplicados, ventana compacta/reapertura, preservación y ocultamiento al salir. Las descripciones distinguen guardar y limpiar material secreto.
- **R4:** A008 toma flags de controles File Cipher efectivos. La reproducción con clave de archivo rellena y clave simétrica vacía ahora produce `true`. La prueba nueva cubre también el caso inverso, tag como referencia sin leer una ruta inexistente, y preservación de resultados y campos.
- **Parte de R3:** al pulsar de nuevo los botones de cifrar/descifrar en modo líneas se eligen `encrypt_lines`/`decrypt_lines`; accesibilidad localizada correcta.
- **Parte de R5:** opciones RSA coinciden con las cuatro listas reales, sin Base64URL; catálogo de charset deriva páginas EBCDIC. Documentación de parsers de claves y OAEP/MGF1 efectivos más precisa, con prueba del proveedor. Se actualiza el máximo al cambiar padding y al cargar/reemplazar texto público no vacío.

## R3 pendiente — la ayuda abierta y las consultas de campos no siguen el modo

La sonda ampliada confirma, en las cuatro combinaciones:

1. Activar líneas y abrir el `?` de cifrar deja `actionId=encrypt_lines`, correctamente.
2. Desactivar líneas **sin volver a pulsar el botón** deja `actionId=encrypt_lines`, aunque `FILE_LINES=false`. La ayuda sigue describiendo registros mientras el handler vigente procesaría archivo completo.
3. Volver a líneas y pulsar el `?` del campo clave deja `actionId=encrypt_file`; esa consulta vuelve a describir archivo completo. Los bindings de clave, IV/nonce, AAD y resultado siguen fijos en `encrypt_file`.

La nueva prueba cambia checkbox y después vuelve a pulsar el `?`: no comprueba la actualización de la ayuda ya abierta. El listener solo actualiza accesibilidad de botones, no la acción activa.

**Cierre:** sincronizar la variante de las acciones dependientes de archivo/líneas y todas sus consultas de campo mientras la ayuda correspondiente está abierta, sin forzar apertura con modo desactivado ni alterar consultas que no dependan de esa variante. Conservar campo seleccionado y dirección cifrar/descifrar, memorias por ruta y selección al volver desde A001/A002/A008. Verificar ambos sentidos con ayuda abierta, actualización programática, ES/EN, 1800/900; la descripción de acción, el campo y el resumen dinámico deben coincidir con el handler actual.

## R5 pendiente — borrar la clave pública conserva metadatos obsoletos

La sonda carga un PEM RSA sintético de 1024 bits mediante la acción existente y luego vacía el área pública. En las cuatro combinaciones obtiene:

```
RSA_LOADED_BITS=1024
RSA_CLEARED_BITS=1024 state=PublicSelection[code=1024]
```

Con el área vacía, `resolvePublicKey` usaría el par compartido del módulo Keys; `publicModulusBitsForHelp` sigue prefiriendo `panelPublicKey` anterior. Por tanto puede mostrar el máximo de una clave que ya no es la seleccionada, tanto si no existe par compartido como si tiene otro módulo. La corrección cubre reemplazo por texto no vacío, pero no borrar el campo.

**Cierre:** al vaciar el área, obtener solo los metadatos públicos ya disponibles del origen de fallback que el handler utilizaría; si no existe material público RSA vigente, borrar el tamaño/máximo. No parsear texto de clave por consultar ayuda ni inspeccionar una privada. Probar borrar con fallback ausente y con par compartido de otro tamaño, retorno a RSA desde auxiliares y padding con ayuda abierta, preservando resultados/controles.

Otro detalle de R5 queda pendiente: `renderFpeApplicability` incorpora literalmente `": 1024"` para el máximo del tweak FF1, aunque el límite procede de `MAX_TWEAK_LENGTH` del motor. Exponer un acceso de solo lectura al límite y consumirlo en la ayuda, sin modificar validación, parámetros ni comportamiento; comprobar que ayuda y límite real coinciden. El encargo requiere derivar los límites del código para evitar textos duplicados.

## Siguiente entrega

Corregir estos remanentes de R3/R5; conservar R1/R2/R4 y todo el alcance C002–C005/A008. Luna mantiene aplicación, pruebas, matriz e inventario; Codex mantiene dictámenes. Regresiones de transiciones reales y verificaciones compartidas pertinentes, Maven secuencial y preferencias aisladas, XML nuevos con recuentos efectivos. Sin modificaciones criptográficas, commits, pushes, nuevos chats/agentes o cambios de modelo. B04 no se aprueba con deuda obligatoria.
