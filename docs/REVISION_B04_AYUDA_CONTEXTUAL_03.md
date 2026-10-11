# Tercera revisión B04 — retorno desde auxiliares

Fecha: 10 de octubre de 2026. Entrega: `01a12661-5fd2-7e62-b237-4418455e9e9d`. Revisor: Codex.

**Dictamen: REQUIERE CORRECCIÓN. R1/R2/R4/R5 cerrados funcionalmente. R3 conserva un defecto de restauración de variante al volver desde A008. B04 sigue pendiente y B05 no comienza.**

## Correcciones verificadas

Se contrastaron matriz/inventario, el encargo y el dictamen anterior, código y pruebas actualizados. La ayuda abierta sigue archivo/líneas al alternar el checkbox con contexto de archivo activo y las consultas de clave/IV/AAD/resultado usan la dirección y variante actuales. El updater no abre una ventana por actualizar contexto, y el modo desactivado permanece cubierto. RSA al borrar el área pública usa el fallback público vigente o elimina el módulo: la sonda muestra `RSA_CLEARED_BITS=0 state=null` cuando falta el par compartido. La prueba inspeccionada comprueba también fallback de otro módulo y cambio de padding. El máximo del tweak se consume desde `FormatPreservingEncryption.maximumTweakLength()`.

Sonda independiente ES/EN a 1800/900 contra clases compiladas, preferencias temporales y pares RSA sintéticos: `target/b04-review/reviewer/closing-expanded-probe.log`. Confirma ambos cierres funcionales y conserva los 41 bindings OpenPGP. No modifica la aplicación ni archivos de entrada.

## R3 pendiente — variante antigua restaurada después de A008

La sonda ampliada adicional `B04ReturnProbe.java`, log `target/b04-review/reviewer/closing-return-probe.log`, termina correctamente y reproduce en las cuatro combinaciones:

1. Activar líneas, abrir cifrar y consultar clave: acción `encrypt_lines`.
2. Abrir el `?` de A008 y desactivar líneas. A008 debe conservar su contexto de análisis; no se pide cambiar esa ficha por la de archivo.
3. Navegar a FPE y volver a File Cipher.
4. Se restaura `actionId=encrypt_lines` con `FILE_LINES=false`.

Resultado exacto:

```
AFTER_A008_RETURN_ACTION=encrypt_lines lines=PublicSelection[code=false]
```

El listener de variante retorna cuando el contexto actual es auxiliar; la memoria guardada conserva la acción antigua y el regreso restaura esa acción sin reconciliarla con el selector real. La prueba nueva regresa desde A008, pero no cambia la variante durante el contexto auxiliar. Este contrato estaba expresamente incluido en R3 del segundo dictamen.

**Cierre:** al restaurar/reabrir el contexto File Cipher, reconciliar toda acción cifrar/descifrar dependiente de variante con el modo efectivo, manteniendo dirección y campo. Aplicar también al retorno de A001/A002 cuando corresponda y a consultas locales desde el auxiliar. Mantener la ficha auxiliar mientras se consulta, sin forzar otra vista por cambiar checkbox. Regresión ambos sentidos, cifrar/descifrar, ES/EN 1800/900, cambio de selector durante ayuda auxiliar y regreso real; datos/resultados/archivos intactos. No modificar el comportamiento operativo del handler.

## Rectificación de evidencia

La cifra declarada de **12 pruebas nuevas** no corresponde a la ejecución 15:19:14–15:19:32 UTC. Los cuatro casos UI sí son nuevos. El XML copiado FPE, con ocho casos, tiene fecha **15:00:01 UTC** y propiedad `test=OperationHelpRegistryTest,RsaPaddingLimitsTest,FormatPreservingEncryptionTest,FxmlContractTest`, `runUiTests=false`: pertenece al cierre anterior. El comando mixto del `run.meta` usó el perfil UI; no ejecutó esos ocho casos FPE.

Codex ejecutó secuencialmente las verificaciones pertinentes tras el cambio compartido: **14 UI compartidas** y **una unidad FPE** (`ff1AcceptsA1024ByteTweakAndRejectsTheNextByte`, con `runUiTests=false`, accede al getter nuevo y contrasta límite/límite+1), todas correctas, sin fallos/errores/omisiones. XML nuevos conservados en `target/b04-review/reviewer/closing-shared-ui-reports/`; logs `closing-shared-ui.log` y `closing-fpe-unit.log`. No se suman a repeticiones históricas como pruebas distintas. Estas verificaciones cierran la carencia de evidencia FPE de la última entrega y no requieren repetir la suite entera.

Actualizar la matriz y `correction-remediation-verified/run.meta` para identificar cuatro UI nuevas y ocho FPE históricas, con referencia a esta unidad posterior del revisor. No atribuir al comando mixto la ejecución de esas ocho pruebas. El informe previo de 22 unidad/19 UI conserva su atribución propia. `git diff --check` pasó en la revisión anterior; volver a comprobarlo tras la corrección.

## Encargo pendiente

Luna corrige únicamente el remanente R3 y la atribución documental, conservando R1/R2/R4/R5 y el alcance completo B04. No modificar documentos del revisor, JOSE/tutorial25, comportamiento criptográfico, modelos ni delegaciones. Prueba focalizada con preferencias aisladas y XML nuevos, Maven secuencial. B05 se enviará después del cierre completo.
