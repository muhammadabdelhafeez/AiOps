package org.aiopsanalysis.dto.connector;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for revealing a connector secret.
 * Requires ADMIN permission.
 */
public record SecretRevealRequest(
        @NotBlank(message = "Field name is required")
        @Pattern(regexp = "^(username|password|token|apiKey|clientId|clientSecret)$",
                message = "Invalid secret field")
        String field,

        @NotBlank(message = "Reason is required for audit")
        @JsonProperty("reason")
        String reason
) {}
