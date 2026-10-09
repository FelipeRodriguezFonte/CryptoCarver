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
