package com.thriftbite.backend.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Signs and verifies JWTs with one shared HMAC-SHA256 key. The key comes from
 * APP_JWT_SECRET and is never hard-coded.
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);
    private static final int MIN_KEY_BYTES = 32;   // HS256 needs at least 256 bits

    @Bean
    SecretKey jwtSigningKey(@Value("${app.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("APP_JWT_SECRET is not set: using a random key. Tokens will stop working after a restart.");
            byte[] random = new byte[MIN_KEY_BYTES];
            new SecureRandom().nextBytes(random);
            return new SecretKeySpec(random, "HmacSHA256");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("APP_JWT_SECRET must be at least " + MIN_KEY_BYTES + " characters long");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
