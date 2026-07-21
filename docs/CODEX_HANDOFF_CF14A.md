# Codex Handoff: CF-14A (Módulo ASN.1)

## 1. Archivos Modificados/Añadidos
- **`src/main/resources/fxml/asn1.fxml`** (NUEVO): Se ha extraído todo el bloque de interfaz de ASN.1 (Decode/Encode, etc.) desde `main-view-modern.fxml`.
- **`src/main/resources/fxml/main-view-modern.fxml`**: Se eliminó el bloque de ASN.1 de la vista moderna y se reemplazó por `<fx:include fx:id="asn1" source="asn1.fxml" />`.
- **`src/main/java/com/cryptoforge/ui/ASN1Controller.java`**: Ahora actúa como controlador exclusivo de `asn1.fxml`. Se han transferido todos los métodos y lógica de parseo, codificación, árbol ASN.1 (incluyendo menús contextuales de edición, comparación y exportación) desde `ModernMainController`. Publica resultados a través de `StatusReporter`.
- **`src/main/java/com/cryptoforge/ui/LegacyASN1Controller.java`** (NUEVO): Se preservó el controlador original `ASN1Controller` bajo este nuevo nombre para no romper la interfaz legacy (`MainController`), cumpliendo con la restricción del plan.
- **`src/main/java/com/cryptoforge/ui/MainController.java`**: Actualizado para usar `LegacyASN1Controller` en lugar de `ASN1Controller`.
- **`src/main/java/com/cryptoforge/ui/ModernMainController.java`**: Se eliminaron ~400 líneas de código relacionadas directamente con ASN.1. Solo se conservan métodos básicos de navegación que delegan en `asn1Controller.selectDecodeTab()` y `selectEncodeTab()`.

## 2. Decisiones de Compatibilidad
- **Legacy UI:** Puesto que `MainController` creaba manualmente su controlador de ASN.1 (y usaba componentes distintos, como `TextArea` en lugar de `TreeView` para el árbol ASN.1), se tomó la decisión de mantener la lógica legacy en `LegacyASN1Controller`. De esta forma, el nuevo `ASN1Controller` puede inyectarse limpiamente mediante FXML (`fx:include`) sin requerir constructor con parámetros y manteniendo cada handler con una única implementación en la interfaz moderna, pero sin eliminar la interfaz antigua.
- **Navegación:** Se introdujeron métodos simples en `ASN1Controller` (`selectDecodeTab()` y `selectEncodeTab()`) para que el shell principal mantenga la responsabilidad de la navegación lateral sin acceder directamente a los componentes hijos (`TabPane`).

## 3. Resultados Literales de Comandos (Verificación Aislada)
Todos los comandos fueron ejecutados en `/private/tmp/cf14a-build` sobre una copia limpia del código:

```text
[INFO] Tests run: 229, Failures: 0, Errors: 0, Skipped: 7
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  16.534 s
[INFO] Finished at: 2026-07-17T21:24:50+02:00
```
La validación sintáctica de FXML se superó sin errores de formato (ambos devuelven código 0):
```text
xmllint --noout src/main/resources/fxml/main-view-modern.fxml
xmllint --noout src/main/resources/fxml/asn1.fxml
```
Nota: `git diff --check` se omitió de la traza de éxito por no estar en un repositorio git clonado (se usó `rsync` sin `.git` para aislar).

## 4. Pruebas Añadidas
- **`src/test/java/com/cryptoforge/ui/ASN1ControllerTest.java`**: Creado para verificar la lógica de interfaz con aislamiento, asegurando que:
  - `handleParseASN1` y `handleEncodeASN1` publiquen resultados correctamente usando `StatusReporter.publish`.
  - Las validaciones fallidas por entradas DER inválidas y formatos de texto erróneos detienen el flujo y lanzan errores dialogables sin publicar nada.
- **`src/test/java/com/cryptoforge/ui/ModernMainControllerFxmlStaticTest.java`**: Actualizado para incluir la validación estática de inyección y handlers en `fxml/asn1.fxml` y `ASN1Controller`.
- **`src/test/java/com/cryptoforge/ui/ModernMainControllerUITest.java`**: Se agregó `testAsn1InjectionAndNavigation` para validar que `asn1Controller` es correctamente inyectado a través del `<fx:include>` y que las opciones de menú enrutan correctamente seleccionando la pestaña correspondiente de *Decode* o *Encode*.

## 5. Recorrido Manual
Dado el entorno restringido, la verificación manual debe completarse tras revisar este handoff. Por favor, verificar en macOS:
1. Abrir aplicación e ir a ASN.1 Decoder desde el panel lateral.
2. Ejecutar operación (Decode ASN.1) y abrir **Expand Result** dos veces seguidas para comprobar que los detalles y el reporte de estados funcionan mediante `StatusReporter`.
3. Comprobar menú contextual de la vista de árbol (Editar, Comparar, Exportar a JSON/Markdown).
4. Redimensionar ventana principal y comprobar comportamiento visual.

## 6. Deuda Restante
- Restan las extracciones de **JOSE**, **Generic**, y **Recent Operations** indicadas en el plan CF-14.
- Se debe validar en las siguientes iteraciones si más partes de la aplicación (como `JOSEController`) requieren un adaptador de compatibilidad para `MainController` de la misma manera que se requirió `LegacyASN1Controller`.
