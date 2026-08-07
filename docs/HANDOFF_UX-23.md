# UX-23 — handoff

## Alcance

Se auditó el banner de error inline de `InlineErrorPresenter`, su integración en `ModernMainController`, el bloque FXML de `main-view-modern.fxml`, los estilos de error/foco de `styles.css` y las claves ES/EN.

## Comportamiento aplicado

- Un error con `fieldKey` válido mantiene el foco en el control objetivo y no desplaza el foco al banner.
- El botón «Ir al campo» conserva el destino, es navegable por teclado y vuelve a enfocar el control inválido.
- El orden FXML del banner es: Ir al campo, Copiar detalles técnicos, Cerrar. Los controles permanecen `focusTraversable` y sus acciones estándar soportan Enter/Space.
- Tab y Shift+Tab recorren los botones visibles; cerrar restaura el foco al campo objetivo o, si no existe, al foco previo válido.
- Título y remedio usan el mismo límite `InlineErrorPresenter.redactSecrets(...)` para texto visible, texto accesible y ayuda accesible.
- Los nombres y ayudas de los botones se localizan en vivo en ES/EN. Los detalles técnicos siguen copiándose únicamente de forma redactada.
- Se reforzó el contraste de los estados de foco del banner y de `.field-error` para TextField, PasswordField, TextArea, ComboBox y ChoiceBox.

## Pruebas

- `Ux23HeadlessTest`: claves accesibles EN/ES y ausencia de secretos en el texto accesible.
- `Ux23AccessibilityLiveUITest` (`@Tag("ui")`, `runUiTests=true`): carga el FXML moderno real y cubre foco de destino, orden Tab/Shift+Tab, activación Enter/Space, cierre/restauración, nombres accesibles EN/ES y redacción de título/remedio.
- `mvn -q -Dtest=Ux23HeadlessTest test` ✅
- `mvn -q test` ✅
- `xmllint --noout src/main/resources/fxml/main-view-modern.fxml` ✅
- `git diff --check` ✅; el entorno puede mostrar el warning preexistente del fsmonitor IPC.

## Limitaciones UI

La prueba JavaFX opt-in no se ejecutó localmente: requiere toolkit gráfico y `-DrunUiTests=true`; la ejecución prevista es el workflow existente con Xvfb. La suite por defecto mantiene excluidas las pruebas del grupo `ui`.

## Deuda restante

- Conviene ejecutar la primera corrida remota del workflow Xvfb y ajustar cualquier diferencia de traversal entre plataformas.
- La revisión visual de contraste debe repetirse con los temas y tamaños de fuente soportados por la aplicación.
- Otros diálogos legacy todavía pueden requerir una auditoría de accesibilidad independiente.

No se ha creado ningún commit ni se ha hecho push.
