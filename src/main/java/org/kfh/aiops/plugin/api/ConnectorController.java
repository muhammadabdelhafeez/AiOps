package org.kfh.aiops.plugin.api;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.kfh.aiops.commandcenter.dto.UiWriteRequest;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.kfh.aiops.plugin.service.ConnectorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/connectors")
public class ConnectorController {

    private final ConnectorService connectorService;

    public ConnectorController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @GetMapping
    public Object list(TenantContext ctx, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return connectorService.list(ctx, page, size);
    }

    @GetMapping("/types")
    public Object types(TenantContext ctx) {
        return connectorService.types(ctx);
    }

    @GetMapping("/{id}")
    public Object get(TenantContext ctx, @PathVariable UUID id) {
        return connectorService.get(ctx, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(TenantContext ctx, @Valid @RequestBody UiWriteRequest request) {
        return connectorService.create(ctx, request);
    }

    @PutMapping("/{id}")
    public Object update(TenantContext ctx, @PathVariable UUID id, @Valid @RequestBody UiWriteRequest request) {
        return connectorService.update(ctx, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(TenantContext ctx, @PathVariable UUID id) {
        connectorService.delete(ctx, id);
    }

    @PatchMapping("/{id}/toggle")
    public Object toggle(TenantContext ctx, @PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        return connectorService.toggle(ctx, id, Boolean.TRUE.equals(body.get("enabled")));
    }

    @PostMapping("/{id}/test")
    public Object test(TenantContext ctx, @PathVariable UUID id) {
        return connectorService.test(ctx, id);
    }

    @PostMapping("/heartbeat")
    public Object heartbeat(TenantContext ctx) {
        return connectorService.heartbeat(ctx);
    }

    @GetMapping("/{id}/logs")
    public Object logs(TenantContext ctx, @PathVariable UUID id) {
        return connectorService.logs(ctx, id);
    }
}

