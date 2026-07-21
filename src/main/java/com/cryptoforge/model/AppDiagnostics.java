package com.cryptoforge.model;

import java.security.Provider;
import java.security.Security;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Builds a safe, copyable runtime report without exposing user data or keys. */
public final class AppDiagnostics {
    private AppDiagnostics() { }

    public static String report() {
        StringBuilder report = new StringBuilder();
        report.append("CryptoCarver diagnostics\n");
        report.append("Generated: ").append(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now())).append('\n');
        append(report, "Application", BuildInfo.name());
        append(report, "Application version", BuildInfo.version());
        append(report, "Release channel", BuildInfo.channel());
        append(report, "Build Java release", BuildInfo.javaRelease());
        append(report, "Java", System.getProperty("java.version"));
        append(report, "Java vendor", System.getProperty("java.vendor"));
        append(report, "JavaFX", System.getProperty("javafx.runtime.version", "Not reported"));
        append(report, "Operating system", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        append(report, "Architecture", System.getProperty("os.arch"));
        append(report, "Locale", java.util.Locale.getDefault().toLanguageTag());
        append(report, "Default charset", java.nio.charset.Charset.defaultCharset().name());
        append(report, "Bouncy Castle", providerVersion("BC"));
        append(report, "DSS", packageVersion("eu.europa.esig.dss.xades.XAdESSignatureParameters"));
        report.append("\nNo key material, input data, credentials or file paths are included in this report.\n");
        return report.toString();
    }

    private static void append(StringBuilder target, String label, String value) {
        target.append(label).append(": ").append(value == null ? "Not reported" : value).append('\n');
    }

    private static String providerVersion(String name) {
        Provider provider = Security.getProvider(name);
        return provider == null ? "Not loaded" : provider.getName() + " " + provider.getVersionStr();
    }

    private static String packageVersion(String className) {
        try {
            Package pkg = Class.forName(className).getPackage();
            return pkg == null || pkg.getImplementationVersion() == null ? "6.3" : pkg.getImplementationVersion();
        } catch (ClassNotFoundException e) {
            return "Not loaded";
        }
    }
}
