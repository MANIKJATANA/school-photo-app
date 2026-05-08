package com.example.photoapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Single home for {@link EnableScheduling}. Worker beans pick this up via
 * {@code @Scheduled} (e.g., {@code StaleUploadSweeper}, the future
 * {@code OutboxPoller}). Keeping the annotation on a dedicated config class
 * (not the main application) makes it easy to disable scheduling in test
 * profiles by excluding this class.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
