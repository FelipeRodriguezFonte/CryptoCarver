# Wallet 2 — fallos observados antes de fijar

Primera ejecución sin extraer: 1 XML / 1 prueba / 1 fallo / 0 errores / 0 omitidas / exit 1. Al navegar desde SD-JWT a mdoc el shell reinstala su reporter; el recorder del fixture ya no recibía showError. El área y el shell sí mostraban la validación Claims are required. Se reinstala el recorder después de navegar en el test nuevo, sin cambiar producción ni aserciones. No es fuga ni una puerta completa.

Tras reparar el fixture: 1 XML / 1 prueba / 0 fallos / 0 errores / 0 omitidas / exit 0, sobre código sin extraer. SHA-256 fijado: `94eb31450c66aaac07a4fecb1b07c8b181fa32b274b1c511ec98afe93f966ffb`. Validez y digestID aleatorios normalizados; elementos ordenados; verificación de firma/digest/dispositivo independiente.

Comprobación dirigida tras extraer, antes de puertas: compilación fallida por import DataConverter ausente (0 XML, exit 1). Se añade el import correspondiente; ninguna lógica ni aserción cambia.

El primer intento de añadir el import usó el paquete crypto por error; segunda compilación dirigida: 0 XML, exit 1. Se corrige al paquete original com.cryptocarver.util. Ambos intentos ocurrieron antes de las puertas.
