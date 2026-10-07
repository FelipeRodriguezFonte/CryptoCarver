# Fase 7: caracterización previa

Base sin extracción: ProcessDesignerExecutionCharacterizationUITest 2/2 y ProcessDesignerPaletteUITest 1/1, sin fallos, errores ni skips, en JavaFX visible del Mac/JDK 25. Comando: `mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest=ProcessDesignerExecutionCharacterizationUITest,ProcessDesignerPaletteUITest`.

Digests verificados sin cambiar el test:

- Ejecución completa/dry-run/paleta: `b925165010bd8ff31c1645f094b3a8073ad12bfa834347cb91212425f987afd3`.
- Privacidad/preflight/FULL_LAB/MASKED/REDACTED: `c8160b2b00c26b16ef19b4a43063f2c9685e058e112806dbbacf9f810596f5d1`.

Sin nuevos fallos de caracterización ni defectos de producto. Se conserva la normalización portable existente y las expectativas completas. Advertencia preexistente de clave de categoría Wallet / eIDAS sin traducción, fuera del alcance.
