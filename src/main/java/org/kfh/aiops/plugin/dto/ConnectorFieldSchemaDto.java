package org.kfh.aiops.plugin.dto;

import java.util.List;

public record ConnectorFieldSchemaDto(
        String key,
        String label,
        String type,
        String section,
        boolean required,
        boolean secret,
        String placeholder,
        String helpText,
        Object defaultValue,
        List<String> options) {
}

