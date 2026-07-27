# Process Designer — Phase 3: Advanced Crypto Nodes

Implementa Phase 3 sobre la base validada de Phase 2. No modifiques ni regreses capacidades ya aprobadas: modelo `FlowValue`/`Representation`, validación previa, `FileWritePolicy`, eventos por nodo, paleta por categorías, inspector contextual ni persistencia existente.

No implementar WSS-Security en esta fase. Será una fase independiente.

## 1. Objetivo

Hacer ejecutables desde Process Designer los nodos criptográficos inicialmente marcados como `PLANNED`:

- Encrypt
- Decrypt
- MAC
- Sign
- Verify

Reutiliza exclusivamente las operaciones criptográficas ya existentes en CryptoForge. No dupliques primitivas criptográficas ni introduzcas algoritmos propios.

## 2. Alcance funcional mínimo

### 2.1 Cifrado y descifrado simétrico

Nodos:

- `ENCRYPT`
- `DECRYPT`

Primera versión soportada:

- `AES/GCM/NoPadding`: obligatorio y opción por defecto.
- `AES/CBC/PKCS5Padding`: opcional únicamente si reutiliza una operación existente y queda cubierto por pruebas.

Configuración contextual:

- Algoritmo/modo.
- Clave manual en Hex o Base64; nunca persistida en `.cfprocess.json`.
- IV/nonce:
  - GCM: generar aleatoriamente por defecto.
  - Permitir nonce/IV explícito para descifrado y pruebas reproducibles.
- Formato de salida:
  - Encrypt produce un sobre binario autocontenible y versionado:

    ```text
    magic | version | algorithm-id | nonce/iv | ciphertext | authentication-tag
    ```

  - Decrypt acepta exclusivamente ese sobre en el MVP.
- Validar antes de ejecutar clave vacía, longitud incorrecta, IV/nonce inválido, algoritmo no permitido, sobre inválido y GCM sin tag o con tag incorrecto.

Representaciones:

- `ENCRYPT` acepta cualquier `Representation` y produce `BINARY`.
- `DECRYPT` acepta exclusivamente `BINARY` y produce `BINARY`.
- La conversión a texto, Hex o Base64 se realiza siempre con nodos explícitos de Phase 2.

### 2.2 MAC

Nodo: `MAC`.

Algoritmos:

- `HmacSHA256`: obligatorio.
- `HmacSHA384` y `HmacSHA512`: opcionales si ya están disponibles y se prueban.

Configuración:

- Algoritmo.
- Clave manual Hex/Base64, nunca persistida.
- Resultado: `BINARY`.

Representaciones:

- Acepta cualquier `Representation`.
- Produce `BINARY`.

### 2.3 Firma y verificación

Nodos:

- `SIGN`
- `VERIFY`

Algoritmos MVP:

- `SHA256withRSA`: obligatorio.
- `SHA256withECDSA`: opcional si reutiliza una capacidad existente y queda probada.

Configuración:

- Sign: ruta de keystore, tipo JKS/PKCS12, alias, contraseña de keystore y contraseña de clave.
- Verify: certificado X.509 desde ruta o clave pública PEM desde ruta.
- Contraseñas y claves nunca se persisten en `.cfprocess.json`; solo rutas, alias y configuración no sensible.

Representaciones:

- `SIGN` acepta el payload y produce firma `BINARY`.
- `VERIFY` debe recibir exactamente dos entradas semánticas: `payload` y `signature`.
- `VERIFY` produce `TEXT_UTF8` con `VALID` o `INVALID`.

Reglas:

- Una firma inválida es un resultado criptográfico válido: devolver `INVALID`, no lanzar excepción.
- Errores de formato, certificado, ruta, clave, algoritmo o configuración deben fallar el nodo con un error seguro.

## 3. Contrato del motor

### 3.1 SPI y puertos

Evolucionar `ProcessNodeHandler` para declarar:

- número de puertos de entrada;
- nombre de cada puerto;
- representaciones admitidas por cada puerto;
- representación de salida;
- requisitos de configuración, sin exponer secretos.

Ejemplo conceptual:

```text
VERIFY:
  input payload: cualquier representación
  input signature: BINARY
  output: TEXT_UTF8
```

### 3.2 Validación

Extender `ProcessEngine.validate()` para que, antes de cualquier lectura, escritura o criptografía:

- valide tipos por puerto;
- impida entradas obligatorias ausentes;
- impida entradas extra;
- rechace conexiones a puertos inexistentes;
- rechace conexiones duplicadas;
- mantenga rechazo de ciclos;
- mantenga rechazo de múltiples entradas para nodos que no las soportan;
- valide configuración criptográfica no sensible;
- no ejecute efectos secundarios si el grafo es inválido.

### 3.3 Modelo de conexión y compatibilidad

Extender `ProcessDefinition.Connection` con un campo opcional `targetPort`.

Reglas:

- Un proceso antiguo sin `targetPort` debe continuar funcionando para nodos de una única entrada.
- En nodos multi-input, `targetPort` es obligatorio.
- Subir versión del proceso si procede.
- Documentar explícitamente la migración y compatibilidad.

### 3.4 Ejecución

- No volver a adoptar una semántica silenciosa de “usar la primera conexión”.
- Cada nodo debe recibir sus entradas por puerto.
- Mantener estados `PENDING`, `RUNNING`, `SUCCESS`, `ERROR` y `SKIPPED`.
- Los eventos deben identificar correctamente nodo, estado y detalle seguro.

## 4. Seguridad de secretos

### 4.1 Reglas obligatorias

- Claves manuales: usar `byte[]` o `char[]`.
- Borrar con ceros los arrays temporales en `finally`.
- Nunca escribir secretos en logs, mensajes de error, panel de resultados, historial, definición `.cfprocess.json`, documentación de ejemplos ni eventos de ejecución.

### 4.2 Persistencia

Se permite guardar algoritmo, modo, formato de clave, ruta de keystore/certificado, alias, tipo de keystore, opciones de nonce/IV y configuración no sensible.

Nunca guardar clave manual, contraseña, PIN, contenido de clave privada ni valor introducido en un `PasswordField`.

Al cargar un proceso con nodos sensibles:

- mantener la configuración no sensible;
- exigir reintroducción de secretos antes de ejecutar;
- mostrar: “Secrets are not stored in process files.”

## 5. UX

### 5.1 Paleta

En la paleta `Crypto`:

- Activar Encrypt, Decrypt, MAC, Sign y Verify.
- Mantener deshabilitados únicamente nodos realmente no implementados.
- Todo nodo pendiente debe mostrar tooltip explicativo.

### 5.2 Inspector

El inspector debe ser plenamente contextual.

Encrypt/Decrypt:

- algoritmo/modo;
- formato de clave Hex/Base64;
- clave;
- nonce/IV, si aplica;
- opción de generar nonce/IV para Encrypt.

MAC:

- algoritmo;
- formato de clave;
- clave.

Sign:

- algoritmo;
- ruta keystore;
- tipo keystore;
- alias;
- contraseña keystore;
- contraseña clave.

Verify:

- algoritmo;
- ruta certificado o clave pública;
- selector del tipo de material público.

### 5.3 Secretos en UI

- Claves y contraseñas se introducen con `PasswordField`.
- Añadir selector Hex/Base64 para clave manual.
- Puede existir botón “Mostrar” temporal, pero el valor no debe terminar en logs, textos de resultado o persistencia.
- Mostrar aviso visible de que esos datos no se guardan.

### 5.4 Puertos visuales

Para nodos de múltiples entradas:

- representar visualmente los puertos de destino `payload` y `signature`;
- las conexiones deben reflejar el puerto destino;
- el usuario debe poder ver inequívocamente qué línea alimenta cada puerto;
- si se intenta conectar a un puerto ya ocupado, mostrar mensaje claro y no sustituir silenciosamente la conexión.

## 6. Formato del sobre AES-GCM

Documentar e implementar un formato binario versionado.

Ejemplo:

```text
Bytes 0..3:   Magic ASCII "CFGE"
Byte 4:       Version 1
Byte 5:       Algorithm ID
Byte 6:       Nonce length
Bytes ...:    Nonce
Bytes ...:    Ciphertext + GCM tag
```

Reglas:

- Definir constants y no números mágicos dispersos.
- Validar longitud mínima y versión antes de descifrar.
- Rechazar algoritmos/versiones no soportados con mensaje seguro.
- El formato debe permitir futuras extensiones sin romper compatibilidad.

## 7. Pruebas obligatorias

Añadir pruebas reales. No basta con pruebas de existencia de clases o botones.

### 7.1 AES-GCM

- Cifrar y descifrar bytes arbitrarios, incluidos `0x00`, `0xFF` y secuencias no UTF-8.
- Dos cifrados del mismo payload y clave con nonce automático producen sobres distintos.
- Modificar ciphertext o tag provoca fallo controlado al descifrar.
- Clave inválida, nonce inválido o sobre corrupto fallan de forma segura.
- No se escribe fichero de salida si la validación falla.

### 7.2 MAC

- `HmacSHA256` contra vector conocido.
- Entrada binaria no UTF-8.
- Clave vacía o inválida: fallo seguro.
- Resultado binario encadenado correctamente a Hex/Base64.

### 7.3 Firma

- Generar material temporal de prueba.
- Firmar y verificar correctamente.
- Firma manipulada devuelve `INVALID`.
- Payload manipulado devuelve `INVALID`.
- Certificado/clave incompatibles falla con error controlado.
- Configuración incompleta falla antes de ejecutar.

### 7.4 Grafo y puertos

- Verify sin `payload` falla antes de ejecutar.
- Verify sin `signature` falla antes de ejecutar.
- Verify con conexiones a puertos invertidos falla por representación.
- Puerto inexistente falla antes de ejecutar.
- Puerto duplicado falla antes de ejecutar.
- Proceso de Phase 2 de una entrada sigue funcionando sin `targetPort`.

### 7.5 Persistencia

- Serializar un proceso con nodos sensibles.
- Verificar que el fichero no contiene clave, contraseña, PIN ni texto secreto introducido.
- Cargar el proceso.
- Verificar que solicita reintroducción de secretos antes de ejecutar.

### 7.6 UI

- Menús de paleta añaden los nodos implementados.
- Inspector muestra solo campos pertinentes.
- Puertos visuales aparecen para `VERIFY`.
- Estados asíncronos se actualizan por nodo.
- Los campos sensibles se tratan como secretos y no aparecen en el resultado.

## 8. Documentación

Actualizar:

- `docs/process-designer-architecture.md`
- Crear `docs/process-designer-phase-3-security.md`
- `docs/OPERATIONS_CATALOG.md`, si se modifica `OperationRegistry`.

El documento de seguridad debe describir contratos de representaciones, contratos de puertos, formato del sobre AES-GCM, compatibilidad de procesos, política de secretos, persistencia segura y extensión futura a WSS-Security.

## 9. Fuera de alcance

No incluir en esta fase:

- WSS-Security;
- XML Encryption;
- XML Digital Signature;
- almacenamiento permanente de secretos;
- ejecución remota;
- HSM/PKCS#11 desde Process Designer;
- nodos de merge/arbitraje de flujos;
- algoritmos no existentes en CryptoForge;
- formatos de cifrado ad hoc no documentados.

## 10. Criterios de entrega

Antes de declarar la fase completada, entrega obligatoriamente:

1. Lista de archivos modificados y motivo.
2. Lista de operaciones reutilizadas de CryptoForge.
3. Decisiones de diseño relevantes: formato de sobre, contratos de puertos, compatibilidad de procesos y manejo de secretos.
4. Resultado literal de estos comandos:

   ```bash
   mvn test -Dtest=ProcessEngineTest,ProcessDesignerControllerTest,OperationRegistryTest
   mvn compile test
   git diff --check
   ```

5. Si una prueba externa falla, por ejemplo `GnuPgInteropTest`, separarla de forma explícita con nombre, motivo, evidencia y por qué no pertenece a Phase 3.
6. No escribir “todo pasa”, “implementación completa” o equivalente si no se ejecutaron los comandos, alguno falló, se omitieron pruebas o queda un requisito fuera de alcance sin indicarlo.

## 11. Regla de trabajo

Para cada requisito funcional:

1. Implementar.
2. Añadir prueba positiva.
3. Añadir prueba de fallo seguro.
4. Ejecutar las pruebas.
5. Informar con evidencia.

Si hay una decisión de diseño que no está definida, detenerse y presentar alternativas con impacto. No inventar semánticas silenciosas ni tomar “la primera conexión” o “el primer valor” como comportamiento por defecto.
