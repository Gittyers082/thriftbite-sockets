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
        User user = accountFor(jwt);
        // "ROLE_OWNER" etc., which is what hasRole('OWNER') checks for
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())), jwt.getSubject());
    }

    /**
     * The account a verified token belongs to, as it is in the database now.
     *
     * @throws InvalidBearerTokenException if the account is gone or isn't approved, or the
     *                                     password has changed since the token was issued
     */
    public User accountFor(Jwt jwt) {
        return users.findById(accountId(jwt))
                .filter(User::isApproved)
                .filter(user -> user.getTokenVersion() == tokenVersion(jwt))
                .orElseThrow(() -> new InvalidBearerTokenException("This login is no longer valid. Log in again."));
    }

    /** Tokens from before versions existed carry no version; they count as version 0. */
    private static int tokenVersion(Jwt jwt) {
        Object version = jwt.getClaim(TokenService.VERSION_CLAIM);
        return version instanceof Number number ? number.intValue() : 0;
    }

    private static UUID accountId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("This login is no longer valid. Log in again.");
        }
    }
}
