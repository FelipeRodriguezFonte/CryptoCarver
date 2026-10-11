# Revisión B03 — cierre C080/C081

Fecha: 10 de octubre de 2026. Codex revisa el turno `01a12606-cc7d-7fb0-9aad-f1f5951a497b`.

**Dictamen: B03 APROBADO.** R1–R3 cerrados. B00–B02 conservan aprobación. Procede B04 completo, incluidas C002–C005 y A008.

## Verificación de correcciones

R1: el shell guarda contexto por ID de operativa y valida acción/campo contra el catálogo destino. Navegación y acceso general restauran solo memoria válida de esa ruta; el adaptador publica selecciones vigentes. La sonda ES/EN, 1800/900, confirma primer acceso a C081 sin campo/acción ajenos y retorno a C080 con su propia acción decode/format_decode. La regresión ampliada comprueba ambos sentidos inmediatamente tras navegar, regreso campo→general y A001/A002, antes de pulsar otro botón local.

R2: C080 explica relleno configurable/default cuando corresponde; decode explica interpretación del bloque. C081 muestra relleno efectivo predeterminado o interno y explicita ausencia de selector. Se contrastó con la llamada real del handler sin padding explícito y con defaults del catálogo. VISA-2, ECI-2/3 y Docutel tienen cobertura, con selector claro discordante que no modifica C081. No se cambiaron reglas del motor.

R3: los campos de resultado/proteger/decodificar ES/EN ahora distinguen cifrado correcto con clave, resultado claro sin clave, clave de descifrado independiente y recuperación del PIN. Presencia de texto no se presenta como prueba de validez/protección. Las referencias locales conservan explicaciones de PAN por formato y particularidad de ISO-4 con campos generados separadamente.

## Alcance y evidencia

C080/C081 cubren catálogos actuales de formatos/rellenos, acciones y campos ES/EN; formato/dirección independientes, estado de claves separado, DES/TDES o AES ISO-4 según ruta, y datos en claro frente a protegidos. Las referencias por ID están en recursos empaquetados. No se atribuye ayuda restante de Payments/B14.

- Luna ejecutó **5 pruebas de catálogo** nuevas a las 15:51:09 y **1 regresión UI PIN** nueva a las 15:54:00 Europe/Madrid. XML inspeccionados en `target/b03-r1r3-review/unit/` y `ui/`, sin fallos/errores/omisiones. La matriz rectifica la atribución previa de 53 y los 41 XML antiguos.
- El revisor reejecutó **15 pruebas UI**: la PIN actual y 14 compartidas B00/B01, por el cambio común de retorno desde un campo. Correctas, XML del 16:03:09–16:03:27 en `target/b03-review/reviewer-closing-ui-reports/`, resumen fechado y log `reviewer-closing-ui.log`. La prueba PIN repetida no se suma como prueba distinta a la de Luna.
- Se conserva la evidencia criptográfica/funcional pertinente de la primera revisión: 41 pruebas criptográficas revalidadas por Codex, y 6 caracterizaciones PIN de Luna. El motor/handlers no cambiaron durante la corrección; no se repitieron esas suites por rutina.
- Sonda independiente `target/b03-review/B03Probe.java`: `reviewer-correction-probe.log` y `reviewer-closing-probe.log`. Producción ES/EN, 1800/900, transiciones de ruta, relleno protegido, activación real por teclado, reapertura compacta con acción conservada y **20 consultas locales por escenario**, sin claves de traducción visibles ni modificación de entradas/resultados. Terminó correctamente; no se cuenta como JUnit.
- `git diff --check` correcto. Preferencias aisladas, sin Maven paralelo ni conteos de XML ajenos a la ejecución declarada.

No queda deuda obligatoria de B03. La cobertura integral de 96 operaciones, 24 auxiliares y entradas dinámicas y el ensayo de recursos fuera del checkout siguen pendientes de los bloques siguientes/B15. Luna actualizará la matriz al estado aprobado durante B04; Codex registra el despacho y seguimiento.
