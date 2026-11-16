package com.thriftbite.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param reason shown to the account's holder when they try to log in, and kept in the activity log */
public record SuspendRequest(@NotBlank @Size(min = 10, max = 300) String reason) {
}
