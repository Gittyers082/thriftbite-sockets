package com.thriftbite.backend.auth.dto;

import java.time.Instant;

/** A fresh login after a password reset, and how many recovery codes the account has left. */
public record RecoverResponse(String token, Instant expiresAt, UserResponse user, int recoveryCodesLeft) {
}
