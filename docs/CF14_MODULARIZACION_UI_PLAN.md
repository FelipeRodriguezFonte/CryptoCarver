# CF-14 — Modularización del controlador moderno y calidad visual

**Estado:** plan para implementación por Antigravity  
**Dependencias cerradas:** CF-01 a CF-13  
**Objetivo:** reducir el acoplamiento de `ModernMainController` sin cambiar el
comportamiento funcional de CryptoCarver ni romper el FXML, el historial, la
visibilidad de secretos o la navegación existente.

## 1. Punto de partida verificado

- `ModernMainController` tiene aproximadamente 6.350 líneas y conserva lógica
  directa de ASN.1, JOSE, herramientas genéricas e histórico.
- Ya existen controladores o servicios reutilizables: `GenericController`,
  `JOSEController`, `ASN1Controller`, `JOSEService`, `StatusReporter` y
  `OperationRegistry`.
- PQC y XML Security ya usan módulos FXML incluidos (`pqc.fxml` y
  `xml_security.fxml`): son el patrón de referencia.
- El FXML principal sigue conteniendo bloques grandes de JOSE, ASN.1 y Generic;
  el histórico se construye dinámicamente en Java.

## 2. Límites del bloque

### Incluido

- Extraer cuatro áreas de UI en módulos con controlador propio:
  ASN.1, JOSE, Generic y Recent Operations.
- Reducir el controlador principal a shell de navegación, barra global,
  inspector, ciclo de vida y puentes explícitos de compatibilidad.
- Mantener los `fx:id`, rutas laterales, recetas, historial y `StatusReporter`.
- Mejorar controles visuales de las vistas extraídas: crecimiento correcto al
  redimensionar, resultados expandibles y mensajes de error legibles.
- Añadir pruebas de carga FXML, navegación y regresión para cada extracción.

### Excluido

- Reescribir toda la navegación basada en strings a IDs de `OperationRegistry`.
  Sólo se añadirá un puente para los módulos extraídos.
- Cambiar algoritmos, semántica criptográfica, formatos de receta o el modelo de
  secreto de CF-04.
- Eliminar la interfaz legacy.
- Añadir dependencias JavaFX, TestFX u otras librerías nuevas.

## 3. Reglas obligatorias

1. No copiar lógica entre `ModernMainController` y el nuevo controlador: cada
   handler tiene una única implementación.
2. Los módulos publican resultados mediante `StatusReporter` y
   `OperationResult`; no actualizan el histórico o inspector global por acceso
   directo a nodos del padre.
3. Toda operación lenta mantiene ejecución asíncrona o no bloqueante. No mover
   trabajo criptográfico costoso al hilo JavaFX durante la extracción.
4. Preservar clasificación `PUBLIC`, `SENSITIVE`, `SECRET` y el comportamiento
   fail-closed de exportación de recetas e informes.
5. Mantener los formatos de entrada/salida explícitos. No introducir conversiones
   automáticas de charset, EBCDIC, compresión o Base64.
6. Trabajar sobre una copia aislada en `/private/tmp` para `mvn clean`; no tocar
   `target/` del árbol principal si puede haber un `./run-modern.sh` activo.

## 4. Entregas de implementación

Cada entrega debe compilar, pasar sus pruebas y poder revertirse de forma
independiente. No mezclar dos entregas en un mismo handoff.

### CF-14A — Módulo ASN.1

**Diseño**

- Crear `fxml/asn1.fxml` con el bloque actual de decoder/encoder/árbol ASN.1.
- Sustituir ese bloque del FXML principal por un `<fx:include>` con `fx:id`
  estable, siguiendo el patrón PQC/XML.
- Convertir `ASN1Controller` en el único dueño de los nodos y handlers ASN.1:
  parse, encode, canonicalizar DER, OID registry, edición de nodos y exportación
  JSON/Markdown/árbol.
- Inyectar `StatusReporter` desde el shell con un método explícito de
  inicialización; no usar reflexión ni referencias estáticas al controlador
  padre.
- El shell conserva sólo la ruta de navegación hacia el módulo y, si hace falta,
  una llamada pública mínima para seleccionar Decode/Encode.

**Pruebas mínimas**

- FXML de ASN.1 se carga desde una ruta con espacios.
- Todos los `onAction` del módulo resuelven contra `ASN1Controller`.
- Parse positivo, DER inválido y OID duplicado/erróneo conservan los tests
  actuales.
- Navegación a Decode ASN.1 y Encode ASN.1 muestra el subpanel correcto.

### CF-14B — Módulo JOSE

**Diseño**

- Crear `fxml/jose.fxml` a partir del bloque JOSE actual y usar `<fx:include>`.
- Migrar al controlador hijo todos los bindings de JWS/JWE/JWK/JWKS, inspector,
  JWS JSON, payload detached `b64=false` y Nested JWT.
- `JOSEController` debe delegar criptografía exclusivamente en `JOSEService`.
  El controlador no debe construir JWS/JWE mediante mapas o JSON manual.
- Conservar las dos firmas visibles y el aviso de interoperabilidad para
  `b64=false`, pero nunca mezclar el aviso dentro del artefacto JOSE exportado.

**Pruebas mínimas**

- Carga estática del FXML JOSE y resolución de todos los handlers.
- JWS JSON General con dos firmas; payload detached `b64=false`; fallo con clave
  incorrecta; Nested JWT sign → encrypt → decrypt → verify.
- Un resultado JOSE publicado se muestra correctamente en inspector, histórico y
  Expand Result sin recuperar contenido de una operación anterior.

### CF-14C — Módulo Generic y herramientas de bytes

**Diseño**

- Crear `fxml/generic.fxml`. Extraer por completo hashing, conversión manual,
  EBCDIC, hexadecimal comprimido, Byte Inspector, BCD/COMP-3, endian, compresión,
  ficheros, UUID, aleatorio, dígitos y aritmética modular.
- Adaptar `GenericController` a inyección FXML gradual. Si conservar el
  constructor actual evita una regresión, introducir un adaptador temporal con
  fecha de retirada documentada; no duplicar handlers.
- Dejar los selectores globales de formato como contexto explícito: la operación
  debe leer los formatos recibidos, no inferirlos de otras áreas visibles.
- Todos los cuadros de fichero siguen usando `FileChooser` sólo en UI; servicios
  de streaming no pueden depender de JavaFX.

**Pruebas mínimas**

- Conversión Hex/UTF-8, Base64URL inválido, EBCDIC explícito y hexadecimal
  comprimido en ambos sentidos.
- Byte Inspector y streaming cancelable conservan tests existentes.
- Cada ruta Generic lateral ilumina el `TitledPane` correcto.
- Una salida Generic nueva sustituye el snapshot anterior del visor expandido.

### CF-14D — Recent Operations y shell de navegación

**Diseño**

- Extraer la vista creada dinámicamente por `showHistoryView()` a
  `fxml/history.fxml` y un `HistoryController`.
- El controlador de histórico consume `HistoryManager`, `HistoryComparator`,
  `OperationRecipe` y `HistoryReportExporter` mediante dependencias explícitas.
- Conservar selección múltiple, rerun, compare, import/export JSON de receta,
  exportación Markdown, política de secretos y tabla tipada de detalles.
- El shell conserva la responsabilidad de restaurar un estado UI/ruta, mediante
  una interfaz pequeña (por ejemplo `OperationNavigator`), sin que el historial
  conozca la estructura del FXML principal.
- Introducir una función de ruteo para los módulos extraídos basada en el
  descriptor/alias del `OperationRegistry`, pero mantener el bridge de strings
  mientras existan rutas legacy.

**Pruebas mínimas**

- `REDACTED`, `MASKED` y `FULL_LAB` se aplican igual a receta JSON e informe
  Markdown.
- Compare exige exactamente dos elementos; export exige exactamente uno.
- Rerun restaura el estado y navega a la operación sin ejecutarla automáticamente.
- Una entrada legacy sin detalles estructurados ofrece una explicación segura y
  no exporta secretos.

## 5. Criterios globales de aceptación

- `ModernMainController` deja de poseer los `@FXML` y handlers de las cuatro
  áreas extraídas. Sus métodos restantes para estas áreas son sólo navegación o
  bridges de compatibilidad documentados.
- `main-view-modern.fxml`, `pqc.fxml`, `xml_security.fxml`, `asn1.fxml`,
  `jose.fxml`, `generic.fxml` e `history.fxml` son XML válidos.
- Las pruebas estáticas verifican `fx:id`, controlador correcto y `onAction` de
  cada FXML individual, incluido el caso de raíz perteneciente a un include.
- Navegación no muestra más de un contenedor funcional principal a la vez.
- Inspector, histórico y visor Expand Result continúan mostrando el resultado de
  la operación actual, no uno cacheado de otro módulo.
- No existen nuevas dependencias ni cambios de algoritmo no justificados.
- No empeora la política de secretos ni se persisten credenciales TSA/HSM.

## 6. Verificación exigida por entrega

En una copia aislada del repositorio:

```bash
/opt/homebrew/bin/mvn -q -Prelease-experimental -DrunUiTests=true clean test
xmllint --noout src/main/resources/fxml/main-view-modern.fxml
xmllint --noout src/main/resources/fxml/asn1.fxml
xmllint --noout src/main/resources/fxml/jose.fxml
xmllint --noout src/main/resources/fxml/generic.fxml
xmllint --noout src/main/resources/fxml/history.fxml
git diff --check
```

Antes de dar CF-14 por terminado, hacer un recorrido manual en macOS:

1. Abrir cada ruta extraída desde el panel lateral.
2. Ejecutar una operación por módulo y abrir **Expand Result** dos veces seguidas.
3. Redimensionar la ventana principal y el visor; confirmar que resultados y
   tablas crecen y no se solapan.
4. Exportar una receta y un informe con `REDACTED`; comprobar que una clave o
   contraseña no aparece en el fichero.

## 7. Entrega y revisión

Antigravity debe detenerse después de cada entrega CF-14A/B/C/D y enviar un
handoff con los archivos, decisiones de compatibilidad, resultados literales de
los comandos, pruebas añadidas, recorrido manual y deuda restante. Codex revisará
cada entrega antes de autorizar la siguiente.
