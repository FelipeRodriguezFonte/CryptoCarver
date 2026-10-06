# JOSE capability phase 5 map — OKP generation / Ed448

Encargo 73 phase 2, following verified privacy phase 4 on `codex/jose-capabilities-2`.

The crypto layer already handles Ed25519/Ed448 and X25519/X448, but UI generation hardcodes Ed25519 for EdDSA. Add an OKP curve selector in `jose.fxml`, new FXML fields and one-line coordinator delegates in `JOSEController`, and curve/use/algorithm coordination in `JoseJwkCoordinator`. Ed curves use `sig`/EdDSA; X curves use `enc`/ECDH-ES. Retain the legacy default Ed25519 generation and the public controller API. No crypto or dependency changes are expected.

Add `JoseOkpCharacterizationUITest`: absent selector must fail before implementation; exercise all four curves, Ed448 JWT and arbitrary JWS signing/verification with independent JDK Ed448 signatures, both JWKS rotation keys by kid, JWK↔PEM preservation, EN/ES labels/readable errors/privacy notices, and all visibility profiles. Only fixed labels/check outcomes enter its digest. Generated keys/IDs/signatures never enter the transcript. Fixtures restore settings and preserve Shelf/history.

Sources: [RFC 8037 Sections 2/3 and Appendix A](https://www.rfc-editor.org/rfc/rfc8037.html). Existing `EdDsaJwsTest` reproduces Appendix A.1/A.4 Ed25519; `JoseCapabilitiesTest` reproduces A.6/A.7 XDH vectors. Ed448 has no JOSE appendix vector; use independent JDK interoperability and round trips. Run the three required phase gates separately with fresh Surefire reports, one Maven process at a time.

## Test en rojo

`JoseOkpCharacterizationUITest` ejecutado con JavaFX real: 1 test, 1 fallo, 0 errores. Falla en `OKP curve selector is missing` antes de implementar. Log: `target/jose-cap-5-red.log`.

## Implementación y transcripción

Selector OKP en el coordinador: Ed25519 (predeterminado), Ed448, X25519 y X448; el cambio de curva selecciona sig/EdDSA o enc/ECDH-ES coherentemente. No se necesitan cambios criptográficos. Se añaden campos FXML y delegados de una línea. El constructor de View del test de privacidad se adapta mecánicamente a los dos controles nuevos; no cambian sus aserciones ni digest.

El test nuevo tenía una aserción inglesa sobre la etiqueta española: corregida para comprobar Signature=VALID en el resultado estructurado. También se corrigió el nombre del accessor del detalle (name). Los fallos eran del test nuevo. Todas las comprobaciones funcionales pasaron antes de fijar el digest inicial: `3794f33085d21f997300f67535bdd7e731e7729e0573e82141fc16af1adc76b1`. Solo se registran etiquetas, estados y marcadores; ninguna salida aleatoria.

Primera ejecución general: {'reports': 441, 'tests': 2900, 'failures': 0, 'errors': 0, 'skipped': 1}. En la revisión se añadió sincronización también al cambiar el algoritmo, preservando las variantes ECDH-ES+KW. Se repetirán las puertas tras este arreglo.

Puerta 1 final: `mvn -o -q test -Plow-cpu`: {'reports': 441, 'tests': 2900, 'failures': 0, 'errors': 0, 'skipped': 1}.

Puerta 2: `mvn -o -q test -Plow-cpu -DrunUiTests=true`: {'reports': 123, 'tests': 539, 'failures': 0, 'errors': 0, 'skipped': 0}.

Puerta 3: `mvn -o -q -Plow-cpu -DrunUiTests=true -Dtest.mode=true -Dprism.order=sw -Dgroups=ui -Dsurefire.reuseForks=false test`: {'reports': 123, 'tests': 539, 'failures': 0, 'errors': 0, 'skipped': 0}.

Las tres puertas finales pasan. Digest nuevo y anteriores verificados sin cambios. Presupuestos: estilos FXML 0, emojis 325.
