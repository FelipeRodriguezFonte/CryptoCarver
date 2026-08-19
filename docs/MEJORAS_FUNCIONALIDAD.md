# Mejoras de funcionalidad — CryptoCarver (análisis 2026-08-03)

Basado en `docs/CRYPTOCARVER_ROADMAP_EVOLUCION.md`, el README y grep directo sobre `src/main/java`. No incluye UX/interfaz (ver `docs/MEJORAS_UX.md`).

## 1. Validación de revocación real (OCSP/CRL) fuera de XAdES

**Estado:** solo el flujo XAdES consulta OCSP en vivo (`OnlineOCSPSource`, `XMLSignatureOperations.java:351`). PAdES, CMS y el generador de cadenas de certificados construyen sin consulta de red:
- `PadesOperations.java:189` — comentario explícito: "No CRL/OCSP URL is fetched".
- `CertificateGenerator.java:828` — cadena construida "without OCSP/CRLDP network lookups".

Es decisión deliberada de laboratorio, pero crea inconsistencia entre módulos de firma y limita el uso como validador realista.

**Qué hacer:** extender el cliente OCSP/CRL ya usado en XAdES a PAdES (Baseline-T/LTV) y al módulo de certificados/CMS, como opción explícita ("validar revocación en línea") que el usuario active — no por defecto, para no romper el modo laboratorio.

## 2. PKCS#11/HSM real — perfiles de proveedor

**Estado:** integración vía SunPKCS11/SoftHSM existe pero incompleta (README, roadmap línea 324): falta enumeración de mecanismos/slots, perfiles de proveedor sin depender del PIN, diagnóstico de biblioteca nativa, e integración con XAdES.

**Qué hacer:**
- Listar slots y mecanismos disponibles de la librería PKCS#11 cargada antes de pedir el PIN.
- Perfiles de proveedor reutilizables (nombre de librería, parámetros) guardables sin credenciales.
- Diagnóstico de carga de la librería nativa con mensaje claro si falla (arquitectura, permisos, ruta).
- Conectar las claves del HSM como firmante disponible en el flujo XAdES.

## 3. EMV Option B

**Estado:** ausente en código (solo Option A implementado); el roadmap lo deja fuera "hasta contar con vectores públicos fiables" (línea 84).

**Qué hacer:** implementar derivación de claves de sesión EMV Option B cuando se disponga de vectores de test públicos verificables (NIST/EMVCo/pyemv); no implementar sin vectores, para no introducir una implementación no verificable en un módulo de pagos.

## 4. TR-31 — cobertura por lotes y matriz de versiones

**Estado:** faltan bloques opcionales completos y la matriz de versiones de header A/B/C/D; sin importación/exportación por lotes (roadmap Fase 7, líneas 441-448).

**Qué hacer:**
- Completar soporte de todos los campos opcionales de header por versión (A, B, C, D).
- Añadir importación/exportación por lotes (CSV/JSONL) reutilizando el motor de Batch Runner ya existente.

## 5. PAdES y ASiC — perfiles avanzados

**Estado (roadmap líneas 382-383):**
- PAdES Baseline-B/T: falta OCSP, LTV completo y política de confianza avanzada.
- ASiC-S/ASiC-E: falta XAdES, revocación, perfiles avanzados y LTV.

**Qué hacer:** una vez resuelto el punto 1 (OCSP real), extender PAdES a Baseline-LT/LTA con archivo de revocación embebido; añadir soporte XAdES dentro de contenedores ASiC.

## 6. WSS-Security — integración con Process Designer

**Estado:** el módulo WSS no está conectado al Process Designer (roadmap línea 398); falta también recorrido manual validado con SoapUI/ReadyAPI (líneas 86, 397).

**Qué hacer:** exponer las operaciones WSS (firma/cifrado de mensajes SOAP) como nodos del Process Designer para poder encadenarlas con otras operaciones; documentar un recorrido de verificación con un cliente SOAP real.

## 7. Huecos puntuales ya señalizados en código

- `PostQuantumController.java:417` — carga de clave PQC desde fichero no implementada.
- `JOSEController.java:1318` — previsualización manual de CEK no soportada para ciertos algoritmos JWE.
- `KeyCertificateFormatService.java:588,615` — conversión JWK OKP (Ed25519/Ed448) no soportada.

**Qué hacer:** cerrar estos tres antes de abordar bloques nuevos — son casos concretos y acotados, no rediseños.

## 8. Batch Runner — catálogo limitado

**Estado:** el motor de batch solo cubre SHA-256 y Base64URL (roadmap línea 540), pese a que la app soporta muchas más operaciones deterministas.

**Qué hacer:** ampliar el catálogo de operaciones batch a todas las operaciones deterministas sin estado de sesión (hashing, MAC con clave explícita, conversión de formatos, dígitos de control), reutilizando el registro de operaciones ya existente.

## 9. API REST local — cobertura mínima

**Estado:** solo 3 endpoints deterministas sin claves (roadmap líneas 552-557), pendiente de ampliación explícita.

**Qué hacer:** ampliar a las mismas operaciones que ya cubre la CLI/Batch (deterministas, sin claves ni cifrado), manteniendo la restricción de solo-loopback.

## 10. Exportación de informes PKI

**Estado:** listada como pendiente (roadmap línea 349); ya existen linter X.509 y diagnóstico de cadena que podrían alimentar un informe.

**Qué hacer:** exportar a Markdown/PDF el resultado del diagnóstico de cadena de certificados (linter X.509, validez, KeyUsage, revocación si se activa el punto 1) para uso en auditorías.

## 11. Nota — el roadmap está desactualizado en un punto

AES DUKPT aparece como pendiente en el roadmap (Fase 7) pero **ya está implementado y expuesto en UI** (`AesDukpt.java`, `PaymentsController.java`). Actualizar `CRYPTOCARVER_ROADMAP_EVOLUCION.md` para no reabrir trabajo ya hecho.

---

### Prioridad sugerida
1. Punto 7 (huecos puntuales) — acotados, cierran deuda visible sin abrir alcance nuevo.
2. Punto 1 (OCSP/CRL real) — desbloquea los puntos 5 y 10; es la pieza de mayor apalancamiento.
3. Punto 2 (PKCS#11 perfiles) — alto valor para el público de pagos/PKI del proyecto.
4. Puntos 4, 8, 9 — ampliaciones de cobertura ya diseñadas, sin riesgo arquitectónico.
5. Punto 3 (EMV Option B) — bloqueado por disponibilidad de vectores públicos, no por esfuerzo.
6. Punto 6 (WSS + Process Designer) — depende de que el Process Designer esté estable.
7. Punto 11 — trivial, hacerlo de paso.
