package org.aiopsanalysis.dto.connector;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for triggering a manual collect run.
 */
public record CollectNowRequest(
        @JsonProperty("windowMinutes")
        Integer windowMinutes,

        @JsonProperty("reason")
        String reason
) {}
