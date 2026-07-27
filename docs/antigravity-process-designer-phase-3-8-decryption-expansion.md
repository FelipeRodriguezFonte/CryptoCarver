# Process Designer — Phase 3.8: Ampliación de descifrado simétrico (DECRYPT)

## Objetivo

Ampliar el nodo `DECRYPT` del Process Designer para descifrar los mismos modos AES ya incorporados al nodo `ENCRYPT` en la Phase 3.7, de forma interoperable, explícita y segura.

Esta fase se limita al nodo `DECRYPT`. No implementar WSS-Security, nuevos cifradores, nodos asimétricos, OpenPGP ni CMS.

Antes de modificar código, revisar el contrato actual de `ENCRYPT`, `SymmetricCipherSpec`, `AdvancedCryptoNodeHandler`, `ProcessEngine`, `ProcessDesignerController` y el Workbench (`CipherController`). Reutilizar primitivas existentes; no reimplementar criptografía.

## Punto de partida aprobado

Phase 3.7 deja el siguiente catálogo de cifrado en `ENCRYPT`:

- `AES/GCM/NoPadding`
- `AES/CBC/PKCS7Padding`
- `AES/CTR/NoPadding`
- `AES/CFB/NoPadding`
- `AES/OFB/NoPadding`
- `AES/ECB/PKCS7Padding` — solo laboratorio, con advertencia visible.

El nodo `DECRYPT` está temporalmente limitado a GCM, CBC y CTR. Phase 3.8 debe ampliarlo para igualar exactamente el catálogo anterior.

Conservar sin regresiones:

- Clave manual HEX/Base64 y clave conectada desde flujo.
- `RANDOM_BYTES`, generación de clave AES y PBKDF2.
- Tipado `FlowValue` y validación topológica.
- Persistencia `.cfprocess.json` sin secretos.
- Trazas de laboratorio con valores completos.
- `RAW` como formato de cifrado por defecto.
- El formato `ENVELOPE` existente para GCM, CBC y CTR.
- La ausencia de ENVELOPE para CFB, OFB y ECB.

## 1. Contrato de DECRYPT

El selector de `DECRYPT` debe contener exactamente los algoritmos de `ENCRYPT`, agrupados visualmente con la misma clasificación:

```text
Modern AEAD
  AES/GCM/NoPadding

AES block modes
  AES/CBC/PKCS7Padding
  AES/CTR/NoPadding
  AES/CFB/NoPadding
  AES/OFB/NoPadding

Laboratory / insecure
  AES/ECB/PKCS7Padding
```

No mostrar algoritmos no implementados. No añadir ChaCha20, DES, 3DES ni algoritmos nuevos en esta fase.

Usar `SymmetricCipherSpec` como fuente única de verdad para la ampliación de DECRYPT:

- algoritmo JCE;
- tipo y modo;
- padding;
- tamaños válidos de clave;
- longitud del IV/nonce;
- AEAD / soporte AAD;
- soporte de ENVELOPE;
- categoría y ayuda visual.

No introducir condicionales de algoritmo dispersos ni un segundo catálogo paralelo.

## 2. Puertos dinámicos de DECRYPT

| Puerto | Cuándo aparece | Regla |
|---|---|---|
| `payload` | Siempre | Obligatorio. Solo `BINARY`; usar nodos de codec para convertir HEX/Base64/TEXT a bytes antes de descifrar. |
| `key` | Siempre | `BINARY`; opcional si existe clave manual. |
| `iv` | GCM, CBC, CTR, CFB, OFB | `BINARY`; opcional si se indica IV/nonce manual. |
| `aad` | Solo GCM | Cualquier representación; opcional. |
| ninguno adicional | ECB | Mostrar advertencia de que no usa IV ni AAD. |

Reglas:

- No mostrar ni permitir `aad` en CBC, CTR, CFB, OFB o ECB.
- No mostrar ni permitir `iv` en ECB.
- Si se cambia de algoritmo y existen conexiones incompatibles, no borrarlas silenciosamente: mostrar un error claro que identifique el enlace y permita borrarlo o reasignarlo.
- Mantener visibles los enlaces en `Connected inputs`, por ejemplo:

```text
payload ← File input
key     ← PBKDF2
iv      ← Random bytes
aad     ← Console input ("header-v1")
```

## 3. Formatos RAW y ENVELOPE

### RAW

Para datos RAW, el algoritmo configurado en el nodo es obligatorio y debe coincidir con los parámetros suministrados.

- GCM: nonce de 12 bytes y AAD opcional.
- CBC, CTR, CFB y OFB: IV de 16 bytes.
- ECB: no IV, no AAD.
- Validar antes de ejecutar la longitud exacta de IV/nonce, el formato de clave y el algoritmo.
- No generar un IV de forma automática al descifrar RAW. Si el algoritmo lo necesita y no llega por configuración o puerto `iv`, fallar de forma clara.

### ENVELOPE

El formato ENVELOPE vigente solamente es compatible con AES-GCM, AES-CBC y AES-CTR.

- Para un ENVELOPE válido, obtener el algoritmo y el IV desde la cabecera del envelope.
- Validar la versión, magic, algoritmo, longitud de IV y longitud mínima de payload.
- El nodo debe rechazar un ENVELOPE con un algoritmo no soportado o una cabecera corrupta.
- CFB, OFB y ECB no deben aceptar ni ofrecer ENVELOPE; deben producir un error explícito si reciben datos con cabecera ENVELOPE.
- Documentar en UI que el algoritmo del ENVELOPE se autodetecta para GCM/CBC/CTR; no ocultar este hecho al usuario.
- No cambiar el formato binario ni la compatibilidad de los ENVELOPE existentes.

## 4. Semántica criptográfica y errores

- GCM debe propagar cualquier fallo de autenticación como error: tag corrupto, AAD incorrecto, clave incorrecta o nonce incorrecto nunca deben generar plaintext.
- CBC y ECB deben propagar padding inválido como error.
- CTR, CFB y OFB deben producir el resultado que determine JCE; no inventar comprobaciones de autenticidad.
- Cifrar y descifrar con los mismos parámetros debe recuperar el valor binario exacto, no una conversión de texto.
- Limpiar arrays temporales de clave, IV y material sensible conforme al patrón existente.
- No persistir claves, contraseñas, IV ni AAD en el archivo del proceso cuando sean secretos de sesión. La traza de laboratorio sí debe mostrarlos durante la ejecución.

## 5. UX y observabilidad

El inspector debe reflejar el algoritmo de DECRYPT seleccionado:

```text
AES-GCM
Nonce: 12 bytes / 96 bits
AAD: optional input port
Authenticated decryption: yes

AES-CFB
IV: 16 bytes / AES block size
AAD: not supported
Authenticated decryption: no

AES-ECB
IV: not used
AAD: not supported
WARNING: ECB mode is insecure for general use.
```

Requisitos de UI:

1. Reutilizar el selector agrupado de Phase 3.7, incluyendo una clase JavaFX de nivel superior. No introducir clases internas, locales ni anónimas.
2. Mostrar aviso rojo persistente para ECB.
3. Ocultar el editor de IV/nonce en ECB.
4. Mostrar AAD solo para GCM.
5. Deshabilitar el selector ENVELOPE para CFB, OFB y ECB.
6. Al ejecutar, mostrar trazas completas para entradas de flujo:

```text
[3] DECRYPT AES/GCM/NoPadding · DECRYPT — SUCCESS
  input: BINARY · N bytes
  key (HEX): ...
  IV/nonce (flow from Random bytes, BINARY): ...
  AAD (flow from Console input, TEXT_UTF8): header-v1
  output: BINARY · M bytes
  value: ...
```

Los errores deben identificar el nodo, algoritmo, puerto y tamaño esperado/recibido cuando aplique.

## 6. Pruebas obligatorias

Añadir pruebas de motor y JavaFX; no sustituir pruebas existentes por otras más débiles.

### Motor

Para cada algoritmo de DECRYPT:

1. Interoperabilidad contra `javax.crypto.Cipher` o contra el Workbench.
2. Clave manual HEX.
3. Clave manual Base64.
4. Clave desde `AES_KEY_GENERATE` o PBKDF2.
5. IV manual correcto.
6. IV desde `RANDOM_BYTES`.
7. IV/nonce con longitud incorrecta: fallo fail-fast.
8. RAW sin IV en GCM/CBC/CTR/CFB/OFB: fallo claro.
9. ECB sin IV: ejecución correcta.
10. AAD correcto en GCM.
11. AAD incorrecto en GCM: fallo de autenticación y ningún plaintext.
12. AAD conectado a CBC/CTR/CFB/OFB/ECB: fallo de validación previo a ejecución.
13. Ciphertext GCM corrupto: fallo.
14. Padding CBC/ECB corrupto: fallo.
15. RAW CFB/OFB interoperable con JCE.
16. ENVELOPE existente GCM/CBC/CTR sigue siendo compatible.
17. ENVELOPE con cabecera corrupta o algoritmo no válido: fallo.
18. ENVELOPE recibido por CFB/OFB/ECB: rechazo explícito.

Las pruebas de los nuevos modos deben usar `ENCRYPT` solo como productor cuando aporte valor de integración; el resultado se validará con JCE para no convertir pruebas de DECRYPT en pruebas circulares.

### UI

1. El selector DECRYPT muestra todos y solo los algoritmos aprobados.
2. Las categorías Modern AEAD, AES block modes y Laboratory/insecure aparecen correctamente.
3. ECB muestra advertencia roja y oculta IV/AAD.
4. GCM muestra nonce de 12 bytes y AAD.
5. CBC/CTR/CFB/OFB muestran IV de 16 bytes y ocultan AAD.
6. `Connect to…` solo ofrece puertos compatibles y libres.
7. Las conexiones incompatibles existentes generan un mensaje claro.
8. CFB/OFB/ECB fuerzan RAW y deshabilitan ENVELOPE.
9. Una ejecución JavaFX real rellena tabla de estado y traza de descifrado.

## Restricciones

- Java 17 y JavaFX actual.
- No modificar WSS-Security ni iniciar Phase 4.
- No modificar ficheros no relacionados.
- No hacer commit.
- No introducir clases internas, locales o anónimas nuevas en los archivos modificados del Process Designer.
- Mantener compatibilidad de procesos existentes que usen `AES/CBC/PKCS5Padding`: normalizarlo a PKCS7/JCE sin romper su ejecución.
- No declarar soporte si no existe prueba real de ejecución e interoperabilidad.
- No ocultar material de laboratorio en la traza. La protección se aplica al guardado de procesos, no a la ejecución.

## Entrega requerida

1. Lista exacta de algoritmos habilitados para DECRYPT.
2. Archivos modificados y motivo.
3. Pruebas nuevas, indicando qué requisito cubren.
4. Vectores o escenarios JCE/Workbench utilizados.
5. Resultado real de:

```bash
mvn clean test -Dtest=ProcessEngineTest,ProcessDesignerControllerTest,ProcessEnginePhase35FileTextTest,OperationRegistryTest
git diff --check
```

6. Indicar limitaciones o pruebas externas no ejecutables sin llamarlo éxito total.
7. No continuar a WSS-Security tras terminar: esperar revisión de Codex.
