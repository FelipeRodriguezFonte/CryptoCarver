package com.cryptocarver.ui;

import com.cryptocarver.model.AppSettings;
import com.cryptocarver.model.FormatProfilePolicy;
import com.cryptocarver.model.OperationDescriptor;
import com.cryptocarver.model.OperationFormatProfile;
import com.cryptocarver.model.OperationFormatRegistry;
import com.cryptocarver.model.OperationRegistry;
import com.cryptocarver.service.I18nService;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Presents navigation chrome and shared format selectors for the active route. */
final class NavigationChromeCoordinator {
    private final ComboBox<String> input, output;
    private final Label inputLabel, contractLabel, title, subtitle, operation;
    private final HBox breadcrumb;
    private final Button sectionButton, moduleButton, favoriteButton;
    private final Label sep1, sep2;
    private final Consumer<String> profileOperationChanged;
    private final Consumer<Object> breadcrumbSectionSelected;
    private final Consumer<String> breadcrumbModuleSelected;
    private final String favoriteShortcut;
    private final I18nService i18n = I18nService.getInstance();
    private final ShellTextResolver textResolver = new ShellTextResolver(i18n::text);
    private final Map<String, String> rememberedInput = new HashMap<>(), rememberedOutput = new HashMap<>();
    private String currentProfileOperation = "Dashboard";

    NavigationChromeCoordinator(ComboBox<String> input, ComboBox<String> output, Label inputLabel,
            Label contractLabel, Label title, Label subtitle, HBox breadcrumb, Button sectionButton,
            Label sep1, Button moduleButton, Label sep2, Label operation, Button favoriteButton,
            String favoriteShortcut, Consumer<String> profileOperationChanged,
            Consumer<Object> breadcrumbSectionSelected, Consumer<String> breadcrumbModuleSelected) {
        this.input=input; this.output=output; this.inputLabel=inputLabel; this.contractLabel=contractLabel;
        this.title=title; this.subtitle=subtitle; this.breadcrumb=breadcrumb; this.sectionButton=sectionButton;
        this.sep1=sep1; this.moduleButton=moduleButton; this.sep2=sep2; this.operation=operation;
        this.favoriteButton=favoriteButton; this.favoriteShortcut=favoriteShortcut;
        if (contractLabel != null) {
            contractLabel.setWrapText(true);
            contractLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
        this.profileOperationChanged=profileOperationChanged;
        this.breadcrumbSectionSelected=breadcrumbSectionSelected; this.breadcrumbModuleSelected=breadcrumbModuleSelected;
    }

    void setInputFormat(String format) { setToolbarFormat(input, format); }
    void setOutputFormat(String format) { setToolbarFormat(output, format); }
    private static void setToolbarFormat(ComboBox<String> combo, String format) {
        if (combo == null) return;
        String canonical=FormatProfilePolicy.normalize(format);
        if (canonical == null) combo.setValue(null);
        else if (combo.getItems().contains(canonical)) combo.setValue(canonical);
        else if (!combo.isDisabled()) combo.setValue(null);
    }

    void updateHeader(String name) {
        String section="Cryptographic Operations", subsection=name;
        var found=OperationRegistry.getInstance().resolveNavigation(name);
        if (found.isPresent()) {
            OperationDescriptor d=found.get(); subsection=d.getTitle()+" · "+operationStatusSummary(d);
        }
        if (name.contains("Post-Quantum") || name.contains("PQC") || name.contains("ML-KEM") || name.contains("ML-DSA") || name.contains("SLH-DSA") || name.contains("Kyber") || name.contains("Dilithium") || name.contains("SPHINCS")) section="Post-Quantum";
        else if (name.contains("XML") || name.contains("XAdES")) section="XML Security";
        else if (name.contains("RSA") || name.contains("ECDSA") || name.contains("DSA")) section="Asymmetric Keys";
        else if (name.contains("Key")) section="Symmetric Keys";
        else if (name.contains("Certificate") || name.contains("CMS")) section="Certificates";
        else if (name.contains("EMV") || name.contains("TR-31")) section="Payments";
        if (title != null) title.setText(localizedSectionText(section));
        if (subtitle != null) subtitle.setText(subsection);
        if (contractLabel != null) contractLabel.setText(subsection);
        updateBreadcrumbs(name);
        updateFavorite(name);
        AppSettings.getInstance().setLastRoute(name);
        applyFormatProfile(name);
    }

    void updateSubtitle(String text) {
        if (subtitle != null) { subtitle.setText(text); boolean has=text != null && !text.isEmpty(); subtitle.setVisible(has); subtitle.setManaged(has); }
    }
    void updateBreadcrumbOnly(String name) { updateBreadcrumbs(name); }
    void updateFavoriteOnly(String name) { updateFavorite(name); }

    private void updateBreadcrumbs(String name) {
        if (breadcrumb == null || name == null) return;
        var route=UiNavigationRegistry.resolve(name);
        com.cryptocarver.model.BreadcrumbPathPolicy.Path path = com.cryptocarver.model.BreadcrumbPathPolicy.unresolved(name);
        String descriptorCategory=null;
        if (route.isPresent()) {
            var r=route.get();
            path=com.cryptocarver.model.BreadcrumbPathPolicy.fromRoute(name,r.module().name(),r.section());
        } else {
            var descriptor=OperationRegistry.getInstance().resolveNavigation(name);
            if (descriptor.isPresent()) {
                var d=descriptor.get(); descriptorCategory=d.getCategory();
                path=com.cryptocarver.model.BreadcrumbPathPolicy.fromOperation(name,descriptorCategory,d.getTitle(),d.getNavigationPath());
            }
        }
        String sectionLabel;
        if (path.sectionKey()==null) sectionLabel=i18n.text("bread.section");
        else if (path.resolvedRoute()) sectionLabel=i18n.text(path.sectionKey());
        else sectionLabel=descriptorCategory==null?i18n.text("bread.section"):localizedSectionText(descriptorCategory);
        String moduleLabel=path.moduleLabel()==null?i18n.text("bread.module"):path.moduleLabel();
        String operationLabel=path.operationLabel();
        if(sectionButton!=null) { sectionButton.setText(sectionLabel); sectionButton.setUserData(route.isPresent()?route.get().module():sectionLabel); sectionButton.setAccessibleText(i18n.text("bread.navigateSection",sectionLabel)); sectionButton.setTooltip(new Tooltip(i18n.text("bread.navigateSection",sectionLabel))); }
        String localized=localizedModuleText(moduleLabel);
        boolean showModule=route.isPresent() && route.get().section()!=null && !route.get().section().isBlank() && !localized.equalsIgnoreCase(sectionLabel) && !localized.equalsIgnoreCase(operationLabel);
        boolean showOperation=!operationLabel.equalsIgnoreCase(sectionLabel);
        if(moduleButton!=null) { moduleButton.setText(localized); moduleButton.setUserData(path.modulePath()); moduleButton.setAccessibleText(i18n.text("bread.navigateModule",localized)); moduleButton.setTooltip(new Tooltip(i18n.text("bread.navigateModule",localized))); moduleButton.setVisible(showModule); moduleButton.setManaged(showModule); }
        if(operation!=null) { operation.setText(operationLabel); operation.setVisible(showOperation); operation.setManaged(showOperation); }
        if(sep1!=null) { boolean show=showModule||showOperation; sep1.setVisible(show); sep1.setManaged(show); }
        if(sep2!=null) { sep2.setVisible(showModule&&showOperation); sep2.setManaged(showModule&&showOperation); }
    }

    Object sectionTarget() { return sectionButton == null ? null : sectionButton.getUserData(); }
    String moduleTarget() { if(moduleButton==null)return null; Object d=moduleButton.getUserData(); return d instanceof String s&&!s.isBlank()?s:moduleButton.getText(); }
    void handleBreadcrumbSectionClick() { if (breadcrumbSectionSelected != null) breadcrumbSectionSelected.accept(sectionTarget()); }
    void handleBreadcrumbModuleClick() { String target=moduleTarget(); if (target != null && breadcrumbModuleSelected != null) breadcrumbModuleSelected.accept(target); }
    void toggleFavorite(String name) { if(name==null||name.isBlank())return; AppSettings.getInstance().toggleFavorite(name); updateFavorite(name); }
    private void updateFavorite(String name) {
        if(favoriteButton==null||name==null)return;
        if(AppSettings.getInstance().isFavorite(name)) { favoriteButton.setText("★"); if(!favoriteButton.getStyleClass().contains("active"))favoriteButton.getStyleClass().add("active"); favoriteButton.setAccessibleText(i18n.text("favorite.remove",name)); favoriteButton.setTooltip(new Tooltip(i18n.text("favorite.active",favoriteShortcut))); }
        else { favoriteButton.setText("☆"); favoriteButton.getStyleClass().remove("active"); favoriteButton.setAccessibleText(i18n.text("favorite.add",name)); favoriteButton.setTooltip(new Tooltip(i18n.text("favorite.tooltip",favoriteShortcut))); }
    }
    private void applyFormatProfile(String name) {
        String key=FormatProfilePolicy.operation(name); OperationFormatProfile p=OperationFormatRegistry.getInstance().getProfile(key);
        if(currentProfileOperation!=null) { if(input!=null)rememberedInput.put(currentProfileOperation,input.getValue()); if(output!=null)rememberedOutput.put(currentProfileOperation,output.getValue()); }
        applyFormat(input,p.allowedInputFormats(),p.defaultInputFormat(),rememberedInput.get(key));
        applyFormat(output,p.allowedOutputFormats(),p.defaultOutputFormat(),rememberedOutput.get(key)); currentProfileOperation=key;
        profileOperationChanged.accept(key);
        if(contractLabel!=null) {
            String text=OperationRegistry.getInstance().resolveNavigation(name).map(OperationDescriptor::getTitle).orElse(name); contractLabel.setText(text);
            String payload=i18n.text("toolbar.payloadTooltip");
            if(p.contractDescription()!=null&&!p.contractDescription().isEmpty()) { Tooltip tip=new Tooltip(p.contractDescription()); contractLabel.setTooltip(tip); if(input!=null)input.setTooltip(new Tooltip(payload+"\n"+p.contractDescription())); if(output!=null)output.setTooltip(tip); }
            else { contractLabel.setTooltip(null); if(input!=null)input.setTooltip(new Tooltip(payload)); if(output!=null)output.setTooltip(null); }
        }
    }
    private static void applyFormat(ComboBox<String> combo, java.util.List<String> allowed,String fallback,String remembered) {
        if(combo==null)return; if(allowed==null||allowed.isEmpty()){combo.setDisable(true);return;} combo.setDisable(false); combo.getItems().setAll(allowed);
        if(remembered!=null&&allowed.contains(remembered))combo.setValue(remembered); else if(fallback!=null&&allowed.contains(fallback))combo.setValue(fallback); else combo.setValue(allowed.get(0));
    }
    private static String operationStatusSummary(OperationDescriptor op) {
        String status=op.getStatus()==OperationDescriptor.Status.EXPERIMENTAL?"Experimental":"Stable";
        return switch(op.getSecretRisk()){case NONE->status;case LOW->status+" · Low sensitivity";case HIGH->status+" · Sensitive material";case EXTREME->status+" · Highly sensitive material";};
    }
    private String localizedSectionText(String value) { return textResolver.localizedSectionText(value); }
    private String localizedModuleText(String value) { return textResolver.localizedModuleText(value); }
}
