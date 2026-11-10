package com.thriftbite.backend.auth;

import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Runs on every request that carries a token, after its signature and expiry checked out.
 *
 * The token only proves who is asking. What that account may do is read from the
 * database each time, so a token stops working the moment its account is deleted
 * (the demo reset does this) or is no longer approved, and a role change takes
 * effect at once instead of when the token expires hours later. The price is one
 * lookup by primary key per logged-in request.
 */
@Component
public class CurrentAccountConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public CurrentAccountConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = users.findById(accountId(jwt))
                .filter(User::isApproved)
                .orElseThrow(() -> new InvalidBearerTokenException("This login is no longer valid. Log in again."));
        // "ROLE_OWNER" etc., which is what hasRole('OWNER') checks for
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())), jwt.getSubject());
    }

    private static UUID accountId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("This login is no longer valid. Log in again.");
        }
    }
}
