# Inspección de Crypto Envelope

Inspeccionar lee estructura codificada y metadatos públicos; no recupera una clave ni prueba autenticidad del remitente. Unwrap es explícito y depende del perfil (Raw OAEP, JWE Compact, CMS EnvelopedData en el selector actual), y requiere material privado compatible. La clave recuperada es una salida sensible.
