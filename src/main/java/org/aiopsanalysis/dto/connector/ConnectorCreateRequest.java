package org.aiopsanalysis.dto.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new connector.
 */
public record ConnectorCreateRequest(
        @NotBlank(message = "Connector type is required")
        @Pattern(regexp = "^(SCOM|vROps|BMC|SolarWinds|Elastic|Azure|Syslog|SMTP|SharePoint|Teams)$",
                message = "Invalid connector type")
        String type,

        @NotBlank(message = "Connector name is required")
        @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_\\-\\s]+$", message = "Name contains invalid characters")
        String name,

        @JsonProperty("enabled")
        Boolean enabled,

        @NotNull(message = "Configuration is required")
        JsonNode config,

        /**
         * Secrets in plain text - will be encrypted before storage.
         * Structure:
         * {
         *   "authType": "BASIC|BEARER|APIKEY|OAUTH2",
         *   "username": "...",
         *   "password": "...",
         *   "token": "...",
         *   "apiKey": "...",
         *   "clientId": "...",
         *   "clientSecret": "..."
         * }
         */
        JsonNode secretsPlain
) {
    public ConnectorCreateRequest {
        if (enabled == null) {
            enabled = true;
        }
    }
}
