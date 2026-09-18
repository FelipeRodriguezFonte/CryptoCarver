package com.cryptocarver.model.process.handlers;

import com.cryptocarver.crypto.CborInspector;
import com.cryptocarver.crypto.JOSEService;
import com.cryptocarver.crypto.SdJwtOperations;
import com.cryptocarver.crypto.StatusListOperations;
import com.cryptocarver.model.process.*;
import com.nimbusds.jose.JWSAlgorithm;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Process Designer adapters for the European wallet's credential formats:
 * SD-JWT VC, Token Status List and CBOR.
 *
 * <p>Thin, like every other handler here — the crypto lives in the facades and
 * this only marshals ports and configuration into them. Which matters for one
 * reason in particular: Disclosures and key-binding keys are secrets, and
 * declaring them as {@link ParameterKind#PASSWORD} is what keeps them in
 * {@code transientSecrets} and out of a saved {@code .cfprocess.json}.</p>
 */
public final class WalletCredentialNodeHandler implements ProcessNodeHandler {

    public static final Set<String> TYPES = Set.of(
            "SDJWT_ISSUE", "SDJWT_ISSUE_VC", "SDJWT_PRESENT", "SDJWT_VERIFY", "SDJWT_INSPECT",
            "STATUS_LIST_RESOLVE", "STATUS_LIST_DESCRIBE",
            "CBOR_INSPECT", "CBOR_TO_JSON", "CBOR_FROM_JSON");

    private static final List<String> SIGN_ALGORITHMS = List.of(
            "ES256", "ES384", "ES512", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512");
    private static final List<String> HASH_ALGORITHMS = List.of("sha-256", "sha-384", "sha-512");
    private static final List<String> CBOR_VIEWS = List.of("tree", "diagnostic", "summary");

    @Override public Set<String> supportedTypes() {
        return TYPES;
    }

    @Override public List<NodeDescriptor> descriptors() {
        return List.of(
                descriptor("SDJWT_ISSUE", "sdJwtIssue", List.of(
                        combo("algorithm", SIGN_ALGORITHMS, "ES256"),
                        secret("key"),
                        multiline("disclosablePaths"),
                        combo("sdAlgorithm", HASH_ALGORITHMS, "sha-256"),
                        text("decoyCount", "0"))),
                descriptor("SDJWT_ISSUE_VC", "sdJwtIssueVc", List.of(
                        combo("algorithm", SIGN_ALGORITHMS, "ES256"),
                        secret("key"),
                        text("vct", ""),
                        text("issuer", ""),
                        multiline("confirmationJwk"),
                        multiline("status"),
                        multiline("disclosablePaths"),
                        combo("sdAlgorithm", HASH_ALGORITHMS, "sha-256"),
                        text("decoyCount", "0"))),
                descriptor("SDJWT_PRESENT", "sdJwtPresent", List.of(
                        multiline("revealClaims"),
                        text("audience", ""),
                        text("nonce", ""),
                        combo("keyBindingAlgorithm", SIGN_ALGORITHMS, "ES256"),
                        secret("holderKey"))),
                descriptor("SDJWT_VERIFY", "sdJwtVerify", List.of(
                        combo("algorithm", SIGN_ALGORITHMS, "ES256"),
                        multiline("issuerPublicKey"),
                        multiline("holderPublicKey"),
                        text("audience", ""),
                        text("nonce", ""))),
                descriptor("SDJWT_INSPECT", "sdJwtInspect", List.of()),
                descriptor("STATUS_LIST_RESOLVE", "statusListResolve", List.of(
                        multiline("statusClaim"),
                        combo("algorithm", SIGN_ALGORITHMS, "ES256"),
                        multiline("issuerPublicKey"))),
                descriptor("STATUS_LIST_DESCRIBE", "statusListDescribe", List.of()),
                descriptor("CBOR_INSPECT", "cborInspect", List.of(combo("view", CBOR_VIEWS, "tree"))),
                descriptor("CBOR_TO_JSON", "cborToJson", List.of()),
                descriptor("CBOR_FROM_JSON", "cborFromJson", List.of()));
    }

    @Override public List<PortDefinition> inputPorts(ProcessDefinition.Node node) {
        Set<Representation> any = Representation.standardValues();
        return switch (node.type) {
            case "SDJWT_ISSUE", "SDJWT_ISSUE_VC" -> List.of(port("claims", any, true), port("key", any, false));
            case "SDJWT_PRESENT" -> List.of(port("sdJwt", any, true), port("holderKey", any, false));
            case "SDJWT_VERIFY" -> List.of(port("presentation", any, true), port("issuerPublicKey", any, false));
            case "SDJWT_INSPECT" -> List.of(port("presentation", any, true));
            case "STATUS_LIST_RESOLVE", "STATUS_LIST_DESCRIBE" -> List.of(port("statusListToken", any, true));
            case "CBOR_INSPECT", "CBOR_TO_JSON" -> List.of(port("cbor", any, true));
            case "CBOR_FROM_JSON" -> List.of(port("json", any, true));
            default -> throw new IllegalArgumentException("Unsupported wallet node: " + node.type);
        };
    }

    @Override public Representation outputRepresentation(ProcessDefinition.Node node, Map<String, Representation> inputs) {
        return "CBOR_FROM_JSON".equals(node.type) ? Representation.BINARY : Representation.TEXT_UTF8;
    }

    @Override public void validateConfiguration(ProcessDefinition.Node node) {
        switch (node.type) {
            case "SDJWT_ISSUE" -> {
                enumValue(node, "algorithm", SIGN_ALGORITHMS);
                supplied(node, "key");
                nonNegativeInteger(node, "decoyCount");
            }
            case "SDJWT_ISSUE_VC" -> {
                enumValue(node, "algorithm", SIGN_ALGORITHMS);
                supplied(node, "key");
                supplied(node, "vct");
                nonNegativeInteger(node, "decoyCount");
            }
            case "SDJWT_PRESENT" -> {
                // Key binding is all-or-nothing: a KB-JWT without an audience and
                // a nonce is not bound to anything, and a nonce with no key to
                // sign it is a silent no-op. Refusing half a configuration is
                // better than emitting a presentation that looks bound and isn't.
                boolean wantsBinding = !value(node, "audience", "").isBlank()
                        || !value(node, "nonce", "").isBlank()
                        || NodeCatalog.isSupplied(node, "holderKey")
                        || node.configuration.containsKey("holderKey");
                if (wantsBinding) {
                    supplied(node, "holderKey");
                    if (value(node, "audience", "").isBlank() || value(node, "nonce", "").isBlank()) {
                        throw new IllegalArgumentException(
                                "Key binding needs both an audience and a nonce");
                    }
                    enumValue(node, "keyBindingAlgorithm", SIGN_ALGORITHMS);
                }
            }
            case "SDJWT_VERIFY", "STATUS_LIST_RESOLVE" -> {
                enumValue(node, "algorithm", SIGN_ALGORITHMS);
                supplied(node, "issuerPublicKey");
            }
            case "CBOR_INSPECT" -> enumValue(node, "view", CBOR_VIEWS);
            case "SDJWT_INSPECT", "STATUS_LIST_DESCRIBE", "CBOR_TO_JSON", "CBOR_FROM_JSON" -> { }
            default -> throw new IllegalArgumentException("Unsupported wallet node: " + node.type);
        }
    }

    @Override public FlowValue execute(ProcessDefinition.Node node,
                                       Map<String, FlowValue> inputs,
                                       ExecutionContext context) throws Exception {
        return switch (node.type) {
            case "SDJWT_ISSUE" -> text(SdJwtOperations.issue(
                    string(inputs, "claims"),
                    lines(value(node, "disclosablePaths", "")),
                    Integer.parseInt(value(node, "decoyCount", "0")),
                    hashAlgorithm(node),
                    JWSAlgorithm.parse(value(node, "algorithm", "ES256")),
                    JOSEService.createSigner(JWSAlgorithm.parse(value(node, "algorithm", "ES256")),
                            setting(node, inputs, "key")),
                    null).serialized());

            case "SDJWT_ISSUE_VC" -> text(SdJwtOperations.issueVerifiableCredential(
                    string(inputs, "claims"),
                    value(node, "vct", ""),
                    blankToNull(value(node, "issuer", "")),
                    blankToNull(value(node, "confirmationJwk", "")),
                    blankToNull(value(node, "status", "")),
                    lines(value(node, "disclosablePaths", "")),
                    Integer.parseInt(value(node, "decoyCount", "0")),
                    hashAlgorithm(node),
                    JWSAlgorithm.parse(value(node, "algorithm", "ES256")),
                    JOSEService.createSigner(JWSAlgorithm.parse(value(node, "algorithm", "ES256")),
                            setting(node, inputs, "key"))).serialized());

            case "SDJWT_PRESENT" -> text(present(node, inputs));

            case "SDJWT_VERIFY" -> {
                JWSAlgorithm algorithm = JWSAlgorithm.parse(value(node, "algorithm", "ES256"));
                SdJwtOperations.VerifiedSdJwt verified = SdJwtOperations.verify(
                        string(inputs, "presentation"),
                        JOSEService.createVerifier(algorithm, setting(node, inputs, "issuerPublicKey")),
                        blankToNull(value(node, "holderPublicKey", "")) == null
                                ? null
                                : JOSEService.createVerifier(algorithm, value(node, "holderPublicKey", "")),
                        blankToNull(value(node, "audience", "")),
                        blankToNull(value(node, "nonce", "")));
                yield text(verified.claimsJson());
            }

            case "SDJWT_INSPECT" -> text(SdJwtOperations.describe(
                    string(inputs, "presentation"), Locale.getDefault()));

            case "STATUS_LIST_RESOLVE" -> {
                StatusListOperations.StatusLookup lookup = StatusListOperations.resolve(
                        value(node, "statusClaim", ""),
                        string(inputs, "statusListToken"),
                        JOSEService.createVerifier(JWSAlgorithm.parse(value(node, "algorithm", "ES256")),
                                setting(node, inputs, "issuerPublicKey")));
                yield text("index " + lookup.index() + " -> " + lookup.status()
                        + " (" + lookup.description() + ")");
            }

            case "STATUS_LIST_DESCRIBE" -> text(StatusListOperations.describe(string(inputs, "statusListToken")));

            case "CBOR_INSPECT" -> {
                byte[] cbor = bytes(inputs, "cbor");
                yield text(switch (value(node, "view", "tree")) {
                    case "diagnostic" -> CborInspector.diagnostic(cbor);
                    case "summary" -> CborInspector.summary(cbor);
                    default -> CborInspector.tree(cbor);
                });
            }

            case "CBOR_TO_JSON" -> text(CborInspector.toJson(bytes(inputs, "cbor")));
            case "CBOR_FROM_JSON" -> FlowValue.binary(CborInspector.fromJson(string(inputs, "json")));

            default -> throw new IllegalArgumentException("Unsupported wallet node: " + node.type);
        };
    }

    /**
     * Presentation takes the Issuer's full serialization on the wire and narrows
     * it. Claims are named rather than selected by digest here, because a canvas
     * node is configured once and reused across runs, and the digest of a claim
     * changes on every issuance — its salt is fresh each time.
     */
    private String present(ProcessDefinition.Node node, Map<String, FlowValue> inputs) throws Exception {
        String serialized = string(inputs, "sdJwt");
        SdJwtOperations.ParsedSdJwt parsed = SdJwtOperations.parse(serialized);
        List<String> wanted = lines(value(node, "revealClaims", ""));

        List<SdJwtOperations.Disclosure> all = parsed.disclosures();
        List<String> digests = new ArrayList<>();
        if (wanted.isEmpty()) {
            all.forEach(d -> digests.add(d.digest()));
        } else {
            for (String claim : wanted) {
                boolean matched = false;
                for (SdJwtOperations.Disclosure disclosure : all) {
                    if (claim.equals(disclosure.claimName()) || claim.equals(disclosure.label())) {
                        digests.add(disclosure.digest());
                        matched = true;
                    }
                }
                if (!matched) {
                    throw new IllegalArgumentException(
                            "This SD-JWT carries no Disclosure for '" + claim + "'");
                }
            }
        }

        // Rebuild an IssuedSdJwt around the parsed token: present() works from
        // Disclosures and their digests, which parse() already recovered.
        SdJwtOperations.IssuedSdJwt reconstructed = new SdJwtOperations.IssuedSdJwt(
                serialized.split("~", -1)[0], all, serialized,
                SdJwtOperations.HashAlgorithm.fromRegistryName(
                        parsed.payload().has("_sd_alg")
                                ? parsed.payload().get("_sd_alg").getAsString()
                                : "sha-256"));

        String audience = value(node, "audience", "");
        SdJwtOperations.KeyBinding binding = audience.isBlank() ? null : new SdJwtOperations.KeyBinding(
                audience,
                value(node, "nonce", ""),
                JWSAlgorithm.parse(value(node, "keyBindingAlgorithm", "ES256")),
                JOSEService.createSigner(JWSAlgorithm.parse(value(node, "keyBindingAlgorithm", "ES256")),
                        setting(node, inputs, "holderKey")));
        return SdJwtOperations.present(reconstructed, digests, binding);
    }

    // ------------------------------------------------------------------ helpers

    private static NodeDescriptor descriptor(String type, String key, List<NodeParameter> params) {
        return new NodeDescriptor(type, "Wallet / eIDAS",
                "module.process.type." + key, "module.process.desc." + key, "🪪", params);
    }

    private static NodeParameter combo(String k, List<String> values, String d) {
        return new NodeParameter(k, "module.process.param." + k, ParameterKind.COMBO, values, d);
    }

    private static NodeParameter secret(String k) {
        return new NodeParameter(k, "module.process.param." + k, ParameterKind.PASSWORD, List.of(), "", true);
    }

    private static NodeParameter multiline(String k) {
        return new NodeParameter(k, "module.process.param." + k, ParameterKind.MULTILINE, "");
    }

    private static NodeParameter text(String k, String d) {
        return new NodeParameter(k, "module.process.param." + k, ParameterKind.TEXT, d);
    }

    private static PortDefinition port(String n, Set<Representation> r, boolean required) {
        return new PortDefinition(n, r, required);
    }

    private static SdJwtOperations.HashAlgorithm hashAlgorithm(ProcessDefinition.Node node) {
        return SdJwtOperations.HashAlgorithm.fromRegistryName(value(node, "sdAlgorithm", "sha-256"));
    }

    /** Paths and claim names, one per line; blank lines and commas both tolerated
     *  because people paste both. */
    private static List<String> lines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[\\r\\n,]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String value(ProcessDefinition.Node n, String k, String d) {
        return n.configuration.getOrDefault(k, d);
    }

    private static void supplied(ProcessDefinition.Node n, String k) {
        if (!n.configuration.containsKey(k)
                && !"true".equalsIgnoreCase(n.configuration.get(k + "FromFlow"))
                && !NodeCatalog.isSupplied(n, k)) {
            throw new IllegalArgumentException(k + " is required");
        }
    }

    private static void enumValue(ProcessDefinition.Node n, String k, List<String> allowed) {
        if (!allowed.contains(value(n, k, ""))) {
            throw new IllegalArgumentException("Unsupported " + k);
        }
    }

    private static void nonNegativeInteger(ProcessDefinition.Node n, String k) {
        String raw = value(n, k, "0");
        try {
            if (Integer.parseInt(raw) < 0) {
                throw new IllegalArgumentException(k + " cannot be negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(k + " must be a whole number, not '" + raw + "'");
        }
    }

    private static byte[] bytes(Map<String, FlowValue> in, String k) {
        FlowValue v = in.get(k);
        if (v == null) throw new IllegalArgumentException(k + " is required");
        return v.bytes();
    }

    private static String string(Map<String, FlowValue> in, String k) {
        FlowValue v = in.get(k);
        if (v == null) throw new IllegalArgumentException(k + " is required");
        return new String(v.bytes(), v.charset() == null ? StandardCharsets.UTF_8 : v.charset());
    }

    private static String setting(ProcessDefinition.Node n, Map<String, FlowValue> in, String k) {
        FlowValue v = in.get(k);
        return v == null ? n.configuration.get(k)
                : new String(v.bytes(), v.charset() == null ? StandardCharsets.UTF_8 : v.charset());
    }

    private static FlowValue text(String value) {
        return FlowValue.text(value, StandardCharsets.UTF_8);
    }
}
