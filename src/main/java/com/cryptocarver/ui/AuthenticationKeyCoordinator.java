package com.cryptocarver.ui;

import com.cryptocarver.crypto.AsymmetricKeyOperations;
import com.cryptocarver.model.MaterialDetectionResult;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.util.function.Supplier;

/** Loads and selects Authentication signature keys into the shared key cache. */
final class AuthenticationKeyCoordinator {
    record View(Supplier<ComboBox<String>> algorithm,
                Supplier<TextArea> privateKeyArea,
                Supplier<TextArea> publicKeyArea,
                Supplier<MenuButton> privateKeyShelf,
                Supplier<MenuButton> publicKeyShelf,
                Supplier<Label> keyStatus,
                Supplier<AuthenticationKeyState> keys) { }

    private final View view;
    private final Supplier<StatusReporter> reporter;

    AuthenticationKeyCoordinator(View view, Supplier<StatusReporter> reporter) {
        this.view = view;
        this.reporter = reporter;
    }

    private StatusReporter reporter() { return reporter.get(); }

    void handleLoadPrivateKey() {
        TextArea privateKeyArea = view.privateKeyArea().get();
        IngestionUIHelper.loadFile(resolveWindow(privateKeyArea), privateKeyArea,
                view.keyStatus().get(), () -> loadPrivateKey(privateKeyArea.getText()),
                MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY);
    }

    void handleLoadPublicKey() {
        TextArea publicKeyArea = view.publicKeyArea().get();
        IngestionUIHelper.loadFile(resolveWindow(publicKeyArea), publicKeyArea,
                view.keyStatus().get(), () -> loadPublicKey(publicKeyArea.getText()),
                MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY,
                MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);
    }

    void handlePastePrivateKey() {
        TextArea privateKeyArea = view.privateKeyArea().get();
        IngestionUIHelper.pasteFromClipboard(privateKeyArea, view.keyStatus().get(),
                () -> loadPrivateKey(privateKeyArea.getText()),
                MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY);
    }

    void handlePastePublicKey() {
        TextArea publicKeyArea = view.publicKeyArea().get();
        IngestionUIHelper.pasteFromClipboard(publicKeyArea, view.keyStatus().get(),
                () -> loadPublicKey(publicKeyArea.getText()),
                MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY,
                MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);
    }

    void handlePopulatePrivateKeyShelf() {
        TextArea privateKeyArea = view.privateKeyArea().get();
        IngestionUIHelper.populateShelfMenu(view.privateKeyShelf().get(), privateKeyArea,
                view.keyStatus().get(), () -> loadPrivateKey(privateKeyArea.getText()),
                MaterialDetectionResult.MaterialType.PEM_PRIVATE_KEY);
    }

    void handlePopulatePublicKeyShelf() {
        TextArea publicKeyArea = view.publicKeyArea().get();
        IngestionUIHelper.populateShelfMenu(view.publicKeyShelf().get(), publicKeyArea,
                view.keyStatus().get(), () -> loadPublicKey(publicKeyArea.getText()),
                MaterialDetectionResult.MaterialType.PEM_PUBLIC_KEY,
                MaterialDetectionResult.MaterialType.PEM_CERTIFICATE);
    }

    void loadGeneratedKeyPair(java.security.KeyPair keyPair, String publicPem, String privatePem) {
        if (keyPair == null || keyPair.getPublic() == null || keyPair.getPrivate() == null) {
            throw new IllegalArgumentException("A complete generated key pair is required");
        }
        AuthenticationKeyState keys = view.keys().get();
        keys.setPublicKey(keyPair.getPublic());
        keys.setPrivateKey(keyPair.getPrivate());
        TextArea publicKeyArea = view.publicKeyArea().get();
        TextArea privateKeyArea = view.privateKeyArea().get();
        if (publicKeyArea != null) publicKeyArea.setText(publicPem == null ? "" : publicPem);
        if (privateKeyArea != null) privateKeyArea.setText(privatePem == null ? "" : privatePem);
        Label keyStatus = view.keyStatus().get();
        if (keyStatus != null) {
            keyStatus.setText("Generated " + keyPair.getPublic().getAlgorithm() + " key pair loaded");
            keyStatus.setStyle("-fx-text-fill: green; -fx-font-size: 10px;");
        }
    }

    private void loadPrivateKey(String pem) {
        AuthenticationKeyState keys = view.keys().get();
        Label keyStatus = view.keyStatus().get();
        ComboBox<String> algorithm = view.algorithm().get();
        try {
            String selectedAlgo = algorithm.getValue();
            if (selectedAlgo == null) selectedAlgo = "RSA";

            if (selectedAlgo.contains("Ed25519")) {
                keys.setPrivateKey(AsymmetricKeyOperations.importEd25519PrivateKeyPEM(pem));
            } else if (selectedAlgo.contains("ECDSA")) {
                keys.setPrivateKey(AsymmetricKeyOperations.importECPrivateKeyPEM(pem));
            } else {
                keys.setPrivateKey(AsymmetricKeyOperations.importPrivateKeyPEM(pem));
            }

            String keyType = keys.privateKey().getAlgorithm();
            int keySize = getKeySize(keys.privateKey());
            keyStatus.setText(String.format("Private: %s %d bits", keyType, keySize));
            keyStatus.setStyle("-fx-text-fill: green; -fx-font-size: 10px;");
            reporter().updateStatus("Private key loaded: " + keyType);

        } catch (Exception e) {
            keys.setPrivateKey(null);
            keyStatus.setText("Error loading private key");
            keyStatus.setStyle("-fx-text-fill: red; -fx-font-size: 10px;");

            String help = "";
            String selectedAlgo = algorithm.getValue();
            if (selectedAlgo != null && selectedAlgo.contains("Ed25519")) {
                help = "\n\nEnsure you are loading a valid Ed25519 PKCS#8 private key.";
            } else if (e.getMessage().contains("RSA")) {
                help = "\n\nHint: Ensure the selected algorithm matches the key type.";
            }
            reporter().showError("Key Error", "Error loading private key: " + e.getMessage() + help);
        }
    }

    private void loadPublicKey(String pem) {
        AuthenticationKeyState keys = view.keys().get();
        Label keyStatus = view.keyStatus().get();
        ComboBox<String> algorithm = view.algorithm().get();
        try {
            String selectedAlgo = algorithm.getValue();
            if (selectedAlgo == null) selectedAlgo = "RSA";

            if (selectedAlgo.contains("Ed25519")) {
                keys.setPublicKey(AsymmetricKeyOperations.importEd25519PublicKeyPEM(pem));
            } else if (selectedAlgo.contains("ECDSA")) {
                keys.setPublicKey(AsymmetricKeyOperations.importECPublicKeyPEM(pem));
            } else {
                keys.setPublicKey(AsymmetricKeyOperations.importPublicKeyPEM(pem));
            }

            String keyType = keys.publicKey().getAlgorithm();
            int keySize = getKeySize(keys.publicKey());
            String currentText = keyStatus.getText();
            if (currentText.contains("Private")) {
                keyStatus.setText(String.format("%s | Public: %s %d bits",
                        currentText, keyType, keySize));
            } else {
                keyStatus.setText(String.format("Public: %s %d bits", keyType, keySize));
            }
            keyStatus.setStyle("-fx-text-fill: green; -fx-font-size: 10px;");
            reporter().updateStatus("Public key loaded: " + keyType);

        } catch (Exception e) {
            keys.setPublicKey(null);
            keyStatus.setText("Error loading public key");
            keyStatus.setStyle("-fx-text-fill: red; -fx-font-size: 10px;");

            String help = "";
            String selectedAlgo = algorithm.getValue();
            if (selectedAlgo != null && selectedAlgo.contains("Ed25519")) {
                help = "\n\nEnsure you are loading a valid Ed25519 public key.";
            } else if (e.getMessage().contains("RSA")) {
                help = "\n\nHint: Ensure the selected algorithm matches the key type.";
            }
            reporter().showError("Key Error", "Error loading public key: " + e.getMessage() + help);
        }
    }

    private int getKeySize(Object key) {
        try {
            if (key instanceof RSAKey rsaKey) return rsaKey.getModulus().bitLength();
            if (key instanceof ECKey ecKey) return ecKey.getParams().getOrder().bitLength();
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static Window resolveWindow(Control control) {
        return control != null && control.getScene() != null ? control.getScene().getWindow() : null;
    }
}
