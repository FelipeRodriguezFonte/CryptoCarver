# Capturas de PIN blocks heredados

Esta campaña fija las mismas entradas para todos los casos. La captura de la herramienta externa permitirá contrastar el bloque en claro y, cuando se ofrezca, el bloque cifrado. No usar claves de producción.

## Entrega común

Para cada identificador, enviar la ventana completa con versión, nombre exacto del formato seleccionado, todos los campos y sus valores por defecto, resultado y opción de copiar o exportar si existe. Si un campo no aparece, indicarlo; no sustituirlo por otro. Conservar visible cualquier ajuste de relleno, longitud o variante.

| Entrada | Valor para todos los casos |
|---|---|
| PIN | `1234` |
| PAN de pruebas | `4111111111111111` |
| Clave de pruebas (TDES doble) | `0123456789ABCDEFFEDCBA9876543210` |

Cada caso debe copiar: **bloque PIN en claro** y, si la herramienta externa lo ofrece, **bloque PIN cifrado**. El bloque en claro identifica el formato; el cifrado solo es comparable si la clave y el modo de cifrado son visibles.

## Formatos solicitados

### `PINBLK-DOCUTEL-00` — Docutel

Seleccionar la variante Docutel que muestre la herramienta externa e indicar si incorpora longitud del PIN, delimitador o relleno numérico configurable. Conservar el relleno mostrado.

- [ ] Bloque en claro capturado: ________________________________
- [ ] Bloque cifrado capturado, si aparece: _______________________

### `PINBLK-DIEBOLD-00` — Diebold

Seleccionar Diebold y registrar el carácter de relleno y cualquier número de coordinación que aparezca.

- [ ] Bloque en claro capturado: ________________________________
- [ ] Bloque cifrado capturado, si aparece: _______________________

### `PINBLK-PLUS-00` — Plus Network

Seleccionar Plus Network y dejar visible qué doce dígitos del PAN intervienen.

- [ ] Bloque en claro capturado: ________________________________
- [ ] Bloque cifrado capturado, si aparece: _______________________

### `PINBLK-EUROPAY-BANKSYS-00` — Europay/Banksys

Seleccionar la opción literal disponible e incluir cualquier dato adicional que exija la herramienta externa. La estructura sigue pendiente de una especificación pública concreta.

- [ ] Bloque en claro capturado: ________________________________
- [ ] Bloque cifrado capturado, si aparece: _______________________

### `PINBLK-AS2805-81-00` — AS2805 8.1

Seleccionar exactamente la variante «8.1» si existe e incluir el código de formato visible. La estructura sigue pendiente de una especificación pública concreta.

- [ ] Bloque en claro capturado: ________________________________
- [ ] Bloque cifrado capturado, si aparece: _______________________

## Alias con equivalencia no verificada

Estos tres alias conservan el comportamiento actual en CryptoCarver. Las capturas deben comprobar la equivalencia sin asumir que el nombre basta como prueba.

### `PINBLK-ECI1-00` — ECI-1

- [ ] Bloque en claro capturado: ________________________________
- [ ] Bloque cifrado capturado, si aparece: _______________________

### `PINBLK-ECI4-00` — ECI-4

- [ ] Bloque en claro capturado: ________________________________
- [ ] Bloque cifrado capturado, si aparece: _______________________

### `PINBLK-VISA4-00` — VISA-4

- [ ] Bloque en claro capturado: ________________________________
- [ ] Bloque cifrado capturado, si aparece: _______________________
