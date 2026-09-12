package com.workflow.service;

import com.workflow.scheduler.TaskSchedulerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Re-exposes TaskSchedulerService for injection in tests.
 * In production Spring manages this automatically via @Service.
 */
@Configuration
public class TestConfig {
    // Intentionally empty — TaskSchedulerService is a @Service so it's auto-discovered.
    // This class exists as a hook for future test-specific beans if needed.
}
