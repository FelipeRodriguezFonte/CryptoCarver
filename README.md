# CryptoCarver 🔐

**Calculadora Criptográfica Avanzada con Interfaz Gráfica**

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-21.0.5-blue.svg)
![License](https://img.shields.io/badge/license-Educational-green.svg)

---

## 📖 Sobre el Proyecto

CryptoCarver es una aplicación que llevaba años queriendo desarrollar. Gracias a la ayuda de **Gemini** y **Claude AI**, finalmente he podido llevarla a cabo en tiempo récord.

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
- Key Derivation (PBKDF2, HKDF)

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
- **Google Gemini** - Por la asistencia en desarrollo y resolución de problemas
- **Claude AI (Anthropic)** - Por la ayuda en arquitectura y documentación

### Proyectos Open Source
- **[pyemv](https://github.com/russss/python-emv)** - Referencia invaluable para implementaciones EMV
- **[psec](https://github.com/square/psec)** - Inspiración para operaciones criptográficas
- **[Bouncy Castle](https://www.bouncycastle.org/)** - El motor criptográfico que hace posible todo

### Estándares y Especificaciones
- ISO, ANSI, EMV Co., NIST - Por mantener y documentar los estándares criptográficos

---

## 📚 Documentación

### Guías Técnicas Completas
- [Guía de Usuario en Español](docs/user_guide_es.md) - Documentación técnica extendida
- [User Guide in English](docs/user_guide_en.md) - Extended technical documentation

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
├── docs/                    # Documentación
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

- [ ] Manuales reproducibles por módulo y capturas verificadas por plataforma.
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
