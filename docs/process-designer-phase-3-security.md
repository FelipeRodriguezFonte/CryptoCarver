# Phase 3 Security and Design

## 1. Contratos de Representaciones y Puertos

Los nodos multi-puerto (ej. `VERIFY`) validan rigurosamente el tipo de representación y el nombre del puerto de destino. Una conexión incorrecta o ausente hace que el `ProcessEngine` aborte la ejecución de toda la cadena antes de aplicar efectos secundarios.

## 2. Formato del sobre AES-GCM

Para garantizar seguridad y versionado de compatibilidad, el payload cifrado por `AdvancedCryptoNodeHandler` usa un encapsulado binario:
- Magic Byte ASCII `CFGE` (4 bytes)
- Versión (`0x01`)
- Algoritmo ID
- IV/Nonce
- Ciphertext + GCM Tag.

El formato protege de alteraciones y es validado rígidamente durante el descifrado.

## 3. Compatibilidad de procesos

El Process Definition (v3) da soporte nativo a `targetPort`. Las definiciones heredadas sin `targetPort` aplican a puertos por defecto, garantizando retrocompatibilidad con procesos single-input.

## 4. Política de Secretos y Persistencia Segura

Claves manuales y contraseñas (arrays de bytes o caracteres) se limpian explícitamente en el recolector local. Está terminantemente prohibido su persistencia serializada en el `.cfprocess.json`. Durante la serialización, si un proceso necesita secretos, la clave se excluye. Durante la deserialización, se marcan para la obligatoria reintroducción del usuario ("Secrets are not stored in process files").

## 5. Extensión futura

En la siguiente etapa, se podría ampliar esta arquitectura de representación mediante abstracciones modulares, incluyendo soporte robusto para WSS-Security sin alterar este núcleo criptográfico, el cual está restringido al scope de Phase 3.
