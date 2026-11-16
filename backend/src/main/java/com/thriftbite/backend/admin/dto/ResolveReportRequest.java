package com.thriftbite.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param reply what the buyer is told; they see it on their account page */
public record ResolveReportRequest(@NotBlank @Size(min = 10, max = 500) String reply) {
}
