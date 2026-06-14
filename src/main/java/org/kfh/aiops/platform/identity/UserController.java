package org.kfh.aiops.platform.identity;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.kfh.aiops.commandcenter.dto.UiWriteRequest;
import org.kfh.aiops.platform.tenant.TenantContext;
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
@RequestMapping("/api/v1/users")
public class UserController {

    private final IdentityAdminService identityAdminService;

    public UserController(IdentityAdminService identityAdminService) {
        this.identityAdminService = identityAdminService;
    }

    @GetMapping
    public Object list(TenantContext ctx, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String environment) {
        return identityAdminService.users(ctx, country, environment, page, size);
    }

    @GetMapping("/{id}")
    public Object get(TenantContext ctx, @PathVariable UUID id) {
        return identityAdminService.user(ctx, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(TenantContext ctx, @Valid @RequestBody UiWriteRequest request) {
        return identityAdminService.createUser(ctx, request);
    }

    @PutMapping("/{id}")
    public Object update(TenantContext ctx, @PathVariable UUID id, @Valid @RequestBody UiWriteRequest request) {
        return identityAdminService.updateUser(ctx, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(TenantContext ctx, @PathVariable UUID id) {
        identityAdminService.deleteUser(ctx, id);
    }

    @PatchMapping("/{id}/toggle")
    public Object toggle(TenantContext ctx, @PathVariable UUID id) {
        return identityAdminService.toggleUser(ctx, id);
    }

    @PatchMapping("/{id}/password")
    public Object resetPassword(TenantContext ctx, @PathVariable UUID id, @Valid @RequestBody UiWriteRequest request) {
        return identityAdminService.resetPassword(ctx, id, request);
    }

    @GetMapping("/roles")
    public Object roles(TenantContext ctx) {
        return identityAdminService.userRoles(ctx);
    }

    @GetMapping("/{userId}/permissions")
    public Object permissions(TenantContext ctx, @PathVariable UUID userId) {
        return identityAdminService.userPermissions(ctx, userId);
    }

    /**
     * Reports that the running server can persist login users to PostgreSQL. Database-backed identity storage is a
     * mandatory startup dependency, so a running application reports writes enabled.
     */
    @GetMapping("/storage-status")
    public Map<String, Object> storageStatus(TenantContext ctx) {
        ctx.requirePermission("IDENTITY_READ");
        return Map.of(
                "databaseBacked", true,
                "writesEnabled", true,
                "reason", "OK");
    }
}

