package org.aiopsanalysis.dto.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing connector.
 */
public record ConnectorUpdateRequest(
        @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_\\-\\s]+$", message = "Name contains invalid characters")
        String name,

        @JsonProperty("enabled")
        Boolean enabled,

        JsonNode config,

        /**
         * Secrets in plain text - will be encrypted before storage.
         * Only provided fields will be updated.
         */
        JsonNode secretsPlain
) {}
