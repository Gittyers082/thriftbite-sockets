package com.thriftbite.backend.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled methods such as ExpiryJob. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
