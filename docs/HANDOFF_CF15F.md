# Implementación CF-15F: CMS Inspector

## Alcance
Se ha implementado el bloque CF-15F correspondiente a la herramienta CMS Inspector, permitiendo la visualización detallada de estructuras CMS/PKCS#7 (EnvelopedData, SignedData) sin exponer datos sensibles, así como la verificación de confianza mediante un truststore opcional y la exportación de resultados a formato JSON.

## Cambios Realizados y Correcciones

1. **Core Criptográfico (`CmsInspector`, `CmsInspectionReport`)**
   - Agregada la validación "short-circuit" para PKIX: Si la firma o el certificado del firmante no son válidos (o no se pudieron evaluar), o si falta el contenido (`ContentState.DETACHED`), la cadena de confianza (`Trust Chain`) reporta `NOT_EVALUATED` en lugar de evaluar una cadena inválida y emitir falsos positivos/negativos.
   - Habilitado el soporte para la construcción de rutas de certificación (`CertPathBuilder`) incorporando los certificados intermedios presentes en la estructura CMS al `CertStore` para su evaluación PKIX.
   - Añadida extracción del algoritmo de cifrado en contenedores `EnvelopedData` (`contentEncryptionAlgorithm`), además de atributos firmados y no firmados.

2. **Capa UI (`CmsInspectorController`)**
   - Límite estricto de **16 MiB** verificado mediante `File.length()` antes de proceder a la lectura en memoria mediante `Files.readAllBytes()`. Esto previene desbordamientos de memoria (`OutOfMemoryError`) en el lado del cliente y se aplica tanto al contenedor CMS como al contenido *detached*.
   - Limpieza segura de credenciales: La contraseña del truststore (si se provee) se borra rellenando el arreglo de caracteres con nulos (`\0`) dentro del bloque `finally`, independientemente de si la carga del `KeyStore` tiene éxito o falla.
   - Generación de informe en JSON (haciendo uso de Gson) enviado atómicamente a `OperationResult.output`.

3. **Registro y Contrato (`OperationRegistry`)**
   - Registrada la operación `op_cms_inspector` bajo la categoría "Certificates".
   - Regenerado el catálogo dinámico `OPERATIONS_CATALOG.md`.

4. **Pruebas y Validaciones**
   - `CmsInspectorTest`: Añadidas pruebas negativas y positivas para la evaluación PKIX (`testTrustChainShortCircuit`, `testIntermediateCertPathBuild`), validando que el inspector no emita estados de validación engañosos ante firmas manipuladas.
   - `CmsInspectorControllerTest`: Verificada la ausencia de fugas de contraseñas (`testAtomicPublicationAndNoPasswordLeak`).
   - `ModernMainControllerFxmlStaticTest`: Se agregó `cms_inspector.fxml` para garantizar la correspondencia de los inyectores FXML con el controlador (`CmsInspectorController`).

## Remates de Auditoría Codex Aplicados
- **Navegación CMS Inspector y Test Robustos**: Añadida la ruta en `ModernMainController` (`handleItemSelected`). Se solucionó un problema de asunción posicional en `expandCertificatesAccordionPane`, instruyéndolo a localizar explícitamente el nodo `Accordion` en la lista de hijos, además de comandar expansiones de forma segura e independiente cuando se trate directamente de `cmsInspector` inyectado. Todo está validado positivamente bajo el test UI aislado (`testCmsInspectorNavigation`).
- **Borrado Seguro de Password**: Sustituido el uso del carácter `'0'` por el carácter nulo (`'\0'`) en `Arrays.fill` durante la limpieza de `char[]` de `truststorePasswordField`.
- **Exportación Dual (JSON y Markdown)**: Actualizado `handleExportReport` para permitir exportar el reporte en formato nativo JSON estructurado y también en formato texto/Markdown legible para el usuario.
- **Inmutabilidad Robusta**: Refactorizados `CmsInspectionReport` y sus submodelos para hacer copias defensivas explícitas de las colecciones `Map` y `List` (`Map.copyOf`, `List.copyOf`), acompañados de nuevos test de integridad (`CmsInspectionReportTest.java`) para asegurar la prohibición de escrituras mutables.
- **Limpieza Absoluta**: Validado y superado un pipeline limpio de `mvn -q test`, tests de UI (`ModernMainControllerUITest#testCmsInspectorNavigation`), `xmllint --noout src/main/resources/fxml/*.fxml` y `git diff --check`. Cero errores de estilo o trailing whitespace.

## Estado de Ejecución
Todos los tests (unitarios e interfaz), incluyendo `mvn -q test`, `xmllint --noout src/main/resources/fxml/*.fxml`, así como la validación de espacios con `git diff --check`, han pasado exitosamente. No se detectaron fugas de contraseñas ni inconsistencias con el modelo.

## Próximos Pasos (Pendiente de Autorización)
- Iniciar el bloque CF-14D correspondiente al Módulo de Historial y Operaciones Recientes, absteniéndose hasta la confirmación de la auditoría.

## Limitaciones Conocidas
- BouncyCastle CMS API exige extraer las estructuras subyacentes manualmente para ciertas operaciones. Esto ha sido abstraído cuidadosamente dentro de `CmsInspector`.
- El truststore solo admite JKS o PKCS12 (formatos nativos de Java) mediante contraseña; las anclas de confianza se cargan de una sola pasada.
