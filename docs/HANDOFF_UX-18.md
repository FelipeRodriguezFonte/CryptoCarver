# UX-18 — Handoff de auditoría funcional

## Alcance auditado

- Contrato global de formatos en `ModernMainController`, `OperationFormatRegistry` y `GenericController`.
- Plantillas seguras y persistencia en `SafeTemplateAllowlist`, `SafeOperationTemplate`, `PersonalTemplateStore` y `SafeTemplateUIHelper`.
- Hashing, Manual Conversion, Random Number Generator, Cipher simétrico/asimétrico y Digital Signatures/MAC como consumidores del contrato compartido.
- Plantillas de Certificates: se añadió el selector local PEM y se confirmó que el parser no debe mutar la barra global.
- Navegación de Generic y objetivos de Clipboard Shelf hacia Hashing, Manual Conversion y Symmetric Cipher; expansión y visibilidad del contenedor.
- Reset Defaults, sincronización local/global, resultados, errores dependientes del formato y compatibilidad de alias ES/EN.

## Fallos encontrados y corregidos

1. `Plain Text`/`Text` podía no existir en la ComboBox global y dejar seleccionado el formato anterior, especialmente `Hexadecimal`. Se normalizan ambos alias a `Text (UTF-8)` en el modelo/helper y en la barra global.
2. Las plantillas personales de Hash, Manual Conversion, Cipher y Digital Signatures no persistían todos los selectores de formato permitidos. Se añadieron los formatos globales y, en Cipher, los selectores asimétricos.
3. Aplicar plantillas personales no normalizaba los valores heredados antes de entregarlos a los setters. La restauración/importación ahora canonicaliza los parámetros de formato.
4. Algunos setters de Manual Conversion/Cipher/Auth no aplicaban los selectores globales; ahora actualizan el mismo contrato usado por la ejecución.
5. `certFormatCombo` se restauraba erróneamente sobre el selector de plantilla de certificados. Se añadió el selector local PEM y Certificates ya no modifica accidentalmente la barra global.
6. Manual Conversion exponía formatos que su operación no ejecutaba con el mismo contrato. Se alinearon sus combos y el registro, incluyendo `Base64URL`.
7. Navegar a una operación sin formatos globales sobrescribía la selección de la barra con `Not applicable`. Ahora deshabilita la barra sin mutar el último valor válido.
8. Los destinos de Clipboard Shelf podían expandir un panel oculto sin mostrar su contenedor. Ahora navegan primero al módulo canónico y después cargan/expanden el panel.
9. Hash y Manual Conversion normalizan alias también en sus métodos de ejecución para que validación, detalles del resultado y bytes usados sean coherentes.

Los campos de entrada/salida, claves, IV, nonce, AAD, secretos, certificados/PEM, PIN, historial y resultados no se incluyen en plantillas; las pruebas de allowlist y preservación de artefactos técnicos permanecen activas.

## Módulos revisados y pendientes

Revisados: Hashing, Manual Conversion, Random Number Generator, Batch Runner/File Conversion a nivel de navegación Generic, Cipher, Digital Signatures/MAC, Certificates/Certificate Inspection, barra global, Inspector, breadcrumb y Clipboard Shelf.

Pendientes: completar una matriz visual de todos los módulos especializados que no usan la barra global (ASN.1, XML/WSS, JOSE, CMS/PAdES y procesos) en una sesión JavaFX con display; no requieren cambios de formato para UX-18, pero conviene validar sus paneles vacíos después de cambiar idioma y volver a navegar.

## Pruebas ejecutadas

- `mvn -q test` — correcto.
- `mvn -q -DskipTests test-compile` — correcto.
- `xmllint --noout` sobre todos los FXML de `src/main/resources` — correcto.
- `git -c core.fsmonitor=false diff --check` — correcto.
- `mvn -q -DrunUiTests=true test` — iniciado; el entorno JavaFX no pudo inicializar QuantumRenderer (`No toolkit found`, sin pantalla/pipeline y sin permisos para la caché nativa), por lo que la suite opt-in no pudo ejecutarse.

Se añadieron regresiones headless de normalización/allowlist/persistencia y pruebas JavaFX opt-in para Hash `Hola` (`Text (UTF-8) → Hexadecimal`, SHA-256), Manual Conversion (`Text (UTF-8) → Base64`), plantilla Cipher, navegación/expansión de Manual Conversion y aplicar-reset-restaurar de plantilla personal de Hash.

## Limitaciones JavaFX

La máquina actual expone las librerías JavaFX, pero no un display utilizable: `Screen.getMainScreen()` devuelve una lista vacía y el pipeline software tampoco puede copiar `libprism_sw.dylib` a la caché del usuario. Los tests UI deben repetirse en una sesión macOS con display o CI configurada para JavaFX.

## Deuda posterior priorizada

1. **Alta:** ejecutar la matriz JavaFX completa en display/CI y revisar visualmente cada ruta especializada en ES/EN, incluido cambio de idioma en vivo.
2. **Media:** extraer a un objeto común el contrato de formato y sus setters para reducir duplicación entre Generic, Cipher y Authentication.
3. **Media:** añadir plantillas explícitas para MAC si se desea guardar perfiles de algoritmo/truncado; actualmente el helper de Authentication cubre Digital Signatures.
4. **Baja:** sustituir etiquetas técnicas restantes de File Conversion (`Text`/`Hex`) por un catálogo canónico separado, ya que ese módulo usa formatos de archivo locales y no la barra global.

No se hizo commit. Se conservaron los cambios locales ajenos presentes al iniciar la tarea.
