package com.thriftbite.backend.common;

import java.time.Duration;

/** Domain errors that map to specific HTTP status codes in {@link ApiExceptionHandler}. */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    /** 404 */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /** 409: e.g. a username that's already taken. */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    /** 400: the request is well-formed but breaks a business rule. */
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    /** 403: logged in, but not allowed to touch this resource (e.g. another owner's item). */
    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
    }

    /** 400: the owner typed the wrong claim code. Kept as its own type so the attempt count is still saved. */
    public static class WrongClaimCodeException extends BadRequestException {
        public WrongClaimCodeException(String message) {
            super(message);
        }
    }

    /**
     * 429: slow down. {@code retryAfter} becomes the Retry-After header, and
     * {@code code} lets the frontend tell the cases apart without parsing text.
     */
    public static class TooManyRequestsException extends RuntimeException {
        private final Duration retryAfter;
        private final String code;

        public TooManyRequestsException(String message, Duration retryAfter, String code) {
            super(message);
            this.retryAfter = retryAfter;
            this.code = code;
        }

        public Duration getRetryAfter() {
            return retryAfter;
        }

        public String getCode() {
            return code;
        }
    }

    /** 403: the user is known, but isn't allowed to do this yet. */
    public static class AccountNotApprovedException extends RuntimeException {
        public AccountNotApprovedException() {
            super("This owner account is waiting for admin approval");
        }
    }
}
