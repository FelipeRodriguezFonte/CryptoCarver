# Encargo 76 — mapa de fase 2

Estado: no iniciada; bloqueo previo de fase 1. Propietario real de todos los métodos:
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
