package org.aiopsanalysis.service.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for connector plugins.
 * Manages all available connector types and their implementations.
 */
@Component
public class ConnectorPluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectorPluginRegistry.class);

    private final Map<String, ConnectorPlugin> plugins = new HashMap<>();

    public ConnectorPluginRegistry(List<ConnectorPlugin> pluginList) {
        for (ConnectorPlugin plugin : pluginList) {
            plugins.put(plugin.getType().toUpperCase(), plugin);
            log.info("Registered connector plugin: {} ({})", plugin.getDisplayName(), plugin.getVersion());
        }
        log.info("Total connector plugins registered: {}", plugins.size());
    }

    /**
     * Get a plugin by type.
     */
    public Optional<ConnectorPlugin> getPlugin(String type) {
        if (type == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(plugins.get(type.toUpperCase()));
    }

    /**
     * Get all registered plugins.
     */
    public Collection<ConnectorPlugin> getAllPlugins() {
        return plugins.values();
    }

    /**
     * Get all plugin types.
     */
    public Collection<String> getAllTypes() {
        return plugins.keySet();
    }

    /**
     * Check if a plugin type is supported.
     */
    public boolean isSupported(String type) {
        return type != null && plugins.containsKey(type.toUpperCase());
    }

    /**
     * Get plugin metadata for all plugins (for UI catalog).
     */
    public List<PluginMetadata> getPluginMetadata() {
        return plugins.values().stream()
                .map(p -> new PluginMetadata(
                        p.getType(),
                        p.getDisplayName(),
                        p.getDescription(),
                        p.getVersion(),
                        p.getConfigSchema()
                ))
                .toList();
    }

    /**
     * Plugin metadata for UI display.
     */
    public record PluginMetadata(
            String type,
            String displayName,
            String description,
            String version,
            ConnectorPlugin.ConfigSchema configSchema
    ) {}
}
