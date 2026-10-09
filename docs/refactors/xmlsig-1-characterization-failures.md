# Fase 1 — fallos observados antes de fijar SHA-256

La primera ejecución dirigida de la caracterización abrió accidentalmente el selector de guardado porque el XML de salida todavía estaba cargado. Se interrumpió la ejecución sin seleccionar ningún fichero y se corrigió el test para limpiar `xmlSignOutputArea` antes de ejercitar la rama de validación. No es un fallo del producto ni una puerta, no hubo escritura de fichero. La repetición pasa: 1 informe / 1 prueba / 0 fallos / 0 errores / 0 omitidas / exit 0.

Transcripción normalizada previa a la extracción: `sign=true|sign-published=true|inspect=true|inspect-published=true|save-validation=true`. SHA-256 fijado en el test: `6fe9a0e7ea7ea421928d23b3ab0295e79faa32ac896ebf5de593e426083caa38`. No incluye firmas, seriales, fechas, rutas, tiempos ni excepciones. La caracterización se ejecutó sobre el código sin extraer.
