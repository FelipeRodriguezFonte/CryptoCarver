# Laboratorio OpenPGP

El panel OpenPGP de Java genera pares RSA de 3072 bits y procesa texto con armadura, archivos binarios y mensajes con firma separada, adjunta o en claro. La verificación de firma separada requiere los bytes exactos originales, la firma independiente y la clave pública. La verificación adjunta y la de firma en claro leen el mensaje firmado completo. La inspección de firma muestra metadatos públicos; no verifica integridad ni Web-of-Trust.

La clave secreta y la frase de paso se usan solo en acciones explícitas de firma o descifrado. El material público y secreto se puede pegar, cargar, guardar, inspeccionar o elegir de la Shelf local. Las operaciones con archivos usan selectores explícitos y tienen un límite de 16 MiB. La ayuda solo indica si los campos contienen texto; no analiza claves ni lee cargas útiles.

La comprobación de GnuPG, su verificación y la prueba aislada de interoperabilidad son acciones externas opcionales. Si GnuPG no está disponible, la implementación Java de OpenPGP sigue funcionando. La generación de claves, el acceso a archivos, el trabajo criptográfico y la ejecución externa solo ocurren al elegir su acción correspondiente.
