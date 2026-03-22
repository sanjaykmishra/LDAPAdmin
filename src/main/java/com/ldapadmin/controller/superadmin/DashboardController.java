package com.ldapadmin.controller.superadmin;

import com.ldapadmin.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/superadmin/dashboard")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public Map<String, Object> get() {
        return dashboardService.getDashboard();
    }
}
