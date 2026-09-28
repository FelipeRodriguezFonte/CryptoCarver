# Encargo 30 — informe

## Diagnóstico antes de corregir CSS

La prueba cargó `main-view-modern.fxml` mediante `Fxml.loader`, mostró un `Stage` de 1400 × 900, aplicó `styles.css` más cada tema y midió tras `applyCss()`/`layout()`. Para módulos se navegó con `ModernMainController.navigateToModule`: Genérico, Claves (`Symmetric Keys`), Cifrado (`Symmetric Ciphers`), Histórico y Sesiones guardadas. Se calculó el ratio WCAG de `Label.getTextFill()` contra el relleno opaco de sí mismo cuando lo tiene o, en otro caso, contra el primer ancestro opaco. El tamaño se comprobó tras layout y se ignoraron nodos ocultos o sin área.

La tabla enumera cada etiqueta/clase una sola vez por tema; los módulos indican dónde fue visible. `label` sin `fx:id` se identifica por su contexto de contenedor en la salida diagnóstica: en claro era el badge experimental de la navegación (fondo ámbar); en oscuro eran los labels de botones del menú principal.

| Tema | fx:id / clase | Ratio mínimo | Vistas |
|---|---|---:|---|
| `theme-dark.css` | `#breadcrumbOperationLabel `.label` breadcrumb-current` | 1.29:1 | Generic, History, Symmetric Ciphers |
| `theme-dark.css` | `#breadcrumbSep1 `.label` breadcrumb-separator` | 1.29:1 | Generic, History, Symmetric Ciphers, Symmetric Keys |
| `theme-dark.css` | `#breadcrumbSep2 `.label` breadcrumb-separator` | 1.29:1 | Symmetric Ciphers |
| `theme-dark.css` | `#inspectorTitleLabel `.label` ux-inline-8eb1b6eca4` | 1.15:1 | Generic, History, Saved Sessions, Shell, Symmetric Ciphers, Symmetric Keys |
| `theme-dark.css` | `#sessionTrailCountLabel `.label` ux-inline-cb001c4862` | 2.23:1 | Generic, History, Saved Sessions, Shell, Symmetric Ciphers, Symmetric Keys |
| `theme-dark.css` | `#statusLabel `.label` status-label-main` | 1.00:1 | Generic, History, Saved Sessions, Shell, Symmetric Ciphers, Symmetric Keys |
| `theme-dark.css` | `#statusLanguageLabel `.label` status-label-info` | 1.00:1 | Generic, History, Saved Sessions, Shell, Symmetric Ciphers, Symmetric Keys |
| `theme-dark.css` | `label` | 1.15:1 | Generic, History, Saved Sessions, Shell, Symmetric Ciphers, Symmetric Keys |
| `theme-dark.css` | `label ux-inline-7917b6784a` | 1.15:1 | Generic, History, Saved Sessions, Shell, Symmetric Ciphers, Symmetric Keys |
| `theme-light.css` | `#breadcrumbOperationLabel `.label` breadcrumb-current` | 1.46:1 | Generic, History, Symmetric Ciphers |
| `theme-light.css` | `#breadcrumbSep1 `.label` breadcrumb-separator` | 1.46:1 | Generic, History, Symmetric Ciphers, Symmetric Keys |
| `theme-light.css` | `#breadcrumbSep2 `.label` breadcrumb-separator` | 1.46:1 | Symmetric Ciphers |
| `theme-light.css` | `#statusLabel `.label` status-label-main` | 1.00:1 | Generic, History, Saved Sessions, Shell, Symmetric Ciphers, Symmetric Keys |
| `theme-light.css` | `#statusLanguageLabel `.label` status-label-info` | 1.00:1 | Generic, History, Saved Sessions, Shell, Symmetric Ciphers, Symmetric Keys |
| `theme-light.css` | `label` | 2.19:1 | History, Symmetric Ciphers, Symmetric Keys |
| `theme-light.css` | `label ux-inline-0665f452ba` | 2.05:1 | Saved Sessions, Shell |
| `theme-light.css` | `label ux-inline-98d654c0fb` | 1.12:1 | Saved Sessions, Shell |

`statusLabel` y `statusLanguageLabel` medían 1.00:1 en ambos temas; queda confirmada la hipótesis de la cascada. El título del inspector y el contador del rastro, afectados por el cambio de `adb94ad`, también fallaban en oscuro.

## Deduplicación

`components.css` tenía 3148 líneas y 87 selectores exactos repetidos. Para cada selector se fusionaron propiedades y se conservó por propiedad la última declaración encontrada; por tanto, el valor efectivo final del selector permanece igual. No cambió ningún valor efectivo de propiedad fuera de las correcciones de contraste. Los selectores con varias decisiones explícitas aparecen aquí; las apariciones coincidentes se señalan individualmente.

| Selector | Apariciones | Decisión por propiedad |
|---|---:|---|
| `.result-status-success` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.result-status-warning` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.main-menu-bar` | 3 | -fx-background-color: -color-bg-sidebar → -cc-text; final -cc-text |
| `.menu-bar` | 3 | -fx-background-color: -color-bg-sidebar → -cc-text; final -cc-text |
| `.menu-bar > .container > .menu-button` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.menu-bar > .container > .menu-button > .label` | 3 | -fx-text-fill: -color-text-light → white; final white |
| `.menu-bar > .container > .menu-button:hover` | 3 | -fx-background-color: -color-bg-sidebar-hover → -cc-bg-surface-raised; final -cc-bg-surface-raised |
| `.menu-bar > .container > .menu-button:showing` | 3 | -fx-background-color: -color-bg-sidebar-hover → -cc-bg-surface-raised; final -cc-bg-surface-raised |
| `.context-menu` | 3 | -fx-background-color: -color-bg-surface → white; final white |
| `.menu-item` | 3 | -fx-background-color: -color-bg-surface → white; final white |
| `.menu-item > .label` | 3 | -fx-text-fill: -color-text-primary → -cc-text; final -cc-text |
| `.menu-item:hover` | 3 | -fx-background-color: -color-primary → -cc-accent; final -cc-accent |
| `.menu-item:hover > .label` | 3 | -fx-text-fill: -color-text-light → white; final white |
| `.menu-item:focused` | 3 | -fx-background-color: -color-primary → -cc-accent; final -cc-accent |
| `.menu-item:focused > .label` | 3 | -fx-text-fill: -color-text-light → white; final white |
| `.radio-menu-item:checked > .label` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.tool-bar` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.main-toolbar` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.inspector-panel` | 3 | -fx-background-color: -color-bg-surface → -cc-text; final -cc-text |
| `.tab-pane` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.tab-pane .tab-header-area .tab-header-background` | 3 | -fx-background-color: -color-bg-sidebar-hover → -cc-bg-surface-raised; final -cc-bg-surface-raised |
| `.tab-pane .tab` | 3 | -fx-background-color: -color-bg-sidebar-hover → -cc-bg-surface-raised; final -cc-bg-surface-raised |
| `.tab-pane .tab .tab-label` | 3 | -fx-text-fill: -color-text-light → -cc-on-accent; final -cc-on-accent |
| `.tab-pane .tab:selected` | 3 | -fx-background-color: -color-bg-sidebar → -cc-text; final -cc-text |
| `.tab-pane .tab:selected .tab-label` | 3 | -fx-text-fill: -color-primary → -cc-accent; final -cc-accent |
| `.text-area` | 3 | -fx-background-color: -color-bg-surface → -cc-bg-surface; final -cc-bg-surface; -fx-border-color: -color-border → -cc-border; final -cc-border |
| `.text-area .content` | 3 | -fx-background-color: -color-bg-surface → -cc-bg-surface; final -cc-bg-surface |
| `.text-area:focused` | 3 | -fx-border-color: -color-border-focus → -cc-accent; final -cc-accent |
| `.text-field` | 3 | -fx-background-color: -color-bg-surface → -cc-bg-surface; final -cc-bg-surface; -fx-border-color: -color-border → -cc-border; final -cc-border |
| `.text-field:focused` | 3 | -fx-border-color: -color-border-focus → -cc-accent; final -cc-accent |
| `.combo-box` | 3 | -fx-background-color: -color-bg-surface → -cc-bg-surface; final -cc-bg-surface; -fx-border-color: -color-border → -cc-border; final -cc-border |
| `.combo-box:focused` | 3 | -fx-border-color: -color-border-focus → -cc-accent; final -cc-accent |
| `.combo-box .list-cell` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.button` | 3 | -fx-background-color: -color-bg-surface → linear-gradient(to bottom, -cc-on-accent, -cc-border); final linear-gradient(to bottom, -cc-on-accent, -cc-border); -fx-text-fill: -color-text-primary → -cc-text; final -cc-text; -fx-border-color: -color-border → -cc-border-strong; final -cc-border-strong |
| `.button:hover` | 3 | -fx-background-color: -cc-bg-surface → linear-gradient(to bottom, -cc-border, -cc-border-strong); final linear-gradient(to bottom, -cc-border, -cc-border-strong) |
| `.button:pressed` | 3 | -fx-background-color: -cc-border → -cc-border-strong; final -cc-border-strong |
| `.link-button` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.action-button` | 3 | -fx-background-color: linear-gradient(to bottom, -color-primary, -color-primary-dark) → linear-gradient(to bottom, -cc-accent, -cc-accent-hover); final linear-gradient(to bottom, -cc-accent, -cc-accent-hover); -fx-text-fill: -color-text-light → white; final white; -fx-border-color: -color-primary-dark → -cc-accent-hover; final -cc-accent-hover |
| `.action-button:hover` | 3 | -fx-background-color: linear-gradient(to bottom, -color-primary-light, -color-primary) → linear-gradient(to bottom, -cc-accent-hover, -cc-accent); final linear-gradient(to bottom, -cc-accent-hover, -cc-accent) |
| `.action-button:pressed` | 3 | -fx-background-color: -color-primary-dark → -cc-accent-hover; final -cc-accent-hover |
| `.operation-group` | 3 | -fx-background-color: -color-bg-surface → -cc-bg-surface; final -cc-bg-surface |
| `.label` | 3 | -fx-text-fill: -color-text-primary → -cc-text; final -cc-text |
| `.scroll-pane` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.scroll-pane .viewport` | 3 | -fx-background-color: -color-bg-base → -cc-on-accent; final -cc-on-accent |
| `.split-pane` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.split-pane .split-pane-divider` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.status-bar` | 2 | -fx-padding: 0 12 → 6 12; final 6 12 |
| `#statusLabel` | 3 | -fx-text-fill: -color-text-light → -color-text-subtle → -cc-text; final -cc-text |
| `.status-label-info` | 3 | -fx-font-size: 12px → 10px; final 10px; -fx-text-fill: -cc-border → -color-text-muted; final -color-text-muted |
| `.separator` | 4 | -fx-background-color: -color-border → -cc-border; final -cc-border |
| `.separator-dark` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.check-box` | 3 | -fx-text-fill: -color-text-primary → -cc-text; final -cc-text |
| `.check-box .box` | 3 | -fx-background-color: -color-bg-surface → -cc-bg-surface; final -cc-bg-surface; -fx-border-color: -color-border → -cc-border; final -cc-border |
| `.check-box:selected .mark` | 3 | -fx-background-color: -color-primary → -cc-accent; final -cc-accent |
| `.dialog-pane` | 3 | -fx-background-color: -color-bg-surface → -cc-bg-surface; final -cc-bg-surface |
| `.dialog-pane .header-panel` | 3 | -fx-background-color: -color-primary → -cc-accent; final -cc-accent |
| `.dialog-pane .header-panel .label` | 3 | -fx-text-fill: -color-text-light → white; final white |
| `.tooltip` | 3 | -fx-background-color: -color-bg-sidebar-hover → -cc-bg-surface-raised; final -cc-bg-surface-raised; -fx-text-fill: -color-text-light → white; final white |
| `.main-content-vbox` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.content-header` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.breadcrumb-current` | 2 | -fx-text-fill: -color-text-primary → -cc-bg-surface; final -cc-bg-surface |
| `.content-scroll-pane` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.content-container-vbox` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.placeholder-label` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.inspector-header` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.inspector-title` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.inspector-close-btn` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.inspector-label-small` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.inspector-value` | 3 | -fx-text-fill: -color-text-light → -cc-text; final -cc-text; -fx-font-size: 16px → 12px; final 12px |
| `.inspector-value-small` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.security-header` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.security-text` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.history-header` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.history-text-empty` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.status-label-main` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.text-area.font-small .content` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.text-area.font-medium .content` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.text-area.font-large .content` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.text-area.font-extra-large .content` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.primary-action` | 3 | -fx-background-color: linear-gradient(to bottom, -cc-accent-hover, -cc-accent-hover) → -cc-accent-hover; final -cc-accent-hover; -fx-text-fill: white → -cc-bg-surface; final -cc-bg-surface; -fx-border-color: -cc-accent-hover → -cc-info-text; final -cc-info-text |
| `.responsive-action-bar` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.responsive-action-bar .menu-button` | 2 | -fx-min-height: 32px → 36px; final 36px |
| `.primary-action:hover` | 2 | -fx-background-color: linear-gradient(to bottom, -cc-accent, -cc-accent-hover) → -cc-accent-hover; final -cc-accent-hover |
| `.btn-danger` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.error-banner-btn-close:focused` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.result-status-blocked` | 2 | Apariciones iguales; se conserva el conjunto de propiedades. |
| `.result-status-incomplete` | 3 | Apariciones iguales; se conserva el conjunto de propiedades. |

La regla genérica `.label` quedó una vez al principio de su sección. Recuento: 3148 líneas antes; 2711 justo después de deduplicar; 2748 en el resultado final con las reglas de contraste. No quedan selectores exactos duplicados.

## Resultado de contraste

La misma prueba se convirtió en regresión: falla si cualquier `Label` visible de las seis vistas y los dos temas queda por debajo de 3:1. Tras los cambios no reportó ninguna etiqueta bajo el umbral.

## Recetas del histórico

Se añadió una prueba sintética para `FULL_LAB`, `MASKED` y `REDACTED`. FULL_LAB restaura el valor en claro capturado; MASKED/REDACTED vacían los campos sensibles y mantienen disponibles los nodos asociados a los marcadores para que el coordinador enfoque el primero visible. Los tests no imprimen valores.

## Documentación revisada

`docs/ENCARGO_23_MAPA.md` y `docs/CRYPTOCARVER_ROADMAP_EVOLUCION.md` no atribuyen la etiqueta del rastro a un problema de ancho. No fue necesario corregirlos.

## Commits

Se añadirá la lista final de hashes al cerrar el trabajo.
