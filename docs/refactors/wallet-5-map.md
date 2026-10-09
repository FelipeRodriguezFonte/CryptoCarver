# Fase 5 — CBOR (encargo 80)

Base de la fase: `b7414ea`.

## Qué se mueve a `WalletCborCoordinator`

| Manejador | Campos FXML | Fachada |
|---|---|---|
| `handleCborInspect` | `cborInputArea`, `cborViewCombo`, `cborOutputArea` | `CborInspector.parseHex`, `tree`, `diagnostic`, `summary` |
| `handleCborToJson` | `cborInputArea`, `cborOutputArea` | `CborInspector.toJson` |
| `handleCborFromJson` | `cborJsonArea`, `cborFromJsonOutputArea` | `CborInspector.fromJson`, `DataConverter.bytesToHex` |

Extiende `WalletCoordinatorBase` (ver `wallet-4-map.md`). La política de material privado viaja con cada manejador: en Inspect se comprueba además la representación JSON del CBOR; en JSON to CBOR, el JSON de entrada.

## Qué se queda en el controlador

Los tres puntos de entrada `@FXML` como delegados, y `initialize`, que sigue rellenando `cborViewCombo` con `tree`, `diagnostic` y `summary`.

## Claves de idioma

`module.wallet.cborRequired`, `jsonRequired`, `status.inspected` y `status.converted` se mueven con su lógica. Sin propietarios que reasignar en tests de presencia en fuente.

## Caracterización

`WalletCborCharacterizationUITest`, SHA-256 `a2e1669cb0691e6038045a4e1792c422f8c6cb49312fe269bb9cea0b16e9a95a`, en inglés y español y en los tres perfiles: validaciones, las tres vistas de Inspect (y la vista por defecto con el combo sin valor), conversión en ambos sentidos y entradas no válidas. Aquí los resultados son deterministas y entran completos en la transcripción, además de compararse con la fachada.
