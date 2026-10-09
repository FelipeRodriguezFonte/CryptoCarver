# Encargo 81 — informe y parada de privacidad

La sección inicial conserva la parada histórica de la primera entrega. El estado actual de la continuación se recoge en «Corrección autorizada y nueva parada»; la tabla de las doce puertas refleja la nueva parada del paso 5.

## Resultado inicial (histórico)

**Parada en fase 0(c): fuga de credenciales TSA confirmada; no se corrige y no se extrae ningún coordinador.** En MASKED y REDACTED, una URL HTTPS con user-info aceptada por `xmlSignTsaUrlText` guarda la contraseña inventada en `settings.json` (`tsaProfiles[].url` y `customTsaUrl`). Recargar el perfil devuelve la credencial al editor del control FXML. La comprobación usa disco y una segunda instancia de AppSettings: no es solo una observación de estado interno.

FULL_LAB conserva los bytes del endpoint y pasa. La reproducción queda roja por diseño en los dos perfiles restringidos, como exige el encargo. No se afirma haber encontrado una fuga de contraseña de keystore ni de clave privada. La auditoría de esas entradas y de las demás operaciones queda pendiente por la parada inmediata.

Mapa: [xmlsig-0-map.md](xmlsig-0-map.md). Reproducción y superficies exactas: [xmlsig-0-characterization-failures.md](xmlsig-0-characterization-failures.md).

## Base y alcance

- Base de `main`: `55c8247c9ec749e3ac293779c5d0087c385e4572`.
- Rama: `codex/xmlsig-1`; worktree: `/Users/feliperodriguezfonte/dev/CryptoCarver-xmlsig-1`.
- Creado con `git worktree add -b codex/xmlsig-1 /Users/feliperodriguezfonte/dev/CryptoCarver-xmlsig-1 main` desde el repositorio indicado.
- Todo Maven y toda edición del repositorio se ejecutaron exclusivamente en ese worktree, con un único Maven a la vez.
- `XMLSignatureController.java`: **747 líneas antes / 747 después**. Sin cambios de producción, comportamiento, nombres FXML, alias de historial ni propiedad de claves de idioma.
- Entorno real: macOS aarch64, Maven 3.9.11, Java 25 (Homebrew); el proyecto compila para release 17. No se afirma haber ejecutado Linux/Java 17.

## Referencias sobre la base intacta

Se completaron G1 y G3 antes de incorporar el mapa o cualquier test. Antes de cada ejecución se eliminó `target/surefire-reports`. Solo se contaron los XML `TEST-*.xml` generados en esa ejecución, al terminar Maven. Se archivaron los informes antes de la limpieza siguiente.

| Referencia | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
|---|---:|---:|---:|---:|---:|---:|
| Base G1 | 462 | 2960 | 0 | 0 | 1 | 0 |
| Base G3 | 139 | 581 | 0 | 0 | 0 | 0 |

G1: `mvn -o -q test -Plow-cpu`.

G3: `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test`.

La excepción autorizada para `ExpandedViewerLifecycleUITest` no fue necesaria: ambas referencias pasan sin fallos. No se excluyó ni modificó esa clase. G2 de referencia no fue solicitada.

## Auditoría ejecutada antes de extracción

Test nuevo: `src/test/java/com/cryptocarver/ui/XMLSignaturePrivacyCharacterizationUITest.java` (71 líneas). Usa el shell de producción y el controlador XML incluido, los controles FXML reales y la fixture existente de aislamiento del shell. Guarda un perfil con URL de autenticación inventada, lee el fichero de ajustes real, vuelve a cargarlo en una instancia independiente y restaura el perfil al editor visible.

Comando focalizado:

`mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false -Dtest=XMLSignaturePrivacyCharacterizationUITest test`

| Ejecución | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
|---|---:|---:|---:|---:|---:|---:|
| Reproducción fase 0 | 1 | 3 | 2 | 0 | 0 | 1 |

FULL_LAB pasa; MASKED y REDACTED fallan cada uno con cuatro aserciones de no exposición. Las comprobaciones previas de guardado, recarga y control unido a la escena pasan. Es una reproducción roja por diseño, **no una puerta fallida**. No se fija digest de esta prueba: no se alcanzaron las caracterizaciones con transcripción de las fases 1–3, y la contraseña es aleatoria e inventada.

## Las doce puertas de fases (estado de esta continuación)

`—` significa no ejecutada, no cero pruebas. Se conservan las tres paradas históricas de privacidad y GC descritas arriba. El contraste independiente resuelve la condición de GC de fase 0 como intermitencia, por lo que G3 se acepta. Una parada nueva en la extracción de fase 1 se documenta al final de este informe.

| Fase | Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit | Estado |
|---|---|---:|---:|---:|---:|---:|---:|---|
| 0 | G1 | 465 | 2978 | 0 | 0 | 1 | 0 | Limpia en la ejecución completa tras la corrección A |
| 0 | G2 | 141 | 591 | 0 | 0 | 0 | 0 | Limpia |
| 0 | G3 | 141 | 591 | 0 | 0 | 0 | 0 | Aceptada con evidencia del revisor en `13e165f`; base: 139/581/0 |
| 1 | G1 | 467 | 2980 | 1 | 0 | 1 | 1 | Detenida; único fallo `SpecializedFeedbackHeadlessTest`, dependencia de propietario fuente movido |
| 1 | G2 | — | — | — | — | — | — | No ejecutada por parada G1 |
| 1 | G3 | — | — | — | — | — | — | No ejecutada por parada G1 |
| 2 | G1 | — | — | — | — | — | — | No ejecutada por parada fase 1 |
| 2 | G2 | — | — | — | — | — | — | No ejecutada por parada fase 1 |
| 2 | G3 | — | — | — | — | — | — | No ejecutada por parada fase 1 |
| 3 | G1 | — | — | — | — | — | — | No ejecutada por parada fase 1 |
| 3 | G2 | — | — | — | — | — | — | No ejecutada por parada fase 1 |
| 3 | G3 | — | — | — | — | — | — | No ejecutada por parada fase 1 |

## Tests, restauración y hallazgos no corregidos

`XMLSignatureControllerUITest` fue leído antes de empezar. Solo cubre selección local/PKCS#11 y visibilidad del formulario local; no se modifica. **Tests existentes modificados: ninguno.** No se reasigna ninguna clave de idioma ni se relaja ninguna aserción o umbral.

El test nuevo no abre selectores de fichero, no contacta ninguna TSA ni resuelve URLs. Solo usa credenciales inventadas. AppSettings usa un fichero JSON temporal en esta base; no se escribe en las preferencias reales del usuario. La fixture restaura AppSettings, idioma, test.mode y Shelf, cierra el shell y limpia su historial aislado; no se llama al historial legado. JUnit TempDir elimina todos los ficheros creados incluso al fallar.

Hallazgo no corregido: guardado de user-info de una URL TSA en perfil y TSA personalizada, independiente de la política MASKED/REDACTED. El mensaje de guardado asegura que no se almacenan credenciales, pero el endpoint admite credenciales dentro de la URL. Tampoco se cambia ese mensaje.

Pendiente: auditoría tras carga de claves, firma, verificación, inspección de XML firmado y tokens generados en memoria; inspección de resultados, estado, inspector, historial y recetas, Shelf, visor expandido y exportaciones de fichero. `handleVerifyXML` abre diálogos de exportación de forma directa y el test existente no proporciona un punto de inyección para evitarlos; no se añadió un hook ni se simuló una verificación completa. No se afirma cobertura limpia de ninguna de esas superficies.

## Higiene y entrega

Comprobación con los ámbitos y rangos de puntos de código del CI: **estilos inline FXML = 0; emojis = 325 de 325**. `git diff --check` pasa. Sin cambios en crypto/, pom.xml, ModernMainController, UiStateSnapshot, StatusReporter, OperationResult ni ningún FXML. Sin imágenes, .local.md, DMG ni ejecutables. No se añaden secretos reales ni red a los tests.

Los logs de referencia y reproducción están en `/tmp/xmlsig-base-g1.log`, `/tmp/xmlsig-base-g3.log` y `/tmp/xmlsig-privacy.log`. Los XML de cada ejecución y sus recuentos se archivaron en `/tmp/xmlsig-evidence/{base-g1,base-g3,privacy-reproduction}`; estos son archivos locales de ejecución, no artefactos permanentes del repositorio. Los recuentos y los fallos relevantes se conservan en este informe y en la documentación de reproducción.

## Commits

1. `aaa5631580e0f71c0a6d6a51f14f6c9b7cf86c62` — mapa de privacidad.
2. `5696042c057fbf6eff2fcef330845fdb44aeaa56` — test nuevo de reproducción, sin cambiar producción.
3. `27072a7c40c3018eb36e90d58b3b543caac96f2d` — fallo observado y parada de fase 0.
4. `docs: deliver assignment 81 privacy stop report` — commit de este informe.

La lista completa con el SHA del propio informe se obtiene con `git log --reverse --format='%H %s' 55c8247c9ec749e3ac293779c5d0087c385e4572..HEAD`. La rama se entrega con todos los cambios commitados y sin cambios pendientes.


## Corrección autorizada y nueva parada

Continuación en el mismo worktree y rama, desde la primera entrega `6b7b02258a775c954a1bdaecf5a99949cd73ea37`. Se conservan arriba el SHA base original, los resultados iniciales y la primera parada. Cada uno de los pasos 1–5 de la continuación tiene un commit separado. **Estado actual: ajustes corregidos; nueva fuga de historial reproducida y sin corregir; refactor pendiente.**

### Cambios autorizados completados

1. AppSettings elimina solo el user-info antes de `setCustomTsaUrl` y `saveTsaProfile`, sin depender del perfil de visibilidad. Esquema, host, puerto, ruta, query, fragmento y escapes conservan sus bytes. Una URI que no se puede analizar conserva el texto, sin excepción nueva. Al cargar ajustes antiguos se sanea en memoria; el siguiente guardado reescribe esas URL. CmsCoordinator y PreferencesService permanecen intactos: usan el mismo límite de persistencia.
2. XMLSignatureController presenta `module.xml.tsaCredentialsNotSaved` mediante showInfo al guardar una URL con user-info. El helper común saveCustomTsa cubre Save TSA, Save profile, Test TSA, firma con TSA y Request timestamp. Se añadieron EN/ES y fallback EN; ninguna clave existente cambió de propietario. La URL local y la URL entregada a crypto/petición siguen siendo completas; solo AppSettings sanea la persistencia. No se agrega ningún emoji.
3. AppSettingsTsaPrivacyTest cubre usuario/contraseña, solo usuario, URI pública, puerto/ruta/query con escapes, IPv6, user-info escapado, texto no analizable y carga de ajustes antiguos. Los siete casos parametrizados prueban ambos escritores y los tres perfiles, con lectura posterior desde disco. El test de carga confirma que no se reescribe inmediatamente y que sí se reescribe con el siguiente guardado.
4. La reproducción XMLSignaturePrivacyCharacterizationUITest queda verde. **Único test ya existente modificado en la continuación: el test propio de este encargo**, con una sustitución de la aserción FULL_LAB para prohibir también allí la persistencia de credenciales. Es el cambio autorizado por el paso 4; las cuatro aserciones MASKED/REDACTED no se tocaron. No cambia ningún otro test existente.
5. XMLSignatureSigningPrivacyUITest avanza la auditoría hasta carga exitosa de PKCS#12 inventado, selección de alias y firma Baseline B exitosa, usando el shell y controles reales. Configura autenticación BASIC separada y una URL con user-info. Comprueba el aviso, settings.json saneado, salida XML, estado, inspector, historial/receta, Shelf y visor expandido. Aquí aparece otra fuga y se aplica la parada expresa. La firma B no contacta una TSA ni resuelve la URL.

### Verificación dirigida de la corrección

Antes de cada ejecución se eliminó target/surefire-reports y se contaron solo los XML de esa ejecución. No hubo Maven simultáneos ni ejecución en el repositorio principal.

| Ejecución dirigida | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
|---|---:|---:|---:|---:|---:|---:|
| AppSettingsTsaPrivacyTest + AppSettingsTest existente | 2 | 11 | 0 | 0 | 0 | 0 |
| Reproducción inicial tras corrección | 1 | 3 | 0 | 0 | 0 | 0 |
| Nueva reproducción de firma | 1 | 3 | 3 | 0 | 0 | 1 |
| Misma reproducción afinando rutas de diagnóstico | 1 | 3 | 3 | 0 | 0 | 1 |

Ajustes: `mvn -o -q test -Plow-cpu -Dtest=AppSettingsTsaPrivacyTest,AppSettingsTest`.

Las tres ejecuciones UI usan `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false -Dtest=<clase> test`, con XMLSignaturePrivacyCharacterizationUITest o XMLSignatureSigningPrivacyUITest. Estas ejecuciones dirigidas **no se presentan como G1/G2/G3**. No se han certificado esas puertas sobre el árbol corregido, porque el paso 5 ordena parar ante otra fuga.

### Nueva fuga sin corregir y cobertura pendiente

AppSettings ya no guarda el secreto. Sin embargo, handleSignXML publica `details.put("TSA", tsaUrl)` con la URL completa y clasificación PUBLIC. La receta automática captura también el editor de URL. El fichero temporal history.json confirma la contraseña inventada en **[0].details**, **[0].structuredDetails** y **[0].parameters["XMLSignatureController.xmlSignTsaUrlText"]**, en los tres perfiles. MASKED y REDACTED muestran además la contraseña en el contenido de #inspectorPanel, detalle TSA.

No es solo un valor interno: el test lee el fichero persistido por HistoryManager y el contenido del inspector del shell. Los controles de entrada PasswordField no se confunden con una salida visible. La contraseña de keystore y la clave privada inventadas no aparecen en las superficies comprobadas de MASKED/REDACTED; tampoco la contraseña del campo BASIC separado. FULL_LAB conserva esas contraseñas permitidas en la receta, pero falla por user-info de URL persistido, prohibido en todos los perfiles por la nueva instrucción.

No se corrige la clasificación de TSA, la receta ni la publicación del shell. El paso 1 autorizado sanea AppSettings, que no participa en el guardado de HistoryManager. Cambiar este camino sería corregir otra fuga y contradice la parada del paso 5. Se mantiene la reproducción roja.

Pendientes por esa parada: verificación de XML, inspección de XML firmado, inspección/validación de tokens generados en memoria, almacenes de confianza y exportaciones de fichero. La auditoría de campos BASIC separados se limita al escenario de firma y superficies comprobadas; no se afirma cobertura de toda su utilización. No se abren selectores de fichero ni se añade ningún hook de producción. Las doce puertas siguen pendientes como refleja la tabla actualizada. No se inicia ninguna de las tres extracciones ni sus caracterizaciones con SHA-256. La excepción GC no necesita evaluarse en las pruebas dirigidas; no se altera ni excluye ExpandedViewerLifecycleUITest.

### Higiene y líneas tras corrección

XMLSignatureController: **747 → 758 líneas** (aviso y detección de user-info). AppSettings: **371 → 399 líneas** (límite de persistencia y migración en memoria). Sin coordinadores extraídos. Estilos inline FXML = **0**; emojis = **325 de 325**; git diff --check pasa.

crypto/, pom.xml, ModernMainController, UiStateSnapshot, StatusReporter, OperationResult, CmsCoordinator, PreferencesService y todos los FXML permanecen intactos. No se modifica ningún test ajeno al encargo ni umbral. Tests con estado aislado/restaurado, credenciales y claves generadas exclusivamente para la prueba, sin imágenes, .local.md, DMG ni ejecutables. No se usa el historial legado. Los temporales se eliminan incluso cuando la reproducción falla.

Logs de la continuación: /tmp/xmlsig-settings-fix.log, /tmp/xmlsig-privacy-fix.log, /tmp/xmlsig-signing-privacy.log y /tmp/xmlsig-signing-privacy-paths.log. Sus recuentos/XML se archivaron bajo /tmp/xmlsig-evidence/{settings-fix,privacy-settings-green,signing-privacy-first,signing-privacy-paths}. Son evidencia local; las conclusiones y recuentos duraderos quedan aquí y en xmlsig-0-characterization-failures.md.

### Commits de continuación

- `31e2763974f9eeaf5a0851c444bf4ff0a7d45ad4` — paso 1: saneamiento en AppSettings.
- `c518049724351c13d016088015a3cba549515460` — paso 2: aviso EN/ES en controlador XML.
- `ce6f7e62de782d6493ab34e846888be6860e41ab` — paso 3: tests dirigidos de ajustes, incluidos ajustes antiguos.
- `4cfa749c1dd8f4988493522c002d6e6e34d55535` — paso 4: expectativa FULL_LAB de la reproducción propia.
- `ab736e592bbbeeec0cca3ab268cb677c67738366` — paso 5: nueva reproducción, mapa y fallo de historial.
- `docs: report authorized XML TSA correction and renewed privacy stop` — actualización de este informe y de la tabla de puertas.

El paso 6 y las fases 1–3 no generan commits porque la nueva parada los precede. Rama entregada con los cambios commitados y sin cambios pendientes; la suite completa contiene la reproducción nueva roja por diseño.


## Segunda continuación autorizada: historial y publicaciones TSA

Se conserva la primera parada de fase 0(c) por persistencia en ajustes y la segunda parada histórica del paso 5 por persistencia en historial de firma. Las dos se reanudan solo por la autorización explícita de esta continuación. Tras completar las correcciones, G1 y G2 pasan; se aplica una parada nueva tras G3 (detalle abajo). No se iniciaron las extracciones de fases 1–3.

### Corrección B — receta de historial

`UiStateSnapshot.captureHistoryRecipe` ahora detecta una cadena cuyo valor completo sea una URI HTTP(S) con user-info, y guarda la URL sin user-info mientras está activo MASKED o REDACTED. No reemplaza la URL por `[REDACTED_SECRET]`, de modo que el endpoint sigue restaurándose. Solo sanea el texto exacto que constituye la URL; texto común, URL sin credenciales y una URL incrustada en una frase se preservan. FULL_LAB conserva la URL completa, como el resto de secretos en historial. El saneador existente de AppSettings se movió a `TsaUrlSanitizer` sin cambiar su implementación de eliminación del user-info; el uso en este archivo está limitado a URLs HTTP(S).

`UiStateSnapshotTest` comprueba usuario/contraseña, solo usuario, URL pública, texto no-URL y URL incrustada en frase para los tres perfiles. La ejecución dirigida pasó. No se cambió otro comportamiento de UiStateSnapshot.

### Corrección A — publicaciones del controlador XML

Se reutiliza `TsaUrlSanitizer` desde `XMLSignatureController`. Para MASKED y REDACTED, los detalles TSA que publica firma y Request timestamp, el diálogo «TSA Saved», «TSA Profile Saved», el resumen de Test TSA y el texto de resultado de Request timestamp eliminan user-info. Los detalles saneados llegan también al inspector, historial, Shelf y visor mediante OperationResult. FULL_LAB mantiene la política normal y conserva los user-info en estas superficies. AppSettings, ya corregido previamente, sigue eliminando user-info al persistir ajustes en cualquier perfil. La URL completa continúa suministrándose a la petición TSA en curso.

`XMLSignatureSigningPrivacyUITest` verifica la firma real BASELINE-B, el detalle de TSA en historial/inspector y el comportamiento de diálogos Save TSA/Save profile en los tres perfiles. La prueba dirigida pasa con 9 casos; no contacta una TSA ni abre diálogos de fichero. Los tests UI de ajuste inicial mantienen intactas sus aserciones MASKED y REDACTED. Se corrigió únicamente su aserción FULL_LAB: ahora espera la conservación de las credenciales URI en historial, conforme a la política de perfiles que aclaró el usuario. El ajuste global de `settings.json` conserva su regla especial: nunca guarda las credenciales en ningún perfil.

El commit `d1c9baf` añade la caracterización de las publicaciones UI; la evidencia previa a A está también en G1 tras B: 2.978 pruebas, tres fallos únicamente en `XMLSignatureSigningPrivacyUITest`. Dos eran la fuga observada en perfiles restringidos; la tercera era una expectativa FULL_LAB heredada que prohibía guardar secretos en historial. El commit `13e165f` aplica la corrección A. El test dirigido posterior pasa.

### Auditoría restante y límites

La carga de PKCS#12 inventado, firma BASELINE-B, URL TSA con autenticación separada BASIC y revisión de salida, detalles, inspector, historial, receta, Shelf y visor están ejercitados por la reproducción de firma. No se detecta exposición de contraseña de keystore, bytes de clave privada ni contraseña BASIC en MASKED/REDACTED. Los campos de contraseña no se clasifican como superficie visible solo por leer su valor interno.

El código de verificación solo publica la política «Truststore configured» y no la ruta ni contraseña; no incluye esos campos en OperationResult. Las exportaciones XML son los reportes devueltos por XMLSignatureOperations. La inspección de XML y de tokens muestra datos estructurales/certificados públicos; la validación de token reporta estado genérico. No se generó en esta continuación una respuesta TSA de red ni se abrió ningún selector de fichero: los tests deben permanecer sin red y sin diálogos, por lo que esas salidas se inspeccionaron en el código y en los tests crypto existentes, no mediante el manejador interactivo completo. Test TSA y Request timestamp se revisaron para confirmar que las llamadas usan la URL original y que cada campo visible que incorpora `report.url()` se sanea al publicarlo.

### Puertas finales de fase 0 y nueva parada

Comandos:

- G1: `mvn -o -q test -Plow-cpu` — exit 0.
- G2: `mvn -o -q test -Plow-cpu -DrunUiTests=true` — exit 0.
- G3: `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test` — Surefire reporta 3 fallos GC; el proceso devolvió exit 0. Se cuentan los XML producidos y no se oculta el fallo en la tabla.

En G3, los tres casos fallidos son exactamente `ExpandedViewerLifecycleUITest`, `Closed UI fixture is still strongly reachable`. Se aisló la misma clase en la base de fase 0 (`6b7b02258a775c954a1bdaecf5a99949cd73ea37`) con las mismas opciones: 1 informe, 3 pruebas, 0 fallos, 0 errores, 0 omitidas, exit 0. Por tanto, no se cumple la condición de excepción GC y la puerta no queda limpia. De acuerdo con la regla de parada no se continúan las fases 1–3. No se modificó ni excluyó el test GC.

### Higiene, restricciones y commits de esta continuación

`git diff --check` pasa. No se modificaron crypto/, pom.xml, ModernMainController, StatusReporter ni OperationResult. `UiStateSnapshot.java` contiene únicamente el cambio autorizado de captura de historial. No se cambiaron claves de idioma ni nombres FXML. Los tests usan secretos inventados y almacenamiento temporal; no contactan servicios externos ni abren diálogos.

Commits añadidos en esta continuación:

- `27614b3` — saneamiento de URL con user-info en receta restringida y extracción del saneador compartido.
- `d1c9baf` — reproducción de privacidad en publicaciones de TSA, con expectativas por perfil.
- `13e165f` — saneamiento de publicaciones TSA del controlador XML.
- Este informe documenta el resultado y la parada G3.

Las dos paradas de privacidad de las continuaciones anteriores quedan conservadas arriba como historial; la ejecución actual queda detenida por una tercera condición independiente: G3 GC no reproducida en la base.

## Tercera continuación: evidencia del revisor y auditoría restante

El revisor aporta contraste independiente de `13e165f` frente a la base de fase 0 `55c8247`: G3 completa en rama, **141 informes / 591 pruebas / 0 fallos**, y base, **139 / 581 / 0**. Además, `ExpandedViewerLifecycleUITest` aislado pasó **3/3 en cada una de ocho ejecuciones alternadas** (cuatro por commit). La G3 de fase 0 queda **aceptada**; el resultado previo con tres fallos GC se conserva como incidencia intermitente histórica, no regresión. No se modificó ni excluyó la clase. Se adopta para puertas futuras el protocolo de reintento completo y contraste alternado rama/base autorizado en esta continuación.

Se aclara la política de perfiles: `settings.json` nunca persiste user-info TSA; historial, recetas, inspector, Shelf y visor obedecen la política normal. MASKED y REDACTED no presentan la credencial; FULL_LAB la conserva. La aserción FULL_LAB de `XMLSignatureSigningPrivacyUITest` es propia de este encargo y fue corregida para esperar conservación; las aserciones restringidas quedaron intactas.

La prueba nueva `XMLSignatureExtendedPrivacyAuditUITest` complementa la auditoría de firma/superficies: fabrica almacenes PKCS#12 y certificados temporales, firma y verifica XML BASELINE-B, inspecciona los tres informes XML exportables y los escribe/lee desde temporales, fabrica un token RFC 3161 local en memoria, crea un truststore TSA y ejercita los handlers reales de inspección XML, inspección del token y validación. Confirma que no se filtran contraseñas inventadas ni bytes codificados de clave privada a las salidas inspeccionadas; tampoco ruta/contraseña de truststore en la validación. La operación de verificación y el contenido de sus exportaciones se caracterizan directamente por `XMLSignatureOperations`, y su publicación shell se sintetiza con el contrato observado; `handleVerifyXML` completo no se invoca porque abre el diálogo de exportación. No se abre selector ni se contacta una TSA.

La ejecución dirigida informada al reanudar fue limpia: **1 informe / 1 prueba / 0 fallos / 0 errores / 0 omitidas / exit 0**. La auditoría de contraseñas BASIC separadas, perfiles TSA y salidas del controlador queda apoyada además por `XMLSignatureSigningPrivacyUITest`; se inspeccionó que verify/truststore y token no publican sus campos separados. No se afirma que se haya probado una exportación elegida desde un diálogo interactivo.

## Parada de fase 1: dependencia de propietario en test existente

El mapa `xmlsig-1-map.md` y la caracterización `XMLSignaturePhase1CharacterizationUITest` quedaron fijados en commits separados. La caracterización, ejecutada antes de extraer y sin diálogos, pasó **1/1** y fija SHA-256 `6fe9a0e7ea7ea421928d23b3ab0295e79faa32ac896ebf5de593e426083caa38`. La primera ejecución abrió el selector de guardado por conservar salida en el control; se interrumpió sin seleccionar nada, se corrigió el test para limpiar la salida, y la repetición pasó. Ese hecho y la transcripción están documentados en `xmlsig-1-characterization-failures.md`.

Se implementó provisionalmente la extracción a `XmlSignatureSigningCoordinator` con `record View`, `Supplier<StatusReporter>` perezoso y cuatro delegados. G1 (`mvn -o -q test -Plow-cpu`) terminó con **467 informes / 2.980 pruebas / 1 fallo / 0 errores / 1 omitida / exit 1**. El único fallo fue `SpecializedFeedbackHeadlessTest.specializedValidationFeedbackHasDistinctEnglishAndSpanishKeys`: espera que la clave `module.xml.feedback.saveRequired` tenga como propietario fuente `XMLSignatureController`, mientras que fase 1 la mueve legítimamente junto con `handleSaveSignedXML`. El contrato observado del comportamiento no falló; falló la aserción de propiedad fuente. Según la regla de puerta no se modifica un test existente para salvar la extracción ni se continúa: la extracción se retiró completamente, dejando el controlador idéntico al commit de caracterización. No hay puerta G1 aceptada para fase 1; G2/G3 y fases 2/3 permanecen sin ejecutar.

La puerta G1 sigue contabilizada como fallida aunque revertir la extracción restaura el propietario anterior, tal como ordena el procedimiento. No se aplica la excepción GC: el único fallo no pertenece a `ExpandedViewerLifecycleUITest`. Se conservan la caracterización y el mapa como evidencia del trabajo.

## Commits añadidos en esta continuación

- `c4ec363` — auditoría ampliada de verificación, XML, token y almacenes de confianza.
- `8c220ac` — mapa previsto de fase 1 y auditoría de cobertura existente.
- `0f68250` — caracterización UI previa a extracción y digest SHA-256.
- Commit actual — evidencia del revisor para G3, recuentos finales disponibles, y parada de fase 1.

El coordinador provisional de fase 1 fue retirado tras G1 y no forma parte de la rama. La rama conserva los cambios commitados de fase 0 y evidencia de fase 1; la extracción de fase 1 no se entrega como cambio funcional.

## Cierre por el revisor: extracción de las tres fases

Tras la parada de la fase 1 (G1 con un fallo en `SpecializedFeedbackHeadlessTest` por el propietario en fuente de `module.xml.feedback.saveRequired`), el usuario pidió al revisor terminar el encargo en esta misma rama. Las paradas anteriores se conservan arriba sin cambios.

**Resultado:** `XMLSignatureController` pasa de 747 líneas en la base `55c8247` (768 tras las correcciones de privacidad de la fase 0) a **303**. Tres coordinadores nuevos:

| Coordinador | Líneas | Contenido |
|---|---:|---|
| `XmlSignatureSigningCoordinator` | 259 | firma, verificación, inspección y guardado del XML firmado |
| `XmlSignatureKeyMaterialCoordinator` | 120 | carga de alias (PKCS#12 y PKCS#11), perfiles y selección de truststore |
| `XmlSignatureTimestampCoordinator` | 335 | TSA, perfiles TSA guardados, petición, inspección y validación de tokens RFC 3161 |

El código se movió literal; solo cambian los accesos a controles (por `View`) y al reporter (por `Supplier<StatusReporter>`, perezoso, porque los tests sustituyen el reporter con `initModule` después de cargar el FXML). No se renombró ningún `fx:id` ni se tocó el FXML.

### Tests existentes modificados

- `SpecializedFeedbackHeadlessTest`: reasignación del propietario de once claves `module.xml.feedback.*`, detallada clave a clave en `xmlsig-1-map.md`, `xmlsig-2-map.md` y `xmlsig-3-map.md`. `keyStoreRequired` y `aliasRequired` se comprueban en sus dos propietarios. No se eliminó ni relajó ninguna aserción; la prohibición de `module.xml.error.required` se amplió a los tres coordinadores.
- `UiStateSnapshotTest`: solo añadidos (fase 0, cambio B).

Antes de cada tanda de puertas se ejecutaron solos `SpecializedFeedbackHeadlessTest` y `ModernMainControllerFxmlStaticTest` (2 informes / 31 pruebas / 0 fallos) y los tests UI de XML (7 informes / 15 pruebas / 0 fallos al final).

### Caracterizaciones

| Fase | Test | SHA-256 | Verificada sin extraer |
|---|---|---|---|
| 1 | `XMLSignaturePhase1CharacterizationUITest` | `6fe9a0e7ea7ea421928d23b3ab0295e79faa32ac896ebf5de593e426083caa38` | sí (Codex, `0f68250`) |
| 2 | `XMLSignaturePhase2CharacterizationUITest` | `8de3a5f03ef1355e6dc0b62c3adc0b6d326a0a674f40395f17b61295b32aec97` | sí, sobre `f85ffd2` |
| 3 | `XMLSignaturePhase3CharacterizationUITest` | `ab9156a4c477a23f0e26ea50c398739f569269c95b0bb040af09c9400211de0c` | sí, sobre `7cea3d2` |

Los tres digests pasan sin cambios después de su extracción.

### Puertas de las fases de refactor

Ejecutadas en un worktree desligado sobre el commit exacto de cada extracción, borrando `target/surefire-reports` antes de cada una y contando solo los XML de esa ejecución. Un único Maven a la vez. macOS, Maven con OpenJDK 25.

| Fase | Commit | Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit |
|---|---|---|---:|---:|---:|---:|---:|---:|
| 1 | `f85ffd2` | G1 | 467 | 2980 | 0 | 0 | 1 | 0 |
| 1 | `f85ffd2` | G2 | 143 | 593 | 0 | 0 | 0 | 0 |
| 1 | `f85ffd2` | G3 | 143 | 593 | 0 | 0 | 0 | 0 |
| 2 | `7cea3d2` | G1 | 468 | 2981 | 0 | 0 | 1 | 0 |
| 2 | `7cea3d2` | G2 | 144 | 594 | 0 | 0 | 0 | 0 |
| 2 | `7cea3d2` | G3 | 144 | 594 | 0 | 0 | 0 | 0 |
| 3 | `28a2a0c` | G1 | 469 | 2982 | 0 | 0 | 1 | 0 |
| 3 | `28a2a0c` | G2 | 145 | 595 | 0 | 0 | 0 | 0 |
| 3 | `28a2a0c` | G3 | 145 | 595 | 0 | 0 | 0 | 0 |

Las nueve pasan limpias; no hizo falta la excepción de GC.

### Hallazgos no corregidos

- `handleValidateTimestampToken` pone el estado "Timestamp token validated." aunque el fichero no sea un token válido; el estado no distingue validación correcta de fallida (`xmlsig-3-characterization-failures.md`).
- Sin cobertura de extremo a extremo por requerir red, token o diálogo: respuesta correcta de `handleTestTSA` y `handleRequestTimestamp`, guardado del token, diálogo de exportación de `handleVerifyXML`, los Browse y la rama PKCS#11 de la carga de alias.
- Advertencia de traducción preexistente `module.process.category.wallet / eidas`.

### Higiene

Estilos en línea en FXML: **0**. Emojis: **325 de 325**. Sin cambios en `crypto/`, `pom.xml`, `ModernMainController`, `StatusReporter` ni `OperationResult`. `UiStateSnapshot` y `AppSettings` solo con los cambios autorizados de la fase 0.

### Commits del cierre

```text
f85ffd2 refactor(xml): extract XAdES signing coordinator with reassigned key owners
2b03528 docs(xml): map key material coordinator extraction and key ownership
37a62b4 test(xml): characterize signing key loading and trust store profiles
7cea3d2 refactor(xml): extract signing key material and trust store coordinator
875f55e docs(xml): map timestamp coordinator extraction and key ownership
f3f980b test(xml): characterize TSA endpoints, saved profiles and local timestamp tokens
28a2a0c refactor(xml): extract TSA and timestamp token coordinator
```
