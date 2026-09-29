# Caracterización de localización del shell moderno

Antes de extraer la localización se cargó `/fxml/main-view-modern.fxml` mediante `Fxml.loader` y se caracterizó el comportamiento desde la UI de producción. Surefire fija `user.home` en `target/test-home`; cada caso restaura el idioma previo.

La ejecución previa a producción fue:

```text
nice -n 19 mvn -o -q -Dtest=ModernMainShellLocalizationCharacterizationTest test
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```

Los siete comportamientos cubiertos son el cambio inglés/español/inglés de menú, búsqueda lateral, botón principal y título de ventana; el menú Laboratorio; restauración del foco; módulo ya cargado y módulo perezoso; error con clave; error sin clave; y textos conocidos/desconocidos de sección y módulo.

El título de la ventana permanece `CryptoCarver` en ambos idiomas. El test fija ese título al crear el `Stage`, ya que el FXML de producción no crea ni configura una ventana.

## Instantáneas base

Se capturó únicamente la pantalla `Hashing`, una vez con `theme-light.css` y otra con `theme-dark.css`, usando `ComputedStyleSnapshotTool` con `styleSnapshotRoute=Hashing` y `styleSnapshotTheme` explícitos. Antes de cada captura se apartó `target/test-home`, dejando que la ejecución creara un directorio limpio.

Archivos base temporales:

- `/tmp/encargo36-before-light.txt`
- `/tmp/encargo36-before-dark.txt`

Las capturas posteriores a la extracción se compararán contra esos archivos.
