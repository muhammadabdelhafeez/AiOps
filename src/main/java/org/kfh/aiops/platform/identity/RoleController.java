package org.kfh.aiops.platform.identity;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final IdentityAdminService identityAdminService;

    public RoleController(IdentityAdminService identityAdminService) {
        this.identityAdminService = identityAdminService;
    }

    @GetMapping
    public Object list(TenantContext ctx) {
        return identityAdminService.roles(ctx);
    }

    @GetMapping("/{id}")
    public Object get(TenantContext ctx, @PathVariable UUID id) {
        return identityAdminService.role(ctx, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(TenantContext ctx, @Valid @RequestBody UiWriteRequest request) {
        return identityAdminService.createRole(ctx, request);
    }

    @PutMapping("/{id}")
    public Object update(TenantContext ctx, @PathVariable UUID id, @Valid @RequestBody UiWriteRequest request) {
        return identityAdminService.updateRole(ctx, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(TenantContext ctx, @PathVariable UUID id) {
        identityAdminService.deleteRole(ctx, id);
    }
}

