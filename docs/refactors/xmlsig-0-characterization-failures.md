# XML Signature — fallo de privacidad de fase 0

## Reproducción requerida, sin extracción ni corrección

Base `55c8247c9ec749e3ac293779c5d0087c385e4572`. `XMLSignaturePrivacyCharacterizationUITest` carga el shell de producción (`main-view-modern.fxml`), navega a Sign XML (XAdES), obtiene el controlador incluido y usa los controles FXML reales.

Entrada inventada: URL HTTPS con user-info `https://invented-user:<contraseña-inventada>@tsa.invalid/timestamp` en `fx:id=xmlSignTsaUrlText`, y nombre de perfil inventado en `xmlSignTsaProfileNameField`. La contraseña se genera exclusivamente en cada test. No se hace ninguna petición ni resolución DNS.

Se invoca `handleSaveTSASavedProfile` y se comprueba el fichero real `settings.json` del directorio temporal; se construye otra instancia de AppSettings desde ese fichero para confirmar que el perfil sobrevivió al almacenamiento. Finalmente `handleLoadTSASavedProfile` vuelve a cargar la URL en el editor del combo del shell.

## Superficies exactas

1. `settings.json`, `tsaProfiles[].url`: credencial de autenticación en claro.
2. `settings.json`, `customTsaUrl`: copia adicional de la misma credencial en claro.
3. `fx:id=xmlSignTsaUrlText`, editor editable: la credencial persistida vuelve a aparecer al cargar el perfil. El control está unido a la escena del Stage y visible. La evidencia en disco basta por sí sola para establecer la fuga; no se deduce del valor interno de un PasswordField.

`isHttpUrl` solo exige esquema HTTP(S) y host. No rechaza ni retira `URI.getUserInfo()`. `getTsaUrl` devuelve el texto completo; `saveTsaProfile` y `saveCustomTsa` persisten ese texto. El mensaje “Only the endpoint is saved; no credentials are stored.” no cubre credenciales transportadas por la propia URL. Los campos separados de autenticación no necesitan intervenir para producir la fuga.

El contrato de privacidad en MASKED y REDACTED exige que esa credencial no se conserve; las aserciones quedan rojas por diseño. FULL_LAB caracteriza la conservación de los bytes originales, sin reinterpretar la política de ese perfil. No se fija por digest una contraseña aleatoria ni texto de excepción.

## Alcance y parada

No se afirma una fuga de clave privada ni de la contraseña de keystore: la fuga confirmada corresponde a una credencial TSA incluida en una URL aceptada por el controlador. Las comprobaciones de carga de claves, firma, verificación, inspección de XML y tokens, exportaciones, inspector, historial/recetas, Shelf y visor expandido quedan pendientes por la parada inmediata de fase 0(c). Tampoco se afirma haber validado las credenciales introducidas en los campos TSA separados.

No se corrige la fuga, no se extraen coordinadores y no se abren diálogos. Las puertas de fase 0(d) y de las fases 1–3 quedan sin ejecutar, por ser posteriores al bloqueo de privacidad. No se usa la reproducción roja para declarar un fallo de puerta.

La fixture restaura AppSettings, idioma, test.mode, Shelf y shell/historial aislado. No se invoca el historial legado. JUnit TempDir elimina ajustes y todos los ficheros creados incluso al fallar. Ningún test existente ni fichero de producción se modifica.

## Resultado observado

Comando: `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false -Dtest=XMLSignaturePrivacyCharacterizationUITest test`.

Informes 1; pruebas 3; fallos 2; errores 0; omitidas 0; exit 1. FULL_LAB pasa; MASKED y REDACTED fallan cada uno con las cuatro aserciones exactas descritas arriba. Las aserciones de guardado exitoso, recarga desde disco y unión del control a la escena pasan antes del fallo. No hay fallo de configuración de la fixture ni error de operación usado como falsa reproducción.

## Continuación: corrección autorizada de ajustes

La fuga inicial de endpoints en `settings.json` se corrige en el punto único AppSettings, en todos los perfiles. Las URL antiguas se sanean en memoria y el siguiente guardado las reescribe. Se conservan componentes y escapes fuera del user-info; texto no analizable sigue guardándose sin excepciones nuevas. No se modifican CmsCoordinator ni PreferencesService. El controlador presenta el aviso EN/ES nuevo y sigue usando la URL original durante la operación.

`AppSettingsTsaPrivacyTest` + `AppSettingsTest`: 2 informes, 11 pruebas, 0 fallos/errores/omitidas, exit 0. La reproducción original: 1 informe, 3 pruebas, 0 fallos/errores/omitidas, exit 0. Solo cambia su aserción FULL_LAB, autorizada por la continuación; las cuatro aserciones MASKED/REDACTED permanecen idénticas.

## Nueva fuga en firma y captura por el shell

`XMLSignatureSigningPrivacyUITest` usa shell/FXML reales, genera clave RSA y certificado inventados, escribe un PKCS#12 temporal y carga sus claves mediante `handleLoadXMLKeys`. Selecciona BASIC con contraseña inventada separada y una URL `https://invented:<contraseña-inventada>@tsa.invalid:8443/tsr?q=lab`. Firma un XML temporal con nivel BASELINE-B, que no hace peticiones TSA. No abre diálogos ni resuelve URLs.

Se confirma firma exitosa (`SignatureValue` en el área original), publicación de un registro de historial, aviso de credenciales no guardadas y URL saneada en settings.json. A continuación se inspeccionan las superficies reales del shell y el fichero history.json. La contraseña de la URL sigue persistida en estas rutas exactas:

- `history.json`, `[0].details`, detalle TSA.
- `history.json`, `[0].structuredDetails`, detalle TSA de clasificación PUBLIC.
- `history.json`, `[0].parameters["XMLSignatureController.xmlSignTsaUrlText"]`, receta automática del historial.
- En MASKED y REDACTED, texto visible de `#inspectorPanel`, detalle TSA.

No hay fuga nueva observada de la clave privada inventada, de la contraseña de keystore ni de la contraseña del campo BASIC separado en las superficies comprobadas bajo MASKED/REDACTED. FULL_LAB mantiene esas contraseñas permitidas en su receta, pero falla por user-info persistido, prohibido expresamente en todos los perfiles por la continuación. El helper compartido usa un mensaje genérico “key/cryptogram”; en esta reproducción el marcador encontrado es concretamente la contraseña URI.

El origen es `handleSignXML`: publica la URL completa como detalle público. UiStateSnapshot captura también el editor editable de URL sin sanear el user-info. AppSettings no interviene en esas persistencias de HistoryManager. Ninguna de esas áreas se corrige.

Comando: `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false -Dtest=XMLSignatureSigningPrivacyUITest test`.

Primera reproducción y segunda ejecución con diagnóstico de rutas: cada una 1 informe, 3 pruebas, 3 fallos, 0 errores, 0 omitidas, exit 1. En la segunda, los tres perfiles muestran las tres rutas JSON exactas arriba; los perfiles restringidos muestran además inspector/historial. No se relaja el contrato ni se modifica producción después de descubrir la fuga. Se afina únicamente el diagnóstico de la reproducción antes de commit.

Se aplica de nuevo la parada del punto 5 de la continuación. No se ejecutan las tres puertas de fase 0 ni se inicia ningún refactor. Verificación, inspección de XML/tokens, almacenes de confianza y exportaciones quedan pendientes. No se presenta la reproducción roja como un fallo de puerta. La fixture restaura estado y Shelf; el historial del shell está aislado y los temporales se eliminan. No se invoca el historial legado.
