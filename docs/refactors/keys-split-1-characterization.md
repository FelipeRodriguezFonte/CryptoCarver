# Caracterización previa de Keys (fase 1)

Producción intacta en esta ejecución. Carga mediante `Fxml.loader("/fxml/main-view-modern.fxml")`, navegación real, `test.mode=true` y `user.home=target/test-home`. Pares RSA generados; vectores TR-31 públicos extraídos de TR31OperationsTest a un fixture compartido, sin cambiar sus valores.

Comando: `mvn -o -q test -Dtest=KeysSplitCharacterizationUITest,TR31OperationsTest`

Salida resumida de Surefire (Maven exit 0):

```
com.cryptocarver.ui.KeysSplitCharacterizationUITest: tests=23, failures=0, errors=0, skipped=0, time=22.519 s
com.cryptocarver.crypto.TR31OperationsTest: tests=14, failures=0, errors=0, skipped=0, time=0.018 s
```

23 invocaciones UI: cuatro versiones TR-31 (wrap/parse/unwrap), bloque publicado independiente, KBPK incorrecta localizada; tres formatos RSA y clave incorrecta en cada uno; TR-34 una/dos pasadas, nonce incorrecto y aviso visible; publicación/histórico/Shelf de cada bloque; configuración portable y recetas redactadas en cada bloque; cambio de idioma en etiquetas y validación.

La caracterización conserva la importación TR-31 que publica longitud y no bytes recuperados, y TR-34 que entrega un resultado no fiable cuando falla el nonce. Los errores negativos esperados aparecen en el log redaccionados, sin material privado.

Ajustes durante preparación, antes de mover producción: statusLabel incluye icono/hora, por lo que se comprueba la frase localizada y el error estructurado; el histórico es una cola acotada a 50 y se comprueba también la operación más reciente; el aviso se inspecciona tras mostrar la ventana y esperar el pulso/animación, igual que la auditoría. No se cambiaron expectativas para ocultar cambios de producción.
