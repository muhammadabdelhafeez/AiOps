package org.kfh.aiops.commandcenter.dashboard;

import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/kpis")
    public Object kpis(TenantContext ctx, @RequestParam(required = false) String country,
            @RequestParam(required = false) String environment) {
        return dashboardService.kpis(ctx, country, environment);
    }

    @GetMapping("/trends")
    public Object trends(TenantContext ctx, @RequestParam(required = false) String country,
            @RequestParam(required = false) String environment) {
        return dashboardService.trends(ctx, country, environment);
    }

    @GetMapping("/top-apps")
    public Object topApps(TenantContext ctx, @RequestParam(required = false) String country,
            @RequestParam(required = false) String environment) {
        return dashboardService.topApps(ctx, country, environment);
    }

    @GetMapping("/summary")
    public Object summary(TenantContext ctx) {
        return dashboardService.summary(ctx);
    }

    @GetMapping("/sources")
    public Object sources(TenantContext ctx) {
        return dashboardService.sources(ctx);
    }
}

