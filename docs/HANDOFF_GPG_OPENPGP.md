# OpenPGP / GPG-compatible laboratory

## Alcance implementado

CryptoCarver incorpora una herramienta OpenPGP desde **Cipher → OpenPGP (GPG compatible)**. El motor usa Bouncy Castle OpenPGP (`bcpg`) y no ejecuta ni requiere el binario externo `gpg`.

Funciones disponibles:

- Generación de un par RSA OpenPGP de 3072 bits protegido con frase de paso.
- Exportación visual de clave pública y secreta en ASCII armor.
- Cifrado y descifrado de texto con AES-256 e integridad OpenPGP.
- Firma detached y verificación de firma detached en ASCII armor.
- Firma adjunta (contenido encapsulado) y verificación de mensajes firmados.
- Clear-sign y verificación de mensajes de texto firmados legibles.
- Carga y guardado explícitos de llaveros armored (`.asc`, `.pgp`, `.gpg`) desde la interfaz.
- Inspector de metadatos de clave: ID, huella, algoritmo, tamaño, identidades y capacidades, sin descifrar ni mostrar material privado adicional.
- Carga y guardado de mensajes, salidas y firmas ASCII-armored; los ficheros de texto se limitan a 16 MiB.

Las áreas de clave aceptan material OpenPGP ASCII-armored, por lo que están preparadas para intercambiar material compatible con GnuPG. La interoperabilidad cruzada con un binario GnuPG real queda como prueba externa pendiente.

## Adaptador opcional de GnuPG

La pantalla incluye **Check GnuPG…**. Detecta el ejecutable `gpg` del sistema o el indicado mediante la variable de entorno `CRYPTOCARVER_GPG_BINARY`; su ausencia no impide utilizar el motor OpenPGP interno.

Cuando GnuPG está disponible, **Verify with GnuPG** importa únicamente la clave pública en un directorio temporal aislado y verifica la firma detached actual. No transfiere al proceso externo claves privadas ni frases de paso y no evalúa la Web of Trust: el resultado sólo afirma validez criptográfica de la firma.

## Uso rápido

1. Escribir un identificador de usuario y una frase de paso; pulsar **Generate RSA Key Pair**.
2. Para cifrar, conservar/pegar la clave pública, introducir texto y pulsar **Encrypt**.
3. Para descifrar, pegar el bloque armored en *Input*, conservar/pegar la clave secreta y pulsar **Decrypt**.
4. Para firmar, introducir el texto, clave secreta y frase de paso; pulsar **Sign Detached**.
5. Para verificar, introducir el texto original, la firma detached y la clave pública; pulsar **Verify Detached**.
6. Para una firma adjunta, usa **Sign Attached**; su resultado viaja con el contenido y se verifica con **Verify Attached**.
7. Para texto visible firmado, usa **Clear-sign** y después **Verify Clear-sign**.
8. Usa **Load/Save** para intercambiar llaveros armored con otras herramientas y **Inspect** para confirmar la huella antes de usarlos.
9. **Clear visible secrets** borra de la pantalla la clave secreta y la frase de paso cuando se haya terminado la prueba.

La pantalla actual procesa texto y ASCII armor. El cifrado streaming de adjuntos binarios permanece como evolución separada para no cargar archivos grandes en memoria.

## Límites de seguridad

Es una función de laboratorio. La interfaz muestra la clave secreta para facilitar pruebas; no debe utilizarse como flujo de custodia de claves de producción. La frase de paso se transforma en un `char[]` y se limpia tras cada operación, pero Java no puede garantizar el borrado físico completo de memoria.

## Verificación automatizada

`OpenPgpOperationsTest` cubre cifrado/descifrado, fallo por frase de paso incorrecta y firmas detached válidas/manipuladas. `OperationRegistryTest` asegura que el catálogo refleja la nueva herramienta y el contrato FXML valida su inclusión en la vista moderna.
