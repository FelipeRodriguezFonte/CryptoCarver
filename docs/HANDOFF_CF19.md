# CF-19 Handoff: Consolidación de DataConverter y contratos de formatos

## 1. Alcance
El objetivo principal es unificar las dos implementaciones existentes de conversión de datos (`com.cryptocarver.util.DataConverter` y `com.cryptocarver.utils.DataConverter`) en una única implementación canónica. 
Se garantizará que ninguna funcionalidad se pierda, fusionando los métodos útiles y adaptando la clase sobrante como un adaptador `@Deprecated` para compatibilidad, sin romper dependencias en GUI, CLI, operaciones batch o tests. No se añadirán nuevos formatos ni se realizarán cambios de interfaz de usuario.

## 2. Archivos Implicados
- **`src/main/java/com/cryptocarver/util/DataConverter.java`**: Se mantendrá como la implementación **canónica**.
- **`src/main/java/com/cryptocarver/utils/DataConverter.java`**: Pasará a ser un adaptador **marcado como `@Deprecated`**. Sus métodos delegarán a la clase canónica.
- **`src/test/java/com/cryptocarver/util/DataConverterTest.java`**: Se ampliará para cubrir los nuevos casos de prueba requeridos.

## 3. Decisiones y Diseño
### 3.1 Ubicación Canónica
La clase `com.cryptocarver.util.DataConverter` ha sido elegida como la canónica debido a:
- Es la más utilizada dentro del proyecto (aparece en cientos de referencias).
- Contiene un catálogo mucho más extenso de formatos modernos (Base58, Base58Check, BCD, COMP-3, Base64Url, QuotedPrintable, etc.).
- Utiliza en gran medida APIs nativas de Java, reduciendo la dependencia absoluta en Apache Commons Codec cuando es posible, aunque lo usa en formatos específicos (Base32).

### 3.2 Fusión de Métodos
Se trasladarán los siguientes métodos desde `com.cryptocarver.utils.DataConverter` hacia `com.cryptocarver.util.DataConverter`, resolviendo cualquier conflicto de firmas:
- `hexToBase64` / `base64ToHex`
- `textToHex` / `hexToText`
- `bytesToJavaArray`
- `formatHex`

### 3.3 Adaptador de Compatibilidad
El paquete antiguo (`com.cryptocarver.utils.DataConverter`) se reescribirá por completo:
- Se eliminará su implementación nativa.
- Todos sus métodos pasarán a ser envoltorios (wrappers) que llaman a `com.cryptocarver.util.DataConverter`.
- Se marcará la clase entera con `@Deprecated`.

## 4. Pruebas y Tests (JUnit)
Se extenderá `DataConverterTest.java` con las siguientes pruebas:
- **Equivalencia de Formatos:** Conversiones iterativas entre Hex ↔ Base64 ↔ Base64URL ↔ UTF-8 para asegurar la falta de pérdida de datos.
- **Validación de Entradas Inválidas:**
  - `hexToBytes` con caracteres no hexadecimales y longitud impar.
  - `decodeBase64Flexible` con payloads corruptos o de tamaño incorrecto.
- **Conversiones de Charset sin Implicidad:** Se testeará que `hexToText` y conversiones de UTF-8 respeten los estándares, y no confíen en el charset por defecto del SO.

## 5. Decisiones de Compatibilidad
1. **`hexToBytes(null/"")`:** La implementación canónica (`util`) devuelve `byte[0]` para nulo o vacío, mientras que la implementación histórica antigua (`utils`) lanzaba `IllegalArgumentException`. Para preservar el 100% de la retrocompatibilidad en el código heredado, se decidió que el wrapper `@Deprecated` en `utils` conserve la excepción histórica antes de delegar a la canónica. Esta decisión ha sido asegurada con tests en `testAdapterCompatibility`.
2. **`formatHex` Infinito:** Se añadió una validación restrictiva en `formatHex` para rechazar un `byteSeparation <= 0`, evitando el potencial cuelgue por bucle infinito de la UI si llegara un cero.

## 6. Riesgos
1. **Regresión en Manejo de Espacios y Saltos de Línea:** Las dos clases actuales manejan `\n` y espacios de manera sutilmente distinta (por ej., `decodeBase64Flexible`). La implementación canónica ha sido actualizada para ser resiliente y no estricta con whitespaces.
2. **Conflictos con Excepciones:** Resuelto mediante adaptadores específicos documentados en el punto 5.

## 7. Pasos de Revisión (Checklist de Verificación)
- [x] Ejecutar `mvn -q test` y asegurar que todos los tests de conversión estén en verde. (Nota: `mvn -q test` se ejecuta localmente con éxito, a excepción de interrupciones de sandbox en el `GnuPgInteropTest` ajenas a este ticket).
- [x] Validar XML del FXML con `xmllint --noout src/main/resources/fxml/main-view-modern.fxml` (por protocolo de calidad de UI). Resultado exitoso sin output.
- [x] Revisar el formato del código usando `git diff --check` para prevenir trailing whitespaces. (Corregidos).
- [x] Buscar usos directos de `com.cryptocarver.utils.DataConverter` en `src/main/` mediante grep. No se encontraron importaciones activas.
