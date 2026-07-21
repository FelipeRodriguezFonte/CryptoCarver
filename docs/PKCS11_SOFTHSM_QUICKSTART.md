# SoftHSM + PKCS#11: prueba rápida con CryptoCarver

Esta guía crea un token **de laboratorio** con una clave RSA no exportable para probar CryptoCarver. SoftHSM no sustituye un HSM físico ni debe usarse para secretos de producción.

Usaremos estos valores de ejemplo:

- etiqueta: `cryptocarver-lab`
- SO PIN: `12345678`
- User PIN: `123456`
- alias de clave: `cryptocarver-rsa`

## macOS

Instala SoftHSM y la utilidad de PKCS#11:

```bash
brew install softhsm opensc
mkdir -p "$HOME/.cryptocarver/softhsm/tokens"
```

Crea `~/.cryptocarver/softhsm/softhsm2.conf` con este contenido:

```text
directories.tokendir = /Users/TU_USUARIO/.cryptocarver/softhsm/tokens
objectstore.backend = file
log.level = ERROR
```

Activa la configuración e inicializa un token:

```bash
export SOFTHSM2_CONF="$HOME/.cryptocarver/softhsm/softhsm2.conf"
softhsm2-util --init-token --free --label cryptocarver-lab --so-pin 12345678 --pin 123456

MODULE="$(find "$(brew --prefix softhsm)" -name 'libsofthsm2.*' -type f | head -1)"
```

Para poder probar firma, JWS y CMS, crea el par RSA **y su certificado X.509** con `keytool`. Es importante usar este comando en lugar de crear solamente el par con `pkcs11-tool`: sin certificado, Java no expondrá la clave bajo un alias utilizable por CMS/JWS.

```bash
PKCS11_CFG="$HOME/.cryptocarver/softhsm/cryptocarver-pkcs11.cfg"
printf 'name = CryptoCarverSoftHSM\nlibrary = %s\nslotListIndex = 0\n' "$MODULE" > "$PKCS11_CFG"

keytool -genkeypair -alias cryptocarver-rsa -keyalg RSA -keysize 2048 \
  -dname 'CN=CryptoCarver SoftHSM Lab, O=CryptoCarver, C=ES' \
  -validity 365 -storetype PKCS11 -storepass 123456 \
  -providerClass sun.security.pkcs11.SunPKCS11 -providerArg "$PKCS11_CFG"
```

Comprueba el token y guarda la ruta de `MODULE`:

```bash
softhsm2-util --show-slots
pkcs11-tool --module "$MODULE" --login --pin 123456 --list-objects
keytool -list -storetype PKCS11 -storepass 123456 \
  -providerClass sun.security.pkcs11.SunPKCS11 -providerArg "$PKCS11_CFG"
```

## Windows

1. Instala SoftHSM2 de una distribución confiable y, para generar la clave desde consola, instala también OpenSC (`pkcs11-tool`). Ambas instalaciones deben ser de la misma arquitectura que tu Java, normalmente x64.
2. Crea `C:\Users\TU_USUARIO\.cryptocarver\softhsm\tokens`.
3. Crea `C:\Users\TU_USUARIO\.cryptocarver\softhsm\softhsm2.conf`:

```text
directories.tokendir = C:/Users/TU_USUARIO/.cryptocarver/softhsm/tokens
objectstore.backend = file
log.level = ERROR
```

En una nueva consola `cmd`:

```bat
set SOFTHSM2_CONF=%USERPROFILE%\.cryptocarver\softhsm\softhsm2.conf
softhsm2-util --init-token --free --label cryptocarver-lab --so-pin 12345678 --pin 123456
```

Localiza `softhsm2.dll` en la instalación y sustituye la ruta. Después crea el par RSA con certificado usando el `keytool` que acompaña a tu JDK:

```bat
set MODULE=C:\Program Files\SoftHSM2\lib\softhsm2.dll
set PKCS11_CFG=%USERPROFILE%\.cryptocarver\softhsm\cryptocarver-pkcs11.cfg
(
  echo name = CryptoCarverSoftHSM
  echo library = %MODULE%
  echo slotListIndex = 0
) > "%PKCS11_CFG%"

keytool -genkeypair -alias cryptocarver-rsa -keyalg RSA -keysize 2048 -dname "CN=CryptoCarver SoftHSM Lab, O=CryptoCarver, C=ES" -validity 365 -storetype PKCS11 -storepass 123456 -providerClass sun.security.pkcs11.SunPKCS11 -providerArg "%PKCS11_CFG%"
pkcs11-tool --module "%MODULE%" --login --pin 123456 --list-objects
keytool -list -storetype PKCS11 -storepass 123456 -providerClass sun.security.pkcs11.SunPKCS11 -providerArg "%PKCS11_CFG%"
```

## Conectarlo a CryptoCarver

1. Abre **Keys → Tools → PKCS#11 Token**.
2. En **Native library**, selecciona `MODULE` (`libsofthsm2` en macOS o `softhsm2.dll` en Windows).
3. Indica nombre `CryptoCarverToken`, **Slot list index** `0` y User PIN `123456`.
4. Pulsa **Connect & Inspect**. Debe aparecer el alias `cryptocarver-rsa`.
5. Prueba primero **Sign Data** con `486F6C61` y `SHA256withRSA`; después **Verify Signature**.

Para cifrado o MAC, el token necesita además una clave secreta con el mecanismo correspondiente. Para la primera prueba basta la clave RSA y las operaciones de firma/JWS.

Si `Slot list index 0` no conecta porque tienes varios tokens, revisa `softhsm2-util --show-slots` y prueba los índices consecutivos `0`, `1`, etc.; CryptoCarver usa el índice de la lista del proveedor Java, no el identificador numérico de slot que muestra SoftHSM.

## Ejecutar la integración real opcional

Con el token anterior disponible, puedes probar el puente JCA sin abrir la interfaz. El test queda omitido, no fallido, si faltan estas variables:

```bash
export SOFTHSM2_MODULE="$MODULE"
export CRYPTOCARVER_SOFTHSM_PIN=123456
export CRYPTOCARVER_SOFTHSM_ALIAS=cryptocarver-rsa
export CRYPTOCARVER_SOFTHSM_SLOT_INDEX=0
mvn test -Dtest=SoftHsmIntegrationTest
```

El test firma/verifica, crea CMS y JWS con la clave no exportable. No imprime el PIN ni exporta la clave privada.

Referencias: [SoftHSM](https://www.opendnssec.org/softhsm/), [Homebrew SoftHSM](https://formulae.brew.sh/formula/softhsm), [OpenSC](https://github.com/OpenSC/OpenSC).
