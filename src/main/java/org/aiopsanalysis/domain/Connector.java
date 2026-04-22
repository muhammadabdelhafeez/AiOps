package org.aiopsanalysis.domain;

/**
 * Connector-related enums and types used across the application.
 * 
 * NOTE: The actual JPA entity is org.aiopsanalysis.domain.postgres.Connector.
 * This class only holds shared enums to avoid circular dependencies.
 */
public class Connector {

    // Private constructor to prevent instantiation - this is an enum holder only
    private Connector() {}

    /**
     * Supported connector types for data source integrations.
     */
    public enum ConnectorType {
        BMC, SOLARWINDS, SCOM, NAGIOS, ZABBIX, PROMETHEUS
    }

    /**
     * Connector operation status values.
     */
    public enum ConnectorStatus {
        SUCCESS, ERROR, TIMEOUT, RUNNING
    }
}
