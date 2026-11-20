package com.thriftbite.backend.report;

import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.report.dto.ReportRequest;
import com.thriftbite.backend.report.dto.ReportResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Buyers report problems here. Nothing in the app shows a store who reported it. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('CONSUMER')")
public class ReportController {

    private final Reports reports;

    ReportController(Reports reports) {
        this.reports = reports;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReportRequest request) {
        return reports.create(TokenService.userId(jwt), request);
    }

    @GetMapping
    public List<ReportResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return reports.mine(TokenService.userId(jwt));
    }
}
