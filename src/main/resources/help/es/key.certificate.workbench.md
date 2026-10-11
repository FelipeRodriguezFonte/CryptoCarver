# Formatos de claves y certificados

La detección y parsing admiten solo los formatos de KeyCertificateFormatService. La conversión depende del material detectado y destino; la contraseña solo se usa en formatos protegidos. Validate compara el par/cadena según el servicio y no decide confianza. El material privado es sensible.
