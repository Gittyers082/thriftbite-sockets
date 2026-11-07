package com.thriftbite.backend.reservation;

import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.reservation.dto.CompleteRequest;
import com.thriftbite.backend.reservation.dto.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The owner's side: incoming pickups, and completing one with the buyer's claim code. */
@RestController
@RequestMapping("/api/owner/reservations")
@PreAuthorize("hasRole('OWNER')")
public class OwnerReservationController {

    private final ReservationService reservations;

    public OwnerReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    @GetMapping
    public List<ReservationResponse> active(@AuthenticationPrincipal Jwt jwt) {
        return reservations.listActiveForOwner(TokenService.userId(jwt));
    }

    @PostMapping("/{reservationId}/complete")
    public ReservationResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reservationId,
                                        @Valid @RequestBody CompleteRequest request) {
        return reservations.complete(TokenService.userId(jwt), reservationId, request.claimCode());
    }
}
