# Keys split, fase 1: mapa previo

Base: `main` / `51c3f22`. `KeysController`: 6907 líneas. Inventario obtenido con `rg -n` y rangos de métodos delimitados por las llaves de clase. Los números se refieren a la base, no al fichero tras la extracción.

## TR-31

| Método | Inicio | Fin |
|---|---:|---:|
| `fillTR31KeyBlockInput` | 647 | 649 |
| `handleTR31Clear` | 652 | 655 |
| `handleTR31Reset` | 658 | 666 |
| `clearTR31Fields` | 668 | 681 |
| `showTR31Validation` | 683 | 689 |
| `showTR31Validation` | 691 | 699 |
| `logTR31Failure` | 706 | 708 |
| `initializeTR31` | 3249 | 3275 |
| `setupTR31Combos` | 3280 | 3330 |
| `handleTR31Export` | 3335 | 3480 |
| `handleTR31Import` | 3485 | 3552 |
| `handleTR31ParseHeader` | 3557 | 3640 |
| `loadProfile` | 5904 | 5943 |

### Campos inyectados

| Campo | Tipo | Línea |
|---|---|---:|
| `tr31KbpkExportField` | `TextField` | 3217 |
| `tr31KeyToWrapField` | `TextField` | 3219 |
| `tr31UsageCombo` | `ComboBox<String>` | 3221 |
| `tr31AlgorithmCombo` | `ComboBox<String>` | 3223 |
| `tr31ModeCombo` | `ComboBox<String>` | 3225 |
| `tr31VersionCombo` | `ComboBox<String>` | 3227 |
| `tr31ExportabilityCombo` | `ComboBox<String>` | 3229 |
| `tr31OptionalBlocksField` | `TextField` | 3231 |
| `tr31OptionalBlockCombo` | `ComboBox<String>` | 3233 |
| `tr31ExportResultArea` | `TextArea` | 3234 |
| `tr31KbpkImportField` | `TextField` | 3237 |
| `tr31KeyBlockField` | `TextArea` | 3239 |
| `tr31KeyLengthField` | `TextField` | 3241 |
| `tr31ImportResultArea` | `TextArea` | 3243 |

Handlers en `keys.fxml`: `handleTR31Clear`, `handleTR31Reset`, `handleTR31Export`, `handleTR31Import`, `handleTR31ParseHeader`.

No comparte controles con los otros dos bloques. Los campos permanecen en la fachada para conservar inyección, claves de instantáneas y validación por fx:id.

## Intercambio RSA

| Método | Inicio | Fin |
|---|---:|---:|
| `initializeRsaKexControls` | 3663 | 3672 |
| `handleRsaKexEnvelopeToggle` | 3675 | 3681 |
| `rsaKexProfileFromCombo` | 3683 | 3690 |
| `handleRsaKexExport` | 3696 | 3791 |
| `handleRsaKexImport` | 3797 | 3883 |
| `handleRsaKexClear` | 3886 | 3889 |
| `handleRsaKexReset` | 3892 | 3902 |
| `clearRsaKexFields` | 3904 | 3913 |
| `showRsaKexValidation` | 3915 | 3923 |
| `logRsaKexFailure` | 3925 | 3927 |

### Campos inyectados

| Campo | Tipo | Línea |
|---|---|---:|
| `rsaKexRecipientPemArea` | `TextArea` | 3649 |
| `rsaKexKeyToWrapField` | `TextField` | 3650 |
| `rsaKexExportProfileCombo` | `ComboBox<String>` | 3651 |
| `rsaKexIncludeEnvelopeCheck` | `CheckBox` | 3652 |
| `rsaKexEnvelopeFieldsBox` | `javafx.scene.layout.HBox` | 3653 |
| `rsaKexKidField` | `TextField` | 3654 |
| `rsaKexKeyVersionField` | `TextField` | 3655 |
| `rsaKexExportResultArea` | `TextArea` | 3656 |
| `rsaKexPrivateKeyArea` | `TextArea` | 3658 |
| `rsaKexWrappedDataArea` | `TextArea` | 3659 |
| `rsaKexImportProfileCombo` | `ComboBox<String>` | 3660 |
| `rsaKexImportResultArea` | `TextArea` | 3661 |

Handlers en `keys.fxml`: `handleRsaKexEnvelopeToggle`, `handleRsaKexExport`, `handleRsaKexImport`, `handleRsaKexClear`, `handleRsaKexReset`.

No comparte controles con los otros dos bloques. Los campos permanecen en la fachada para conservar inyección, claves de instantáneas y validación por fx:id.

## TR-34

| Método | Inicio | Fin |
|---|---:|---:|
| `parseCertificatePem` | 3955 | 3959 |
| `handleTr34Distribute` | 3965 | 4050 |
| `handleTr34Receive` | 4056 | 4143 |
| `handleTr34GenerateChallenge` | 4147 | 4152 |
| `handleTr34Clear` | 4155 | 4158 |
| `handleTr34Reset` | 4161 | 4165 |
| `clearTr34Fields` | 4167 | 4180 |
| `tr34KcvIfEligible` | 4183 | 4192 |
| `showTr34Validation` | 4194 | 4202 |
| `logTr34Failure` | 4204 | 4206 |

### Campos inyectados

| Campo | Tipo | Línea |
|---|---|---:|
| `tr34SenderPrivateKeyArea` | `TextArea` | 3940 |
| `tr34SenderCertArea` | `TextArea` | 3941 |
| `tr34ReceiverCertArea` | `TextArea` | 3942 |
| `tr34KeyToDistributeField` | `TextField` | 3943 |
| `tr34KeyIdField` | `TextField` | 3944 |
| `tr34BindingNonceField` | `TextField` | 3945 |
| `tr34IncludeEnvelopeCheck` | `CheckBox` | 3946 |
| `tr34DistributeResultArea` | `TextArea` | 3947 |
| `tr34ReceiverPrivateKeyArea` | `TextArea` | 3949 |
| `tr34ExpectedSenderCertArea` | `TextArea` | 3950 |
| `tr34DistributedDataArea` | `TextArea` | 3951 |
| `tr34ChallengeNonceField` | `TextField` | 3952 |
| `tr34ReceiveResultArea` | `TextArea` | 3953 |

Handlers en `keys.fxml`: `handleTr34Distribute`, `handleTr34Receive`, `handleTr34GenerateChallenge`, `handleTr34Clear`, `handleTr34Reset`.

No comparte controles con los otros dos bloques. Los campos permanecen en la fachada para conservar inyección, claves de instantáneas y validación por fx:id.

## Dependencias y contratos

- Reportero mutable `mainController: StatusReporter`: estado, `showError(UserFacingError)` y `publish(OperationResult)`. Se resolverá mediante proveedor, incluso después de `init`. `loadSymmetricKeysContent` en ModernMainController llama a `keysController.init(this, callback)` tras materializar el módulo; `connectShellServices` conecta otros módulos. No hay clase ShellServices independiente.
- `updateStatus` y `t` son auxiliares globales compartidos: el primero respeta el fallback de consola y el segundo consulta I18nService al ejecutar. Validación propia de cada bloque redacciona con InlineErrorPresenter; no abre diálogos modales. TR-31 usa fallback TextArea visible/managed; RSA/TR-34 Consumer de texto.
- No usa HsmProvider, Key Lab ni OperationExecutor: estas tres operaciones son síncronas. Las llamadas a crypto se preservan. No hay claves/resultados persistidos en campos no FXML de estos bloques.
- Inspector e histórico se alimentan a través de publish. Shelf y sesiones guardadas consumen el snapshot de la shell; no necesitan referencias de módulo. Los resultados importados RSA/TR-34 se clasifican SECRET; TR-31 import publica sólo longitud y parámetros, no bytes recuperados. Se caracteriza esa asimetría sin corregirla en una extracción.
- UiStateSnapshot visita campos @FXML de KeysController. Las configuraciones portables preservan entradas editables/selectores; captureHistoryRecipe redacta secretos según perfil. Mover la propiedad de los campos rompería nombres y alcance: por eso permanecen.
- ModuleI18n se mantiene en la fachada; etiquetas de FXML y texto de errores se actualizan por I18nService. setupHexValidation compartido permanece en KeysController.
- TR-31 tiene initializeTR31 público (también reconfigura controles externos), fillTR31KeyBlockInput usado desde fillClipboardTarget y loadProfile para perfiles. Mantener delegación y permitir reinicializar la vista.
- parseCertificatePem sólo se usa en TR-34; tr34KcvIfEligible también. rsaKexProfileFromCombo sólo se usa en RSA. Estos auxiliares y formatos/envelopes pasan a lógica sin JavaFX.
- ModernMainController accede mediante getKeysController, rutas UiNavigationRegistry y fachada. Process Designer usa `model/process/handlers/KeyOperationsNodeHandler` y llama directamente a TR31Operations; no llama estos handlers. `LaboratoryMenuCoordinator` carga perfiles por `getKeysController().loadProfile`. La paleta consume rutas, no campos ni coordinadores. Tests actuales Ux21LiveUITest, Ux22HeadlessTest, UiStateSnapshotTest y auditorías dependen de contratos/campos. El test headless de feedback deberá apuntar al propietario extraído, manteniendo sus aserciones.

## Separación prevista

Coordinadores: vista record de controles, proveedor de StatusReporter, pintado, eventos y publicación. Lógica sin JavaFX: normalización y validación de parámetros, perfil RSA, parseo PEM/envelopes, KCV y formateo exacto de informes. Dependencias globales compartidas se pasan como callbacks; no se captura ningún controlador de otro módulo.

## Fase 2: bloques restantes

| Área | Rango base | Campos @FXML declarados en rango |
|---|---|---:|
| Inicialización, generación simétrica, validación, material, almacenes y PKCS#11 | 48–2569 | 220 |
| Generación asimétrica | 2570–3211 | 2 |
| KDF y key wrap | 4207–4793 | 0 |
| CMS / PKCS#7 | 4794–5368 | 0 |
| Cadena de certificados | 5369–5468 | 0 |
| Auxiliares globales, resumen, Shelf, perfiles y Key Lab | 5469–6559 | 0 |
| Variant LMK | 6560–6693 | 8 |
| Key Block LMK | 6694–6759 | 3 |
| Atalla | 6760–6907 | 15 |

Los campos asimétricos, CMS, certificados y Key Lab se declaran mayoritariamente antes de initialize (48–454), incluso cuando su lógica está al final. Hay campos no @FXML recibidos por initializeCMS/initializeCertificateChain y controles de módulos incluidos: requieren inventario propio antes de fase 2. No extraer por rango sin atender sus contratos públicos.

## Comprobación del inventario tras la extracción

Se conservan los **287 campos privados @FXML**, con los mismos nombres, tipos y orden. El número 384 del contexto no es el recuento de campos: el original contiene 385 anotaciones @FXML contando handlers y la anotación cualificada. Los tres bloques usan 39 campos (14 TR-31, 12 RSA, 13 TR-34), todos conservados en la fachada. El inventario de declaraciones de la tabla suma 248 campos para las áreas restantes.
