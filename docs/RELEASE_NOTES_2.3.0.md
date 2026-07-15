# CryptoCarver 2.3.0

Versión de laboratorio centrada en reproducibilidad para pagos, firmas XML y criptografía post-cuántica.

## Aspectos destacados

- **AES DUKPT:** derivación host-side y operación AES sobre bloques PIN preformateados de 16 bytes.
- **TR-31:** los bloques opcionales ya no son solo informativos; se incorporan a la cabecera autenticada del bloque. El campo compacto de Export acepta, por ejemplo, `0100KS02ABCD`.
- **EMV:** el inspector TLV explica importes, moneda, país, fecha, ATC y tipo de criptograma. El constructor DOL informa de los campos suministrados y los rellenados con ceros.
- **ARQC:** validación de longitud/formato antes del MAC y verificación con el mismo método de padding que se utilizó para generar el criptograma.
- **XAdES:** el inspector enumera transforms, certificados incrustados, validez temporal local, usos de clave, restricciones CA/EKU y sellos XAdES. No sustituye la validación de confianza.
- **PQC:** evita mezclar parámetros incompatibles y reconoce alias como `Kyber512`/`ML-KEM-512` y `Dilithium3`/`ML-DSA-65`.

## Ejecución

```bash
./run-modern.sh
```

Tras empaquetar, el JAR es `target/cryptocarver-2.3.0.jar`.

## Alcance

CryptoCarver es una herramienta de desarrollo, pruebas, formación y diagnóstico. Las claves y resultados sensibles pueden mostrarse para facilitar el trabajo de laboratorio; no se debe usar con material de producción.
