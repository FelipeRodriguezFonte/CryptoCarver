# Revisión B04 — C002–C005 y A008

Fecha: 10 de octubre de 2026. Revisor: Codex. Entrega de Luna: turno `01a12628-ac51-7a51-808f-206b0c3ecb4b`.

**Dictamen: REQUIERE CORRECCIÓN. B04 no está aprobado; B05 no debe comenzar.** B00–B03 conservan sus dictámenes. La entrega incorpora catálogo, referencias locales y conexiones de contexto, pero quedan defectos reproducibles de traducción, integración y correspondencia con los controles reales.

## Evidencia y límites

Se contrastaron el encargo C002–C005/A008, el inventario, la matriz, el catálogo, los recursos ES/EN, coordinadores y pruebas. Los XML archivados por Luna en `target/b04-review/unit/` suman **42 pruebas** en nueve clases; los de `ui/`, **45** en nueve clases, todos sin fallos, errores ni omisiones. Sus fechas de archivo corresponden a la copia conjunta de las 16:34; no se usa esa fecha como hora individual de ejecución. Se inspeccionaron las pruebas nuevas de catálogo/Cipher/OpenPGP: no comprueban ausencia de fallback y la de OpenPGP prepara CSS antes de configurar la ayuda, a diferencia de la carga real del shell.

No se repitieron las suites criptográficas. Se ejecutó una sonda JavaFX independiente con preferencias temporales en **ES/EN y anchuras 1800/900**, contra las clases compiladas de la entrega. Fuente y resultados finales: `target/b04-review/reviewer/B04Probe.java` y `probe-final.log`. Terminó correctamente y confirma R1, R2, R3, R4 y las listas reales de R5. Los primeros intentos de la sonda necesitaron aplicar CSS/layout tras habilitar los botones; no se atribuye ese ajuste del instrumento a la aplicación. `git diff --check` pasó.

La exploración confirma que la ruta actual Analyze ejecuta directamente el analizador con opciones predeterminadas; no hay un diálogo de opciones en ese flujo. La mención preliminar del revisor a ese diálogo se rectifica aquí. No se exige crear acciones de promoción/exportación que la UI no ofrece. Sí se exige explicar correctamente la ruta disponible y sus parámetros.

## R1 — Traducciones ES incompletas y prueba que acepta fallback

`messages_es.properties` carece de numerosas entradas B04 que existen en el recurso base: FPE 12, archivos 40, RSA 27 y análisis 32 (los recuentos incluyen algunas entradas no utilizadas del análisis). Por ejemplo faltan `help.field.op_fpe.algorithm.description`, las descripciones de campos de archivo, y el ejemplo del análisis. La sonda en ES muestra literalmente «Algoritmo: FF1 or FF3-1 from the implementation…» y opciones FF3-1 en inglés.

`ResourceBundle.containsKey` también consulta el bundle padre; la prueba `b04CipherModuleCataloguesHaveLocalizedActionsFieldsAndPackagedReferences` pasa sin verificar traducción propia. El encargo prohíbe contar fallback como ayuda completa.

**Cierre:** traducciones propias completas de todas las claves utilizadas de C002–C005/A008, incluidos acciones, ejemplos, campos, opciones y textos dinámicos. Comprobar el recurso ES directamente o con un mecanismo que excluya padres; recorrer las consultas en ambas vistas e idiomas. Retirar/clasificar las claves de acciones que no existen en la UI.

## R2 — OpenPGP sin botones en la carga real y acciones fuera del catálogo

En la sonda que carga `main-view-modern.fxml` y navega a OpenPGP, el binder tiene **cero bindings y cero botones**, en las cuatro combinaciones. `OpenPgpController.configureContextualHelp` busca controles mediante `openPgpAccordion.lookup` antes de disponer de la estructura CSS requerida y deja creado un binder vacío; no vuelve a instalarlo. La prueba aislada realiza CSS/layout antes de configurar y no reproduce esa secuencia.

Además, la inspección estática identifica otro defecto que debe corregirse al instalar los botones: los bindings de claves usan acciones `public_key` y `secret_key`, que **no son IDs de acción** de `openPgpDefinition`. El catálogo registra `paste_public_key`, `load_public_key`, etc., agrupando solo sus claves de texto. Abrir un binding con el ID de grupo no selecciona la acción registrada. Dos consultas para User ID y passphrase se instalan sobre el botón de generación; el campo User ID no recibe su propia consulta.

**Cierre:** instalación fiable dentro del ciclo de carga real, sin depender de CSS previo ni generar duplicados al volver; IDs válidos específicos para pegar/cargar/guardar/inspeccionar/Shelf; consulta propia del campo User ID y de generar. Explicar efectos distintos por acción, especialmente guardar secreto frente a limpiar. Regresión desde el shell real para todos los botones/campos, ES/EN, 1800/900, teclado, navegación/reapertura y cierre. Assert de que cada `actionId`/`fieldId` emitido existe en su definición y de preservación de los datos.

## R3 — Modo por líneas con ayuda de archivo completo

Con `fileCipherLinesCheck=true`, pulsar el `?` de cifrar emite `encrypt_file` y el título «Cifrar archivo completo» / «Encrypt whole file». En EN presenta como efecto de esa acción una etiqueta separada de 16 bytes, aunque el handler por líneas usa el layout por registro. Descifrar tiene el mismo binding fijo. El renderer añade una nota de líneas, pero no sustituye la acción incompatible.

**Cierre:** bindings dinámicos `encrypt_lines`/`decrypt_lines` cuando aplica; accesibilidad, descripción, ejemplos y reglas coherentes con la variante vigente. Actualización al cambiar el checkbox por usuario o programa, sin ejecutar. Comprobar AEAD por registro y CBC compacto; la ficha general puede comparar variantes, la acción seleccionada debe describir su handler efectivo.

## R4 — A008 usa la presencia de material de otra pantalla

El adaptador enlaza `ANALYSIS_HAS_KEY/IV/TAG/AAD` a los controles simétricos generales, mientras `handleAnalyzeFileCipher` toma los de archivo y solo los copia **al ejecutar**. Reproducción: clave de archivo con 32 bytes Hex, campo simétrico vacío; abrir A008 produce `ANALYSIS_HAS_KEY=false` y dice que la clave está vacía. También puede indicar material antiguo presente cuando el formulario de archivo está vacío. La consulta no debe invocar el handler para sincronizarlos.

**Cierre:** contexto de la ruta de análisis desde sus controles efectivos; para etiqueta, distinguir presencia de referencia de archivo de etiqueta de bytes ya cargados, sin leer el archivo. El análisis recorre hipótesis, por lo que un selector del generador no debe presentarse como identificación confirmada. Publicar solo flags/códigos permitidos y conservar parámetros de cada origen al navegar. Prueba con formularios discordantes, apertura/actualización ES/EN y preservación de ambos, resultados y archivos; ningún análisis ni acceso a disco provocado por consultar ayuda.

## R5 — Opciones RSA y detalles de parámetros incompletos

La sonda verifica listas reales RSA: **Text (UTF-8), Hexadecimal, Base64, Binary** para entrada y salida. El catálogo usa **Text, Hex, Base64, Base64URL, Binary**: ofrece Base64URL que no admite el panel y no identifica exactamente los códigos de texto/Hex. No se debe ampliar el panel para hacer cierta la ayuda. Los textos de claves «material reconocido por el panel» tampoco detallan los formatos aceptados y su prioridad; el encargo exige explicarlos.

Revisar asimismo la cobertura exigida de límites/unidades/opciones: FF1 permite tweak hasta el límite de 1024 bytes del motor y no basta decir «límite de implementación» sin mostrarlo; archivo genera nonce de 12 bytes para GCM/ChaCha y IV de 16 para CTR/CBC, y tiene páginas EBCDIC reales que el catálogo no enumera. Derivar/contrastar esas reglas con sus consumidores; separar tamaños recomendados/generados de los que valida cada ruta. La referencia RSA debe precisar los formatos y parámetros efectivos, incluida configuración OAEP/MGF1, sin modificar la criptografía.

**Cierre:** catálogo que coincida con cada selector actual y reglas completas, traducidas y aplicables por variante; pruebas comparando opciones con controles/fuentes vivos. Máximo RSA actualizado al cambiar padding desde metadatos públicos vigentes, sin analizar material privado ni mostrar un máximo de una clave reemplazada aún no cargada. Verificar cambio de clave pública ya cargada con ayuda abierta y regreso desde auxiliares.

## Nueva entrega

Luna conserva la propiedad de código, pruebas, matriz e inventario. Corregir R1–R5 y entregar B04 completo, pudiendo dividir trabajo sin declarar aprobado el bloque con deuda. Añadir las regresiones que reproducen estos defectos y las verificaciones comunes pertinentes por cambios del binder/shell. Mantener datos/resultados/archivos, lifecycle, modo desactivado, privacidad y todos los casos positivos/negativos del encargo. No cambios de algoritmo, políticas o parámetros operativos; no commits/pushes ni nuevas delegaciones/modelos.

Ejecutar Maven secuencial con preferencias aisladas, evitar el filtro UI al ejecutar unidad y conservar XML nuevos con hora de inicio/fin, clases/métodos y recuentos efectivos. Los 87 resultados archivados de esta entrega no prueban los contratos que faltan; no se suman a nuevas ejecuciones repetidas como pruebas distintas. B05 espera el dictamen de cierre completo.
