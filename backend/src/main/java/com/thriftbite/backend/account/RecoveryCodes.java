package com.thriftbite.backend.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One-time recovery codes: the way back into an account when the password is
 * forgotten. ThriftBite has no email or phone number to send a reset link to.
 *
 * A code is 12 random characters (about 59 bits), shown once and stored only as
 * a SHA-256 hash. A fast hash is fine here, unlike for passwords: nobody chose
 * these, so there's no dictionary to try, and guessing against the live site
 * is stopped by the attempt limits in AuthService.
 */
@Component
public class RecoveryCodes {

    public static final int CODES_PER_SET = 8;
    private static final int CODE_LENGTH = 12;
    /** No 0/O, 1/I/L: these get misread when copied from paper. */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;

    RecoveryCodes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @param madeAt when the current set was made, or null if the account has none */
    public record Status(int left, Instant madeAt) {
    }

    /** Replaces the account's codes with a fresh set and returns them. This is the only time they can be read. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> replace(UUID userId) {
        jdbc.update("DELETE FROM recovery_codes WHERE user_id = ?", userId);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < CODES_PER_SET; i++) {
            String code = newCode();
            jdbc.update("INSERT INTO recovery_codes (user_id, code_hash) VALUES (?, ?)", userId, hash(userId, code));
            codes.add(format(code));
        }
        return codes;
    }

    /** Whether {@code typed} is one of the account's unused codes. Doesn't use it up. */
    public boolean matches(UUID userId, String typed) {
        String code = normalize(typed);
        return code != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM recovery_codes WHERE user_id = ? AND code_hash = ? AND used_at IS NULL)",
                Boolean.class, userId, hash(userId, code)));
    }

    /**
     * Uses the code up. The check and the write are one statement, so the same code
     * sent twice at the same moment works only once.
     *
     * @return false if it isn't one of the account's unused codes
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean use(UUID userId, String typed) {
        String code = normalize(typed);
        return code != null && jdbc.update(
                "UPDATE recovery_codes SET used_at = now() WHERE user_id = ? AND code_hash = ? AND used_at IS NULL",
                userId, hash(userId, code)) == 1;
    }

    public Status status(UUID userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FILTER (WHERE used_at IS NULL) AS unused, MIN(created_at) AS made_at FROM recovery_codes WHERE user_id = ?",
                (rs, row) -> {
                    Timestamp madeAt = rs.getTimestamp("made_at");
                    return new Status(rs.getInt("unused"), madeAt == null ? null : madeAt.toInstant());
                }, userId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteAll(UUID userId) {
        jdbc.update("DELETE FROM recovery_codes WHERE user_id = ?", userId);
    }

    private String newCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** "7KQ2M9XAP4WD" is shown as "7KQ2-M9XA-P4WD". */
    private static String format(String code) {
        return code.substring(0, 4) + "-" + code.substring(4, 8) + "-" + code.substring(8);
    }

    /** People type codes with dashes, spaces or lower case; all of those are accepted. Null if it can't be a code. */
    static String normalize(String typed) {
        if (typed == null) {
            return null;
        }
        String code = typed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return code.length() == CODE_LENGTH ? code : null;
    }

    /** The account id is mixed in, so the same code on two accounts doesn't store the same hash. */
    private static String hash(UUID userId, String code) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest((userId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // every JVM has SHA-256
        }
    }
}
