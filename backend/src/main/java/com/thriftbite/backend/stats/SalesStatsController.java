package com.thriftbite.backend.stats;

import com.thriftbite.backend.auth.TokenService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/owner/stats")
public class SalesStatsController {

    private final SalesStatsService stats;

    public SalesStatsController(SalesStatsService stats) {
        this.stats = stats;
    }

    /**
     * Sales for the signed-in owner's store, e.g. {@code ?from=2026-10-31&to=2026-11-06}.
     * Both dates are Manila calendar days and inclusive. Without them: the last 7 full days.
     */
    @GetMapping
    public SalesStats get(@AuthenticationPrincipal Jwt jwt,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.forOwner(TokenService.userId(jwt), from, to);
    }
}
