package com.thriftbite.backend.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CompleteRequest(
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be 6 digits") String claimCode
) {
}
