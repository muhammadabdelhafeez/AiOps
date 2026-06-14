package org.kfh.aiops.commandcenter.inventory;

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
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public Object list(TenantContext ctx, @RequestParam(required = false) String country,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventoryService.list(ctx, country, environment, page, size);
    }

    @GetMapping("/{id}")
    public Object get(TenantContext ctx, @PathVariable UUID id) {
        return inventoryService.get(ctx, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(TenantContext ctx, @Valid @RequestBody UiWriteRequest request) {
        return inventoryService.create(ctx, request);
    }

    @PutMapping("/{id}")
    public Object update(TenantContext ctx, @PathVariable UUID id, @Valid @RequestBody UiWriteRequest request) {
        return inventoryService.update(ctx, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(TenantContext ctx, @PathVariable UUID id) {
        inventoryService.delete(ctx, id);
    }

    @GetMapping("/{id}/dependencies")
    public Object dependencies(TenantContext ctx, @PathVariable UUID id) {
        return inventoryService.dependencies(ctx, id);
    }

    @GetMapping("/{id}/alerts")
    public Object alerts(TenantContext ctx, @PathVariable UUID id) {
        return inventoryService.alerts(ctx, id);
    }
}

