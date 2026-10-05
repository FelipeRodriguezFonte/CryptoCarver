# Encargo 71 — detenido por la puerta CI de fase 2

El encargo **no está completado**. Se detiene según la regla expresa de parada: la fase 2 no pasa limpia su puerta CI, aunque el fallo se reproduce con el código existente de main. No se inicia otra fase y no se relaja ningún test.

Trabajo exclusivamente en `/Users/feliperodriguezfonte/dev/CryptoCarver-jose-3`, rama `codex/jose-controller-1`, base/main `4e44749d0a49ec1e57ab609ec8600661d9883cfa`. Una sola Maven a la vez. Sin merge, push ni cambio de rama.

| Fase | Estado | Líneas antes → después | Motivo |
|---|---|---:|---|
| 1 JWT/JWS | Hecha | 2141 → 1828 | Mapa, caracterización, extracción y tres puertas verdes tras justificar la aserción fuente obsoleta. |
| 2 JWE | Parcial | 1828 → 1608 | Extracción y digests verificados; default/UI verdes; CI falla en tres pruebas de ciclo de vida. |
| 3 JWK/JWKS | Saltada | 1608 → 1608 | No iniciada por la parada de fase 2. |
| 4 Inspector/arranque | Saltada | 1608 → 1608 | No iniciada por la parada de fase 2. |

La medición real de partida es 2141 líneas y 101 métodos explícitos, incluidos constructores, frente a la estimación 2142/105 del encargo. El script `jose_method_map.py` empareja llaves excluyendo literales y comentarios. Fase 1 mueve 16 métodos; fase 2 mueve 7, contando sobrecargas. Ambos coordinadores usan record View, proveedores vivos, Supplier<StatusReporter>, getters perezosos y delegados de una línea. No capturan controladores. La API pública y los nombres/tipos FXML coinciden con main.

## Puertas y recuentos

Comandos ejecutados, por fase y sin Maven paralelo:

1. `mvn -o -q test -Plow-cpu`
2. `mvn -o -q test -Plow-cpu -DrunUiTests=true`
3. `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test`

| Fase | Puerta | Resultado | Tests | Fallos | Errores | Omitidos | XML |
|---|---|---|---:|---:|---:|---:|---:|
| 1 | Default, primera | Fallida, contrato fuente obsoleto | 2892 | 1 | 0 | 1 | 435 |
| 1 | Default, tras ajustar propietario | Verde | 2892 | 0 | 0 | 1 | 435 |
| 1 | UI | Verde | 531 | 0 | 0 | 0 | 117 |
| 1 | CI | Verde | 531 | 0 | 0 | 0 | 117 |
| 2 | Default | Verde | 2895 | 0 | 0 | 1 | 436 |
| 2 | UI | Verde | 534 | 0 | 0 | 0 | 118 |
| 2 | CI | Fallida | 534 | 3 | 0 | 0 | 118 |
| 3 | Todas | No ejecutadas | — | — | — | — | — |
| 4 | Todas | No ejecutadas | — | — | — | — | — |

Se borró target/surefire-reports antes de cada puerta. El recuento CI usa exclusivamente los XML recién producidos y se guardó antes de las repeticiones aisladas en `jose-controller-validation.json`. La propiedad explícita runUiTests activa el perfil de groups=ui; las pruebas headless también se comprobaron en default.

## Digests portables verificados

Cada digest se verificó antes y después de su extracción, tras corregir los defectos de privacidad. No contienen salidas aleatorias, rutas, fechas, tiempos ni textos de excepción de JDK/proveedores. El suplemento crit real se verificó aisladamente con el controlador anterior a la extracción y con el extraído.

| Transcripción | SHA-256 |
|---|---|
| JWT/JWS principal | `526ed1435a5950126aae7065abbaeee3b6c9bb81c8cee77c3a4841e7e599537e` |
| JWT crit real | `0776e413f48a43f8d7afa8f0e4dadee462ef2aae969b4fb41d71e268fc7b499a` |
| JWE serializaciones/opciones/privacidad | `898aa2dc9da06352c580966e0a54dee58d8db86d94ea2bcc3febe5beb7d42401` |
| JWE algoritmos/avisos/CEK/errores | `784d6a61e011dee39d8c2c5f6798bd4a29484f2fbc86d8f041d4e39ed69272fd` |
| Capacidades existentes, sin modificar | `bc36301eed279b7a7c7a83ac29214da2dcd1fc9e79eb3a52e034261dea5e64db` |

## Fallos y comprobación contra main

La caracterización detectó tres salidas descifradas clasificadas PUBLIC por omisión: JWT anidado, JWE Compact y JWE JSON. Se anotó cada fallo antes de arreglarlo, con un commit por reparación que clasifica exclusivamente la salida como SECRET. Los constructores afectados coinciden con main en esa clasificación original. La fuga se reprodujo antes de las correcciones en el visor expandido bajo MASKED; la primera caracterización JWT se ejecutó sobre el controlador de main. Las ramas JWE se comprobaron sobre su código sin extraer, idéntico a main salvo delegados de avisos. No se modificó ningún servicio crypto.

La puerta CI de fase 2 falla únicamente en ExpandedViewerLifecycleUITest: closingTableViewerDropsItsSnapshotAndScene, shellShutdownClosesAnOpenResultWindow y closingTextViewerReleasesItsSceneWhileViewerRemainsAlive. Las tres llegan a ShellWindowLifecycleUITest.assertReleased: una Stage cerrada continúa alcanzable.

- Repetición aislada con las opciones CI y `-Dtest=ExpandedViewerLifecycleUITest`: 3 tests, 3 fallos, 0 errores/omitidos.
- Comparación aislada sustituyendo temporalmente JOSEController por el de main 4e44749 en este mismo worktree: 3 tests, los mismos 3 fallos, 0 errores/omitidos. El controlador extraído se restauró al terminar.
- ModernMainController, las clases de ventanas y los dos tests de ciclo de vida no tienen diferencias respecto a main. Los coordinadores nuevos permanecen en el classpath, pero estas pruebas no los usan. No se ejecutó toda la suite de main ni se cambió de rama.

Esto confirma reproducción con el código existente de main en el entorno local. No demuestra la causa exacta ni contradice el CI histórico verde; tampoco convierte la puerta actual en verde. No se atribuye el fallo a la extracción ni se omite el bloqueo.

Un intento temprano de compilar coincidió con la escritura del nuevo test delegado y falló sintácticamente: error de coordinación propio, anotado y corregido antes de reanudar Maven, sin cambios ajenos ni otra Maven. La primera prueba crit usaba un override correctamente rechazado por el generador; se corrigió la expectativa y se añadió una transcripción con un token crítico real. Ningún digest cambió sin explicación.

## Privacidad y alcance

Fixtures con datos inventados, EN/ES y FULL_LAB/MASKED/REDACTED. Verifican renderizado del shell, historial, captura real de Shelf, estado e inspector con las políticas existentes. Settings restaurados, Shelf global inalterado e historial aislado limpiado. No se publican CEKs manuales ni secretos en resultados; los avisos públicos siguen visibles. Se revisaron los puntos de logging/telemetría de los métodos extraídos; no se añade telemetría ni se imprimen secretos.

No se tocó **ningún fichero de crypto/**, **pom.xml**, ModernMainController, UiStateSnapshot, StatusReporter ni OperationResult. Tampoco las cuatro pruebas JOSE originales exigidas. El único test existente ajustado es SpecializedFeedbackHeadlessTest: las comprobaciones de presencia de claves/traducciones siguen iguales, consultando el nuevo propietario de JWT/JWS y JWE. Cada caso está justificado en su mapa.

## Commits y reversión

Mapas, caracterizaciones, extracciones y correcciones tienen commits separados. El inventario, fixture y soporte común están en commits propios para conservarlos al revertir una fase que comparte infraestructura con otra. Se separaron con un índice Git temporal, sin checkout y verificando igualdad exacta del árbol final probado. No se afirma que revertir fases fuera de orden esté libre de conflictos de contexto; deben conservarse los helpers compartidos y ajustarse los delegados de la fase revertida.

Commits desde main, en orden (el commit final del informe se indica en la entrega):

```
1f5cace docs(jose): add shared brace-balanced inventory tool
817045e docs(jose): map JWT and JWS extraction boundaries
f90957f test(jose): add shared UI characterization fixture
cd270a5 test(jose): characterize JWT flows and record nested plaintext leak
48c2766 fix(jose): classify decrypted nested JWT plaintext as secret
e6b09da test(jose): freeze portable JWT and JWS transcript after privacy fix
ea40a16 refactor(jose): add shared live reporting and warning helpers
c835d90 refactor(jose): extract JWT and JWS UI coordinator
fea8e24 test(jose): follow extracted owners in localization source contract
57ad652 test(jose): verify real critical headers before and after extraction
699e55f docs(jose): map JWE state and manual CEK extraction
8efe9bc test(jose): characterize JWE flows and record compact plaintext leak
21888d9 fix(jose): classify compact JWE decrypted output as secret
d69085f docs(jose): record JSON decrypted plaintext leak before repair
ebabd50 fix(jose): classify JSON JWE decrypted output as secret
df3096a test(jose): freeze portable JWE transcripts after plaintext fixes
dcf987c refactor(jose): extract JWE UI and manual CEK coordinator
23def1f test(jose): verify JWE localization keys in extracted owner
b463f51 docs(jose): record phase two CI blockade reproduced against main
```

La rama queda limpia tras el commit final de este informe. El bloqueo CI permanece abierto y las fases 3/4 pendientes.
