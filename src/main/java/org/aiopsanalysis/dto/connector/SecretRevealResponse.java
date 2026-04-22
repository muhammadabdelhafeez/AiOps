package org.aiopsanalysis.dto.connector;

/**
 * Response DTO for a revealed secret.
 */
public record SecretRevealResponse(
        String field,
        String value
) {}
