# CryptoCarver: laboratorio frente a producción

CryptoCarver está diseñado para desarrollo, diagnóstico, formación y reproducción
de pruebas criptográficas. No es un HSM, un gestor de secretos ni una plataforma
certificada de firma o pagos.

## Qué facilita deliberadamente el modo laboratorio

- Mostrar claves públicas, privadas y secretos compartidos para estudiar el flujo.
- Exportar resultados y recetas, con una política de visibilidad seleccionable.
- Usar un almacén HSM simulado, sólo de sesión y sin garantía de hardware.
- Ejecutar vectores didácticos de pagos, XAdES/TSA, JOSE y post-cuántica.
- Conectar a TSA, truststores y perfiles locales configurados por el usuario.

Estas decisiones son útiles para inspección; implican que los datos mostrados,
copiados, guardados o presentes en el histórico deben tratarse como sensibles.

## No utilizar para

- Custodiar material de producción, PINs reales, KBPK/BDK operativos o certificados
  de clientes.
- Declarar conformidad PCI DSS, EMVCo, PSD2, eIDAS u otra certificación.
- Sustituir un HSM, controles de acceso, auditoría centralizada, rotación de claves
  o segregación de funciones.
- Firmar o sellar documentos de producción sin un perfil de confianza, una política
  de validación y una revisión independiente adecuadas.
- Inferir compatibilidad de terceros sólo porque una operación local haya tenido
  éxito: valida siempre con la contraparte y sus vectores.

## Recomendaciones operativas

1. Usa valores sintéticos y truststores de prueba.
2. Elige `REDACTED` al exportar recetas o históricos fuera de tu equipo.
3. Borra los resultados sensibles de pantalla, del historial y de los ficheros
   temporales al terminar una sesión.
4. Verifica la versión, el checksum y el SBOM de cada entrega antes de instalarla.
5. Para un caso real, traslada el diseño a componentes aprobados por tu organización:
   HSM/tokens, almacén de secretos, logging controlado y revisión de seguridad.
