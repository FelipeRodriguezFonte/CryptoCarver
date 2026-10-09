# Fase 4 — fallos antes de fijar la caracterización

Ninguno: `WalletTrustCharacterizationUITest` pasó a la primera sobre `1b2ac58`, sin extraer.

Comportamiento previo que la transcripción deja fijado, sin corregir:

- **Hallazgo:** `handleTrustedListVerify` sobre una lista sin firma publica el resultado con estado "Verified" (el informe dice `signature: INVALID`). El estado no distingue firma válida de no válida.
