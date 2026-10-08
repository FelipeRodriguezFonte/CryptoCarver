# Fase 0 ampliada — segunda parada por fugas en mdoc y Status List

La ampliación se ejecuta tras compartir el detector JOSE y proteger los informes SD-JWT. No se extrae código ni se corrigen estas nuevas fugas.

## Entradas y reproducción

Test nuevo: `WalletAdditionalPrivacyCharacterizationUITest`. Carga el shell de producción con Stage y Scene, navega a la operación y usa los controles FXML reales. Despliega el TitledPane del resultado, aplica CSS y layout, ejecuta el manejador y comprueba una publicación y un informe no vacío. Genera claves EC exclusivamente de laboratorio.

- mdoc: documento IssuerSigned válido generado localmente con MdocOperations.issue, certificado sintético y namespace `org.iso.18013.5.1`. Su elemento `lab_private_fixture` lleva un JWK privado o un PEM privado. Se pega el documento hexadecimal en `mdocVerifyInputArea` y se pulsa Inspect sin clave externa. No se ejecuta Issue en la UI: así no se atribuye a Inspect una receta de emisión anterior.
- Status List: token local con lista `[0,1,0,1]` y URI `.invalid`, firmado ES256. Su header tiene un miembro adicional `lab_private_fixture` con JWK privado o PEM privado. El test verifica la firma independientemente antes de pegarlo en `statusListTokenArea` y ejecutar Describe. Ninguna URL se resuelve.

Se rastrea `d` para JWK y el cuerpo Base64 del PKCS#8 para PEM. No son nombres de campos ni valores internos: se observa el TextArea real y se abren las superficies del shell.

## Hallazgos confirmados

Cada fila representa dos fallos, uno en MASKED y otro en REDACTED.

| Operación | Material | Control visible | Otras superficies con el material |
|---|---|---|---|
| handleMdocInspect | JWK privado | mdocVerifyOutputArea | Resultado del shell, Shelf, contentArea del visor expandido |
| handleMdocInspect | PEM privado | mdocVerifyOutputArea | Ninguna de las otras superficies auditadas |
| handleStatusListDescribe | JWK privado | statusListResolveOutputArea | Resultado del shell, Shelf, contentArea del visor expandido |
| handleStatusListDescribe | PEM privado | statusListResolveOutputArea | Ninguna de las otras superficies auditadas |

Inspector, barra de estado, HistoryManager y su `history.json` no exponen los marcadores de estas entradas en la reproducción ampliada. Esto no certifica toda su privacidad: la reproducción SD-JWT original sigue exponiendo `sdJwtClaimsArea` en las recetas, como recoge wallet-0-correction-validation.md. Los cuatro casos FULL_LAB de esta ampliación pasan y conservan los informes originales con material privado.

## Ejecución y preparación

```sh
mvn -o -q test -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dtest=WalletAdditionalPrivacyCharacterizationUITest
```

Ejecución confirmatoria: **1 XML / 12 pruebas / 8 fallos / 0 errores / 0 omitidas / exit 1**. `target/surefire-reports` se borró antes; solo se cuentan XML de esta ejecución. JVM Maven/Surefire: OpenJDK 25 en macOS, compilación release 17; no se afirma ejecución en Linux/Java 17.

La primera preparación dio 12 pruebas / 10 fallos / 0 errores / 0 omitidas / exit 1: cuatro fugas mdoc y seis fallos de fixture en Status List (UnsupportedOperationException al añadir un customParam sobre una copia del header con mapa inmutable). Se corrigió únicamente la construcción del token del test, con un Builder nuevo que conserva algoritmo y typ. No se tocaron aserciones ni producción para hacer avanzar ese caso. La segunda ejecución llega a ambos manejadores y produce los ocho fallos de privacidad de la tabla. No se fija el texto de esa excepción ni un digest de una auditoría fallida.

## Parada

La instrucción “Si alguno expone el material, no lo corrijas: para y documenta” se aplica a ambos módulos. Por tanto no se ejecutan G1/G2/G3 de fase 0 ni las fases de extracción 1–3. No hay extracción que retirar ni se invoca la excepción GC.

El test hereda la fixture que restaura settings, preferencias y Shelf; usa HistoryManager temporal y restaura entradas/ruta de OperationHistory incluso con aserciones fallidas. No modifica tests anteriores. Persiste en los logs la advertencia de traducción preexistente `module.process.category.wallet / eidas`; no se corrige.
