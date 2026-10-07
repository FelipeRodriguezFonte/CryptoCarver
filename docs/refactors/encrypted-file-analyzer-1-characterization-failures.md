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
