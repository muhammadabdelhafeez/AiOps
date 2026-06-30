package org.kfh.aiops.plugin.security;

import java.util.Map;
import java.util.Locale;

/** Shared connector TLS certificate-verification helpers. */
public final class ConnectorTlsSupport {

    public static final String VERIFY_SSL_FIELD = "verifySsl";
    private static final String EXPIRED_CERT_GUIDANCE = "The remote TLS certificate is expired. Renew/rebind the connector or WinRM HTTPS certificate on the destination server and retest. If the same error also reports unknown CA or CN mismatch, issue the renewed certificate from a CA trusted by the connector host and include a SAN/CN matching the configured hostname. Disabling certificate-chain verification may skip CA/CN/revocation checks for PowerShell remoting, but it does not make an expired WinRM HTTPS certificate acceptable.";
    private static final String REVOCATION_GUIDANCE = "The connector host cannot complete certificate revocation checking. Allow the connector host to reach the certificate CRL/OCSP endpoints from the issuing CA, or temporarily disable TLS certificate verification for this connector test to make PowerShell remoting use SkipRevocationCheck while HTTPS encryption remains enabled.";
    private static final String TRUST_CHAIN_GUIDANCE = "Java does not trust the connector TLS certificate chain. Import the corporate CA into the JVM truststore, or temporarily disable TLS certificate verification for this connector test in a governed dev/hybrid setup.";

    private ConnectorTlsSupport() {
    }

    public static boolean verifySsl(Map<String, ?> values) {
        if (values == null || !values.containsKey(VERIFY_SSL_FIELD)) {
            return true;
        }
        var value = values.get(VERIFY_SSL_FIELD);
        if (value instanceof Boolean bool) {
            return bool;
        }
        var text = String.valueOf(value).trim();
        return text.isBlank() || Boolean.parseBoolean(text);
    }

    public static String verificationModeMessage(boolean verifySsl) {
        return verifySsl
                ? "TLS certificate chain verification is enabled."
                : "TLS certificate chain verification is disabled for this connector test; HTTPS encryption is still used.";
    }

    public static String enrichCertificateFailure(String message) {
        if (isAlreadyEnriched(message)) {
            return message;
        }
        if (isCertificateExpiredFailure(message)) {
            return appendGuidance(message, EXPIRED_CERT_GUIDANCE);
        }
        if (isCertificateRevocationFailure(message)) {
            return appendGuidance(message, REVOCATION_GUIDANCE);
        }
        if (!isCertificatePathFailure(message)) {
            return message;
        }
        return appendGuidance(message, TRUST_CHAIN_GUIDANCE);
    }

    private static boolean isAlreadyEnriched(String message) {
        var lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return lower.contains("the remote tls certificate is expired")
                || lower.contains("cannot complete certificate revocation checking")
                || lower.contains("java does not trust the connector tls certificate chain");
    }

    private static String appendGuidance(String message, String guidance) {
        var base = message == null ? "" : message.strip();
        if (base.endsWith(".")) {
            return base + " " + guidance;
        }
        return base + ". " + guidance;
    }

    private static boolean isCertificateExpiredFailure(String message) {
        var lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return lower.contains("certificate is expired")
                || lower.contains("ssl certificate is expired")
                || lower.contains("certificate has expired")
                || lower.contains("notafter");
    }

    private static boolean isCertificateRevocationFailure(String message) {
        var lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return lower.contains("could not be checked for revocation")
                || lower.contains("revocation might be unreachable")
                || lower.contains("revocation server was offline")
                || lower.contains("unable to check revocation")
                || lower.contains("revocation check failed");
    }

    private static boolean isCertificatePathFailure(String message) {
        var lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return lower.contains("pkix path building failed")
                || lower.contains("unable to find valid certification path")
                || lower.contains("sun.security.provider.certpath.sun certpathbuilderexception")
                || lower.contains("certpathbuilderexception");
    }
}

