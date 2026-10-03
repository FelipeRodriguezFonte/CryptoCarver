package com.cryptocarver.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Accordion;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

import java.util.Map;
import java.util.function.Supplier;

/** Pane selection and reveal behavior; providers resolve lazy modules at call time. */
final class ModulePaneNavigator {
    private final Map<UiNavigationRegistry.Module, Supplier<ModuleHost>> hosts;
    private final Supplier<KeysController> keysController;
    private final Supplier<CipherController> cipherController;
    private final Supplier<CertificatesController> certificatesContainerController;
    private final Supplier<PostQuantumController> postQuantumContainerController;
    private final Supplier<XMLSignatureController> xmlSecurityContainerController;
    private final Supplier<WssSecurityController> wssSecurityContainerController;
    private final Supplier<ScrollPane> mainScrollPane;
    private final Supplier<VBox> contentContainer;

    ModulePaneNavigator(Map<UiNavigationRegistry.Module, Supplier<ModuleHost>> hosts,
            Supplier<KeysController> keysController,
            Supplier<CipherController> cipherController,
            Supplier<CertificatesController> certificatesContainerController,
            Supplier<PostQuantumController> postQuantumContainerController,
            Supplier<XMLSignatureController> xmlSecurityContainerController,
            Supplier<WssSecurityController> wssSecurityContainerController,
            Supplier<ScrollPane> mainScrollPane,
            Supplier<VBox> contentContainer) {
        this.hosts = Map.copyOf(hosts);
        this.keysController = keysController;
        this.cipherController = cipherController;
        this.certificatesContainerController = certificatesContainerController;
        this.postQuantumContainerController = postQuantumContainerController;
        this.xmlSecurityContainerController = xmlSecurityContainerController;
        this.wssSecurityContainerController = wssSecurityContainerController;
        this.mainScrollPane = mainScrollPane;
        this.contentContainer = contentContainer;
    }

    private ModuleHost host(UiNavigationRegistry.Module module) {
        Supplier<ModuleHost> provider = hosts.get(module);
        return provider == null ? null : provider.get();
    }

    private static Accordion moduleAccordion(ModuleHost host) {
        return host == null ? null : findAccordion(host);
    }

    private static Accordion findAccordion(Node node) {
        if (node instanceof Accordion accordion) return accordion;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Accordion found = findAccordion(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    void expandCipherAccordionPane(String itemName) {
        boolean symmetric = itemName.contains("Symmetric") || itemName.contains("AES")
                || itemName.contains("DES") || itemName.contains("Padding");
        if (cipherController.get() != null) cipherController.get().showSymmetricWorkspace(symmetric);
        if (symmetric) {
            mainScrollPane.get().setVvalue(0);
            return;
        }
        Accordion accordion = moduleAccordion(host(UiNavigationRegistry.Module.CIPHER));

        if (accordion != null) {
            String targetPane = "";
            if (itemName.contains("Format-Preserving")) {
                targetPane = "Format-Preserving";
            } else if (itemName.contains("File Cipher")) {
                targetPane = "File Cipher";
            } else if (itemName.contains("OpenPGP") || itemName.contains("GPG")) {
                targetPane = "OpenPGP";
            } else if (itemName.contains("Symmetric") || itemName.contains("AES") || itemName.contains("DES")
                    || itemName.contains("Padding")) {
                targetPane = "Symmetric";
            } else if (itemName.contains("Asymmetric") || itemName.contains("RSA") || itemName.contains("ECC")) {
                targetPane = "Asymmetric";
            }

            for (TitledPane pane : accordion.getPanes()) {
                if (!targetPane.isEmpty()
                        && ModulePaneMatcher.matches(pane, targetPane, ModuleTextCatalog.cipher())) {
                    accordion.setExpandedPane(pane);
                    revealExpandedPane(pane);
                    break;
                }
            }
        }
    }

    void expandAuthenticationAccordionPane(String itemName) {
        Accordion accordion = moduleAccordion(host(UiNavigationRegistry.Module.AUTHENTICATION));

        if (accordion != null) {
            String targetPane = "";
            if (itemName.contains("Signature") || itemName.contains("Sign")) {
                targetPane = "Signatures";
            } else if (itemName.contains("MAC")) {
                targetPane = "MAC";
            }

            for (TitledPane pane : accordion.getPanes()) {
                if (!targetPane.isEmpty()
                        && ModulePaneMatcher.matches(pane, targetPane, ModuleTextCatalog.authentication())) {
                    accordion.setExpandedPane(pane);
                    revealExpandedPane(pane);
                    break;
                }
            }
        }
    }

    void expandPaymentsAccordionPane(String itemName) {
        Accordion accordion = moduleAccordion(host(UiNavigationRegistry.Module.PAYMENTS));

        if (accordion != null) {
            String targetPane = "";
            // ISO 8583 and the host commands first: their names would otherwise match "ISO"
            // (Encrypted PIN Blocks) or nothing at all.
            if (itemName.contains("ISO 8583") || itemName.contains("ISO8583")) {
                targetPane = "ISO 8583 Message Inspector";
            } else if (itemName.contains("Host Command")) {
                targetPane = "Host Command Bank";
            } else if (itemName.contains("DUKPT")) {
                targetPane = "DUKPT KSN";
            } else if (itemName.contains("CVV")) {
                targetPane = "CVV";
            } else if (itemName.contains("PIN Block Operations")) {
                targetPane = "Clear PIN Blocks";
            } else if (itemName.contains("Clear") || itemName.contains("Encode") || itemName.contains("Decode")) {
                targetPane = "Clear PIN";
            } else if (itemName.contains("Encrypted") || itemName.contains("ISO")) {
                targetPane = "Encrypted PIN";
            } else if (itemName.contains("Generation") || itemName.contains("IBM") || itemName.contains("Generate")
                    || itemName.contains("Verify")) {
                targetPane = "PIN Generation";
            }

            for (TitledPane pane : accordion.getPanes()) {
                if (!targetPane.isEmpty()
                        && ModulePaneMatcher.matches(pane, targetPane, ModuleTextCatalog.payments())) {
                    accordion.setExpandedPane(pane);
                    revealExpandedPane(pane);
                    break;
                }
            }
        }
    }

    void expandAccordionPane(String paneName) {
        if (keysController.get() == null) return;
        // Keys was the one module that expanded a pane without scrolling to it. That went
        // unnoticed while every destination lived in the accordion, which lifts the pane it
        // opens near the top by collapsing the previous one; the included panes below it have
        // no such effect and stayed off-screen.
        revealExpandedPane(keysController.get().expandSymmetricPane(paneName));
    }

    void expandAsymmetricAccordionPane(String paneName) {
        if (keysController.get() == null) return;
        revealExpandedPane(keysController.get().expandAsymmetricPane(paneName));
    }

    void expandCertificatesAccordionPane(String paneName) {
        if (certificatesContainerController.get() != null) certificatesContainerController.get().expandPane(paneName);
    }

    void expandGenericAccordionPane(String paneName) { NavigationRouter.expandByTitle(host(UiNavigationRegistry.Module.GENERIC), paneName, ModuleTextCatalog.generic(), this::revealExpandedPane); }

    void expandEMVAccordionPane(String title) {
        Accordion acc = moduleAccordion(host(UiNavigationRegistry.Module.EMV));
        if (title != null && !title.isBlank() && acc != null) {
            for (TitledPane pane : acc.getPanes()) {
                if (ModulePaneMatcher.matches(pane, title, ModuleTextCatalog.emv())) {
                    acc.setExpandedPane(pane);
                    revealExpandedPane(pane);
                    break;
                }
            }
        }
    }

    void revealExpandedPane(TitledPane pane) {
        if (pane == null) {
            return;
        }
        Platform.runLater(() -> {
            pane.requestFocus();
            if (mainScrollPane.get() == null || contentContainer.get() == null || pane.getScene() == null) {
                return;
            }
            javafx.geometry.Bounds contentBounds = contentContainer.get().localToScene(contentContainer.get().getBoundsInLocal());
            javafx.geometry.Bounds paneBounds = pane.localToScene(pane.getBoundsInLocal());
            if (contentBounds == null || paneBounds == null) {
                return;
            }
            double scrollableHeight = contentContainer.get().getBoundsInLocal().getHeight()
                    - mainScrollPane.get().getViewportBounds().getHeight();
            if (scrollableHeight <= 0) {
                return;
            }
            double target = (paneBounds.getMinY() - contentBounds.getMinY()) / scrollableHeight;
            mainScrollPane.get().setVvalue(Math.max(0, Math.min(1, target)));
        });
    }

    void expandPQCAccordionPane(String itemName) {
        if (postQuantumContainerController.get() != null) {
            postQuantumContainerController.get().expandAccordionPane(itemName);
        }
    }

    void expandXMLAccordionPane(String itemName) {
        if (xmlSecurityContainerController.get() != null) {
            xmlSecurityContainerController.get().expandAccordionPane(itemName);
        }
    }

    void expandWssAccordionPane(String itemName) {
        if (wssSecurityContainerController.get() != null) {
            wssSecurityContainerController.get().expandAccordionPane(itemName);
        }
    }
}
