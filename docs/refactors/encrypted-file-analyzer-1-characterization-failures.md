# Fase 1 — fallos antes de fijar la caracterización

Base de producción: 257dd2b, EncryptedFileAnalyzer sin cambios.

La nueva EncryptedFileAnalysisPrivacyReproductionTest falla para MASKED y REDACTED:
report.txt y report.html contienen el marcador ASCII inventado recuperado por CBC.
Las comprobaciones de ausencia de la clave inventada pasan. Se verifica antes que el
candidato recupera exactamente el plaintext generado; no es una coincidencia fortuita.
No se comprueban otras superficies después de confirmar este defecto bloqueante.

Comando:

```sh
mvn -o -q test -Plow-cpu -Dtest=EncryptedFileAnalysisPrivacyReproductionTest,EncryptedFileAnalyzerTest,SpecializedFeedbackHeadlessTest,PaymentsValidationHeadlessTest
```

Se borró target/surefire-reports antes de ejecutar. Recuento de los XML de esa ejecución:
4 informes / 10 pruebas / 2 fallos / 0 errores / 0 omitidas / exit 1.

| Clase | Informes | Pruebas | Fallos | Errores | Omitidas |
| --- | ---: | ---: | ---: | ---: | ---: |
| EncryptedFileAnalysisPrivacyReproductionTest | 1 | 2 | 2 | 0 | 0 |
| EncryptedFileAnalyzerTest | 1 | 4 | 0 | 0 | 0 |
| SpecializedFeedbackHeadlessTest | 1 | 2 | 0 | 0 | 0 |
| PaymentsValidationHeadlessTest | 1 | 2 | 0 | 0 | 0 |

Cada invocación de privacidad contiene dos aserciones fallidas (TXT y HTML); Surefire
cuenta una prueba fallida por perfil. Los mensajes son «report.txt exposes recovered text»
y «report.html exposes recovered text».

Causa observada: evaluatePlaintextQuality crea preview con el texto recuperado;
formatAnalysisReport y writeHtmlReport lo escriben sin consultar el perfil de visibilidad.
El analizador no recibe ni consulta AppSettings. No se cambia esa lógica.

El test usa AppSettings aislado y restaura la instancia anterior en finally. No crea ni
muta historial, Shelf, barra de estado o visor. @TempDir elimina todos sus archivos.
No fija excepciones, rutas, fechas, duraciones ni orden de mapas en digests.

Incidencia de preparación: la primera compilación del test usó RAW_BINARY, que no existe;
se corrigió a RAW antes de ejecutar la reproducción. No fue un defecto de producción.
Entorno disponible: macOS ARM64, Java 21.0.8; Java 17 no instalado. No se afirma
validación en Linux/Java 17.

Decisión: detener el encargo antes de extracción, mantener el test con sus aserciones
estrictas (falla intencionalmente sobre la base), no fijar nuevos digests y no corregir
el defecto, conforme a la instrucción expresa del usuario.


## Segunda continuación — caracterización sobre código corregido

Base de fase: 9e3ad06 (producción corregida y fase 0 aprobada).
Se reutiliza CBC FULL_LAB ya fijado y la batería UI existente; se fijan TXT/HTML protegidos
para MASKED/REDACTED dentro de la reproducción y se añaden solo muestreo/sin candidatos,
semilla 7601, con archivos generados por el test. Normalización de ruta, timestamp del
directorio y CRLF. No se fijan textos de excepciones, duración, ni mapas sin orden.

Captura con valores provisionales (sin extracción):
`mvn -o -q test -Plow-cpu -Dtest=EncryptedFileAnalysisPrivacyReproductionTest,EncryptedFileAnalysisRefactorCharacterizationTest`
Resultado: 2 informes / 7 pruebas / 4 fallos / 0 errores / 0 omitidas / exit 1.
Los cuatro fallos eran solo hashes aún no fijados (dos perfiles y dos escenarios),
con dos digests por invocación. Las aserciones funcionales y de privacidad pasan.
No se observa defecto nuevo de producción; no se corrige comportamiento en fase 1.

Tras fijar digests, y todavía sin extraer:
`mvn -o -q test -Plow-cpu -Dtest=EncryptedFileAnalyzerTest,EncryptedFileAnalysisCharacterizationUITest,EncryptedFileAnalysisPrivacyReproductionTest,EncryptedFileAnalysisRefactorCharacterizationTest,EncryptedFileAnalysisInspectorPrivacyUITest`
Resultado: 5 informes / 19 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0.
Antes de cada ejecución se borró target/surefire-reports.


Tras extraer, la misma batería focal produce 5 informes / 19 pruebas / 0 fallos /
0 errores / 0 omitidas / exit 0, sin actualizar hashes ni aserciones.
Contratos de claves aislados: 2 informes / 4 pruebas / 0 fallos / 0 errores / 0 omitidas /
exit 0. Higiene exacta de quality-gates: inline=0, emoji=325.


Se retira también la aserción auxiliar de contenido de inspectorOutput de la reproducción
propia, conforme a la segunda continuación. La privacidad se comprueba en el presenter
real y el roundtrip permanece cubierto por EncryptedFileAnalyzerTest sin modificarlo.
La reproducción final aislada: 1 informe / 5 pruebas / 0 fallos / 0 errores / 0 omitidas /
exit 0. G1 de esta fase se ejecutó antes de retirar esa aserción auxiliar y pasó también
esa comprobación adicional; no hubo cambio de producción entre G1 y G2.


Puertas de fase 1 aprobadas sin excepción GC:
G1 454 informes / 2929 pruebas / 0 fallos / 0 errores / 1 omitida / exit 0;
G2 y G3, cada una, 132 informes / 552 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0.
Se borraron los informes antes de cada puerta. No se retira la extracción porque las tres pasan.
