package org.kfh.aiops.plugin.dto;

import java.util.List;
import java.util.Map;

public record ConnectorTypeMetadataDto(
        String pluginType,
        String displayName,
        String category,
        String icon,
        String description,
        boolean available,
        List<String> supportedCountries,
        List<String> supportedEnvironments,
        List<ConnectorFieldSchemaDto> fields,
        Map<String, Object> defaults) {
}

