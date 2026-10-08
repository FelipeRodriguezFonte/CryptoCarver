# Fase 6: fallos antes de fijar la caracterización

Base: `b193a67`; showSection sin extraer. Comando `mvn -o -q test -Plow-cpu -Dtest=JoseSectionCharacterizationUITest`.

Captura provisional con PENDING: 1 XML / 1 prueba / 1 fallo / 0 errores / 0 omitidas / exit 1. Fallo únicamente de fijación del digest, SHA obtenido `781e560222264aed10bc3396167b9bafa86991fe0e679204608024052963c81e`. No se observa un defecto previo ni se modifica producción. El test describe la selección sensible a prefijos existente y su orden de setters; no introduce equivalencias de navegación nuevas.

Transcripción sin fechas, rutas, tiempos, claves ni textos de excepción: nombres de rutas ordenados explícitamente y cambios de propiedades JavaFX sincrónicos registrados en orden de ejecución. Se fija ese SHA y se comprueba verde antes de extraer.

Verificación fijada sobre showSection sin extraer: 1 XML / 1 prueba / 0 fallos / 0 errores / 0 omitidas / exit 0. Producción idéntica a b193a67 al realizar esta comprobación.
