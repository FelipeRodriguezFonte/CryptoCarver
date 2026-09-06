# Process Designer — Análisis de carencias antes de la Fase 5

Documento de diagnóstico previo a los bloques `antigravity-process-designer-phase-5a` y
`-5b`. Recoge el estado real del módulo en `main` (commit base `ec966aa`), con evidencia
en archivo y línea, y fija los criterios con los que Luna/Codex auditarán la entrega.

No es una especificación: es el material de partida que justifica lo que se pide.

---

## 1. Estado actual verificado

| Elemento | Ubicación | Estado |
|---|---|---|
| Panel del diseñador | `src/main/resources/fxml/process_designer.fxml` | `TitledPane` colapsado dentro de un `Accordion` |
| Montaje | `src/main/resources/fxml/generic.fxml:7` | `fx:include` dentro del módulo **Generic** |
| Lienzo | `process_designer.fxml:102` | `Pane` fijo `minWidth=760 prefWidth=760 prefHeight=310` |
| Contenedor del lienzo | `process_designer.fxml:101` | `ScrollPane fitToWidth="true" fitToHeight="false"` |
| Inspector | `process_designer.fxml:104-260` | `VBox` con **27** grupos de campos codificados a mano |
| Controlador | `src/main/java/com/cryptocarver/ui/ProcessDesignerController.java` | 1 803 líneas, **96** campos `@FXML` |
| Motor | `src/main/java/com/cryptocarver/model/process/ProcessEngine.java` | 8 handlers registrados (`ProcessEngine.java:19-27`) |
| Tipos de nodo | handlers en `model/process/handlers/` | **30** tipos |
| Operaciones del producto | `src/main/java/com/cryptocarver/model/OperationRegistry.java` | **80** operaciones registradas |

Línea base de compilación y pruebas comprobada en local con Temurin 21:

```
mvn -o test-compile            -> OK
mvn -o test -Dtest='Process*,FxmlQualityGateTest,FxmlContractTest'
   -> Tests run: 73, Failures: 0, Errors: 0, Skipped: 0 / BUILD SUCCESS
```

---

## 2. Carencias de UX del lienzo

### 2.1 El lienzo no puede crecer — defecto funcional, no estético

`workflowCanvas` es un `Pane` con `prefHeight=310` que **nunca se redimensiona**: en
`ProcessDesignerController.java` no existe ninguna llamada a `setPrefHeight` /
`setPrefWidth` sobre el lienzo (la única aparición de esos métodos, líneas 458 y 462,
afecta al inspector).

Consecuencias comprobables:

- `fitToWidth="true"` fuerza el ancho del contenido al del viewport, por lo que **el
  desplazamiento horizontal está desactivado**. Los presets ya colocan nodos en `x=1000`
  (`ProcessDesignerController.java:1058`, preset AES-GCM): esos nodos quedan fuera de
  alcance en cuanto la ventana es estrecha.
- `fitToHeight="false"` con `prefHeight=310` deja el desplazamiento vertical limitado a
  310 px. El manejador de arrastre (`:1204`) sólo acota por abajo con `Math.max(0, …)`:
  un nodo arrastrado a `y=800` **deja de ser visible y deja de ser seleccionable**, y
  sigue guardándose en el `.cfprocess.json`.

Esto no es «poco espacio»: es pérdida de acceso al trabajo del usuario.

### 2.2 No hay zoom, ni ajuste al contenido, ni paneo real

No existe ninguna transformación `Scale` sobre el lienzo. `pannable="true"` es inocuo
porque el contenido nunca excede el viewport. Un proceso de 10 nodos ya no cabe.

### 2.3 El arrastre está calculado en coordenadas de escena

`ProcessDesignerController.java:1203-1204` guarda `sceneX - layoutX` en el `MOUSE_PRESSED`
y aplica `sceneX - offset` en el `MOUSE_DRAGGED`. Funciona por accidente mientras la
escala sea exactamente 1 y el lienzo no esté desplazado durante el gesto. **En cuanto se
introduzca zoom, el nodo se desplazará a una velocidad distinta a la del puntero.** La
conversión correcta es `workflowCanvas.sceneToLocal(...)`.

### 2.4 Cada píxel de arrastre reconstruye el grafo entero

`MOUSE_DRAGGED` llama a `redraw()` (`:1161`), que:

1. vacía `workflowCanvas.getChildren()` y el mapa `views`;
2. ejecuta `ProcessEngine.validate(toDefinition())` — orden topológico, detección de
   ciclos y negociación de representaciones — **una vez por evento de ratón**;
3. recrea todos los `StackPane` y todas las conexiones.

Con 40 nodos esto es inaceptable, y además descarta la vista sobre la que el usuario
está arrastrando en mitad del gesto.

### 2.5 El inspector puede quedar recortado

`nodeInspector` (`process_designer.fxml:104`) es un `VBox` sin `ScrollPane`. Con un nodo
WSS seleccionado se muestran simultáneamente grupos de keystore, credenciales, timestamp
y puertos; los botones de acción («Save block settings», «Connect to…», «Delete
selected») son los últimos hijos y **desaparecen por debajo del borde** en cuanto la
ventana no es alta. En una pantalla de portátil, con el diseñador ocupando media
pantalla dentro del acordeón, esto ocurre de forma rutinaria.

### 2.6 Conectar nodos no se parece a conectar nodos

El modelo es «selecciona dos bloques y pulsa Connect» (`:522`, `:481`). Los puertos se
dibujan como etiquetas de texto no interactivas (`:1180-1189`). No hay arrastre
puerto-a-puerto, ni realimentación de compatibilidad durante el gesto, ni tooltip con las
representaciones aceptadas. El usuario descubre que una conexión es inválida **después**
de crearla.

### 2.7 Ausencias de productividad

No hay deshacer/rehacer, ni selección múltiple por rectángulo, ni duplicar nodo, ni
copiar/pegar, ni auto-organizar, ni borrar con la tecla `Supr`, ni ajuste a rejilla, ni
minimapa.

---

## 3. Carencia estructural: el inspector no escala

Añadir **un** tipo de nodo hoy obliga a tocar, como mínimo, seis lugares:

1. un `ProcessNodeHandler` (o ampliar uno) y registrarlo en `ProcessEngine.java:19`;
2. `process_designer.fxml`: un `MenuItem` en la paleta y N grupos de campos nuevos;
3. `ProcessDesignerController`: `handleAddX()`, valores por defecto en `addNode()`
   (`:1113-1156`), una rama de visibilidad en `select()` y otra en
   `saveSelectedNodeSettings()`;
4. `ModuleTextCatalog.processDesigner()` más `messages.properties` y
   `messages_es.properties`;
5. `docs/OPERATIONS_CATALOG.md`;
6. pruebas.

El coste está concentrado en el punto 3. Medido sobre el código actual:

- `select(ProcessDefinition.Node)` ocupa **281 líneas** (`:1207-1487`) y es una cadena de
  `boolean tipo = "X".equals(node.type)` seguida de pares
  `setVisible()` / `setManaged()` por cada uno de los 27 grupos.
- `saveSelectedNodeSettings()` (`:564-657`) repite exactamente la misma cadena de
  comparaciones para la escritura.

Cualquier nodo nuevo alarga las dos cadenas y multiplica los estados posibles. Con las
~70 operaciones que faltan por cubrir, este diseño produce un controlador de varios miles
de líneas imposible de auditar y con una superficie de regresión que ninguna prueba
razonable cubre.

**Conclusión: la expansión del catálogo no puede empezar antes de sustituir el inspector
codificado a mano por un inspector dirigido por esquema.** Ese es el motivo de que la
Fase 5A sea puramente arquitectónica y de UX, sin operaciones nuevas.

---

## 4. Carencia funcional: cobertura del catálogo

El Process Designer expone 30 tipos de nodo frente a 80 operaciones registradas en
`OperationRegistry`. Familias completas de CryptoCarver **no son alcanzables desde el
lienzo**:

| Familia | Fachada existente | Nodos hoy |
|---|---|---|
| Pagos (PIN, CVV, PVV, DUKPT, EMV, Track 2) | `PaymentOperations`, `EMVOperations`, `AesDukpt`, `EmvTlv` | ninguno |
| TR-31 / TR-34 | `TR31Operations`, `TR34Operations` | ninguno |
| KCV, paridad, componentes XOR | `KeyOperations` | ninguno |
| KDF distintos de PBKDF2 (HKDF, SP800-108, X9.63, scrypt, Argon2) | `KeyDerivation` | ninguno |
| AES Key Wrap RFC 3394 / 5649 | `KeyWrapOperations` | ninguno |
| JOSE (JWS/JWE/JWT) | `JOSEService` | ninguno |
| COSE (Sign1/Mac0/Encrypt0) | `COSEOperations` | ninguno |
| CMS / PKCS#7, CAdES | `CMSOperations` | ninguno |
| XAdES, PAdES, ASiC | `XMLSignatureOperations`, `PadesOperations`, `AsicOperations` | ninguno |
| OpenPGP | `OpenPgpOperations` | ninguno |
| Post-cuántica (firma y KEM) | `PostQuantumOperations` | ninguno |
| Certificados (parse, validar, CSR, emitir) | `CertificateGenerator`, `CertificateAuthorityOperations` | ninguno |
| ICSF / CCA | `crypto/icsf` | ninguno |
| Códecs Base32 / Base58 / Base58Check / EBCDIC / compresión / ASN.1 | `codec/impl`, `EBCDICConverter`, `CompressionCodec`, `asn1` | ninguno |
| Utilidades (dígitos de control, aritmética modular, UUID, estadística de bytes) | `CheckDigitCalculator`, `ModularArithmetic`, `UUIDGenerator`, `ByteStatistics` | ninguno |

También faltan las piezas de **fontanería** sin las cuales muchas de las anteriores no se
pueden encadenar: concatenar, recortar, rellenar, XOR entre dos ramas y —muy
importante para nuestro propio proceso de revisión— un nodo de **aserción** que compare
el resultado con un valor esperado en tiempo constante y detenga el proceso si difiere.
Con ese nodo, un `.cfprocess.json` se convierte en una prueba ejecutable y auditable.

Nada de esto requiere criptografía nueva: **todas las fachadas ya existen y están
probadas**. La Fase 5B es integración, no implementación.

---

## 5. Riesgos que la entrega debe respetar

Estos invariantes están vigentes hoy y **no** pueden regresarse:

1. **Los secretos no se persisten.** `ProcessDefinitionCodec` no debe escribir claves,
   contraseñas, PIN ni PAN. Al reabrir un proceso, los campos sensibles vuelven vacíos.
2. **Sin red.** Ningún handler abre sockets. Esto excluye OCSP/CRL en línea y sellado
   RFC 3161 contra TSA remota mientras no exista una fase específica.
3. **Contrato de representaciones.** `FlowValue`, `Representation` y la negociación de
   puertos de `ProcessEngine.validate()` son estables; los nodos nuevos se adaptan a
   ellos, no al revés.
4. **La validación previa y el handler aplican la misma regla.** `ProcessValidator`
   delega en `handler.validateConfiguration(node)` (`ProcessValidator.java:200`); un nodo
   que valide distinto en preflight y en ejecución es un fallo bloqueante.
5. **i18n obligatorio.** Todo literal nuevo pasa por `ModuleTextCatalog` y por
   `messages.properties` + `messages_es.properties`. Lo vigilan `ModuleI18nLiveUITest` e
   `IncludedPaneI18nUITest`.
6. **Controles ocultos también desgestionados**: `visible=false` **y** `managed=false`
   (regla de `docs/LUNA_QA_PROTOCOL.md`).
7. **Perfil de visibilidad de secretos.** Los resultados y la tabla de ejecución respetan
   `SecretVisibilityProfile` (`FULL_LAB` / `MASKED` / `REDACTED`).

---

## 6. Plan propuesto

| Bloque | Contenido | Entregable |
|---|---|---|
| **5A** | Lienzo expandible con zoom, ventana propia, paleta buscable, inspector dirigido por esquema, rendimiento, deshacer/rehacer | `docs/antigravity-process-designer-phase-5a-workbench-canvas.md` |
| **5B.1** | Fontanería y representación (concat, slice, pad, XOR, aserción, Base32/58, EBCDIC, compresión, ASN.1, utilidades) | `docs/antigravity-process-designer-phase-5b-operation-catalog.md` |
| **5B.2** | Claves y pagos (KCV, paridad, XOR de componentes, KDF, Key Wrap, TR-31, ICSF, PIN, CVV, PVV, DUKPT, EMV) | mismo documento, ola 2 |
| **5B.3** | Sobres y firmas (JOSE, COSE, CMS, XAdES, PAdES, OpenPGP, PQC, certificados) | mismo documento, ola 3 |

**5A es requisito previo de 5B.** Enviar 5B antes de que 5A esté aprobada obliga a
Antigravity a ampliar la cadena de `if` de `select()`, que es justo lo que queremos
eliminar.

Cada ola de 5B se envía, se audita y se integra por separado. Una ola no se declara
completa si falta cualquiera de sus nodos: la prueba de cobertura del catálogo
(§5B, `NodeCatalogCoverageTest`) lo impide de forma mecánica.

---

## 7. Cómo lo vamos a validar

### 7.1 Puertas automáticas que deben seguir en verde

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # el proyecto exige Java 17+
mvn -o test
mvn -o -DrunUiTests=true -Dtest.mode=true test
git diff --check
```

En CI, la suite JavaFX corre bajo Xvfb mediante `scripts/run-ui-tests.sh`
(`.github/workflows/ui-tests.yml`).

### 7.2 Señales de entrega correcta

- El diff toca handlers, descriptores, FXML, i18n y pruebas — y **no** añade ramas nuevas
  a `select()` ni a `saveSelectedNodeSettings()`.
- Cada nodo nuevo delega en una fachada existente de `com.cryptocarver.crypto`.
- Cada nodo nuevo trae prueba positiva con vector conocido, prueba de rechazo seguro y
  prueba de integración en el motor.
- El `.cfprocess.json` guardado tras configurar todos los campos sensibles del catálogo
  no contiene ninguno de ellos.

### 7.3 Señales de alarma para el revisor

- Criptografía reimplementada dentro de un handler en lugar de llamar a la fachada.
- Literales en inglés incrustados en FXML o en el controlador.
- `setVisible(false)` sin `setManaged(false)`.
- `ProcessEngine.validate()` invocado dentro de un manejador de `MOUSE_DRAGGED`.
- Pruebas que sólo comprueban que un control existe (no prueban comportamiento; regla
  explícita de `docs/LUNA_QA_PROTOCOL.md`).
- Cualquier `Socket`, `URLConnection` o `HttpClient` nuevo bajo `model/process`.
- Cambios en `FlowValue`, `Representation` o la firma de `ProcessNodeHandler.execute`
  que no estén pedidos.

### 7.4 Procedimiento de auditoría

Se aplica `docs/LUNA_QA_PROTOCOL.md` sin cambios: veredicto, matriz de criterios con
evidencia archivo/línea, hallazgos clasificados (Bloqueante / Importante / Menor),
comandos realmente ejecutados y prompt de corrección mínimo si procede.
