# Fase 8: observaciones antes de fijar SHA-256

La primera ejecución dirigida de ProcessDesignerPaletteCharacterizationUITest pasó las aserciones de texto con trim/case, categoría Conversions, cero coincidencias, espacios, null y doble clic con cantidad live. Llegó al marcador provisional SHA de 64 ceros, único fallo deliberado: digest observado `e5919c4b74289c4943dec6ca56218164ddff40ffee4baeac5c957bfdaf4a6a76`.

No es un defecto de producto ni un cambio en digests anteriores. La transcripción incluye headers/etiquetas/descripciones de tarjetas en orden de catálogo y dos adiciones HASH con coordenadas fijas, sin IDs, rutas, fechas ni resultados de cifrado. NodeCatalog documenta categorías en orden de inserción/lógico y ProcessEngine registra handlers en una CopyOnWriteArrayList inicializada con List.of (orden explícito). Se anota este resultado antes de fijar la expectativa. No se modifica ningún test existente.

Segunda ejecución, antes de extraer: las 4 pruebas dirigidas (nueva paleta 1, paleta existente 1, ejecución/privacidad 2) pasan sin fallos, errores ni omisiones. Digest fijado `e5919c4b74289c4943dec6ca56218164ddff40ffee4baeac5c957bfdaf4a6a76`; los dos digests anteriores se mantienen intactos. No se encontró defecto de producto.
