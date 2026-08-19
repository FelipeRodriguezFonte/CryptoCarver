# HANDOFF_UX-15D

## Alcance terminado

Se ha aplicado la internacionalización progresiva ES/EN a los módulos Payments (incluidos EMV, DUKPT, TR-31 y PIN), OpenPGP, PAdES y ASiC-S. Se reutilizan `I18nService`, `ModuleI18n` y `ModuleTextCatalog`; no se ha añadido ninguna dependencia ni un sistema paralelo de traducción.

Se localizaron los textos estables de FXML —títulos, ayudas, etiquetas, botones, tooltips, `accessibleText`, estados iniciales y prompts— y los principales mensajes de validación, error y progreso de los controladores. Los bindings se actualizan mediante el listener existente de `I18nService`, por lo que el cambio ES/EN se refleja en vistas abiertas.

## Archivos modificados

- `src/main/java/com/cryptocarver/ui/ModuleTextCatalog.java`
- `src/main/java/com/cryptocarver/ui/PaymentsController.java`
- `src/main/java/com/cryptocarver/ui/EMVController.java`
- `src/main/java/com/cryptocarver/ui/OpenPgpController.java`
- `src/main/java/com/cryptocarver/ui/PadesController.java`
- `src/main/java/com/cryptocarver/ui/AsicController.java`
- `src/main/resources/i18n/messages.properties`
- `src/main/resources/i18n/messages_es.properties`
- `src/main/resources/fxml/payments.fxml`
- `src/main/resources/fxml/emv.fxml`
- `src/main/resources/fxml/openpgp.fxml`
- `src/main/resources/fxml/pades.fxml`
- `src/main/resources/fxml/asic.fxml`
- `src/test/java/com/cryptocarver/ui/PaymentsFormatsI18nTest.java`
- `src/test/java/com/cryptocarver/ui/PaymentsFormatsLiveI18nUITest.java`

Los cambios FXML se limitan a identificadores de raíz/`Accordion` para conectar el binding de módulo; no se han cambiado handlers ni contratos de carga.

## Preservación técnica

Los algoritmos y operaciones no se han modificado. Los artefactos y valores técnicos permanecen bajo control del código de operación: tags EMV, KSN, TR-31, PIN blocks, PAN, claves, certificados, PEM, OpenPGP armor, PDF, ASiC, hashes, bytes, HEX/Base64, IDs, recetas, histórico y resultados no se traducen ni se transforman. Los argumentos de excepciones se conservan literalmente y solo se localiza la envoltura descriptiva cuando corresponde.

## Pruebas y verificaciones

Resultado literal: todas las siguientes ejecuciones terminaron con código `0`.

```text
/opt/homebrew/bin/mvn -q -Dtest=PaymentsFormatsI18nTest,PaymentsFormatsLiveI18nUITest,SpecializedI18nTest,SpecializedLiveI18nUITest,ResultFlowI18nTest,ResultFlowsLiveI18nUITest,I18nServiceTest,ModuleTextCatalogTest,ModernMainControllerFxmlStaticTest,ModernMainControllerI18nFxmlTest test
PASS (exit 0)
```

La salida incluye únicamente los diagnósticos esperados de fallback de claves/bundles ausentes usados por las pruebas (`missing.ux15d.key`, etc.).

```text
/opt/homebrew/bin/mvn -q -DrunUiTests=true -Dtest=PaymentsFormatsLiveI18nUITest test
PASS (exit 0)
```

Las pruebas JavaFX emitieron solo advertencias del runtime sobre acceso nativo/configuración de módulos.

```text
xmllint --noout src/main/resources/fxml/payments.fxml
xmllint --noout src/main/resources/fxml/emv.fxml
xmllint --noout src/main/resources/fxml/openpgp.fxml
xmllint --noout src/main/resources/fxml/pades.fxml
xmllint --noout src/main/resources/fxml/asic.fxml
PASS (exit 0)

git diff --check
PASS (exit 0)
```

Las pruebas cubren ES/EN para los cinco catálogos, fallback de clave inexistente, igualdad de un artefacto que contiene TLV/KSN/OpenPGP armor entre idiomas, parseo XML de todos los FXML modificados y cambio en vivo de Payments/OpenPGP.

## Limitaciones

Las pruebas JavaFX son opt-in (`-DrunUiTests=true`) y verifican la actualización del árbol de controles, no una revisión visual con pantalla real ni accesibilidad mediante lector de pantalla. Los mensajes de operación que son contenido técnico o proceden directamente de una excepción permanecen literalmente intactos por compatibilidad y trazabilidad.

## Deuda pendiente

Quedan fuera de UX-15D los módulos y diálogos indicados por los bloques anteriores, así como cualquier texto legacy no incluido en estos cinco FXML/controladores. También queda pendiente una pasada futura para localizar de forma sistemática estados dinámicos secundarios que no sean texto estable ni envolturas accionables, manteniendo siempre la separación entre descripción traducible y artefacto técnico.

No se ha creado ningún commit.
