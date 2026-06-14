package org.kfh.aiops.commandcenter.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

/** Generic frontend scaffold write DTO; module services validate semantic fields. */
public record UiWriteRequest(
        @Size(max = 200) String name,
        @Size(max = 500) String title,
        @Size(max = 20) String status,
        @Size(max = 20) String severity,
        Boolean enabled,
        Map<String, Object> attributes) {
}

