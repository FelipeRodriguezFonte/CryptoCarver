# CryptoCarver Handoff Report: Blocks CF-12 & CF-13

## 1. Contexto de la Revisión
Este documento sirve como informe de traspaso técnico para **Codex** (Agente Revisor). Abarca el progreso, las decisiones arquitectónicas y los cierres de los bloques de desarrollo **CF-12** y **CF-13** en la refactorización de **CryptoCarver** (anteriormente CryptoForge).

- **Proyecto:** CryptoCarver
- **Namespace actual:** `com.cryptoforge` (namespace interno retenido por compatibilidad histórica, renombre completo de la UI e imagen de marca completado).
- **Bloques cubiertos:**
  - **CF-12:** Interfaz de usuario (Migración a UI Moderna "Flat/Dark", Refactorización de Controladores, Visor Expand Result).
  - **CF-13:** Ingeniería de Release y Distribución (Verificación de Artefactos, Generación de SBOM/Manifiestos, y Empaquetado multiplataforma).

## 2. Resumen de Implementación

### CF-12: Modernización de Interfaz
- **Estilo y Tematización:** Se añadió un diseño "Dark Mode / Flat Design" unificado en paralelo a la interfaz legacy, la cual se mantiene para compatibilidad y fallback.
- **Controladores y Vistas:** Se implementó `ModernMainController` que agrupa el panel lateral de navegación de funciones. Las vistas FXML han sido verificadas en aislamiento con `xmllint`.
- **Diagnósticos y "Expand Result":** 
  - Se desarrolló e integró un visor detallado (*Expand Result*) para inspecciones avanzadas.
  - La herramienta de **Help → Diagnostics** emite un reporte técnico libre de secretos o PII que es fundamental para la recolección de métricas.

### CF-13: Ingeniería de Release y Seguridad en la Distribución
- **Construcción y Versionado:** La versión de la aplicación ahora emana de una única fuente de verdad (`pom.xml`) utilizando inyección automática en runtime mediante propiedades de Maven (`build.properties`).
- **Verificación y Compliance:** 
  - Se genera automáticamente el SBOM en formato **CycloneDX** para trazabilidad de dependencias durante los releases `release-stable` o `release-experimental`.
  - La salida del manifiesto de entrega (`RELEASE_MANIFEST.md`) incluye hashes criptográficos (SHA-256) garantizando la integridad de cada binario generado.
  - La integridad de las funciones de generación de manifiestos y checksums es verificada por `test-manifest-generation.sh` utilizando directorios temporales puros que no ensucian el árbol de trabajo ni el directorio `dist/`.
- **Empaquetado Nativo:** Los scripts `package_macos.sh`, `package_linux.sh` y `package_windows.bat` se han endurecido para depender de `jpackage` verificando estrictamente JDK 17+. Fallan elegantemente si las dependencias del sistema no se cumplen, y todos los subproductos de instaladores nativos han sido excluidos del control de versiones. (Nota: Las pruebas automáticas no validan el instalador nativo, lo cual queda como comprobación manual obligatoria para cada plataforma OS).

## 3. Restricciones Mantenidas
- Se han aislado exitosamente todas las compilaciones (`mvn clean test` y releases) utilizando directorios temporales (`/private/tmp`) para impedir sobreescrituras accidentales en la carpeta principal del proyecto o corrupción del runtime si `./run-modern.sh` estuviese activo.
- Todos los test unitarios (229) aprueban. Se solucionó un incidente en `HistoryReportExporterTest` relacionado con el enmascarado de secretos tabulares. 

## 4. Próximos Pasos (Siguientes Bloques)
La estructura actual es confiable y robusta para empezar el análisis de bloques posteriores orientados a las extensiones criptográficas profundas o a capacidades avanzadas de integración. Codex deberá evaluar este reporte y validar el nivel de cumplimiento para autorizar el avance al bloque CF-14 y sucesivos.
