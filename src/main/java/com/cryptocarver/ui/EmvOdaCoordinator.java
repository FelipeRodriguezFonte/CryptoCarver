package com.cryptocarver.ui;

import com.cryptocarver.crypto.EmvOdaOperations;
import com.cryptocarver.model.OperationResult;
import javafx.scene.control.*;
import java.util.function.Supplier;

/** Coordinates the extracted EMV actions using the controller's existing controls. */
final class EmvOdaCoordinator {
    record View(Supplier<TextArea> odaIccCertificateArea,
            Supplier<TextArea> odaSsadArea,
            Supplier<TextArea> odaStaticDataArea,
            Supplier<TextArea> odaSdadArea,
            Supplier<TextInputControl> odaTerminalDataField,
            Supplier<TextInputControl> odaCidField,
            Supplier<TextArea> odaTransactionDataArea,
            Supplier<TextArea> odaCaModulusArea,
            Supplier<TextInputControl> odaCaExponentField,
            Supplier<TextArea> odaIssuerCertificateArea,
            Supplier<TextInputControl> odaIssuerRemainderField,
            Supplier<TextInputControl> odaIssuerExponentField,
            Supplier<TextInputControl> odaIccRemainderField,
            Supplier<TextInputControl> odaIccExponentField,
            Supplier<TextInputControl> odaPanField,
            Supplier<TextArea> odaResultArea) { }
    private final View view;
    private final Supplier<StatusReporter> reporter;
    EmvOdaCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }
    private String t(String key, Object... args) {
        return com.cryptocarver.service.I18nService.getInstance().text(key, args);
    }
    /** The test card's Unpredictable Number, so DDA and CDA stay reproducible. */
    private static final String TEST_UNPREDICTABLE_NUMBER = "01020304";
    private static final String TEST_PAN = "4761739001010119";

    private interface OdaStep {
        String run() throws Exception;
    }
    void handleOdaRecoverKeys() {
        runOda(() -> {
            StringBuilder report = new StringBuilder();
            EmvOdaOperations.IssuerCertificate issuer = recoverIssuer();
            report.append(EmvOdaOperations.describe(issuer));
            if (!text(view.odaIccCertificateArea().get()).isEmpty()) {
                report.append('\n').append(EmvOdaOperations.describe(recoverIcc(issuer)));
            }
            return report.toString();
        }, "EMV ODA Key Recovery");
    }

    void handleOdaVerifySda() {
        runOda(() -> {
            EmvOdaOperations.IssuerCertificate issuer = recoverIssuer();
            if (!issuer.passed()) {
                return EmvOdaOperations.describe(issuer)
                        + "\nThe issuer key did not come back clean, so the SSAD below was not checked.\n";
            }
            return EmvOdaOperations.describe(EmvOdaOperations.verifyStaticApplicationData(
                    text(view.odaSsadArea().get()), issuer.issuerPublicKey(), text(view.odaStaticDataArea().get())));
        }, "EMV SDA");
    }

    void handleOdaVerifyDda() {
        runOda(() -> {
            EmvOdaOperations.IccCertificate icc = recoverIcc(recoverIssuer());
            if (!icc.passed()) {
                return EmvOdaOperations.describe(icc)
                        + "\nThe ICC key did not come back clean, so the dynamic signature was not checked.\n";
            }
            return EmvOdaOperations.describe(EmvOdaOperations.verifyDynamicApplicationData(
                    text(view.odaSdadArea().get()), icc.iccPublicKey(), text(view.odaTerminalDataField().get())));
        }, "EMV DDA");
    }

    void handleOdaVerifyCda() {
        runOda(() -> {
            EmvOdaOperations.IccCertificate icc = recoverIcc(recoverIssuer());
            if (!icc.passed()) {
                return EmvOdaOperations.describe(icc)
                        + "\nThe ICC key did not come back clean, so the combined signature was not checked.\n";
            }
            return EmvOdaOperations.describe(EmvOdaOperations.verifyCombinedApplicationData(
                    text(view.odaSdadArea().get()), icc.iccPublicKey(), text(view.odaTerminalDataField().get()),
                    text(view.odaCidField().get()), text(view.odaTransactionDataArea().get())));
        }, "EMV CDA");
    }

    void handleOdaIssueTestCard() {
        runOda(() -> {
            java.security.KeyPair ca = odaKeyPair(1024);
            java.security.KeyPair issuer = odaKeyPair(768);
            java.security.KeyPair icc = odaKeyPair(512);

            String staticData = "70115A0844AAAAAAAAAAAAAA5F3401009F0702FF00";
            String expiry = java.time.YearMonth.now().plusYears(3).format(
                    java.time.format.DateTimeFormatter.ofPattern("MMyy"));

            EmvOdaOperations.IssuedCertificate issuerCertificate = EmvOdaOperations.signIssuerCertificate(
                    EmvOdaOperations.RsaPrivateKey.of((java.security.interfaces.RSAPrivateKey) ca.getPrivate()),
                    EmvOdaOperations.RsaPublicKey.of((java.security.interfaces.RSAPublicKey) issuer.getPublic()),
                    TEST_PAN.substring(0, 8), expiry, "000001");
            EmvOdaOperations.IssuedCertificate iccCertificate = EmvOdaOperations.signIccCertificate(
                    EmvOdaOperations.RsaPrivateKey.of((java.security.interfaces.RSAPrivateKey) issuer.getPrivate()),
                    EmvOdaOperations.RsaPublicKey.of((java.security.interfaces.RSAPublicKey) icc.getPublic()),
                    TEST_PAN, expiry, "000002", staticData);

            String ssad = EmvOdaOperations.signStaticApplicationData(
                    EmvOdaOperations.RsaPrivateKey.of((java.security.interfaces.RSAPrivateKey) issuer.getPrivate()),
                    "1234", staticData);

            String transactionData = "000000010000000000000000097801020304";
            String dynamicData = EmvOdaOperations.combinedDynamicData("1122334455667788", "80",
                    "A1B2C3D4E5F60718", EmvOdaOperations.transactionDataHashCode(transactionData));
            String sdad = EmvOdaOperations.signDynamicApplicationData(
                    EmvOdaOperations.RsaPrivateKey.of((java.security.interfaces.RSAPrivateKey) icc.getPrivate()),
                    dynamicData, TEST_UNPREDICTABLE_NUMBER);

            set(view.odaCaModulusArea().get(), EmvOdaOperations.RsaPublicKey.of(
                    (java.security.interfaces.RSAPublicKey) ca.getPublic()).modulusHex());
            set(view.odaCaExponentField().get(), EmvOdaOperations.RsaPublicKey.of(
                    (java.security.interfaces.RSAPublicKey) ca.getPublic()).exponentHex());
            set(view.odaIssuerCertificateArea().get(), issuerCertificate.certificate());
            set(view.odaIssuerRemainderField().get(), issuerCertificate.remainder());
            set(view.odaIssuerExponentField().get(), issuerCertificate.exponent());
            set(view.odaIccCertificateArea().get(), iccCertificate.certificate());
            set(view.odaIccRemainderField().get(), iccCertificate.remainder());
            set(view.odaIccExponentField().get(), iccCertificate.exponent());
            set(view.odaStaticDataArea().get(), staticData);
            set(view.odaPanField().get(), TEST_PAN);
            set(view.odaSsadArea().get(), ssad);
            set(view.odaSdadArea().get(), sdad);
            set(view.odaTerminalDataField().get(), TEST_UNPREDICTABLE_NUMBER);
            set(view.odaCidField().get(), "80");
            set(view.odaTransactionDataArea().get(), transactionData);

            return t("module.emv.oda.testCardIssued");
        }, "EMV ODA Test Card");
    }

    void handleOdaClear() {
        for (TextInputControl field : new TextInputControl[] {
                view.odaCaModulusArea().get(), view.odaCaExponentField().get(), view.odaIssuerCertificateArea().get(), view.odaIssuerRemainderField().get(),
                view.odaIssuerExponentField().get(), view.odaIccCertificateArea().get(), view.odaIccRemainderField().get(), view.odaIccExponentField().get(),
                view.odaStaticDataArea().get(), view.odaPanField().get(), view.odaSsadArea().get(), view.odaSdadArea().get(), view.odaTerminalDataField().get(),
                view.odaCidField().get(), view.odaTransactionDataArea().get(), view.odaResultArea().get()}) {
            if (field != null) field.clear();
        }
    }

    private EmvOdaOperations.IssuerCertificate recoverIssuer() {
        return EmvOdaOperations.recoverIssuerPublicKey(
                text(view.odaIssuerCertificateArea().get()), text(view.odaIssuerRemainderField().get()), text(view.odaIssuerExponentField().get()),
                EmvOdaOperations.RsaPublicKey.of(text(view.odaCaModulusArea().get()), text(view.odaCaExponentField().get())),
                text(view.odaPanField().get()));
    }

    private EmvOdaOperations.IccCertificate recoverIcc(EmvOdaOperations.IssuerCertificate issuer) {
        if (issuer.issuerPublicKey() == null) {
            throw new IllegalArgumentException(t("module.emv.oda.noIssuerKey"));
        }
        return EmvOdaOperations.recoverIccPublicKey(
                text(view.odaIccCertificateArea().get()), text(view.odaIccRemainderField().get()), text(view.odaIccExponentField().get()),
                issuer.issuerPublicKey(), text(view.odaStaticDataArea().get()), text(view.odaPanField().get()));
    }

    private static java.security.KeyPair odaKeyPair(int bits) throws Exception {
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(new java.security.spec.RSAKeyGenParameterSpec(bits, java.math.BigInteger.valueOf(3)));
        return generator.generateKeyPair();
    }

    private void runOda(OdaStep step, String operation) {
        try {
            String report = step.run();
            if (view.odaResultArea().get() != null) view.odaResultArea().get().setText(report);
            if (reporter.get() != null) {
                reporter.get().publish(OperationResult.forOperation(operation)
                        .output(report.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .status(t("module.emv.oda.status"))
                        .build());
            }
        } catch (Exception e) {
            if (view.odaResultArea().get() != null) {
                view.odaResultArea().get().setText(t("module.emv.oda.error", String.valueOf(e.getMessage())));
            }
        }
    }

    private static String text(TextInputControl field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private static void set(TextInputControl field, String value) {
        if (field != null) field.setText(value);
    }
}
