# Proceso de caracterización de fase 4

Antes de fijar el digest, la caracterización pasó todas las aserciones de renderizado e interacción y falló únicamente al comparar el marcador provisional `PENDING` con el SHA-256 calculado. No se observó un defecto de producto.

Transcripción de referencia observada en la rama antes de la extracción:

```text
selection source=pending marker=SOURCE target=active
redraw nodes=2 curve=1 reps=TEXT_UTF8/HEX port=input input-handles=1 validation=+1
selected-connection stroke=0xf6c344ff width=4.0
drag snap=80,110 curve-start=230,145 undo=60,80 redo=80,110
```

Digest calculado antes de fijarlo en el test: `e8d1bafcbc0a35484c18428979328002eb7658976c380b61f40c2a63118ff014`.

La primera versión del fixture buscaba los estilos de selección después de llamar directamente a la ruta de conexión; esta ruta elige el destino activo y su vista queda pendiente de renderizado. Se ordenó la caracterización como selección, conexión y selección del enlace, que conserva el flujo observable del canvas. El fallo inicial era del orden del fixture, no un comportamiento cambiado ni un defecto del producto. La versión que fija el digest mantiene las aserciones de estado observable y comprueba la ruta completa de arrastre de nodo.
