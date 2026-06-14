package org.kfh.aiops.platform.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IdentitySignInRequest(
        @NotBlank @Size(max = 80) String username,
        @NotBlank @Size(max = 128) String password,
        @NotBlank @Size(max = 8) String countryCode,
        @NotBlank @Size(max = 16) String environment) {
}

