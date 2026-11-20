package com.thriftbite.backend.store.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** One open day. Times are local (Manila), e.g. "07:00" and "19:00". */
public record StoreHoursEntry(
        @NotNull DayOfWeek weekday,
        @NotNull LocalTime opens,
        @NotNull LocalTime closes
) {
}
