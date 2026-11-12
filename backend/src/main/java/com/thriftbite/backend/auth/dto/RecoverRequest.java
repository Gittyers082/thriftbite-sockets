package com.thriftbite.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Resets a forgotten password with a recovery code.
 *
 * @param newPassword left out to only check the code (the first step of the form);
 *                    the code is used up only when a new password is saved
 */
public record RecoverRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(max = 40) String recoveryCode,
        @Size(min = 8, max = 72) String newPassword
) {
}
