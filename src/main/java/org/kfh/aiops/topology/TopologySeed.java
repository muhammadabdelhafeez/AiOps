package org.kfh.aiops.topology;

import java.util.List;

/**
 * Hand-modelled KFH topology seed used until CMDB/agent-discovered topology exists. Mirrors the flows
 * shown in the UI: Fund Transfer, KFHOnline and WAMD, with shared components (API Gateway, Oracle
 * Core, SAN Storage) so one root cause can impact multiple applications.
 *
 * <p>Dependency direction: Channel → Gateway → Service → Database → Storage. {@code dependsOn} points
 * to foundations; the reverse (dependents) is the blast radius.
 */
final class TopologySeed {

    private TopologySeed() {
    }

    static TopologyModel build() {
        var apps = List.of(
                new BusinessApplication("fund-transfer", "Fund Transfer", "Critical", List.of("Mobile Transfer", "Net Banking Transfer")),
                new BusinessApplication("kfhonline", "KFHOnline", "Critical", List.of("Login", "Account Summary")),
                new BusinessApplication("wamd", "WAMD", "High", List.of("Instant Payment")));

        var components = List.of(
                new Component("mob-app", "Mobile Banking", "Channel", List.of("api-gw"), List.of("fund-transfer", "wamd")),
                new Component("web-portal", "Web Portal", "Channel", List.of("api-gw"), List.of("kfhonline")),
                new Component("api-gw", "API Gateway", "Gateway", List.of("svc-transfer", "svc-auth", "svc-payment"), List.of("fund-transfer", "kfhonline", "wamd")),
                new Component("svc-transfer", "Transfer Service", "Service", List.of("oracle-core"), List.of("fund-transfer")),
                new Component("svc-auth", "Auth Service", "Service", List.of("ldap", "oracle-core"), List.of("kfhonline")),
                new Component("svc-payment", "Payment Service", "Service", List.of("oracle-core"), List.of("wamd")),
                new Component("oracle-core", "Oracle Core", "Database", List.of("san-storage"), List.of("fund-transfer", "kfhonline", "wamd")),
                new Component("ldap", "LDAP / IAM", "Directory", List.of(), List.of("kfhonline")),
                new Component("san-storage", "SAN Storage", "Storage", List.of(), List.of("fund-transfer", "kfhonline", "wamd")));

        var assets = List.of(
                new Asset("MOB-APP-KW", "Mobile Banking KW", "MobileApp", "mob-app"),
                new Asset("WEB-ONLINE-KW", "KFHOnline Web", "WebApp", "web-portal"),
                new Asset("API-GW-01", "API Gateway node 1", "ApiGateway", "api-gw"),
                new Asset("API-GW-02", "API Gateway node 2", "ApiGateway", "api-gw"),
                new Asset("API-GATEWAY", "API Gateway", "ApiGateway", "api-gw"),
                new Asset("SVC-TRANSFER", "Transfer Service", "CoreBanking", "svc-transfer"),
                new Asset("TRANSFER SERVICE", "Transfer Service", "CoreBanking", "svc-transfer"),
                new Asset("SVC-AUTH", "Auth Service", "Service", "svc-auth"),
                new Asset("SVC-PAYMENT", "Payment Service", "Service", "svc-payment"),
                new Asset("ORACLE-CORE-01", "Oracle Core DB", "OracleDatabase", "oracle-core"),
                new Asset("LDAP-01", "LDAP directory", "Directory", "ldap"),
                new Asset("SAN-STORAGE-02", "SAN Storage array", "Storage.SAN.Lun", "san-storage"));

        return new TopologyModel(apps, components, assets);
    }
}
