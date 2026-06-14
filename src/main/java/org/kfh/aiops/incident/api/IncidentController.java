package org.kfh.aiops.incident.api;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.kfh.aiops.commandcenter.dto.PageResponse;
import org.kfh.aiops.commandcenter.dto.UiWriteRequest;
import org.kfh.aiops.incident.service.IncidentService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public PageResponse<Map<String, Object>> list(TenantContext ctx,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return incidentService.list(ctx, country, environment, page, size);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(TenantContext ctx, @PathVariable UUID id) {
        return incidentService.get(ctx, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(TenantContext ctx, @Valid @RequestBody UiWriteRequest request) {
        return incidentService.create(ctx, request);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(TenantContext ctx, @PathVariable UUID id,
            @Valid @RequestBody UiWriteRequest request) {
        return incidentService.update(ctx, id, request);
    }

    @PatchMapping("/{id}/status")
    public Map<String, Object> status(TenantContext ctx, @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        return incidentService.updateStatus(ctx, id, body.getOrDefault("status", "OPEN"));
    }

    @GetMapping("/{id}/evidence")
    public Object evidence(TenantContext ctx, @PathVariable UUID id) {
        return incidentService.evidence(ctx, id);
    }

    @GetMapping("/{id}/related")
    public Object related(TenantContext ctx, @PathVariable UUID id) {
        return incidentService.related(ctx, id);
    }

    @GetMapping("/{id}/timeline")
    public Object timeline(TenantContext ctx, @PathVariable UUID id) {
        return incidentService.timeline(ctx, id);
    }
}

