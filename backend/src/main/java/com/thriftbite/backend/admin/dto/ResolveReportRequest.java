package com.thriftbite.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reply      what the buyer is told; they see it on their account page
 * @param excuseMiss true to stop the missed pickup this report is about counting against the buyer
 *                   (the store was closed, say); only for a report about a pickup that was missed
 */
public record ResolveReportRequest(@NotBlank @Size(min = 10, max = 500) String reply, Boolean excuseMiss) {
}
