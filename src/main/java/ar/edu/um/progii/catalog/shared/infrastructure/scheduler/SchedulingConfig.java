package ar.edu.um.progii.catalog.shared.infrastructure.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Enciende los métodos @Scheduled de todos los módulos
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
