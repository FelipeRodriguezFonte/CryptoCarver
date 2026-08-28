# CryptoCarver 🔐

**Laboratorio criptográfico avanzado con interfaz gráfica, CLI y automatización local**

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-21.0.5-blue.svg)
![License](https://img.shields.io/badge/license-Educational-green.svg)

---

## 📖 Sobre el Proyecto

CryptoCarver es una aplicación para explorar, comprobar y documentar operaciones
criptográficas de forma visual y reproducible. Su desarrollo ha contado con la
participación de **Claude**, **Codex** y **Antigravity**, junto con la dirección y
validación de Felipe Rodríguez Fonte.

### ¿Por qué CryptoCarver?

El objetivo principal es **facilitar cálculos criptográficos** a todos aquellos profesionales y estudiantes que necesitan realizarlos de forma **rápida y sencilla**, sin tener que estar codificando cada vez que necesitan:

- Generar un CVV
- Calcular un MAC
- Derivar claves de sesión EMV
- Crear bloques PIN
- Firmar digitalmente
- Y muchas operaciones más...

### 🎯 Filosofía

La idea es ir **evolucionando las capacidades** de la herramienta según las necesidades de la comunidad y los estándares que vayan surgiendo. Este es un proyecto vivo que seguirá creciendo.

> CryptoCarver es un laboratorio educativo y de pruebas; no es un HSM ni un
> servicio de custodia para producción.

---

## ⚡ Características Principales

### 🔢 Operaciones Genéricas
- Hashing (MD5, SHA-1, SHA-256, SHA-384, SHA-512, SHA3, SHAKE, RIPEMD160)
- Conversión de formatos (Hex, Base64, Binary, Text)
- Aritmética modular
- Generación de UUIDs
- Cálculo de dígitos de control (Luhn, Verhoeff, Damm)

### 🔐 Criptografía Simétrica
- Algoritmos: DES, 3DES, AES (128/192/256 bits)
- Modos: ECB, CBC, CTR, GCM, CFB, OFB
- Padding: PKCS5/7, ISO10126, ISO7816-4, Zeros

### 🔑 Gestión de Claves
- Generación de claves simétricas y asimétricas
- Key Check Value (KCV) - múltiples métodos
- Key Component Splitting (XOR)
- **TR-31 Key Blocks** (wrap/unwrap)
- **Key tokens ICSF / CCA** (IBM z/OS) - analizador individual, análisis en lote y exportación/importación nativa
- Key Derivation (PBKDF2, HKDF)

### 🖥 Key tokens ICSF / CCA (IBM z/OS)

Análisis de los *key tokens* nativos del coprocesador criptográfico de host, y
reproducción en claro de los verbos con los que el host entrega y recibe claves.
**No son bloques TR-31**: son formatos distintos, los manejan verbos distintos
(`CSNBKEX` / `CSNBKIM` frente a export/import TR-31) y en la interfaz viven en
paneles separados dentro de **Keys → Tools**.

**Formatos reconocidos**

| Formato | Identificador | Tabla del manual |
|---|---|---|
| AES *fixed-length* interno | X'01' v X'04' | 614 |
| DES *fixed-length* interno / externo | X'01' / X'02', v X'00'/X'01' | 615 / 616 |
| RKX DES externo | X'02' v X'10' | 617 |
| Simétrico *variable-length* (AES/DES/HMAC) | X'00'/X'01'/X'02' v X'05' | 618-631 |
| PKA (RSA / ECC / QSA) | X'00'/X'1E'/X'1F' | 637-659 |

**Analizador individual** — ámbito, algoritmo, tipo de clave, longitud y
**fortaleza efectiva**, estado del material, envoltura, exportabilidad,
Control Vector, TVV, MKVP, historia de seguridad y *pedigree*.

**Análisis en lote** — inventario normalizado de 18 columnas, estadísticas
sobre 12 dimensiones, catálogo de 23 hallazgos de auditoría, e informes en
`.txt` y `.csv`. Lee tres formas de pegar tokens, detectadas bloque a bloque:
uno por línea, dos filas del host por token (dígito alto arriba, bajo abajo), o
un bloque entero de hexadecimal apilado. Las tres se pueden forzar a mano.

**Procedencia** — no se deduce de los bytes, así que se declara: copia cruda del
data set, `CSNBKRR`/`CSNDKRR`, o inferir. Cambia cómo se leen un MKVP y un TVV
ausentes: normal en un registro tal como se guarda en un CKDS no-KDSR (p. 1560),
anómalo en un token entregado por Key Record Read.

El informe completo —ficha, detalle campo a campo, avisos y notas de
procedencia— está **en español y en inglés**. Los códigos de veredicto
(`SYM_FIXED_DES_EXT`, `IMPORTER`, `DOUBLE`) y los datos decodificados se
mantienen idénticos en ambos idiomas: son identificadores, no palabras.

> ⚠️ **Aviso de seguridad.** El análisis **no descifra nada**: el material de
> clave protegido solo es recuperable dentro del coprocesador criptográfico,
> bajo su master key. Pero sus salidas **sí llevan los tokens enteros en
> hexadecimal**, así que los ficheros que genera hay que tratarlos con el mismo
> cuidado que el volcado del que salieron.

#### Exportación e importación con los verbos nativos

Reproduce **en claro, byte a byte**, lo que hace el host con los verbos nativos
de CCA, para poder comparar lo que sale de él con lo que espera quien recibe la
clave, sea otro CCA o un HSM de otro fabricante. No habla con ningún
coprocesador: es un banco de pruebas.

| Verbo | Operación |
|---|---|
| `CSNBKEX` / `CSNBDKX` | Key Export — interno → externo bajo un EXPORTER |
| `CSNBKIM` / `CSNBDKM` | Key Import — externo → operativo bajo la master key |

Cuatro operaciones en el panel **ICSF / CCA Key Export / Import**:

- **Exportar** — clave y KEK en claro, tipo de clave o Control Vector explícito,
  y el token externo con su TVV ya calculado.
- **Importar** — recupera la clave de un token de 64 bytes o de un criptograma
  suelto sin token, que es lo que llega de un sistema que no produce tokens CCA.
- **Inspeccionar** — todo lo que el token dice de sí mismo cuando no se tiene el
  KEK: ámbito, método de envoltura, longitud, Control Vector y TVV.
- **Resolver** — prueba todas las combinaciones razonables de variante del KEK,
  modo y Control Vector, y dice cuál reproduce la clave. Es el diagnóstico
  cuando la clave no cuadra en el otro extremo.

**Esquemas de protección.** La variante del KEK (`KEK XOR CV`, el KEK tal cual
de un EXPORTER con el bit **NOCV**, o las mitades del CV intercambiadas) y el
modo (ECB por partes, que es WRAP-ECB, o CBC encadenado) son elecciones
independientes, y equivocar cualquiera produce un token que el otro lado no
abre.

**El byte 4.** La Tabla 616 dice X'01' para una clave doble o triple, pero los
hosts reales dejan ese byte a X'00'. La casilla correspondiente reproduce lo que
hace el host, que es lo que permite comparar byte a byte; sin ella, el token
difiere en dos bytes —el 4 y el primero del TVV, que lo suma— y el criptograma
es idéntico.

**KCV y verification pattern son números distintos.** El KCV de la industria son
los 3 primeros bytes de cifrar ceros con la clave; `CSNBKYT` lo llama ENC-ZERO y
da 4. Su verification pattern por defecto es **otro** algoritmo (p. 1720). Si
cada lado mira un número distinto, la comparación no cuadra nunca, y esa es la
mitad de los descuadres.

La envoltura mejorada (WRAP-ENH, WRAPENH2, WRAPENH3) **no se puede reproducir
fuera del coprocesador**, y se dice en vez de devolver bytes que parecerían una
clave sin serlo.

> ⚠️ **Aviso de seguridad.** Aquí el material de clave se maneja **en claro**,
> porque reproducir la aritmética del host lo exige: se dan la clave y el KEK en
> claro. Usa claves de prueba. Los informes que genera llevan claves y tokens
> enteros en hexadecimal.

Desde la CLI, con los mismos cuatro verbos:

```bash
java -jar cryptocarver.jar icsf-export --key 0123456789ABCDEFFEDCBA9876543210 --kek 404142434445464748494A4B4C4D4E4F --type EXPORTER
java -jar cryptocarver.jar icsf-import --token 020000000000C000... --kek 404142434445464748494A4B4C4D4E4F
java -jar cryptocarver.jar icsf-inspect --token 020000000000C000...
java -jar cryptocarver.jar icsf-resolve --token 020000000000C000... --kek 4041... --expected-key 0123...
```

| Opción | Para qué |
|---|---|
| `--key`, `--kek`, `--token` | Material de entrada, en hexadecimal |
| `--type`, `--cv` | Tipo de clave (Tabla 676) o Control Vector explícito |
| `--variant` | `cv` (por defecto), `nocv`, `cv-swapped` |
| `--mode` | `ecb` (por defecto) o `cbc` |
| `--table616-version` | Byte 4 a X'01' según la Tabla 616; por defecto se escribe X'00', como los hosts reales |
| `--nocv` | Marca el token con el bit NOCV |
| `--expected-key`, `--expected-kcv` | Referencia para que `icsf-resolve` sea concluyente |
| `--json`, `--lang`, `--out` | Salida legible por máquina, idioma del informe, y guardarlo |

Con `--json` los veredictos salen como **códigos** (`MATCHES_KEY`, `POSSIBLE_EVEN`)
además de como texto, para poder ramificar en un script sin depender del idioma.

Desde la CLI:

```bash
java -jar cryptocarver.jar icsf-token 020000000100C000... --provenance kds-crudo
java -jar cryptocarver.jar icsf-batch tokens.txt --csv inventario.csv --txt informe.txt --no-detail
```

### 💳 Algoritmos de Pago
- **CVV/CVC/iCVV** Generation & Verification
- **Dynamic CVV (dCVV)**
- **PIN Verification Value (PVV)**
- **IBM 3624** PIN Offset
- Bloques PIN (ISO-0, ISO-1, ISO-2, ISO-3, ISO-4, IBM 3624)

### 🏧 EMV
- Derivación de claves de sesión (EMV Option A)
- **ARQC/ARPC** Generation (Method 1 & 2)
- Script MAC
- Verificación de criptogramas

### 🔏 MAC (Message Authentication Code)
- ISO 9797-1 Algorithm 1 (CBC-MAC)
- ISO 9797-1 Algorithm 3 (Retail MAC / X9.19)
- ISO 9797-1 Algorithm 5 (CMAC)
- HMAC (SHA-256, SHA-384, SHA-512)

### ✍️ Firmas Digitales
- RSA (SHA256withRSA, SHA384withRSA, SHA512withRSA)
- RSA-PSS
- ECDSA (P-256, P-384, P-521)
- Ed25519
- Paquetes de validación pre-configurados

### 📜 ASN.1 Parser
- Visualización jerárquica de estructuras ASN.1
- Schemas: X.509, PKCS#8, PKCS#10, PKCS#7, CRL
- OID Registry integrado

### 📦 CMS / PKCS#7
- SignedData (attached/detached)
- EnvelopedData
- Verificación de firmas PKCS#7

### 🎫 JOSE (JWT/JWS)
- Generación y validación de JWT
- Algoritmos: HS256/384/512, RS256/384/512, PS256/384/512, ES256/384/512, Ed25519
- Formatos: PEM, JWK (RSA, EC, oct)

---

## 🚀 Inicio Rápido

Para recorridos reproducibles por conversión, claves, XAdES/TSA, PQC, pagos e
histórico, consulta la [guía de inicio rápido](docs/QUICKSTART.md). Los límites
explícitos entre laboratorio y producción se recogen en
[docs/LAB_VS_PRODUCTION.md](docs/LAB_VS_PRODUCTION.md).
El catálogo exacto de representaciones, charsets y EBCDIC está en
[docs/FORMATS_AND_CHARSETS.md](docs/FORMATS_AND_CHARSETS.md).

### Requisitos
- **Java 17** o superior (LTS recomendado)
- **Maven 3.8+**

### Compilar y Ejecutar

```bash
# Clonar o descargar el proyecto
cd CryptoCarver

# Compilar con Maven
mvn clean package

# Ejecutar la aplicación
mvn javafx:run
```

O ejecutar el JAR directamente:
```bash
java -jar target/cryptocarver-<version>.jar
```

Consulta la [guía operativa](docs/GUIA_OPERATIVA_CRYPTOCARVER.md) para EBCDIC, XAdES/TSA, histórico, diagnóstico y logs.

### Tutoriales guiados

El repositorio incluye una colección de **24 tutoriales prácticos**, con capturas,
datos de laboratorio y versiones PDF. Empieza por el
[índice de tutoriales](tutoriales/README.md) o descarga el
[índice en PDF](tutoriales/pdfs/00-indice.pdf). Cubre búsquedas, operaciones
genéricas, cifrado, autenticación, claves, PQC, XML, certificados, JOSE, COSE,
pagos, ASN.1, historial y recorridos avanzados de AES-KW, TR-31, TR-34, PKCS#11,
CMS, PAdES, XAdES y WS-Security.

Todos los ejemplos usan material de laboratorio. No copies claves, PIN,
certificados ni datos de producción en la aplicación ni en los tutoriales.

### CLI local

La CLI usa las mismas operaciones locales y deterministas que el Batch Runner. No inicia
la interfaz ni un servicio de red.

```bash
# macOS / Linux
./run-cli.sh sha256 abc
./run-cli.sh base64url-encode "hola"
./run-cli.sh batch sha256 datos.csv --format csv --output jsonl
```

```bat
:: Windows (cmd.exe)
run-cli.bat sha256 abc
run-cli.bat batch sha256 datos.csv --format csv --output jsonl
```

Los lotes CSV/JSON Lines (`.jsonl`) requieren una columna o propiedad `input`. JSON Lines
no es un documento JSON único: contiene un objeto JSON independiente por línea. Añade
`--json` a las operaciones individuales para obtener una respuesta JSON estable.

La API local es opcional y se inicia explícitamente desde la CLI; solo escucha en
`127.0.0.1` y no acepta claves ni operaciones de cifrado:

```bash
./run-cli.sh serve --port 8787
curl -s http://127.0.0.1:8787/health
curl -s -X POST http://127.0.0.1:8787/v1/sha256 -H 'Content-Type: application/json' -d '{"input":"abc"}'
```

---

## 💻 Multiplataforma

La aplicación funciona en:
- ✅ **Windows** (10/11)
- ✅ **macOS** (10.15+)
- ✅ **Linux** (Ubuntu, Debian, Fedora, etc.)

En Windows puedes ejecutarla desde fuentes con `run.bat`, desde un JAR ya compilado
con `run_simple.bat` (que reutiliza el JAR dejado por `run.bat` o por
`mvn package`), o generar una aplicación autocontenida mediante
`package_windows.bat`. Consulta [docs/WINDOWS.md](docs/WINDOWS.md) para los pasos exactos.

---

## 🆕 Evolución desde la primera versión

La primera versión, publicada como **CryptoForge**, se centraba en una calculadora
gráfica con operaciones criptográficas y de pagos esenciales. Desde entonces el
proyecto se ha renombrado a **CryptoCarver** y ha evolucionado hasta la versión
**2.3.0**, con mejoras destacadas en los siguientes ámbitos:

- **Cobertura funcional:** se añadieron PQC (ML-KEM, ML-DSA y SLH-DSA), XAdES,
  TSA, WS-Security, COSE, CMS/PKCS#7, PAdES, ASiC, AES Key Wrap, DUKPT AES,
  TR-34, análisis de archivos cifrados e inspección avanzada de ASN.1.
- **Pagos y claves:** TR-31 amplía el soporte de bloques de clave y sus bloques
  opcionales; EMV incorpora inspector BER-TLV, CDOL/DDOL y comprobación ARQC;
  los flujos de PIN, CVV, PVV y DUKPT tienen laboratorios y vectores de prueba.
- **Automatización reproducible:** se incorporaron Batch Runner, recetas
  encadenadas, CLI, API local limitada a `127.0.0.1`, catálogo de operaciones y
  perfiles de laboratorio.
- **Seguridad y trazabilidad:** el histórico diferencia datos públicos,
  sensibles y secretos; hay una caché HSM simulada de sesión, límites de entrada,
  cancelación, diagnóstico, SBOM CycloneDX, manifiestos y checksums de release.
- **Documentación:** se añadieron guía rápida, guía de operación, límites entre
  laboratorio y producción, formatos/charsets y 24 tutoriales reproducibles con
  capturas, material de apoyo y PDF.

Consulta el [CHANGELOG](CHANGELOG.md) para el detalle por versión y los cambios
pendientes de publicación posteriores a 2.3.0.

---

## ⚠️ Notas Importantes

### Verificación de Funcionalidades

Esta aplicación incluye **muchas funcionalidades** criptográficas. A priori, todas están **verificadas y probadas**, pero debido a los continuos cambios y evolución del proyecto, es posible que algo se me haya escapado.

**Si encuentras algo que no te cuadra, no dudes en contactarme:**

📧 **felipe.rodriguez.fonte@gmail.com**

Tu feedback es valioso para mejorar la herramienta.

### Uso Responsable

⚠️ **Consideraciones de Seguridad:**
- Esta herramienta está destinada para **desarrollo, testing y educación**
- Nunca uses claves de producción en entornos no confiables
- Siempre protege tus keystores con contraseñas fuertes
- Sigue las políticas de gestión de claves de tu organización

---

## 🙏 Agradecimientos

Este proyecto no habría sido posible sin:

### Inteligencia Artificial
- **Claude (Anthropic)** - Asistencia en arquitectura, desarrollo y documentación
- **Codex (OpenAI)** - Asistencia en desarrollo, validación y documentación
- **Antigravity** - Asistencia en desarrollo y evolución del proyecto

### Proyectos Open Source
- **[pyemv](https://github.com/russss/python-emv)** - Referencia invaluable para implementaciones EMV
- **[psec](https://github.com/square/psec)** - Inspiración para operaciones criptográficas
- **[Bouncy Castle](https://www.bouncycastle.org/)** - El motor criptográfico que hace posible todo

### Estándares y Especificaciones
- ISO, ANSI, EMV Co., NIST - Por mantener y documentar los estándares criptográficos

---

## 📚 Documentación

### Guías Técnicas Completas
- [Guía de Usuario en Español](docs/guide_es_extended.md) - Documentación técnica extendida
- [User Guide in English](docs/guide_en_extended.md) - Extended technical documentation
- [Guía de inicio rápido](docs/QUICKSTART.md) - Recorridos reproducibles de las funciones principales
- [Guía operativa](docs/GUIA_OPERATIVA_CRYPTOCARVER.md) - Operación, diagnóstico e histórico
- [Tutoriales prácticos](tutoriales/README.md) - 24 laboratorios guiados, con capturas y PDF
- [Cambios por versión](CHANGELOG.md) - Evolución funcional y de compatibilidad
- [Key tokens ICSF / CCA](docs/HANDOFF_ICSF_CCA.md) - Arquitectura, catálogo de hallazgos y decisiones de diseño del módulo de host

### Estructura del Proyecto
```
CryptoCarver/
├── src/
│   ├── main/
│   │   ├── java/com/cryptocarver/
│   │   │   ├── ui/          # Controllers (JavaFX)
│   │   │   ├── crypto/      # Operaciones criptográficas
│   │   │   ├── utils/       # Utilidades
│   │   │   └── model/       # Modelos de datos
│   │   └── resources/
│   │       ├── fxml/        # Archivos de interfaz
│   │       ├── css/         # Estilos
│   │       └── images/      # Iconos
│   └── test/                # Tests unitarios
├── docs/                    # Guías técnicas, operación y release
├── tutoriales/              # 24 tutoriales, material de laboratorio y PDF
└── pom.xml                  # Configuración Maven
```

---

## 🔧 Dependencias Principales

```xml
<!-- Cryptographic Provider -->
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78.1</version>
</dependency>

<!-- JavaFX -->
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>21.0.5</version>
</dependency>

<!-- Utilities -->
<dependency>
    <groupId>commons-codec</groupId>
    <artifactId>commons-codec</artifactId>
    <version>1.17.1</version>
</dependency>
```

---

## 🛣️ Roadmap

### Próximas Características
- [x] Soporte ampliado de derivación de claves (KDF).
- [x] Operativas post-cuánticas estandarizadas (ML-KEM, ML-DSA y SLH-DSA).
- [x] Procesamiento batch y recetas encadenadas.
- [x] Importación/exportación de recetas y perfiles de laboratorio.
- [x] CLI, automatización y API local con límites seguros.
- [x] HSM simulado de sesión para laboratorio.
- [x] Vectores y perfiles reproducibles para PQC, TSA, JOSE y pagos.

### En evolución

- [x] Manuales reproducibles por módulo, capturas y PDF.
- [ ] Changelog por versión con migraciones y compatibilidades.
- [~] Integración PKCS#11/HSM real de laboratorio mediante SunPKCS11 y SoftHSM; faltan perfiles de proveedor, mecanismos/slots e integración XAdES.

---

## 🤝 Contribuciones

Las contribuciones son bienvenidas. Si deseas contribuir:

1. Fork el repositorio
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

---

## 📄 Licencia

Este proyecto se proporciona tal cual para fines educativos y de desarrollo.

---

## 📞 Contacto

**Felipe Rodríguez Fonte**

📧 Email: felipe.rodriguez.fonte@gmail.com
💼 LinkedIn: [Felipe Rodríguez Fonte](https://www.linkedin.com/in/felipe-rodriguez-fonte)

---

## 🌟 Si te resulta útil...

Si este proyecto te ha sido útil, considera:
- ⭐ Darle una estrella al repositorio
- 🐛 Reportar bugs o sugerir mejoras
- 📢 Compartirlo con otros profesionales de seguridad

---

<p align="center">
  <strong>Hecho con ❤️ para la comunidad de criptografía y seguridad</strong>
</p>

<p align="center">
  <sub>Inspirado por años de trabajo en sistemas de pago y criptografía aplicada</sub>
</p>
