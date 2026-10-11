# Primera revisión de B01: C001 y A001

**Corrección de evidencia del 10 de octubre, 13:18:** el recuento integrado de 91 que figura abajo mezcló XML anteriores con pruebas realmente ejecutadas bajo el filtro UI. No acredita una sola ejecución de todas esas clases. Véase [revisión 03](REVISION_B01_AYUDA_CONTEXTUAL_03.md) para la causa, ejecuciones nuevas y conteos verificados. Se mantiene el dictamen de corrección y sus reproducciones independientes.

Fecha: 10 de octubre de 2026. Implementadora: Luna. Revisor: Codex.

Entrega revisada: turno `01a12546-0d45-7f50-8483-eed90a8eedb1`.

**Dictamen: REQUIERE CORRECCIÓN. B01 no está aprobado; B02 no debe iniciarse.** Hay avances verificables en catálogo, campos, acciones y perfiles, pero las declaraciones COMPLETO de la matriz no están respaldadas por todos los recorridos ni por el contenido editorial requerido.

## Evidencia y aspectos aceptados

El catálogo incluye acciones diferenciadas y campos semánticos; las opciones se obtienen de las listas actuales de cifrado y los perfiles reales de formatos. La integración añade accesos `?`, selecciones públicas y presencia derivada de material, sin incluir clave, payload ni identificador privado de Key Lab. Los listeners tienen registro/liberación y la presentación sigue usando contexto compartido.

La ayuda documenta que los handlers dedicados actuales de ChaCha20-Poly1305/XChaCha20-Poly1305 no utilizan el AAD visible y que el selector por bloques no garantiza todas las combinaciones del proveedor. El revisor acepta registrar estas limitaciones sin cambiar comportamiento criptográfico. Son límites de implementación, no permiso para omitir el significado de opciones o requisitos de parámetros.

El revisor ejecutó una sola suite integrada de **91 pruebas, cero fallos, errores y omisiones**, con preferencias aisladas. Incluye las caracterizaciones simétricas y de clasificación del resultado, contexto, catálogo, recursos, FXML, idiomas y lifecycle. `git diff --check` pasó.

```sh
mvn -o -q -Dtest=ContextualHelpRegressionUITest,SymmetricCipherCharacterizationUITest,SymmetricOutputClassificationCharacterizationUITest,ShellWindowLifecycleUITest,ModernMainControllerI18nUITest,HelpContextTest,OperationHelpRegistryTest,SymmetricCapabilityPolicyTest,SymmetricCipherTest,LocalHelpServiceTest,PreferencesServiceTest,ModernMainControllerFxmlStaticTest,FxmlContractTest,FxmlQualityGateTest -DrunUiTests=true test
```

Log: `target/b01-review/review-integrated.log`. La prueba nueva de B01 solo cubre parte de los criterios: no vuelve desde el campo a la ficha, no reactiva el modo tras una ficha auxiliar, no completa Hashing → JWE → Hashing ni verifica ausencia de claves de traducción visibles. La existencia de una clave en ambos bundles tampoco demuestra que el contenido español esté traducido.

Sonda adicional con FXML de producción, datos sintéticos y preferencias independientes: `target/b01-review/B01ContextProbe.java`; evidencia `target/b01-review/context-probe.log`. Se ejecutó en ES/EN y anchos 1800/900. Sus consultas conservaron clave, entrada y resultado centinela. No modifica aplicación ni pruebas de producción.

## R1 — P2: se muestran claves internas de traducción

**Fuente:** [ContextualHelpCoordinator.java](../src/main/java/com/cryptocarver/ui/ContextualHelpCoordinator.java), líneas 148–150.

El segundo bloque de renderizado de la acción pasa `descriptionKey` y `exampleKey` directamente a `addLocalizedParagraph`, cuyo argumento es texto ya resuelto. Por tanto, además de la explicación traducida anterior, aparecen líneas como `help.action.op_sym_ciphers.encrypt.description` y `help.action.op_sym_ciphers.encrypt.example`. El defecto afecta también a las acciones de A002 renderizadas por esta vista común.

**Evidencia:** las dos claves aparecen como `Label` visibles en ES/EN, inspector y ventana compacta.

**Corrección/aceptación:** resolver las claves antes de presentarlas y evitar la duplicación de explicación/ejemplo. Ningún texto visible debe ser una clave de recurso. Probar acciones y campos C001 y A002, en ambas presentaciones e idiomas, verificando textos reales y ausencia de claves sin resolver.

## R2 — P2: «Volver a la operación» elimina las selecciones activas

**Fuente:** [ContextualHelpCoordinator.java](../src/main/java/com/cryptocarver/ui/ContextualHelpCoordinator.java), líneas 176–179; resolución de aplicabilidad, líneas 266–281.

El botón crea un contexto con `Map.of()`. Al borrar algoritmo/modo/origen de clave, la vista general utiliza AES-256/CBC por defecto aunque la pantalla siga configurada para ChaCha20-Poly1305. Seleccionar después una acción tampoco recupera esas selecciones. El mismo riesgo existe al pasar de una ficha auxiliar a la general de C001 si no se vuelve a publicar el contexto vigente del módulo.

**Evidencia en las cuatro combinaciones:**

```text
before_back_algorithm=PublicSelection[code=chacha20_poly1305]
after_back_algorithm=null
back_shows_wrong_default=true
```

**Corrección/aceptación:** volver a la ficha cambia la sección consultada, preservando o repoblando las selecciones públicas reales y la disponibilidad de material. Conservar identidad de resultado independiente. Probar vuelta desde un campo, selección posterior de acción y vuelta desde A001/A002, con un algoritmo y modo distintos del fallback, ES/EN y compacto. Ninguna ruta de consulta debe mostrar AES/CBC si ese no es el estado vigente; conservar entradas y resultado.

## R3 — P2: reactivar el modo desde ayuda auxiliar oculta los botones C001

**Fuente:** [ModernMainController.java](../src/main/java/com/cryptocarver/ui/ModernMainController.java), líneas 1324–1327.

La activación usa `contextualHelpState.operationId` para decidir si el módulo simétrico está activo. Ese ID identifica la ficha consultada; puede ser `aux_global_formats` mientras la ruta real sigue siendo Symmetric Ciphers. Se calcula `false` y solo después se abre la ficha general. Los botones permanecen ocultos pese a que el modo se ha activado.

**Reproducción confirmada:** en Symmetric Ciphers, abrir ayuda de formato; desactivar el modo y reactivarlo. `symAlgorithmHelpBtn.isVisible()` es `false` en ES/EN y ancho normal/compacto.

**Corrección/aceptación:** decidir visibilidad/activación de adaptadores por la ruta real, independientemente de la ficha auxiliar o del resultado seleccionado. Probar este recorrido con A001 y A002, reinicio desde preferencia persistente y navegación de ida/vuelta. Comprobar visible/managed, teclado y conservación de datos.

## R4 — P2: los ejemplos de doce acciones siguen en inglés en ES

**Fuente:** `src/main/resources/i18n/messages_es.properties`, líneas 4446–4482, claves `help.action.op_sym_ciphers.*.example`.

Los ejemplos de generar IV, seleccionar origen, pegar/cargar/inspeccionar/guardar/usar clave, copiar/estante/ampliar/guardar/reutilizar resultado tienen frases completas en inglés. Por ejemplo, el acceso de generación muestra `Generate for AES/GCM: the field receives 12 random bytes (24 hexadecimal digits).` en español. No se trata de conservar códigos técnicos.

**Corrección/aceptación:** traducir íntegramente los ejemplos y revisar los textos B01 ES/EN, incluidas etiquetas accesibles que dependen del origen de clave. Mantener códigos técnicos y datos sintéticos. Verificar contenido renderizado por acción en ambos idiomas; la mera presencia de claves en el bundle no acredita traducción. Resolver las entradas duplicadas del piloto y la versión ampliada para que exista una definición inequívoca de cada clave.

## R5 — P2: varias opciones aún no explican su efecto o parámetros permitidos

**Fuente:** `messages_es.properties`, líneas 4486 y 4494–4498; `messages.properties`; referencia local `help/es/cipher.symmetric.md` y equivalente EN.

PKCS7Padding, ISO10126Padding e ISO7816-4Padding repiten «Opción de padding por bloques expuesta para modos que lo admiten» sin explicar qué relleno aplica cada opción. PKCS5Padding solo remite al comportamiento del proveedor. NoPadding no explica la condición de alineación de entrada para transformaciones por bloques que la requieran. La opción ChaCha20 indica que el núcleo valida longitudes de nonce, pero no enumera los 8/12 bytes que acepta su validación existente; la referencia extensa solo menciona 12 generados.

El contrato editorial exige efecto/diferencia de cada opción y representación, unidad, rango y condiciones de cada campo. Un texto genérico que confirma la existencia del selector no cumple ese requisito.

**Corrección/aceptación:** completar las diferencias de los paddings y sus condiciones reales, sin prometer combinaciones rechazadas por el proveedor; precisar para cada variante los tamaños admitidos frente a los generados/recomendados. Verificar el comportamiento en código/proveedor y consultar documentación primaria si se necesita información normativa externa. Aplicar la misma revisión a las opciones A001 para que se entiendan decodificación y representación reales. Mantener todo en ES/EN, con ejemplos concretos o referencia reutilizable que explique la opción. No cambiar el motor para ajustar la documentación.

## R6 — P2: acceso de inspección huérfano y controles compartidos sin clasificación

**Fuente:** [CipherController.java](../src/main/java/com/cryptocarver/ui/CipherController.java), líneas 424–433; `fxml/cipher.fxml`, grupo de origen de clave; inventario UI de la sección simétrica y Templates.

Todos los botones de ayuda reciben la misma visibilidad del modo, sin asociarla a la de su acción. En origen manual, la acción Inspect está oculta, pero su `?` permanece visible antes de los botones de gestión de clave. La sonda confirma `inspect_action_visible=false inspect_help_tree_visible=true` en las cuatro combinaciones. Los accesos de campos tag/AAD sí respetan el ocultamiento por su contenedor y no presentan este defecto.

Además, la matriz detallada no clasifica los controles existentes de plantillas y Reset Defaults, ni incluye una referencia común explícita o su asignación auxiliar. El encargo obliga a clasificar los controles adicionales del inventario; no basta con enumerar los once campos principales y catorce acciones elegidas.

**Corrección/aceptación:** sincronizar la visibilidad de accesos con acciones ocultas y mantener consultable su motivo desde la ficha donde corresponda. Probar cambios Manual Input ↔ Simulated HSM y sus etiquetas/accesos. Revisar el inventario de la sección, clasificar cada control/acción y enlazar una explicación local o referencia común explícita. Los flujos completos de plantillas siguen en A003/B13: registrar esa responsabilidad y no adelantar su implementación completa, sin omitir la explicación de controles realmente incluidos en C001 ni atribuir cobertura inexistente.

## Encargo de corrección y entrega

Corregir R1–R6 conservando el alcance completo C001/A001 y las regresiones de B00/A002. Completar los recorridos exigidos: GCM → CBC → flujo, Key Lab con/sin material, formato Hashing → JWE → Hashing y restauración programática, así como las nuevas reproducciones. Mantener datos, ejecutabilidad y liberación de listeners; no transformar documentación de límites en cambios criptográficos.

Luna es responsable de coordinadores/adaptadores/conexiones y recursos B01, pruebas y matriz. Codex conserva revisión y coordinación. Respetar JOSE/tutorial 25 y cambios ajenos. Sin subagentes, commits, pushes, publicación ni cambios de motor, algoritmos, parámetros, defaults o política. Se permiten subentregas con pendientes explícitos; no marcar B01 completo con deuda obligatoria. B02 espera aprobación completa de B01.

## Seguimiento de correcciones R1–R6

Se aplicaron las correcciones en los coordinadores, C001 y A001, los bundles ES/EN, el inventario UI y la matriz. La solución conserva el alcance: el uso completo del flujo de plantillas sigue asignado a A003/B13 y el motor criptográfico no cambió. B02 sigue sin iniciar y este seguimiento no sustituye el dictamen final de aceptación.

Validación posterior: compilación correcta y suite dirigida `OperationHelpRegistryTest,SymmetricCapabilityPolicyTest,ContextualHelpRegressionUITest`: **15 pruebas, 0 fallos, 0 errores, 0 omitidas**. La UI recorre las 21 acciones C001 en español, confirma que explicación y ejemplo se renderizan una sola vez sin claves internas, preserva selecciones al volver, prueba reactivación desde A001/A002 y sincronización Inspect/Key Lab con y sin material. Log: `target/b01-review/r1-r6-final.log`.
