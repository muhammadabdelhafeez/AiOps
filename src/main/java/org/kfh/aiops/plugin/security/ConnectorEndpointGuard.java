package org.kfh.aiops.plugin.security;

import java.net.IDN;
import java.net.InetAddress;
import java.util.Locale;
import org.kfh.aiops.platform.exception.ValidationException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Connector-specific SSRF guard for governed hybrid KFH environments.
 *
 * <p>Public and private KFH hybrid connector hosts are allowed after structural URL validation.
 * Metadata, localhost, loopback, link-local, and multicast targets remain denied for SSRF protection.
 */
@Component
public class ConnectorEndpointGuard {

    private final Environment environment;

    public ConnectorEndpointGuard(Environment environment) {
        this.environment = environment;
    }

    public void validateLiteralHost(String connectorName, String host) {
        var normalized = normalizeHost(host);
        if (isAlwaysBlockedName(normalized)) {
            throw new ValidationException(connectorName + " host is not allowed for connector SSRF protection");
        }
        if (isUnsafeLiteralHost(normalized)) {
            throw new ValidationException(connectorName + " host targets a loopback, link-local, metadata, or multicast address");
        }
    }

    public void validateResolvedAddresses(String connectorName, String host) {
        var normalized = normalizeHost(host);
        if (!resolveHosts()) {
            return;
        }
        try {
            for (var address : InetAddress.getAllByName(normalized)) {
                if (isUnsafeAddress(address)) {
                    throw new ValidationException(connectorName + " host resolves to a loopback, link-local, metadata, or multicast address");
                }
            }
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationException(connectorName + " host could not be resolved");
        }
    }

    public static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "";
        }
        return IDN.toASCII(host.trim()).toLowerCase(Locale.ROOT);
    }

    private boolean resolveHosts() {
        return environment.getProperty("kfh.security.ssrf.resolve-hosts", Boolean.class, true);
    }

    private static boolean isAlwaysBlockedName(String host) {
        return host.equals("localhost")
                || host.endsWith(".localhost")
                || host.equals("169.254.169.254")
                || host.equals("metadata.google.internal")
                || host.equals("metadata")
                || host.equals("::1");
    }

    private static boolean isUnsafeLiteralHost(String host) {
        try {
            var address = InetAddress.getByName(host);
            return isUnsafeAddress(address);
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean isUnsafeAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress();
    }
}

