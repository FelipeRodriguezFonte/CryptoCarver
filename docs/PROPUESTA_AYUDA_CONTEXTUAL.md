# Propuesta de ayuda contextual por operativa

Fecha: 10 de octubre de 2026. Análisis del código disponible en el workspace; la funcionalidad descrita es una propuesta y no está implementada.

Desarrollo detallado para implementación y revisión: [Encargo para Luna, 96 operativas y 24 casos auxiliares](ENCARGO_LUNA_AYUDA_CONTEXTUAL.md), con [inventario de controles y acciones de los 30 FXML](AYUDA_CONTEXTUAL_INVENTARIO_UI.md).

La recomendación es incorporar un **modo de ayuda contextual**, activable desde cada operativa, con explicaciones por campo y un panel lateral que cambie con la acción y las opciones seleccionadas. La ayuda debe explicar qué hace la operación, cómo introducir sus datos, qué combinaciones admite la aplicación y cómo interpretar el resultado.

El trabajo más importante es definir y mantener el contenido y las reglas de cada operativa. El panel visual puede ser común para toda la aplicación.

## 1. Base existente y carencias comprobadas

| Elemento actual | Qué aporta | Qué falta para esta propuesta |
|---|---|---|
| `OperationRegistry` / `OperationDescriptor` | 96 entradas con ID, título, categoría, propósito breve, estado, sensibilidad y navegación. | Acciones concretas, campos, condiciones, ejemplos y explicación de opciones. Una entrada puede agrupar varias acciones. |
| `OperationInspectorPresenter` | Contexto antes de ejecutar y detalles después de ejecutar. | La orientación se elige principalmente por categoría; no describe individualmente los campos ni sus combinaciones. Varios textos están escritos directamente en inglés. |
| `OperationFormatRegistry` | Contratos y formatos de la barra global para 16 rutas explícitas. | La cobertura es parcial; el fallback «Not applicable» no permite deducir los formatos locales de otras pantallas. |
| `OperationPreflightEngine` / `ReadinessPanelCoordinator` | Comprobaciones previas y enfoque del campo que necesita corrección. | El coordinador integra cinco familias: cifrado simétrico, RSA, hash, firmas y MAC. Los demás módulos tienen otras validaciones. |
| `GuidedStepPolicy` | Cinco recorridos: cifrar, hash, firma, certificado y conversión. | Los textos son generales, están en inglés y no cubren todas las operativas ni todas sus acciones. |
| `LocalHelpService` | Lectura local de Markdown por idioma y búsqueda de una sección por su encabezado. | No hay consumidores en el código principal. Busca `docs/guide_*_extended.md` en el classpath, pero las guías están en `docs/` y el empaquetado actual solo incluye `src/main/resources`. |
| Tutoriales | 25 tutoriales numerados, con ejemplos y material avanzado. | Son una base editorial; hay que extraer fichas breves, comprobar su correspondencia con la implementación y preparar la cobertura en ambos idiomas. |
| Process Designer | `NodeParameter` ya contiene opciones, sensibilidad, `helpKey` y visibilidad condicional; el inspector muestra esa ayuda. | Conectar esa base con la ayuda común y ampliar las explicaciones de nodos y resultados donde falten. |

La búsqueda por texto de un encabezado de Markdown es insuficiente para identificar una operación de forma estable. Los títulos cambian con el idioma y no distinguen necesariamente acciones como importar, exportar, analizar o verificar.

El roadmap ya contempla un centro de ayuda contextual parcialmente completado. Esta propuesta concreta esa línea, partiendo de los componentes existentes.

## 2. Experiencia dentro de la aplicación

1. **Acceso visible:** botón «Ayuda de esta operación» junto al título y entrada en el menú Ayuda. F1 ya abre los atajos de teclado: conservar ese comportamiento en la primera entrega.
2. **Modo de ayuda:** al activarlo aparecen botones `?` junto a los campos configurables. Al pulsarlos se abre su explicación y se destaca el control relacionado. Debe funcionar también con teclado y lector de pantalla.
3. **Panel lateral:** usar el espacio del inspector con dos vistas, «Resultado» y «Ayuda». Mantener el resultado disponible mientras se consulta una explicación. En ventanas estrechas, abrir la misma vista de ayuda en una ventana independiente.
4. **Actualización contextual:** cambiar el contenido al navegar, abrir una pestaña, seleccionar algoritmo, modo, formato, origen de clave o perfil. La ayuda sigue la pantalla activa incluso si el inspector está mostrando un resultado anterior.
5. **Acciones explícitas:** cuando una pantalla contiene Cifrar y Descifrar, o Generar y Verificar, la ayuda debe permitir consultar ambas acciones antes de ejecutar. No depender únicamente del último botón pulsado.
6. **Preferencia persistente:** recordar si el usuario tiene activado el modo de ayuda. Activarlo no cambia los parámetros ni ejecuta operaciones.

El panel tendrá cinco bloques reutilizables:

- **Qué hace:** propósito, caso de uso, entradas y salida esperada.
- **Campos:** significado, formato, unidad, tamaño, obligatoriedad y dependencias.
- **Opciones permitidas:** significado de cada opción, compatibilidad y motivo de un campo deshabilitado.
- **Ejemplo y diagnóstico:** ejemplo de laboratorio, resultado que se debe comprobar y errores frecuentes con enlace al campo afectado.
- **Ampliar:** tutorial local y referencias revisadas, con la versión o perfil al que se refieren.

La explicación breve de un campo debe poder leerse sin abrir un tutorial. Los detalles extensos pueden desplegarse dentro del panel.

## 3. Contrato editorial de cada ficha

Cada operativa necesita una ficha identificada por su ID estable. Dentro de ella se describen sus acciones y variantes. Cada campo tendrá:

| Información | Ejemplo de contenido |
|---|---|
| Nombre y significado | «Nonce: valor que utiliza esta operación junto con la clave». |
| Representación | Hexadecimal, texto UTF-8, JSON, PEM, decimal, archivo o referencia de clave. |
| Unidad y restricciones | Bytes frente a bits; número de dígitos; valores mínimos y máximos. |
| Obligatoriedad | Siempre obligatorio, opcional, producido por la operación o requerido para una acción concreta. |
| Dependencias | «Solo aplica al perfil seleccionado»; «se utiliza al descifrar». |
| Opciones y efecto | Qué significa cada opción y qué resultado cambia al seleccionarla. |
| Diagnóstico | Qué corregir cuando el dato tiene tamaño, formato o combinación incorrectos. |
| Ejemplo | Datos sintéticos separados de los valores introducidos por el usuario. |

Conviene distinguir tres conceptos: **compatible con la implementación**, **opción aconsejada para un caso de uso revisado** y **opción heredada o experimental**. Una opción admitida no se convierte automáticamente en una recomendación.

También hay que distinguir «modo de operación» de «formato de datos». CBC/GCM son modos de cifrado; Hex/Base64 son representaciones; Compact/JSON son serializaciones; importar/exportar son acciones. La ayuda debe utilizar el vocabulario propio de cada pantalla.

## 4. Ejemplos de comportamiento contextual

### Cifrado simétrico: AES-256 / GCM

La ficha explicaría que la acción cifra datos y produce texto cifrado y una etiqueta de autenticación, o verifica esa etiqueta y recupera los datos al descifrar.

| Campo | Explicación que debería mostrar la aplicación |
|---|---|
| Entrada | Se interpreta con el formato del payload de la barra superior. |
| Clave manual | Hexadecimal; AES-256 requiere 32 bytes, equivalentes a 64 dígitos hexadecimales. Si se usa Key Lab, explicar la referencia y la disponibilidad del material. |
| IV / nonce | La implementación de GCM admite 8, 12 o 16 bytes y propone 12 al generar. Mostrar tanto los tamaños aceptados como el propuesto. |
| Padding | El selector se fija en `NoPadding` para GCM; explicar por qué no se configura relleno en esta variante. |
| AAD | Datos adicionales autenticados. En la ruta de cifrado por bloques revisada se intenta interpretar como Hex y, si falla, como ASCII; este comportamiento debe explicarse expresamente. |
| Tag | La implementación utiliza 16 bytes. Es un resultado al cifrar y un dato necesario al descifrar. |
| Salida | Explicar dónde aparece el ciphertext y dónde la etiqueta, para que se puedan reutilizar correctamente. |

Si el usuario cambia a CBC, la ayuda retira AAD/tag del grupo aplicable, explica el IV correspondiente y presenta el relleno admitido. Si cambia a un algoritmo de flujo, explica por qué los selectores de modo y relleno no aplican.

Las listas actuales de modos y algoritmos simétricos son globales. Antes de publicar una tabla completa hay que comprobar la combinación **algoritmo × modo × padding × proveedor**. La presencia de dos opciones en sus respectivos selectores no demuestra que puedan utilizarse juntas.

### JOSE: JWE

La ayuda distinguiría `alg`, que selecciona la gestión de la clave, de `enc`, que selecciona el cifrado del contenido. Debe explicar el material esperado según se elija RSA, ECDH, AES-KW, `dir` o PBES2.

Las reglas deben seguir `JweComposer`, que ya centraliza las listas de algoritmos y serializaciones. Ejemplos de dependencias comprobadas en el código:

- AAD externo requiere una serialización JSON; Compact lo rechaza.
- General JSON puede obtener varios destinatarios de un JWKS; en esa rama el algoritmo se obtiene de las claves de los destinatarios.
- En esa rama multidestinatario, `kid`, `apu` y `apv` proceden de cada JWK; los campos globales correspondientes no se aceptan.

### Pagos: PIN block

La ficha utilizaría `PinBlockFormat` para saber si el formato usa PAN, qué rellenos admite y cuál es el valor predeterminado. Separaría la construcción del bloque de su protección criptográfica. La ruta protegida utiliza AES para ISO-4 y TDES para los demás formatos soportados por ese panel.

El formato elegido debe determinar las explicaciones del PAN y del relleno; estos datos no tienen el mismo significado ni la misma obligatoriedad en todos los formatos.

## 5. Arquitectura recomendada

Separar contenido, reglas y presentación permite incorporar la ayuda sin añadir otro bloque grande de lógica al controlador principal.

| Componente propuesto | Responsabilidad |
|---|---|
| `OperationHelpRegistry` | Fichas por ID de operativa, acciones, campos, opciones y enlaces. Texto mediante claves de traducción y secciones locales con identificadores explícitos. |
| `OperationHelpContext` | ID de operativa, acción, variante, campo consultado, idioma y selecciones públicas necesarias para elegir la explicación. |
| Políticas de capacidades por familia | Resolver qué campos aplican, cuáles son obligatorios y qué opciones son compatibles. Compartir esas reglas con los controles y la validación. |
| Adaptador de ayuda por módulo | Asociar campos semánticos con controles JavaFX y comunicar cambios de pestaña, acción o selección. |
| `ContextualHelpCoordinator` / presentador | Abrir el panel, resolver contenido, actualizarlo, gestionar foco y liberar suscripciones. |

El contexto de ayuda no necesita copiar claves, PIN, contraseñas o payloads. Para elegir explicaciones bastan la variante y las selecciones públicas. Los mensajes de diagnóstico pueden usar tamaños y estados derivados sin incorporar el contenido secreto.

Usar el ID de `OperationDescriptor` como identidad principal y un `actionId` para sus acciones. Asociar los campos con identificadores semánticos estables; el adaptador los enlaza con los `fx:id` actuales. Evitar que la ayuda dependa de coincidencias de texto, títulos traducidos o acceso por reflexión a campos privados.

Los módulos se cargan de forma diferida. El coordinador debe enlazar los controles al cargar el módulo, actualizar el contexto al activar su sección y evitar registrar listeners duplicados al volver a abrirlo.

### Reglas compartidas

Reutilizar los catálogos existentes cuando ya describen capacidades: `PinBlockFormat`, `JweComposer`, `OperationFormatRegistry` y `NodeParameter`. Para otras familias, extraer gradualmente las condiciones dispersas en presentadores, coordinadores y validadores.

La ayuda, el selector y la validación deben consultar la misma regla de compatibilidad. La validación criptográfica conserva la comprobación final antes de ejecutar.

Los errores deberían poder asociarse a una regla y un campo para abrir la explicación pertinente. No conviene deducir esa relación analizando el texto traducido de una excepción.

### Contenido local e idiomas

Guardar definiciones estructurales junto al código y textos en los recursos de localización españoles e ingleses. Los tutoriales extensos pueden mantenerse como Markdown, con un índice explícito de ID de operación → documento → sección.

Empaquetar los documentos y sus imágenes locales mediante una selección declarada en Maven o recursos dedicados. Comprobar los enlaces desde el JAR distribuido, sin depender del directorio de desarrollo. La ausencia de una ficha debe mostrar una explicación de disponibilidad y el resumen conocido, nunca un panel vacío.

Una vista con controles JavaFX y secciones plegables basta para la ayuda breve. La primera entrega no necesita incorporar un navegador ni un nuevo motor de renderizado de Markdown.

## 6. Cobertura que hay que completar

El inventario actual tiene 96 entradas en 13 categorías. Es un punto de partida: hay que añadir el inventario de pestañas, acciones y campos, así como herramientas accesibles fuera del catálogo. No equivale a 96 fichas simples e idénticas.

| Categoría del catálogo | Entradas | Contenido que requiere tratamiento propio |
|---|---:|---|
| Cipher | 5 | Cifrar/descifrar, algoritmos, IV, autenticación, FPE, archivos y OpenPGP. |
| Generic | 12 | Representaciones, tamaños, conversiones, aritmética, lotes y Process Designer. |
| Wallet | 9 | Emitir/presentar/verificar, perfiles, confianza, estado y límites del análisis. |
| Authentication | 2 | Firmar/verificar, calcular/verificar MAC y origen de claves. |
| Keys | 21 | Generación, KCV, derivación, envoltura, transporte, almacenes y tokens ICSF. |
| Post-Quantum | 2 | Familias, parámetros, encapsular/decapsular y firmar/verificar. |
| XML Security | 10 | Firmas, referencias, perfiles SOAP, cifrado, timestamps y confianza. |
| Certificates | 10 | Inspección, CSR, emisión, cadenas, revocación y contenedores. |
| JOSE | 5 | JWS/JWT/JWE/JWK/JWKS, validación, cabeceras y serializaciones. |
| COSE | 3 | Firmas, MAC, cifrado, claves y representación CBOR. |
| Payments | 12 | PIN/PAN, CVV/PVV, DUKPT, EMV y perfiles concretos. |
| ASN1 | 2 | Decodificar/codificar, representación y tipos. |
| History | 3 | Consultar, reabrir, guardar y exportar con sus políticas de visibilidad. |

## 7. Implantación y aceptación

**Primera entrega: infraestructura y tres pilotos completos.** Cifrado simétrico, JWE y PIN block prueban campos condicionales, varias acciones y reglas entre opciones. Incluir panel, botones de ayuda, traducciones, adaptación a ventanas estrechas y contenido empaquetado. Son pilotos para validar el diseño, no la cobertura final.

**Segunda entrega: ficha básica para todo el inventario.** Todas las entradas y herramientas detectadas deben explicar propósito, acciones, entradas, salida y acceso a documentación. Mantener un inventario explícito del detalle que queda por completar.

**Tercera entrega: campos y capacidades de todas las familias.** Completar explicaciones por campo y opción, dependencias, diagnósticos y ejemplos comprobados. Migrar reglas compartidas por familia con cambios acotados. Esta entrega cumple el objetivo de ayuda en cada operativa.

**Cuarta entrega: aprendizaje guiado.** Ampliar los recorridos existentes con las fichas y reglas ya validadas. Un asistente paso a paso es una ampliación; la ayuda completa debe poder consultarse directamente desde cualquier campo.

La aceptación de la cobertura completa requiere:

- Toda operativa y acción inventariada abre su ficha correcta; los campos y opciones configurables tienen explicación propia o una referencia común explícita.
- Las combinaciones documentadas coinciden con los selectores, validadores y ejecución; las opciones no aplicables explican su motivo.
- Cambiar algoritmo, acción, pestaña o idioma actualiza la ayuda; volver a abrir módulos no duplica listeners.
- Los ejemplos permiten verificar el resultado; si es aleatorio, se comprueba estructura, longitud o ida y vuelta.
- El JAR contiene los recursos de ayuda y sus enlaces locales funcionan sin acceso a Internet.
- La ayuda funciona con teclado, no modifica los datos de la operativa y no incorpora valores secretos al contenido mostrado o guardado.

Las pruebas deben cubrir reglas de compatibilidad, correspondencia del inventario, recursos e idiomas y unos recorridos JavaFX representativos. No hace falta repetir toda la suite criptográfica por cada texto añadido; sí probar las combinaciones cuando se extraiga o cambie una regla.

## 8. Evidencia y alcance de este análisis

Revisión estática del código, del empaquetado Maven y de la documentación. Se contabilizaron las entradas declaradas del catálogo y los recursos existentes. No se realizaron pruebas de interfaz ni una auditoría exhaustiva de todas las combinaciones criptográficas. No se modificó la funcionalidad ni los archivos que ya tenían cambios locales.

Fuentes principales del repositorio:

- [Catálogo de operaciones](../src/main/java/com/cryptocarver/model/OperationRegistry.java).
- [Inspector actual](../src/main/java/com/cryptocarver/ui/OperationInspectorPresenter.java).
- [Formatos globales](../src/main/java/com/cryptocarver/model/OperationFormatRegistry.java).
- [Servicio local de ayuda](../src/main/java/com/cryptocarver/service/LocalHelpService.java) y [empaquetado](../pom.xml).
- [Validación previa](../src/main/java/com/cryptocarver/ui/ReadinessPanelCoordinator.java) y [recorridos guiados](../src/main/java/com/cryptocarver/model/GuidedStepPolicy.java).
- [Capacidades JWE](../src/main/java/com/cryptocarver/crypto/JweComposer.java), [formatos PIN](../src/main/java/com/cryptocarver/crypto/PinBlockFormat.java) y [parámetros de nodos](../src/main/java/com/cryptocarver/model/process/NodeParameter.java).
- [Índice de tutoriales](../tutoriales/README.md) y [roadmap existente](CRYPTOCARVER_ROADMAP_EVOLUCION.md).
