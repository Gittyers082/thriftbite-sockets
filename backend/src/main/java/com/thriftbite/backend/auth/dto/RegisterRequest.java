package com.thriftbite.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 30)
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "may only contain letters, numbers, '.', '_' and '-'")
        String username,

        // BCrypt only uses the first 72 bytes of a password, so longer ones are rejected.
        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotNull
        AccountType accountType,

        // Owners only: the store's name as buyers will see it. Defaults to the username.
        @Size(max = 80)
        String storeName
) {
    /** The roles anyone can sign up for. ADMIN isn't here, so it can't be requested. */
    public enum AccountType {
        OWNER,
        CONSUMER
    }
}
