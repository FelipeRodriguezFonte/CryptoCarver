> **DETENIDO por orden humana de 2026-10-10.** El usuario indica: «Paralo. No me convence las interrogaciones por todos lados». Luna confirma que está parada; automatización PAUSED. No implementar, revisar nuevas entregas, probar, empaquetar, instalar ni enviar siguientes bloques hasta nueva instrucción del usuario. Conservar cambios existentes. El encargo siguiente queda como histórico, no activo.

# Ayuda contextual — trabajo detenido

Actualizado por Codex, revisor/coordinador, 2026-10-10 20:43 Europe/Madrid.

**Trabajo vigente: corregir R12–R15 de B05.2b1 / C010 según REVISION_B05_AYUDA_CONTEXTUAL_06.md. No volver a B04 ni iniciar otros nodos.**

Este archivo resume la secuencia autorizada por el usuario para evitar retomar un encargo histórico después de compactar contexto. No amplía permisos ni sustituye las instrucciones humanas. El encargo humano original pide implementar `ENCARGO_LUNA_AYUDA_CONTEXTUAL.md` completo; el usuario ha autorizado a Codex a enviar todas las tareas restantes a este mismo chat y revisar las entregas.

- B00–B04 aprobados. No requieren reinicio ni nuevas pruebas.
- B05.1 aprobado: siete fichas genéricas y UUID.
- B05.2a/C009 Batch aprobado e instalado: dictamen `REVISION_B05_AYUDA_CONTEXTUAL_05.md`.
- C010 Process Designer sigue parcial; B05.3 y B06 esperan.

## Corrección vigente

Completar semántica de 30 parámetros/146 opciones; corregir negativos reales y ES (CHARSET_CONVERT US-ASCII sustituye, no falla); integrar borrar selección nodo/conexión; probar texto realmente renderizado, teclado y cambio de idioma en mismo shell. Matriz granular y evidencia válida. Véase dictamen 06 para reproducción y aceptación. Conservar infraestructura existente y 36 IDs siguientes.

## Alcance que se conserva

1. Todas las acciones/controles/menús generales reales de Process Designer, con ayuda directa accesible, ES/EN, teclado y significado/efectos/condiciones: ejecutar/simular/cancelar, abrir/guardar, editar, presets, crear/conectar/reversa/borrar, inspector/focus, resultados/ventana y demás controles efectivos.
2. Todos los nodos de Inputs, Outputs, Conversions, Plumbing y Utilities: 36 tipos. Inventario exacto ya leído en el turno `01a126f5-cadd-7913-a6c2-4f854fae0a2e` antes de compactar:

| Categoría | Tipos |
|---|---|
| Inputs | CONSOLE_INPUT, FILE_INPUT |
| Outputs | CONSOLE_OUTPUT, FILE_OUTPUT |
| Conversions | BASE64, BASE64_ENCODE, BASE64_DECODE, BASE64URL_ENCODE, BASE64URL_DECODE, HEX_ENCODE, HEX_DECODE, UTF8_ENCODE, UTF8_DECODE, BASE32_ENCODE, BASE32_DECODE, BASE58_ENCODE, BASE58_DECODE, BASE58CHECK_ENCODE, BASE58CHECK_DECODE, EBCDIC_ENCODE, EBCDIC_DECODE, COMPRESS, DECOMPRESS, CHARSET_CONVERT |
| Plumbing | CONCAT, SLICE, PAD, UNPAD, XOR, ASSERT_EQUALS |
| Utilities | ASN1_DECODE, CHECK_DIGIT_CALC, CHECK_DIGIT_VERIFY, MODULAR_ARITHMETIC, UUID_GENERATE, BYTE_STATISTICS |

Cotejar con NodeCatalog vivo. Para cada nodo: propósito, puertos/representaciones, parámetros/defaults/unidades/límites, condiciones y diferencias por opción, ejemplo concreto y error, con consumidor efectivo. Base de familia más diferencias permitida; paráfrasis del nombre y ejemplos genéricos no cuentan. Otros 158 nodos permanecen borradores; C010 `complete=false` hasta cierre integral.

## Entrega y restricciones

Implementar código/recursos/pruebas/inventario/matriz directamente y entregar un resultado revisable. No terminar solo con plan ni pedir confirmación ya concedida. Preservar datos/resultados/grafo/conexiones/trabajo; consulta no ejecuta acciones ni lee/publica valores libres o secretos. Contexto sigue nodo/acción/campo, ES/EN a 1800/900, modos off, teclado, primera/reapertura compacta, navegación/idioma y lifecycle. Usar renderer existente.

Pruebas apropiadas de consumidores y cambios, Maven secuencial y preferencias aisladas; conservar XML nuevos y atribución, sin repetir suites sin causa. Inventario auditable por control y nodo/parámetro/opción, regla/ejemplo/fuente/evidencia/pendiente. Leer fuentes con salidas acotadas; no volcar documentos/controller completos si basta una sección. Tras compactar, consultar primero este archivo y el registro de coordinación.

Luna modifica aplicación/pruebas/matriz; Codex dictámenes/registro y este puntero. Respetar JOSE/tutorial y cambios ajenos; sin agentes/chats/modelos/esfuerzo nuevos, commits/push, motores/algoritmos/parámetros, empaquetado o instalación durante implementación. Al entregar esperar dictamen. El siguiente bloque solo se envía tras revisión; todos los 194 nodos y B05.3/B06–B15 continúan obligatorios.
