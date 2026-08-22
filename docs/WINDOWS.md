# Ejecutar CryptoCarver en Windows

CryptoCarver admite Windows 10 y 11. El paquete nativo de Windows debe construirse en Windows; `jpackage` no permite generar un `.exe` de Windows desde macOS o Linux.

## Opción recomendada: aplicación autocontenida

Instala en el equipo de construcción:

- JDK 17 o posterior, incluyendo `jpackage` (Temurin 17/21 es válido).
- Maven 3.8 o posterior.
- Git, solamente si vas a clonar el repositorio.

Abre `cmd.exe` en la raíz del proyecto y ejecuta:

```bat
package_windows.bat
```

El resultado predeterminado es:

```text
dist\CryptoCarver\CryptoCarver.exe
```

La carpeta `dist\CryptoCarver` contiene también su runtime Java privado. Debes copiar la carpeta completa al equipo de destino; ese equipo no necesita tener Java ni Maven instalados.

Para crear un instalador, instala WiX Toolset y ejecuta uno de estos comandos antes del script:

```bat
set PACKAGE_TYPE=exe
package_windows.bat
```

```bat
set PACKAGE_TYPE=msi
package_windows.bat
```

## Desarrollo desde el código fuente

Con JDK 17+ y Maven en `PATH`:

```bat
run.bat
```

Es equivalente a ejecutar:

```bat
mvn package -DskipTests
run_simple.bat
```

La compilación es incremental (sin `clean`), y deja el JAR ejecutable en `target\`,
de modo que después puedes arrancar la aplicación con `run_simple.bat` sin Maven.

## Ejecutar un JAR compilado

Necesita un JAR ya construido, por `run.bat` o por Maven:

```bat
mvn clean package -DskipTests
run_simple.bat
```

Este método requiere Java 17 o posterior en el equipo donde se ejecute. Para distribuir la aplicación a usuarios finales es preferible el paquete autocontenido.

## Comprobaciones rápidas

```bat
java -version
jpackage --version
mvn -version
echo %JAVA_HOME%
```

Si Windows no encuentra alguno de los comandos, añade los directorios `bin` del JDK y Maven a `PATH`, y define `JAVA_HOME` apuntando a la raíz del JDK.

Windows SmartScreen puede avisar al abrir un ejecutable no firmado. Para una distribución pública será necesario firmar el instalador con un certificado de firma de código; el aviso no implica un error del paquete local de laboratorio.
