package org.kfh.aiops.commandcenter.reports;

import java.util.Map;
import java.util.UUID;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public Object list(TenantContext ctx, @RequestParam(required = false) String country,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reportService.list(ctx, country, environment, page, size);
    }

    @GetMapping("/{id}")
    public Object get(TenantContext ctx, @PathVariable UUID id) {
        return reportService.get(ctx, id);
    }

    @GetMapping("/runs")
    public Object runs(TenantContext ctx) {
        return reportService.runs(ctx);
    }

    @GetMapping("/runs/{runId}/artifacts")
    public Object artifacts(TenantContext ctx, @PathVariable UUID runId) {
        return reportService.artifacts(ctx, runId);
    }

    @GetMapping("/artifacts/{artifactId}/download")
    public ResponseEntity<String> download(TenantContext ctx, @PathVariable UUID artifactId) {
        ctx.requirePermission("REPORT_READ");
        return ResponseEntity.ok("Evidence artifact download placeholder: " + artifactId);
    }

    @PostMapping("/generate")
    public Object generate(TenantContext ctx, @RequestBody Map<String, Object> request) {
        return reportService.generate(ctx, request);
    }
}

