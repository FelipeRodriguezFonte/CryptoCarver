# Fase 2 — fallos antes de fijar la caracterización

Ninguno. `XMLSignaturePhase2CharacterizationUITest` pasó a la primera sobre `f85ffd2` (controlador sin extraer la fase 2) y su transcripción se fijó sin ajustes.

Comportamientos previos que la transcripción deja fijados tal como están, sin corregir:

- Con contraseña incorrecta, `handleLoadXMLKeys` muestra "Key Load Error" y conserva en el combo los alias de la carga anterior.
- `handleLoadXMLTrustStoreProfile` no hace nada, ni avisa, cuando el perfil seleccionado no existe o no hay selección.
