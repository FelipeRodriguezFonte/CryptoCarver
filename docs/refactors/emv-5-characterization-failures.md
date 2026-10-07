# Fase 5: fallos antes de fijar la caracterización

Primera ejecución focalizada, sobre producción sin extraer: exit 1 en testCompile; la interfaz FxAction del nuevo soporte era privada y la subclase no podía usar la lambda. Se corrigió a protected en el soporte nuevo. No se modificó producción ni un test existente. Log: target/emv5-characterization.log.

Segunda ejecución: exit 0, 1 informe / 1 prueba / 0 fallos, errores u omitidas. Se fijó SHA-256 `1fddbe0e74ac769692e229cc638067924e109be447fa9648c34987d82bc92d25` sobre transcripción UTF-8 sin salto final, tras verificar éxito sobre código sin extraer. El transcript completo está en target/emv-sm-75-transcript.txt. Solo se fijan salidas de aplicación y criptografía determinista de claves inventadas; sin diagnósticos JDK/proveedor.
