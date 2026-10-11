# Revisión de B00: infraestructura de ayuda contextual

Fecha: 10 de octubre de 2026. Implementadora: Luna. Revisor: Codex.

**Dictamen: REQUIERE CORRECCIÓN.** La base compila y las pruebas existentes pasan, pero el piloto no permite consultar sus acciones y la ventana independiente pierde la actualización de contexto e idioma. B00 no queda aprobado para avanzar sobre esta infraestructura hasta corregir los cuatro hallazgos siguientes.

Esta revisión corresponde al estado local de la entrega descrita en [la matriz](AYUDA_CONTEXTUAL_MATRIZ_IMPLEMENTACION.md), contrastado con [el encargo](ENCARGO_LUNA_AYUDA_CONTEXTUAL.md). No se modificó código de producción ni se atribuyeron a B00 los cambios anteriores de JOSE y tutorial 25. Se añadió este informe y se guardó una sonda de revisión en `target/b00-review`, fuera del código de producción.

## 1. Hallazgos que impiden aprobar B00

### R1 — P2: los botones de acción no muestran su explicación

**Fuente:** [ContextualHelpCoordinator.java](../src/main/java/com/cryptocarver/ui/ContextualHelpCoordinator.java), líneas 70–81; especialmente la línea 73.

El botón llama a `actionSelected.accept(action.id())`, cuyo valor inicial es una función vacía. Ningún consumidor de producción conecta `setActionSelected`, y `showAction` tampoco tiene consumidores. Por tanto, los textos de descripción y ejemplo del piloto existen en los recursos, pero resultan inaccesibles desde la UI.

**Reproducción:** abrir Symmetric Ciphers, abrir Ayuda y pulsar Cifrar y Descifrar. El contenido permanece idéntico. La descripción y el ejemplo de cifrado no aparecen.

**Corrección para Luna:** hacer que la selección cambie el estado de acción y renderice la explicación en ambas presentaciones. Si se conserva un callback externo, separar la selección interna de la notificación opcional. Seleccionar una acción de ayuda no debe ejecutar el cifrado o descifrado de la aplicación.

**Aceptación:** pulsar cada botón por ratón y teclado muestra su descripción y ejemplo en ES y EN, sin alterar entradas, resultados ni estado de ejecución. Debe poder consultarse cualquiera de las dos acciones antes de ejecutar.

### R2 — P2: la ventana independiente conserva la operación anterior

**Fuente:** [ModernMainController.java](../src/main/java/com/cryptocarver/ui/ModernMainController.java), líneas 1122–1124; actualización de navegación en líneas 941–949.

La ventana crea un coordinador local y recibe la operación una sola vez. La navegación posterior actualiza exclusivamente el coordinador del inspector. Con el modo contextual activado, el usuario cambia de operativa y la ventana visible continúa describiendo la anterior.

**Reproducción:** usar un ancho de 900 píxeles, navegar a Symmetric Ciphers y activar el modo contextual. Mantener abierta la ayuda y navegar a Hashing. El inspector oculto ya muestra Hashing, pero la ventana sigue mostrando Symmetric Ciphers y el título `Ayuda — Symmetric Ciphers`.

**Corrección para Luna:** gestionar explícitamente la presentación independiente y transmitirle los cambios de ruta y acción de la ayuda activa. Compartir un estado de contexto o mantener coordinadores sincronizados; no depender de una variable local que se pierde al terminar el método. Mantener separadas la identidad de la ayuda activa y la del resultado histórico.

**Aceptación:** con la ventana abierta, Symmetric Ciphers → Hashing → Symmetric Ciphers actualiza contenido y título. El comportamiento debe coincidir con el inspector, tanto mediante navegación manual como mediante las rutas programáticas existentes. No requiere completar ahora las fichas de Hashing: su fallback debe corresponder a la ruta actual.

### R3 — P2: la ayuda independiente no cambia de idioma

**Fuente:** [ModernMainController.java](../src/main/java/com/cryptocarver/ui/ModernMainController.java), línea 657 y líneas 1123–1128.

`applyLocalization` refresca la ayuda del inspector, pero no la de las ventanas independientes. Estas tampoco registran una actualización propia. La misma ayuda responde de manera diferente al idioma según el ancho de la ventana principal.

**Reproducción:** abrir ayuda independiente en español y cambiar la aplicación a inglés. Sus etiquetas, referencia y título permanecen en español; la ayuda del inspector sí cambia.

**Corrección para Luna:** actualizar todas las presentaciones abiertas al cambiar el idioma, incluyendo título, acciones, descripción, ejemplo y referencia local. Conservar la acción consultada. Si se añade una suscripción a `I18nService`, mantener la referencia del listener según su contrato y liberarla con el ciclo de vida de la vista.

**Aceptación:** ES → EN → ES con ayuda abierta actualiza inmediatamente ambas presentaciones. La selección de acción se conserva y la reapertura no multiplica listeners.

### R4 — P2: las ventanas independientes carecen de gestión de ciclo de vida

**Fuente:** [ModernMainController.java](../src/main/java/com/cryptocarver/ui/ModernMainController.java), líneas 1127–1131; integración ausente en `shutdown`, líneas 549–561.

Cada apertura crea otro `Stage`. El shell no conserva una referencia para reutilizarlo, cerrarlo ni liberarlo al sustituir su escena. `initOwner` no cubre la sustitución de escena, un ciclo de vida que la aplicación admite y prueba.

**Reproducción:** abrir Ayuda dos veces con ancho reducido. Hay dos ventanas independientes. Sustituir la escena del shell: las dos continúan visibles, aunque el shell anterior ya se ha cerrado y liberado sus otros servicios.

**Corrección para Luna:** introducir un propietario explícito de la ventana de ayuda dentro del ciclo de vida del shell. Reutilizar o enfocar la ventana existente al volver a solicitarla y cerrarla/liberar suscripciones al cerrar o sustituir el shell. Mantener esta responsabilidad fuera de un crecimiento de lógica por operación en `ModernMainController`.

**Aceptación:** varias aperturas mantienen una única ventana por shell; cerrar y reabrir funciona; `shutdown` y sustitución de escena dejan cero ventanas y suscripciones pertenecientes al shell anterior. Estas pruebas deben incluir una ventana de ayuda realmente abierta, porque la prueba actual del shell no la abre.

## 2. Evidencia ejecutada por el revisor

Las comprobaciones se ejecutaron secuencialmente respecto a Maven, con preferencias de prueba aisladas.

| Comprobación | Resultado | Alcance |
|---|---|---|
| OperationHelpRegistryTest, LocalHelpServiceTest, PreferencesServiceTest | 3 pruebas correctas | Catálogo/fallback, resolución por ID y persistencia del booleano. |
| ModernMainControllerFxmlStaticTest, FxmlContractTest, FxmlQualityGateTest | 32 pruebas correctas | Contratos y estructura FXML; no verifican los nuevos clics. |
| ModernMainControllerI18nUITest, ModernShellCharacterizationUITest | 5 pruebas correctas | Localización y shell existentes, incluido el contrato de F1. |
| ShellWindowLifecycleUITest | 1 prueba correcta | Liberación del shell sin abrir la nueva ventana de ayuda. |
| ResultPanelWiringUITest | 8 pruebas correctas | Comportamiento existente de ResultPanel. |
| Sonda JavaFX sobre FXML de producción | Cuatro defectos reproducidos | Acciones, navegación, idioma y ventanas. |
| Carga real de referencias ES/EN desde classpath | Correcta | Ambos recursos `cipher.symmetric` están accesibles en `target/classes`. |
| `git diff --check` | Correcto | Sin problemas de espacios en el diff. |

**Total de pruebas JUnit ejecutadas: 49, sin fallos, errores ni omisiones.** Pasar estas pruebas no contradice los defectos: no cubren la interacción nueva que los dispara.

Comandos de verificación:

```sh
mvn -o -q -Dtest=OperationHelpRegistryTest,LocalHelpServiceTest,PreferencesServiceTest,ModernMainControllerFxmlStaticTest,FxmlContractTest,FxmlQualityGateTest -DrunUiTests=false test
mvn -o -q -Dtest=ModernMainControllerI18nUITest,ModernShellCharacterizationUITest,ShellWindowLifecycleUITest,ResultPanelWiringUITest -DrunUiTests=true test
git diff --check
```

La sonda temporal se encuentra en `target/b00-review/B00ReviewProbe.java`, con salida en `target/b00-review/probe.log`. Usa el FXML real, JavaFX, un shell con ancho inicial de 1400 píxeles y otro estado de 900 píxeles, valores sintéticos y un directorio de preferencias aislado. Su código de salida cero significa que consiguió recoger las observaciones; **no significa que las comprobaciones funcionales pendientes hayan pasado**.

Extracto relevante de las observaciones:

```text
encrypt_description_visible_after_click=false
encrypt_example_visible_after_click=false
content_unchanged_after_both_clicks=true
help_preserves_key_input_output=true
help_contains_sentinels=false
docked_help_updates_language=true
compact_help_labels_update_language=false
compact_help_title_updates_language=false
docked_context_after_hashing=Hashing
compact_context_after_hashing=Symmetric Ciphers
help_windows_after_second_open=2
help_windows_still_open_after_shell_scene_replacement=2
packaged_reference_es_present=true
packaged_reference_en_present=true
```

No se realizó un recorrido gráfico manual ni una evaluación visual completa de foco, contraste o dimensiones. No se validó un JAR distribuible fuera del checkout; esa comprobación integrada sigue prevista para B15. La comprobación con centinelas se limita a la apertura de ayuda del piloto y no representa una auditoría completa de todos los módulos.

## 3. Aspectos correctos y límites de alcance

La carga por referencias explícitas elimina la búsqueda parcial por encabezados y rechaza rutas como `../cipher.symmetric`. Los recursos reales de los dos idiomas están accesibles. La preferencia del modo se guarda y recarga. El fallback se declara incompleto y no se presenta como cobertura editorial terminada. La apertura de ayuda del piloto conserva clave, entrada y salida sintéticas, y su texto no contiene los centinelas utilizados. El nuevo renderizador no lee valores de formularios.

La entrega declara pendientes los botones `?`, las reglas por campo y la cobertura de las 96 operativas, 24 auxiliares y entradas dinámicas. Esta revisión no exige completar esos contenidos para corregir R1–R4.

La matriz debe precisar también la deuda de infraestructura: `HelpContext` está definido pero no tiene consumidores, y no se ha entregado todavía el contrato base de adaptadores de módulo. Además, la ficha A002 `aux_preflight_error` está asignada a B00 en el encargo, aunque la matriz agrupa los auxiliares como pendientes generales. Registrar esa ficha por su ID y su bloque pendiente antes de considerar cerrado el alcance de B00. Una reasignación de entrega debe quedar explícita en la matriz y en el encargo; no contar estos elementos como completados por la mera existencia de los records.

## 4. Encargo de corrección para Luna

Corregir R1–R4 como una entrega B00 de revisión, manteniendo el piloto mínimo y sin iniciar todavía el contenido completo de B01. Propiedad de cambios: `ContextualHelpCoordinator`, un coordinador de ventana o presentación si se necesita, conexiones acotadas del shell, pruebas de ayuda y matriz. Respetar todos los cambios ajenos; no crear subagentes, commits ni pushes.

1. Conectar la selección de acción de ayuda en ambas presentaciones y conservar un foco útil para teclado.
2. Gestionar una ventana de ayuda por shell, con actualización de ruta/acción/idioma y liberación al cerrar o sustituir la escena.
3. Añadir pruebas de regresión que reproduzcan cada hallazgo y fallen con la implementación actual. Incluir preservación de entradas/resultados y que la consulta de acción no ejecuta operaciones.
4. Mantener las referencias locales y los textos ES/EN; actualizar la matriz con los resultados reales, la deuda de adaptadores y el estado explícito de A002.
5. Entregar archivos modificados, pruebas ejecutadas y limitaciones restantes para una segunda revisión de Codex.

La segunda revisión comprobará los cuatro casos reproducibles y la evidencia nueva. No bastará con volver a ejecutar las pruebas estáticas existentes.
