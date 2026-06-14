package org.kfh.aiops.commandcenter.alerts;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public Object list(TenantContext ctx, @RequestParam(required = false) String country,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return alertService.list(ctx, country, environment, page, size);
    }

    @GetMapping("/{id}")
    public Object get(TenantContext ctx, @PathVariable UUID id) {
        return alertService.get(ctx, id);
    }

    @PostMapping("/acknowledge")
    public Object acknowledge(TenantContext ctx, @RequestBody Map<String, List<UUID>> body) {
        return alertService.acknowledge(ctx, body.getOrDefault("ids", List.of()));
    }

    @GetMapping("/activity")
    public Object activity(TenantContext ctx) {
        return alertService.activity(ctx);
    }
}

