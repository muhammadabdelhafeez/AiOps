package org.kfh.aiops.commandcenter.schedules;

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
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public Object list(TenantContext ctx, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return scheduleService.list(ctx, page, size);
    }

    @GetMapping("/{id}")
    public Object get(TenantContext ctx, @PathVariable UUID id) {
        return scheduleService.get(ctx, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(TenantContext ctx, @Valid @RequestBody UiWriteRequest request) {
        return scheduleService.create(ctx, request);
    }

    @PutMapping("/{id}")
    public Object update(TenantContext ctx, @PathVariable UUID id, @Valid @RequestBody UiWriteRequest request) {
        return scheduleService.update(ctx, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(TenantContext ctx, @PathVariable UUID id) {
        scheduleService.delete(ctx, id);
    }

    @PatchMapping("/{id}/toggle")
    public Object toggle(TenantContext ctx, @PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        return scheduleService.toggle(ctx, id, Boolean.TRUE.equals(body.get("enabled")));
    }

    @PostMapping("/{id}/run")
    public Object run(TenantContext ctx, @PathVariable UUID id) {
        return scheduleService.run(ctx, id);
    }

    @GetMapping("/{id}/runs")
    public Object runs(TenantContext ctx, @PathVariable UUID id) {
        return scheduleService.runs(ctx, id);
    }
}

