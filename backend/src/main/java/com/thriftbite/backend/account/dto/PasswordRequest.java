package com.thriftbite.backend.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * For actions that ask for the password again (new recovery codes, deleting the account),
 * so a phone left unlocked or a stolen login can't be turned into a permanent takeover.
 */
public record PasswordRequest(@NotBlank @Size(max = 72) String password) {
}
