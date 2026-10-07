# Fase 9: observaciones antes de fijar SHA-256

Primera ejecución dirigida de ProcessDesignerCanvasEventsCharacterizationUITest: todas las aserciones de handlers instalados, prioridad de teclado, consumo, flechas/Shift, duplicación/undo/redo, delete/backspace, rueda normal/control/shortcut, target del click y cancelación con Escape pasaron. También pasó la igualdad de la curva con sceneToLocal y el anclaje del nodo. El único fallo fue el marcador provisional deliberado de 64 ceros; digest medido `964211ade5ffbf3408904c5eca1160b21bc8227864b80bd2aa83dff50f2c24c3`.

Se anota antes de fijar la expectativa. No es defecto de producto. La transcripción fija resultados lógicos y posiciones de nodos explícitas, no coordenadas de pantalla/ventanas, IDs, tiempos, textos de excepciones o valores aleatorios. Los handlers se invocan desde sus propiedades instaladas para medir consumo; la propagación real de hijos sigue cubierta por CanvasCharacterization, SelectionCharacterization, Zoom y ConnectionCharacterization existentes. No se modifica ningún test existente.

Segunda ejecución dirigida antes de extraer: 9 informes / 13 pruebas / 0 fallos / 0 errores / 0 omitidas. SHA de eventos fijado y verificado; canvas, ejecución/privacidad y paleta conservan sus digests originales. No se detectó defecto de producto.
