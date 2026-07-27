# Encargo para Antigravity: Process Designer — Fase 2

## Contexto

CryptoForge es una aplicación Java 17 con JavaFX. El MVP de `Process Designer` ya existe en el área **Generic** y permite diseñar un flujo con nodos arrastrables, enlaces dirigidos, inspector plegable, persistencia JSON y ejecución local.

Puntos de entrada actuales:

- `src/main/java/com/cryptocarver/ui/ProcessDesignerController.java`
- `src/main/java/com/cryptocarver/model/process/ProcessDefinition.java`
- `src/main/java/com/cryptocarver/model/process/ProcessDefinitionCodec.java`
- `src/main/java/com/cryptocarver/model/process/ProcessEngine.java`
- `src/main/resources/fxml/process_designer.fxml`
- `src/test/java/com/cryptocarver/model/process/ProcessEngineTest.java`

No reescribas módulos criptográficos existentes: reutiliza las operaciones ya probadas en `com.cryptocarver.crypto` cuando corresponda. Mantén Java 17 y JavaFX; no añadas dependencias externas salvo que sea imprescindible y se justifique.

## Objetivo

Evolucionar el diseñador de un MVP demostrable a un editor de flujos criptográficos seguro, extensible y usable. Esta fase debe incorporar codecs bidireccionales y una arquitectura de nodos preparada para el resto de operaciones criptográficas.

## Alcance funcional obligatorio

### 1. Nodos de codec

Añade nodos separados, configurables y ejecutables para:

- Base64 encode y Base64 decode.
- Base64URL encode y Base64URL decode (sin padding por defecto; el decoder debe aceptar la entrada flexible que ya soporta CryptoForge cuando sea posible).
- Hex encode y Hex decode.
- UTF-8 text encode y UTF-8 text decode.

Semántica:

- `Hash` entrega **bytes de digest**, no el texto hexadecimal mostrado en consola.
- `Base64 encode` sobre un digest codifica los bytes del digest. Para `SHA-256("Hola")` debe producir `5jP0/Hm63qHcXblwzzl8gki6xHzDrPmRW6YLXXaw6I8=`.
- `Hex encode` representa bytes como hexadecimal en mayúsculas para mantener la convención actual de CryptoForge.
- Los decodificadores producen bytes y deben indicar claramente errores de formato, nodo y causa.
- La salida de consola debe renderizar texto como UTF-8 y bytes como hexadecimal mayúsculo, sin alterar el valor interno.

### 2. Modelo de datos tipado

Sustituye el transporte interno basado solamente en `String` por un valor de flujo inmutable y tipado, por ejemplo:

```java
record FlowValue(byte[] bytes, Representation representation, Charset charset) { }
enum Representation { BINARY, TEXT_UTF8, HEX, BASE64, BASE64URL }
```

No persistas secretos ni valores de ejecución en el JSON del proceso. El modelo debe permitir que los nodos declaren qué representaciones aceptan y producen; la validación previa debe rechazar conexiones incompatibles con un mensaje accionable.

### 3. Nodos de fichero

Completa el comportamiento de entrada/salida de fichero:

- `File input`: lectura binaria por defecto; para modos textuales, charset configurable (UTF-8, ISO-8859-1 y EBCDIC ya soportados).
- `File output`: escritura binaria o textual según el tipo de valor entrante y la configuración explícita de salida.
- Permitir al usuario seleccionar ruta mediante `FileChooser`, no solo escribirla.
- Nunca sobrescribir un fichero existente sin confirmación explícita.
- Errores de ruta, permisos y decodificación deben aparecer en el resultado y en el nodo que falló.

### 4. UX del lienzo

- Mantener el inspector plegable y el lienzo expandible.
- Flechas visibles y seleccionables.
- Conectar dos nodos seleccionados con una acción.
- Invertir un enlace mediante la selección de la flecha o de sus dos nodos.
- Añadir validación visual: nodos inválidos en rojo, nodo seleccionado claramente distinguible y tooltip/estado con el tipo de entrada/salida.
- Añadir un pequeño panel de ejecución: estado por nodo (`pending`, `running`, `success`, `error`), duración y mensaje de error.
- No bloquear el hilo de JavaFX durante operaciones de fichero: ejecutar procesos en una `Task` y publicar cambios de UI en el hilo FX.

### 5. Preparación para criptografía avanzada

Define una interfaz interna para proveedores de nodos, separada de JavaFX, por ejemplo `ProcessNodeHandler`/`ProcessNodeDefinition`. Debe permitir registrar nuevos nodos sin ampliar un `switch` central de forma indefinida.

Incluye, aunque sea como nodos no ejecutables todavía, las familias futuras:

- Encrypt / Decrypt (simétrico y fichero).
- Sign / Verify.
- MAC / Verify MAC.
- XML / WSS-Security (marcados como `PLANNED`, sin simular resultados).

Los nodos que requieran clave deben usar una **referencia de secreto** (`keystore alias`, `PKCS#11 alias` o `prompt at runtime`), nunca la clave en el JSON ni en texto plano persistente.

## Reglas de seguridad

- No loguear contenido sensible, claves, contraseñas ni valores completos de nodos secretos.
- Al pedir una clave temporal, manejarla como `char[]`/`byte[]` y limpiarla al finalizar.
- Validar tamaños de clave, IV/nonce, AAD y compatibilidad de algoritmos reutilizando validadores existentes.
- Marcar operaciones de cifrado/firma como experimentales hasta que tengan pruebas de interoperabilidad.

## Persistencia y compatibilidad

- Mantener lectura de los `.cfprocess.json` v1 existentes.
- Versionar explícitamente el nuevo esquema si es necesario y ofrecer migración determinista desde v1.
- Persistir: grafo, posición de nodos, configuración no sensible, etiquetas, y referencias de secreto.
- No persistir: datos introducidos por consola por defecto, resultados, claves, passwords o buffers binarios sensibles.

## Pruebas de aceptación obligatorias

Añade pruebas unitarias y, cuando aplique, JavaFX/FXML para demostrar como mínimo:

1. `"Hola" → SHA-256 → Base64 encode` da `5jP0/Hm63qHcXblwzzl8gki6xHzDrPmRW6YLXXaw6I8=`.
2. Base64 encode/decode recupera exactamente los bytes originales.
3. Hex encode/decode recupera exactamente los bytes originales y rechaza hexadecimal inválido.
4. Base64URL encode/decode cubre entradas sin padding.
5. Flujo de fichero binario entrada → codec → salida mantiene los bytes esperados.
6. Un enlace incompatible o un ciclo se rechaza antes de ejecutar con diagnóstico claro.
7. El botón Run no bloquea JavaFX; los estados por nodo se actualizan correctamente.
8. Guardar/cargar un flujo conserva topología y configuración no secreta, sin guardar secretos ni resultados.
9. `git diff --check` sin salida y tests relevantes verdes.

## Entregables

1. Implementación completa y compilable.
2. Pruebas nuevas y actualizadas.
3. Breve documento de diseño en `docs/` explicando el modelo de valores, extensión de nodos y política de secretos.
4. Resumen final con archivos modificados, decisiones de compatibilidad y comandos de prueba ejecutados.

## Fuera de alcance en esta fase

- Implementación completa de WSS-Security. Solo preparar el contrato/nodos planificados.
- Editor de expresiones, bucles, bifurcaciones, reintentos o ejecución distribuida.
- Persistir secretos de cualquier forma.
