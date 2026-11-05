package com.thriftbite.backend.common;

import com.thriftbite.backend.common.ApiExceptions.AccountNotApprovedException;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

    @ExceptionHandler(AccountNotApprovedException.class)
    ProblemDetail notApproved(AccountNotApprovedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }
}
