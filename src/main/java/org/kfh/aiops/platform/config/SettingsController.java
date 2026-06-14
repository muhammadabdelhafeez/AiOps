package org.kfh.aiops.platform.config;

import java.util.Map;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public Object get(TenantContext ctx) {
        return settingsService.get(ctx);
    }

    @PutMapping
    public Object update(TenantContext ctx, @RequestBody Map<String, Object> request) {
        return settingsService.update(ctx, request);
    }

    @PostMapping("/{section}/test")
    public Object test(TenantContext ctx, @PathVariable String section, @RequestBody Map<String, Object> request) {
        return settingsService.test(ctx, section, request);
    }
}

