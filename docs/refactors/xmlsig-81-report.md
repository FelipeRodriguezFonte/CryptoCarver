# Encargo 81 — informe y parada de privacidad

## Resultado

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

## Las doce puertas de fases

`—` significa no ejecutada, no cero pruebas. La parada de fase 0(c) precede a fase 0(d) y a las tres fases de extracción. Ejecutar puertas o continuar la auditoría después de confirmar la fuga contradiría la instrucción de parar. No hubo extracción que retirar.

| Fase | Puerta | Informes | Pruebas | Fallos | Errores | Omitidas | Exit | Estado |
|---|---|---:|---:|---:|---:|---:|---:|---|
| 0 | G1 | — | — | — | — | — | — | Pendiente: parada 0(c) |
| 0 | G2 | — | — | — | — | — | — | Pendiente: parada 0(c) |
| 0 | G3 | — | — | — | — | — | — | Pendiente: parada 0(c) |
| 1 | G1 | — | — | — | — | — | — | Fase no iniciada |
| 1 | G2 | — | — | — | — | — | — | Fase no iniciada |
| 1 | G3 | — | — | — | — | — | — | Fase no iniciada |
| 2 | G1 | — | — | — | — | — | — | Fase no iniciada |
| 2 | G2 | — | — | — | — | — | — | Fase no iniciada |
| 2 | G3 | — | — | — | — | — | — | Fase no iniciada |
| 3 | G1 | — | — | — | — | — | — | Fase no iniciada |
| 3 | G2 | — | — | — | — | — | — | Fase no iniciada |
| 3 | G3 | — | — | — | — | — | — | Fase no iniciada |

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
