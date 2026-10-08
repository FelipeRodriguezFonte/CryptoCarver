# Encargo 76 — mapa de fase 2

Registro inicial conservado. Estado en aquella ejecución: no iniciada; bloqueo previo de fase 1. Propietario real de todos los métodos:
EncryptedFileAnalyzer. No se ha movido código ni se ha fijado una nueva caracterización.

Etapas previstas de analyze, en el orden actual:

1. Normalizar opciones y crear directorio; leer archivo y salir si está vacío.
2. Muestrear; resolver codificaciones, algoritmos y combinaciones por longitud de clave.
3. Por cada codificación: decodificar, probar contenido completo, probar contenedor
   independiente y finalmente bloques heurísticos en orden de tamaños y combinaciones.
4. Escribir attempts.csv, incluso cuando no hay candidatos.
5. Crear salida sin candidatos o seleccionar top, asignar confianza y seleccionar probables.
6. Escribir TXT y HTML; construir detalles, historial, estado y salida de inspector.

El troceado requeriría conservar contadores de intentos/éxitos/índice y las listas ordenadas
entre etapas, sin cambiar límites de try/catch ni el orden de llamadas. No se implementa.

computePaddingEvidence depende de CipherCombination, CipherInputs, descifrado sin padding,
extracción/división de chunks, tamaño de bloque y validadores de patrones. evaluatePlaintextQuality
depende de PlaintextQuality, UTF-8, Cp037/Cp500, ratios, safePreview y DataConverter.
No se decide una extracción a clase de evidencia sin completar primero las fases obligatorias.
Estos métodos y sus dependencias permanecen en el analizador.

Claves de idioma movidas: ninguna. Claves de idioma en el analizador: ninguna.
Los propietarios de contratos de claves permanecen según el mapa de fase 1.
Cobertura y hueco bloqueante: véase encrypted-file-analyzer-1-map.md.

## Continuación autorizada: nuevo bloqueo de fase 0

La exposición en TXT/HTML/CSV se corrige mediante autorización explícita en CipherInputs,
calculada por CipherController con AppSettings.isFullLab(). El analizador no lee AppSettings.
El mapa de extracción no cambia: no se mueven métodos ni claves. La fase 0 se detiene en
su paso 3 porque Outcome.inspectorOutput sigue exponiendo texto recuperado bajo MASKED
y REDACTED. Véase encrypted-file-analyzer-0-characterization-failures.md y el informe 76.


## Mapa operativo previsto (antes de extraer)

La base será el commit de informes aprobado por sus tres puertas. Todas las etapas y
datos permanecen en EncryptedFileAnalyzer; no hay cambio de propietario de claves
(la clase no usa claves de bundles). Se ejecutan aislados los mismos contratos de claves.

| Etapa privada | Responsabilidad / dependencias |
| --- | --- |
| testInputEncodings | Bucle original de codificaciones; decodeFileData; mantiene continue ante decode fallido/vacío |
| testFullContent | Bucle original de combinaciones para contenido completo; descifrado, evidencia y candidato dentro del mismo try/catch |
| testIndependentBlocks | Lee tamaño estructurado; llama a estructurados y luego heurísticos, sin alterar opciones |
| testStructuredBlocks | Bucle original de combinaciones y registro de intentos de contenedor |
| testGuessedBlocks | Tamaños exteriores y combinaciones interiores en el orden original; mismo try/catch |
| writeNoCandidateOutcome | Construcción original sin candidato, TXT antes de HTML, Outcome sin inspector/historial |
| writeCandidateOutcome | Ranking, confianza, probables, TXT, HTML, detalles y Outcome en ese orden |
| AnalysisProgress | Contadores y listas ordenadas de candidatos/intentos de esta ejecución; sin estado compartido entre análisis |
| analyze | Normalizar, crear directorio, leer/vacío, muestrear, resolver opciones; llamar a etapas; CSV antes de selección |

No se unifican los cuerpos de los tres tipos de intentos ni se cambian los límites de
captura de excepciones. Las sustituciones de identificadores deben omitir literales y
comentarios. La caracterización fijada en fase 1 se comprobará antes de este troceado,
además de la batería original, sin fijar nuevos mensajes de error ni orden de mapas.

### Decisión sobre evidencia

No hay traslado directo de ambos métodos a una clase independiente: computePaddingEvidence
usa CipherCombination, helpers de chunks y decryptBlockChunkNoPadding, que obtiene el IV
mediante CipherInputs y el propio analizador. evaluatePlaintextQuality sí es autónomo con
sus helpers de charset/ratios/preview, pero separar ambos exigiría un nuevo contrato de
callback de descifrado o mover también esa responsabilidad. Se conservan los dos cuerpos
intactos y privados, evitando ampliar este encargo de troceado con ese acoplamiento.

Base efectiva de fase 2: c6ea897. La caracterización anterior al troceado pasa:
5 informes / 19 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0.


Después del troceado: la misma batería focal da 5 informes / 19 pruebas / 0 fallos /
0 errores / 0 omitidas / exit 0, con los mismos hashes. Contratos de claves aislados
antes de puertas: 2 informes / 4 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0.
Se conserva el conjunto de literales Java; el resto del archivo desde sus constantes
(evidencia, ranking, helpers de descifrado y CSV) es byte a byte idéntico a c6ea897.
Líneas: analyze 311 → 37; archivo 1396 → 1437 (se añaden límites de métodos y estado local).
No se mueve la evidencia a otra clase por la dependencia descrita; no se toca su lógica.


Puertas de fase 2 aprobadas sin excepción GC:
G1 454 informes / 2929 pruebas / 0 fallos / 0 errores / 1 omitida / exit 0;
G2 y G3, cada una, 132 informes / 552 pruebas / 0 fallos / 0 errores / 0 omitidas / exit 0.
Se borraron los informes antes de cada puerta. Todas pasan: se conserva el troceado.
