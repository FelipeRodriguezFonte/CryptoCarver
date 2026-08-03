# UX-25 — handoff

## Alcance

Se amplió la cobertura JavaFX opt-in de las validaciones legacy migradas en CMS y JOSE. No fue necesario modificar `CmsInspectorController`, `JOSEController`, `InlineValidationSupport`, `UserFacingError` ni los FXML: las rutas ya exponían los `fieldKey` correctos y el contrato compartido funcionó con los controles reales.

## Matriz UI real cubierta

| Módulo | Escenario | `fieldKey` | Comprobaciones |
|---|---|---|---|
| CMS | Entrada CMS vacía | `cmsInputArea` | Mensaje localizado y específico, foco, resaltado y banner visible |
| CMS detached | Contenido original vacío con verificación detached | `cmsContentArea` | Mensaje localizado, foco, resaltado, banner visible y secreto ausente |
| JOSE JWT | Token vacío | `jwtValidateTokenArea` | Mensaje específico, foco y `field-error` |
| JOSE detached | Algoritmo no seleccionado | `detachedAlgoCombo` | Mensaje específico, foco del selector y `field-error` |
| JOSE detached | Clave de verificación vacía | `detachedVerificationKeyArea` | Remedio específico, foco, resaltado y secreto ausente |

Los escenarios cargan `cms_inspector.fxml` y `jose.fxml` con sus controladores reales. El reporter de prueba conecta el resultado a una instancia real de `InlineErrorPresenter`; por tanto, se comprueban también la visibilidad del banner, el foco y la clase `field-error`. Las rutas no muestran `Alert` ni llaman al feedback genérico.

## Prueba headless

- `Ux25HeadlessTest`: comprueba las claves CMS/JOSE ES/EN y verifica que todos los `fieldKey` esperados existen en los FXML reales.
- `Ux25CmsJoseValidationLiveUITest` (`@Tag("ui")`, `runUiTests=true`): escenarios reales CMS, CMS detached, JWT, JWS detached por algoritmo y clave, con foco y resaltado.

## Defectos encontrados

No se encontró ningún defecto funcional en producción durante esta ampliación. No se alteraron serialización JOSE, JWT/JWE/JWS, CMS, claves ni excepciones criptográficas profundas.

## Verificaciones

- `mvn -q -Dtest=Ux25HeadlessTest test` ✅
- `mvn -q test` ✅
- `git diff --check` ✅ (ejecutado con `core.fsmonitor=false` por el warning IPC preexistente del worktree).
- No se modificó FXML; `xmllint` no aplica.

## Limitación Xvfb/CI

`Ux25CmsJoseValidationLiveUITest` está anotada con `@Tag("ui")` y `runUiTests=true`. No se ejecuta en la suite Maven normal ni se ha ejecutado localmente en esta estación; debe validarse mediante el workflow Xvfb existente. No se ha hecho push.

No se ha creado ningún commit.
