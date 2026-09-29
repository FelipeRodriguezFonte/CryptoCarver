package com.cryptocarver.model;

/** Purely derives canonical breadcrumb parts from a route or operation descriptor. */
public final class BreadcrumbPathPolicy {
    private BreadcrumbPathPolicy() { }

    public record Path(String sectionKey, String moduleLabel, String modulePath,
                       String operationLabel, boolean resolvedRoute) { }

    public static Path fromRoute(String operation, String module, String section) {
        String sectionKey = switch (module == null ? "" : module) {
            case "KEYS_SYMMETRIC" -> "bread.symmetricKeys";
            case "KEYS_ASYMMETRIC" -> "bread.asymmetricKeys";
            case "CIPHER" -> "bread.ciphers";
            case "AUTHENTICATION" -> "bread.signaturesMac";
            case "CERTIFICATES" -> "bread.certificatesCms";
            case "JOSE" -> "bread.joseJwt";
            case "COSE" -> "bread.coseSign1";
            case "WALLET" -> "bread.wallet";
            case "POST_QUANTUM" -> "bread.postQuantumPqc";
            case "XML_SECURITY" -> "bread.xmlSecurity";
            case "WSS_SECURITY" -> "bread.wssSecurity";
            case "EMV" -> "bread.emvSmartcards";
            case "PAYMENTS" -> "bread.paymentCryptography";
            case "GENERIC" -> "bread.utilities";
            case "HISTORY" -> "bread.history";
            case "CLIPBOARD_SHELF" -> "bread.clipboardShelf";
            case "SAVED_SESSIONS" -> "bread.savedSessions";
            case "PROCESS_DESIGNER" -> "nav.processDesigner";
            default -> "bread.section";
        };
        boolean hasSection = section != null && !section.isBlank();
        return new Path(sectionKey, hasSection ? section : module,
                hasSection ? section : operation, operation, true);
    }

    public static Path fromOperation(String operation, String category, String title, String navigationPath) {
        String fallback = title == null ? operation : title;
        return new Path(category, category, navigationPath == null ? fallback : navigationPath,
                fallback, false);
    }

    public static Path unresolved(String operation) {
        return new Path(null, null, operation, operation, false);
    }
}
