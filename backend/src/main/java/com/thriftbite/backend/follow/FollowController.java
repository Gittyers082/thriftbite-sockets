package com.thriftbite.backend.follow;

import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.follow.FollowService.FollowState;
import com.thriftbite.backend.follow.FollowService.Followed;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Only buyers follow stores. */
@RestController
@PreAuthorize("hasRole('CONSUMER')")
public class FollowController {

    private final FollowService follows;

    public FollowController(FollowService follows) {
        this.follows = follows;
    }

    /** @param alertsOn leave out (or true) for alerts; false keeps the store on your list quietly */
    public record FollowRequest(Boolean alertsOn) {
    }

    @GetMapping("/api/follows")
    public List<Followed> followed(@AuthenticationPrincipal Jwt jwt) {
        return follows.followed(TokenService.userId(jwt));
    }

    /** Follow, or change whether alerts are on. Safe to repeat. */
    @PutMapping("/api/stores/{slug}/follow")
    public FollowState follow(@AuthenticationPrincipal Jwt jwt, @PathVariable String slug,
                              @RequestBody(required = false) FollowRequest request) {
        boolean alertsOn = request == null || request.alertsOn() == null || request.alertsOn();
        return follows.follow(TokenService.userId(jwt), slug, alertsOn);
    }

    @DeleteMapping("/api/stores/{slug}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal Jwt jwt, @PathVariable String slug) {
        follows.unfollow(TokenService.userId(jwt), slug);
    }
}
