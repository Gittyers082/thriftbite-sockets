package com.thriftbite.backend.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        // BCrypt only uses the first 72 bytes of a password, so longer ones are rejected.
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {
}
