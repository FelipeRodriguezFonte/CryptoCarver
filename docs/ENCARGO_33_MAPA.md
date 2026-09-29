# Encargo 33 — mapa y extracción de presentación de navegación

## Mapa previo en `29c699a`

`ModernMainController` tenía 4.213 líneas. Los handlers de ruta están aproximadamente entre las líneas 1179 y 1318 y siguen en el controlador para el encargo 34. La presentación estaba en el mismo bloque y llegaba hasta `updateContentSubtitle`.

| Grupo y métodos | Llamadores y contratos | Controles FXML | Estado compartido |
|---|---|---|---|
| Formatos: `setInputFormat`, `setOutputFormat`, `setToolbarFormat`, `normalizeToolbarFormat` | `StatusReporter`; controladores de Cipher, Authentication y Generic notifican cambios; pruebas `ModernMainControllerFormatNormalizationTest`, `ModernMainControllerUITest` | `inputFormatCombo`, `outputFormatCombo` | perfiles de `OperationFormatRegistry`; formatos recordados y operación de perfil |
| Cabecera: `updateContentHeader`, `updateContentSubtitle`, `operationStatusSummary` | `handleItemSelected`, placeholders y pantallas propias (Process Designer, EMV, Saved Sessions, Quick Start); actualización de idioma | `contentTitleLabel`, `contentSubtitleLabel`, `contractOperationLabel` | `currentActiveOperation`, `OperationRegistry`, `i18n`, `AppSettings.lastRoute` |
| Miga: `updateBreadcrumbs`, `handleBreadcrumbSectionClick`, `handleBreadcrumbModuleClick` | `initialize`/refresco de idioma; FXML `breadcrumbSectionBtn`, `breadcrumbModuleBtn`; `handleItemSelected` a través de cabecera | `breadcrumbContainer`, `breadcrumbSectionBtn`, `breadcrumbSep1`, `breadcrumbModuleBtn`, `breadcrumbSep2`, `breadcrumbOperationLabel` | `UiNavigationRegistry`, `OperationRegistry`, `i18n`; callbacks de selección de sección y ruta al controlador |
| Favoritos: `handleToggleFavorite`, `updateFavoriteToggleState` | FXML `favoriteToggleBtn`; `initialize`/refresco de idioma y `handleItemSelected` | `favoriteToggleBtn` | `AppSettings` favoritos, operación activa e `i18n`; refresco de `SidePanel` |
| Perfiles: `applyOperationFormatProfile`, `formatProfileOperation`, `applyFormatToCombo` | `updateContentHeader`; `formatProfileOperation` también se usa al restaurar estado portable; perfiles configurados en `OperationFormatRegistry` | combos, `contractOperationLabel` | formatos recordados; `OperationFormatRegistry`; `OperationRegistry`; contrato activo notificado a `GenericController` |

`OperationNavigator` continúa siendo implementado por el controlador: History/Shelf y los demás consumidores llaman al controlador, que conserva `navigateTo`/`navigateToModule`. `GenericController`, `CipherController` y `AuthenticationController` usan `StatusReporter` para cambiar los formatos. `handleItemSelected`, `activateNavigationRoute` y `restoreStartupLastRoute` no se extrajeron.

Lógica independiente de controles: `FormatProfilePolicy` normaliza nombres legacy (`Text`, `Plain Text`) y canoniza el alias de perfil `Hashing:*`. `BreadcrumbPathPolicy` construye partes canónicas (clave de sección, módulo, destino y operación) a partir de una ruta o descriptor; `UiNavigationRegistry` resuelve la ruta. La traducción y escritura de nodos pertenecen al coordinador. `operationStatusSummary` deriva texto de `OperationDescriptor.Status` y `SecretRisk`.

## Caracterización previa

`NavigationChromeCharacterizationTest` carga `/fxml/main-view-modern.fxml` usando `Fxml.loader`. Ejecutado con el controlador original de `29c699a`: **1 test, 0 fallos, 0 errores**. Recorre Hashing, Symmetric Ciphers, Key Generation, JWT (Signed) y PIN Generation en español e inglés; verifica etiqueta de sección traducida, operación, click de sección/módulo, estado de cabecera, perfiles, normalización de formato, aplicación de una plantilla real de Hashing a través del `GenericController` y persistencia al alternar favorito. La prueba guarda y restaura idioma, favoritos y última ruta.

Caracterización preexistente adicional: `ModernMainControllerFormatNormalizationTest` y `ModernMainControllerI18nUITest` ejecutadas antes del movimiento junto con la captura visual inicial. Esta última prueba ejercita la localización de la shell con `Fxml.loader`.

## Resultado de la extracción

`NavigationChromeCoordinator` concentra actualización de cabecera, migas, favoritos, perfiles y selects de formato. Recibe nodos y un callback de actualización del contrato activo; las acciones de la miga invocan callbacks que regresan al controlador. El coordinador no navega ni conserva referencias a controladores de módulo. `ModernMainController` conserva handlers FXML y contratos `StatusReporter`/`OperationNavigator` como adaptadores, además de la decisión de enrutado.

`FormatProfilePolicy` contiene las decisiones puras de normalización y nombre de perfil. `OperationRegistry` y `UiNavigationRegistry` son las fuentes compartidas de operaciones y rutas. No se modificó `crypto/` ni el FXML/CSS.

## Roadmap

El siguiente paso sigue siendo el encargo 34: separar únicamente `navigateTo`, `navigateToModule`, `handleItemSelected`, `activateNavigationRoute` y `restoreStartupLastRoute`. Esta rama deja esos métodos intactos.
