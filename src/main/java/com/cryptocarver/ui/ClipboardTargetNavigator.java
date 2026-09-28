package com.cryptocarver.ui;

import com.cryptocarver.model.ClipboardEntry;
import com.cryptocarver.model.ShelfPackage;
import java.util.function.Consumer;

/** Routes Shelf items into the currently supported operation forms. */
final class ClipboardTargetNavigator {
    private final GenericController generic;
    private final CipherController cipher;
    private final XMLSignatureController xml;
    private final WssSecurityController wss;
    private final PaymentsController payments;
    private final KeysController keys;
    private final JOSEController jose;
    private final Consumer<String> navigate;
    private final Consumer<String> expandGeneric, expandCipher, expandXml, expandWss, expandPayments, expandKeys;
    private final Runnable showJose;
    private final Consumer<String> status;

    ClipboardTargetNavigator(GenericController generic, CipherController cipher,
            XMLSignatureController xml, WssSecurityController wss,
            PaymentsController payments, KeysController keys, JOSEController jose,
            Consumer<String> navigate, Consumer<String> expandGeneric, Consumer<String> expandCipher,
            Consumer<String> expandXml, Consumer<String> expandWss, Consumer<String> expandPayments,
            Consumer<String> expandKeys, Runnable showJose, Consumer<String> status) {
        this.generic=generic; this.cipher=cipher; this.xml=xml; this.wss=wss; this.payments=payments; this.keys=keys; this.jose=jose;
        this.navigate=navigate; this.expandGeneric=expandGeneric; this.expandCipher=expandCipher; this.expandXml=expandXml;
        this.expandWss=expandWss; this.expandPayments=expandPayments; this.expandKeys=expandKeys; this.showJose=showJose; this.status=status;
    }

    void fill(String targetType, String value, ClipboardEntry.Format format, ShelfPackage packageData) {
        if (value == null) return;
        switch (targetType) {
            case "MANUAL_CONVERSION" -> { if (generic != null) { navigate.accept("Manual Conversion"); generic.fillManualConversionInput(value, format); expandGeneric.accept("Manual Conversion"); } }
            case "SYMMETRIC_CIPHER" -> { if (cipher != null) { navigate.accept("Symmetric Ciphers"); if (packageData != null) cipher.fillSymmetricCipherPackage(packageData); else cipher.fillSymmetricCipherInput(value, format); expandCipher.accept("Symmetric Ciphers"); } }
            case "HASHING" -> { if (generic != null) { navigate.accept("Hashing"); generic.fillHashInput(value, format); expandGeneric.accept("Hashing"); } }
            case "XML_SECURITY" -> { if (xml != null) { navigate.accept("XML Security"); xml.fillClipboardInput(value); expandXml.accept("Inspect Signed XML"); } }
            case "WSS_SECURITY" -> { if (wss != null) { navigate.accept("WSS Security"); wss.fillClipboardInput(value); expandWss.accept("Sign SOAP"); } }
            case "PAYMENTS" -> { if (payments != null) { navigate.accept("Payments"); payments.fillClipboardInput(value); expandPayments.accept("Clear PIN Blocks"); } }
            case "TR31" -> { if (keys != null) { navigate.accept("TR-31 Key Blocks"); keys.fillTR31KeyBlockInput(value); expandKeys.accept("TR-31 Key Blocks"); } }
            case "JOSE_JWT" -> { if (jose != null) { jose.fillJwtPayload(value); showJose.run(); } }
            default -> status.accept("Unsupported target: " + targetType);
        }
    }
}
