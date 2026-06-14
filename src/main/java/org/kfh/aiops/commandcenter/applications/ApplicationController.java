package org.kfh.aiops.commandcenter.applications;

import jakarta.validation.Valid;
import java.util.UUID;
import org.kfh.aiops.commandcenter.dto.UiWriteRequest;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public Object list(TenantContext ctx, @RequestParam(required = false) String country,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return applicationService.list(ctx, country, environment, page, size);
    }

    @GetMapping("/{id}")
    public Object get(TenantContext ctx, @PathVariable UUID id) {
        return applicationService.get(ctx, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(TenantContext ctx, @Valid @RequestBody UiWriteRequest request) {
        return applicationService.create(ctx, request);
    }

    @PutMapping("/{id}")
    public Object update(TenantContext ctx, @PathVariable UUID id, @Valid @RequestBody UiWriteRequest request) {
        return applicationService.update(ctx, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(TenantContext ctx, @PathVariable UUID id) {
        applicationService.delete(ctx, id);
    }

    @GetMapping("/{id}/inventory")
    public Object inventory(TenantContext ctx, @PathVariable UUID id) {
        return applicationService.inventory(ctx, id);
    }

    @GetMapping("/{id}/incidents")
    public Object incidents(TenantContext ctx, @PathVariable UUID id) {
        return applicationService.incidents(ctx, id);
    }

    @GetMapping("/{id}/health")
    public Object health(TenantContext ctx, @PathVariable UUID id) {
        return applicationService.health(ctx, id);
    }
}

