package com.thriftbite.backend.reservation;

import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.reservation.dto.ReservationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The buyer's side: check out the cart, see reservations, cancel one. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('CONSUMER')")
public class ReservationController {

    private final ReservationService reservations;

    public ReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ReservationResponse> checkout(@AuthenticationPrincipal Jwt jwt) {
        return reservations.checkout(TokenService.userId(jwt));
    }

    @GetMapping("/reservations")
    public List<ReservationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return reservations.listForBuyer(TokenService.userId(jwt));
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reservationId) {
        return reservations.cancel(TokenService.userId(jwt), reservationId);
    }
}
