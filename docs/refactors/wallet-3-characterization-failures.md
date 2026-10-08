# Wallet 3 — caracterización antes de extracción

Primera ejecución sin extraer: 1 XML / 1 prueba / 0 fallos / 0 errores / 0 omitidas / exit 0. No hubo fallos observados antes de fijar el digest. El fixture instala el recorder después de navegar, según lo aprendido en la fase mdoc.

SHA-256 fijado: `3b6e931309fc81d8842d56448e61738288209a39cef8d668636d31587e46c50d`.

Issue, Resolve y Describe con token local generado y firmado. La URI sintética es solo un claim; no hay resolución de red. Firma ES256 y valor resuelto se verifican independientemente. Se excluyen firmas, fechas y bytes/longitud comprimidos; permanecen formas, longitud de firma, cantidad de entradas, valores y campos estables. Errores estructurales sin texto de excepción. Digest fijado comprobado de nuevo antes de extraer.
