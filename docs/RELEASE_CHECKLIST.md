# CryptoCarver release checklist

Esta lista convierte una compilación local en una entrega trazable. La versión se
declara una sola vez en `pom.xml`; no se edita en los scripts de empaquetado.

## Preparación

- [ ] Revisar el changelog y las notas de versión.
- [ ] Ejecutar `mvn -q clean test`.
- [ ] Ejecutar `git diff --check` y revisar los cambios intencionados.
- [ ] Hacer un recorrido manual de las herramientas modificadas y de **Expand Result**.
- [ ] Abrir **Help → Diagnostics** y confirmar que el informe se puede copiar o
  guardar, sin incluir claves, entradas ni rutas de usuario.

## Artefactos y procedencia

- [ ] Elegir el canal: `release-stable` para una entrega validada o
  `release-experimental` para una compilación de pruebas. El diagnóstico y About
  muestran el canal incluido en el build.
- [ ] Construir el JAR, SBOM CycloneDX, manifiesto y SHA-256: `RELEASE_CHANNEL=stable bash scripts/build-release.sh`.
- [ ] Empaquetar macOS con `./package_macos.sh` (usar `PACKAGE_TYPE=dmg` si se
  desea instalador) y Windows con `package_windows.bat`.
- [ ] Empaquetar Linux con `bash package_linux.sh` (usar `PACKAGE_TYPE=deb` o
  `PACKAGE_TYPE=rpm` en la distribución correspondiente si se desea instalador).
- [ ] Añadir al manifiesto los instaladores que se vayan a publicar, usando
  `bash scripts/create-release-manifest.sh <version> <artifact> [...]`.
- [ ] Añadir al mismo manifiesto cada instalador generado (`.dmg`, `.msi`, `.exe`,
  `.deb` o `.rpm`) al distribuirlo.
- [ ] Desde la carpeta de release, verificar un checksum desde una máquina limpia
  con `cd dist/release-<version> && shasum -a 256 -c SHA256SUMS` (o la herramienta
  equivalente del sistema).

## Validación por plataforma

- [ ] macOS: generar instalador, abrir la aplicación empaquetada, comprobar icono y arranque, y usar una operación de cifrado y otra de firma. **(Validación manual requerida)**. La firma de código (notarización) no se incluye en el proceso estándar de laboratorio y requiere ejecución manual si fuera necesario.
- [ ] Windows: abrir el app-image o instalador, comprobar arranque, exportación y selector de archivos. **(Validación manual requerida)**. (No se ofrece instalador firmado por defecto).
- [ ] Linux: abrir el app-image o instalar el paquete nativo, y comprobar arranque y creación de ficheros desde el selector. **(Validación manual requerida)**.
- [ ] La generación de checksums (SHA256SUMS), manifiesto, y SBOM es **automática** vía los scripts `build-release.sh` y `create-release-manifest.sh`. Las pruebas automáticas incluyen consistencia criptográfica y validación del modelo, pero NO validan las UI nativas compiladas.
- [ ] Documentar en las notas las plataformas y arquitecturas realmente probadas. No prometer soporte de producción ni garantías fuera de entornos de test.

## Publicación y recuperación

- [ ] Publicar JAR, SBOM, `SHA256SUMS`, `RELEASE_MANIFEST.md` y notas juntos.
- [ ] Identificar el commit exacto de origen y conservar los artefactos.
- [ ] Indicar si hay migraciones de configuración o pasos de downgrade.
