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

Las capturas posteriores a la extracción se compararon contra esos archivos.

## Extracción y validación final

`ShellLocalizationCoordinator` aplica las claves a los controles JavaFX, actualiza accesibilidad y tooltips, localiza el menú Laboratorio y restaura el foco. `ModernMainController.applyLocalization()` delega en una línea y construye una vista de controles por llamada. La clase no guarda controladores de módulos. `ShellTextResolver` concentra la resolución de secciones, módulos y errores; no importa JavaFX. `NavigationChromeCoordinator` reutiliza ese resolver para evitar mantener una segunda tabla de traducciones.

La prueba unitaria de `ShellTextResolver` cubre traducciones conocidas, valores sin traducción y errores mapeados/no mapeados. La pasada dirigida final informó 7 casos de FXML y 6 unitarios: 13 pruebas, sin fallos ni errores.

Las instantáneas posteriores se tomaron con la misma ruta (`Hashing`), tema y `user.home` limpio en cada captura. Los dos comandos `diff -u` no produjeron salida: diferencias vacías en `theme-light.css` y `theme-dark.css`.

La suite completa se ejecutó al final con `mvn -o -q test`: 2407 pruebas, 0 fallos, 0 errores y 1 omitida; tiempo real medido: 215,50 s. No apareció `Java heap space`. No hice una prueba manual.

`ModernMainController` pasa de 3342 a 3216 líneas.
