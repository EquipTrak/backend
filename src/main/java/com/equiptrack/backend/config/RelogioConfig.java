package com.equiptrack.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class RelogioConfig {

	/** Relógio único da aplicação, para que as regras de data possam ser testadas com um horário fixo. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
