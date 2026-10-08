package com.cryptocarver.ui;

import javafx.scene.layout.VBox;
import java.util.function.Supplier;

/** Selects JOSE panels using the existing ordered prefix contract. */
final class JoseSectionCoordinator extends JoseCoordinatorSupport {
    record View(VBox joseContainer, VBox jwtSection, VBox jweSection,
            VBox jwkSection, VBox jwaSection, VBox inspectorSection) { }

    private final Supplier<View> controls;

    JoseSectionCoordinator(Supplier<View> controls, Supplier<StatusReporter> reporter) {
        super(reporter);
        this.controls = controls;
    }

    private View view() { return controls.get(); }

    void showSection(String sectionName) {
        View v = view();
        if (v.joseContainer() != null) {
            v.joseContainer().setManaged(true);
            v.joseContainer().setVisible(true);
        }
        if (v.jwtSection() != null) {
            v.jwtSection().setManaged(false);
            v.jwtSection().setVisible(false);
        }
        if (v.jweSection() != null) {
            v.jweSection().setManaged(false);
            v.jweSection().setVisible(false);
        }
        if (v.jwkSection() != null) {
            v.jwkSection().setManaged(false);
            v.jwkSection().setVisible(false);
        }

        if (v.jwaSection() != null) {
            v.jwaSection().setManaged(false);
            v.jwaSection().setVisible(false);
        }
        if (v.inspectorSection() != null) {
            v.inspectorSection().setManaged(false);
            v.inspectorSection().setVisible(false);
        }

        if (sectionName == null)
            return;

        if (sectionName.startsWith("JWT")) {
            if (v.jwtSection() != null) {
                v.jwtSection().setManaged(true);
                v.jwtSection().setVisible(true);
            }
        } else if (sectionName.startsWith("JWE")) {
            if (v.jweSection() != null) {
                v.jweSection().setManaged(true);
                v.jweSection().setVisible(true);
            }
        } else if (sectionName.startsWith("JWK")) {
            if (v.jwkSection() != null) {
                v.jwkSection().setManaged(true);
                v.jwkSection().setVisible(true);
            }
        } else if (sectionName.startsWith("JWA")) {
            if (v.jwaSection() != null) {
                v.jwaSection().setManaged(true);
                v.jwaSection().setVisible(true);
            }
        } else if (sectionName.startsWith("Token Inspector")) {
            if (v.inspectorSection() != null) {
                v.inspectorSection().setManaged(true);
                v.inspectorSection().setVisible(true);
            }
        }

    }
}
