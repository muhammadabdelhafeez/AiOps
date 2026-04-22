package org.aiopsanalysis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

/**
 * SSRF (Server-Side Request Forgery) validation utility.
 *
 * SECURITY:
 * - Blocks requests to internal IPs (private ranges, localhost, metadata)
 * - Validates URL format
 * - Optional allowlist for trusted domains
 */
@Component
public class SsrfValidator {

    private static final Logger log = LoggerFactory.getLogger(SsrfValidator.class);

    // Blocked IP ranges
    private static final Set<String> BLOCKED_IP_PREFIXES = Set.of(
            "127.",           // Localhost
            "10.",            // Private Class A
            "192.168.",       // Private Class C
            "169.254.",       // Link-local / AWS metadata
            "0.",             // Invalid
            "::1",            // IPv6 localhost
            "fe80:",          // IPv6 link-local
            "fc00:",          // IPv6 private
            "fd00:"           // IPv6 private
    );

    // Blocked hostnames
    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "169.254.169.254",  // AWS/GCP/Azure metadata
            "metadata.google.internal",
            "metadata.azure.internal"
    );

    // 172.16.0.0 - 172.31.255.255 (Class B private)
    private boolean isPrivate172(String ip) {
        if (ip.startsWith("172.")) {
            try {
                String[] parts = ip.split("\\.");
                int secondOctet = Integer.parseInt(parts[1]);
                return secondOctet >= 16 && secondOctet <= 31;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Value("${aiops.ssrf.allowed-domains:#{null}}")
    private List<String> allowedDomains;

    @Value("${aiops.ssrf.allow-private:false}")
    private boolean allowPrivate;

    /**
     * Validate a URL for SSRF safety.
     *
     * @param urlString The URL to validate
     * @return Validation result
     */
    public ValidationResult validate(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return ValidationResult.invalid("URL is required");
        }

        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            return ValidationResult.invalid("Invalid URL format: " + e.getMessage());
        }

        // Check protocol
        String protocol = url.getProtocol().toLowerCase();
        if (!protocol.equals("http") && !protocol.equals("https")) {
            return ValidationResult.invalid("Only HTTP and HTTPS protocols are allowed");
        }

        // Get hostname
        String host = url.getHost().toLowerCase();

        // Check blocked hostnames
        if (BLOCKED_HOSTNAMES.contains(host)) {
            return ValidationResult.invalid("Blocked hostname: " + host);
        }

        // Resolve hostname and check IP
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                String ip = address.getHostAddress();

                // Check blocked IP prefixes
                if (!allowPrivate) {
                    for (String prefix : BLOCKED_IP_PREFIXES) {
                        if (ip.startsWith(prefix)) {
                            return ValidationResult.invalid("Blocked IP range: " + ip);
                        }
                    }

                    // Check 172.16-31 range
                    if (isPrivate172(ip)) {
                        return ValidationResult.invalid("Blocked private IP: " + ip);
                    }
                }

                // Check if loopback
                if (address.isLoopbackAddress()) {
                    return ValidationResult.invalid("Loopback address not allowed: " + ip);
                }

                // Check if link-local
                if (address.isLinkLocalAddress()) {
                    return ValidationResult.invalid("Link-local address not allowed: " + ip);
                }
            }
        } catch (UnknownHostException e) {
            return ValidationResult.invalid("Cannot resolve hostname: " + host);
        }

        // Check allowed domains (if configured)
        if (allowedDomains != null && !allowedDomains.isEmpty()) {
            boolean allowed = allowedDomains.stream()
                    .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
            if (!allowed) {
                return ValidationResult.invalid("Domain not in allowlist: " + host);
            }
        }

        return ValidationResult.success();
    }

    /**
     * Validation result record.
     */
    public record ValidationResult(boolean valid, String error) {
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }
    }
}
