# Caracterización previa de Keys, fase 3

Producción sin modificar: `a548f8b`. FXML real mediante Fxml.loader; test.mode y user.home=target/test-home configurados por Surefire.

Maven offline, exit 0 en los grupos de caracterización y estilo/auditoría. La ejecución conjunta anterior de las fases 1/2 y servicios pasó esos tests; tras corregir supuestos del test nuevo, se repitió el nuevo grupo antes de mover producción.

```text
com.cryptocarver.ui.KeysSplit3CharacterizationUITest: tests=31, failures=0, errors=0, skipped=0, time=16.62s
com.cryptocarver.model.KeyLabSaveGeneratedTest: tests=5, failures=0, errors=0, skipped=0, time=0.015s
com.cryptocarver.service.KeystoreWorkbenchServiceTest: tests=7, failures=0, errors=0, skipped=0, time=0.245s
com.cryptocarver.ui.ComputedStyleSnapshotTool: tests=1, failures=0, errors=0, skipped=0, time=10.416s
com.cryptocarver.ui.ClippedTextAuditTool: tests=1, failures=0, errors=0, skipped=0, time=8.286s
com.cryptocarver.ui.KeysSplitCharacterizationUITest: tests=23, failures=0, errors=0, skipped=0, time=21.493s
com.cryptocarver.ui.ShelfStaleSnapshotUITest: tests=3, failures=0, errors=0, skipped=0, time=0.673s
com.cryptocarver.crypto.hsm.KeyLabTest: tests=11, failures=0, errors=0, skipped=0, time=0.037s
com.cryptocarver.ui.KeysSplit2CharacterizationUITest: tests=31, failures=0, errors=0, skipped=0, time=22.012s
```

31 casos nuevos: los tres formatos de pagos, vector público y round trips disponibles, claves equivocadas y sincronización cabecera/selectores; generación AES/TDES, KCV, XOR y longitud inválida; alta DES y rechazo actual de AES en Key Lab, búsqueda, estado, detalle, importación, visibilidad FULL_LAB/MASKED/REDACTED y manifiesto sin valores; PKCS12 generado, extracción opt-in, paneles incluidos y ausencia de token; certificado, CSR, CRL y validación individual; configuración portable, idioma y redacción de histórico. Shelf y publicaciones a histórico/inspector se complementan con las fases 1/2 y ShelfStaleSnapshotUITest.

Las correcciones durante la elaboración del test nuevo no cambian producción: Variant LMK no autentica y una clave equivocada produce otro KCV; el tercer formato publica la autenticación fallida; Key Block LMK puede fallar al leer la longitud descifrada. Generate Certificate y CSR generan pares propios y no sustituyen el último par de generación asimétrica. Key Lab no acepta el selector AES como tipo de generación (se caracteriza el error existente y se usa DES para el alta válida). UiStateSnapshot excluye keyLabImportBytesField y conserva el CN como parámetro público; se comprueban ambas reglas.

Exportación/importación de manifiestos ejercitada por el proveedor usado por los handlers; no se automatizan los diálogos nativos de selección de fichero. Sin token físico y sin prueba manual.
