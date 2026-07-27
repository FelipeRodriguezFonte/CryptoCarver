# Process Designer — Phase 3.7: Ampliación de cifrado (ENCRYPT únicamente)

## Objetivo

Ampliar el nodo `ENCRYPT` para cubrir de forma consistente los cifradores simétricos que ya ofrece CryptoForge en el Workbench. Esta fase es solo de cifrado: no implementar ni modificar funcionalmente `DECRYPT` todavía.

Antes de tocar código, inspecciona las capacidades reales de `CipherController`, `SymmetricCipher`, `FileCipher` y el registro de operaciones. Reutiliza las primitivas y validaciones existentes; no implementes criptografía nueva ni dupliques lógica.

## Punto de partida confirmado

Conservar sin regresiones:

- `AES/GCM/NoPadding`, `AES/CBC/PKCS7Padding` y `AES/CTR/NoPadding`.
- Clave manual HEX/Base64 o conectada desde flujo.
- IV/nonce manual, autogenerado o conectado al puerto `iv`.
- AAD por puerto `aad` en AES-GCM.
- Salida `RAW` por defecto y `ENVELOPE` opcional.
- `RANDOM_BYTES`, generación de clave AES y PBKDF2.
- Trazas completas de laboratorio: claves, IV/nonces, AAD, valores y material generado.
- Persistencia `.cfprocess.json` sin secretos.
- Puertos visuales, `Connected inputs` y borrado de conexiones.

No romper estos contratos.

## 1. Catálogo real de algoritmos y modos

Construir el selector de `ENCRYPT` exclusivamente a partir de algoritmos realmente soportados por el core.

Incorporar, si el core ya los soporta:

- AES/GCM/NoPadding
- AES/CBC/PKCS7Padding
- AES/CTR/NoPadding
- AES/CFB/NoPadding
- AES/OFB/NoPadding
- AES/ECB/PKCS7Padding solo si ya existe en el Workbench; marcarlo como inseguro y solo laboratorio.
- ChaCha20-Poly1305, si está disponible y cuenta con prueba de ejecución.
- Otros cifradores simétricos existentes en CryptoForge únicamente si tienen implementación y pruebas reales.

No añadir DES, 3DES, RC2, RC4 ni otros algoritmos obsoletos salvo que ya se expongan como `Legacy / laboratory only`; en ese caso, agruparlos aparte con advertencia visible.

No incluir RSA, EC, OpenPGP o CMS en `ENCRYPT`; son nodos futuros independientes.

## 2. Especificación única por algoritmo

Eliminar condicionales dispersos como `if GCM ... else 16`.

Crear una especificación interna clara y testeable por algoritmo/modo. Preferir una clase de nivel superior o métodos privados; no crear clases internas, locales o anónimas nuevas.

La especificación debe definir:

- identificador de algoritmo y algoritmo JCE;
- tipo de cifrador;
- tamaños de clave aceptados;
- tamaño de IV/nonce;
- AEAD y soporte de AAD;
- padding aplicable;
- soporte de ENVELOPE;
- texto de ayuda UI;
- clasificación `modern`, `legacy` o `insecure laboratory`.

Motor, validaciones, generación de IV, parámetros JCE y UI deben consumir la misma fuente de verdad.

## 3. Puertos dinámicos

`payload` siempre es obligatorio.

| Puerto | Cuándo aparece | Regla |
|---|---|---|
| `key` | Cifrado simétrico | BINARY; opcional si existe clave manual |
| `iv` | Modos que lo requieran | BINARY; obligatorio sin valor manual/autogeneración |
| `aad` | Solo AEAD: GCM y ChaCha20-Poly1305 si se soporta | Cualquier representación; opcional |
| ninguno adicional | ECB | Aviso de que no usa IV |

No mostrar `aad` en CBC, CTR, CFB, OFB ni ECB. No mostrar `iv` en ECB. Las conexiones a puertos incompatibles deben fallar antes de ejecutar.

Mantener el inspector de enlaces, por ejemplo:

```text
payload ← Console input ("Hola")
key     ← Generate AES key
iv      ← Random bytes
aad     ← Console input ("header-v1")
```

## 4. Inspector y UX

Agrupar el selector de forma legible:

```text
Modern AEAD
  AES/GCM/NoPadding
  ChaCha20-Poly1305

AES block modes
  AES/CBC/PKCS7Padding
  AES/CTR/NoPadding
  AES/CFB/NoPadding
  AES/OFB/NoPadding

Laboratory / insecure
  AES/ECB/PKCS7Padding
  [solo legacy existente y probado]
```

Al seleccionar o cambiar algoritmo, actualizar inmediatamente:

- tamaño de clave esperado;
- longitud del IV/nonce;
- disponibilidad de AAD;
- condición AEAD;
- ayuda y advertencias;
- puertos visuales y menú `Connect to…`.

Ejemplos de ayuda:

```text
AES-GCM
Nonce: 12 bytes / 96 bits
AAD: optional input port
Authenticated encryption: yes

AES-CBC
IV: 16 bytes / AES block size
AAD: not supported
Authenticated encryption: no
```

No borrar en silencio conexiones incompatibles ya existentes. Mostrar error claro y permitir borrar/reasignar el enlace.

## 5. Salida y trazabilidad

Mantener `RAW (standard ciphertext)` como salida por defecto. No anteponer cabeceras propietarias a RAW.

`ENVELOPE` solo debe ofrecerse donde esté definido, compatible y probado. Deshabilitarlo para algoritmos nuevos sin formato verificable.

En modo laboratorio, mostrar valores completos:

```text
[3] ENCRYPT AES/CFB/NoPadding · ENCRYPT — SUCCESS
  input: TEXT_UTF8 · 4 chars
  value: Hola
  key (HEX): ...
  IV/nonce (flow from Random bytes, BINARY): ...
  AAD (flow from Console input, TEXT_UTF8): header-v1
  output: BINARY · N bytes
  value: ...
```

No ocultar claves, IV, nonce, AAD ni aleatorios de la traza. La única protección obligatoria es la persistencia: claves y contraseñas nunca deben aparecer en `.cfprocess.json`.

## 6. Validaciones fail-fast

Validar antes de ejecutar:

- algoritmo soportado;
- tamaño de clave;
- formato HEX/Base64;
- longitud exacta de IV/nonce, también desde puerto `iv`;
- AAD exclusivamente en AEAD;
- puertos inexistentes o incompatibles;
- combinaciones inválidas de algoritmo, padding, IV, AAD o ENVELOPE;
- IV/nonce autogenerado nuevo para cada ejecución.

Los errores deben incluir nodo, puerto y tamaño esperado/recibido.

## Pruebas obligatorias

### Motor

Para cada algoritmo añadido:

1. Vector determinista cuando sea viable.
2. Clave manual HEX y Base64.
3. Clave conectada desde `AES_KEY_GENERATE` o PBKDF2.
4. IV manual correcto.
5. IV desde `RANDOM_BYTES`.
6. IV incorrecto: error fail-fast.
7. AAD correcto para AEAD.
8. AAD rechazado para no AEAD.
9. RAW sin cabecera propietaria.
10. ENVELOPE solo donde sea compatible.
11. JSON sin secretos.

Como mínimo, comparar AES-CBC y AES-GCM contra `CipherController` / Workbench. Para cada algoritmo nuevo, comparar contra JCE o componente existente.

### UI

1. Solo aparecen algoritmos implementados.
2. GCM → CBC oculta AAD y pasa de nonce 12 B a IV 16 B.
3. CBC → GCM restaura AAD.
4. `Connect to…` ofrece solo puertos libres y compatibles.
5. `Connected inputs` muestra origen y valor por puerto.
6. Las conexiones quedan separadas y etiquetadas.
7. Se puede borrar una conexión sin borrar el nodo.
8. ECB/legacy muestra advertencia.
9. Tabla y traza se prueban con una ejecución JavaFX real.

## Restricciones

- Java 17 y JavaFX actual.
- No modificar código no relacionado.
- No debilitar ni sustituir pruebas existentes.
- No declarar soporte si solo compila: cada algoritmo necesita prueba de ejecución.
- No modificar funcionalmente `DECRYPT` en esta fase.
- No alterar el comportamiento actual de GCM, CBC PKCS7 ni CTR.
- Evitar clases internas/locales/anónimas nuevas en `ProcessDesignerController` y handlers modificados, por los `NoClassDefFoundError` previos de clases internas.

## Entrega requerida

Entregar:

1. Lista de algoritmos incorporados y excluidos, con motivo.
2. Archivos modificados y justificación.
3. Vectores / escenarios de interoperabilidad.
4. Resultado real de:

```bash
mvn clean test -Dtest=ProcessEngineTest,ProcessDesignerControllerTest,ProcessEnginePhase35FileTextTest,OperationRegistryTest
git diff --check
```

5. Tests no ejecutables por entorno, si aplica, sin llamar a eso “éxito total”.
6. No hacer commit ni modificar ficheros ajenos.
