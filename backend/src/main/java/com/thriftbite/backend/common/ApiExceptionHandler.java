package com.thriftbite.backend.common;

import com.thriftbite.backend.common.ApiExceptions.AccountNotApprovedException;
import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.ForbiddenException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.common.ApiExceptions.TooManyRequestsException;
import com.thriftbite.backend.reservation.Reservation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns domain exceptions into consistent JSON error responses. Every error
 * comes back in the same shape, and stack traces never reach the client.
 * Validation errors (400) are handled by Spring's built-in problem-details support.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** A database constraint caught something the code didn't, e.g. two signups with the same name at once. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail constraintViolation(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    /** Same message for "no such user" and "wrong password", so attackers can't probe for usernames. */
    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials(BadCredentialsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    /** The "code" property lets the frontend show the waiting-for-approval page instead of a plain error. */
    @ExceptionHandler(AccountNotApprovedException.class)
    ProblemDetail notApproved(AccountNotApprovedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problem.setProperty("code", "account_not_approved");
        return problem;
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ProblemDetail> tooManyRequests(TooManyRequestsException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setProperty("code", e.getCode());
        long seconds = Math.max(1, e.getRetryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(problem);
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /** Someone else changed this row (e.g. a buyer reserved stock) since the client last read it. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail staleUpdate(OptimisticLockingFailureException e) {
        boolean reservation = e instanceof ObjectOptimisticLockingFailureException o
                && Reservation.class.getName().equals(o.getPersistentClassName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, reservation
                ? "This reservation changed at the same moment (it may have been picked up, cancelled or expired). Reload and try again."
                : "This item changed since you loaded it. Reload and try again.");
    }
}
