package com.thriftbite.backend.account.dto;

import java.util.List;

/** A fresh set of recovery codes. This is the only time they can be read. */
public record RecoveryCodesResponse(List<String> codes) {
}
