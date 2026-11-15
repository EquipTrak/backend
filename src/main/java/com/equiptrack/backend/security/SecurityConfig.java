package com.equiptrack.backend.security;

import com.equiptrack.backend.auth.AuthService;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer")
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService authService,
			ErroSegurancaHandler erroSegurancaHandler) throws Exception {
		return http
			// A API não usa cookies: o token vai no cabeçalho Authorization, então não há superfície de CSRF.
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				// Cadastro de equipamento (RF04): todo perfil que usa a visão do operador.
				// Histórico do equipamento (RF15): mesma regra do cadastro.
				.requestMatchers(HttpMethod.GET, "/api/equipamentos/*/historico")
					.hasAnyRole("OPERADOR", "ENCARREGADO", "ADMINISTRADOR")
				.requestMatchers(HttpMethod.POST, "/api/equipamentos")
					.hasAnyRole("OPERADOR", "ENCARREGADO", "ADMINISTRADOR")
				// Criar solicitação (RF08): só quem tem perfil de solicitante.
				.requestMatchers(HttpMethod.POST, "/api/solicitacoes").hasRole("SOLICITANTE")
				// Minhas solicitações (RF18) e cancelamento (RF23): também só do solicitante.
				.requestMatchers(HttpMethod.GET, "/api/solicitacoes/minhas").hasRole("SOLICITANTE")
				.requestMatchers(HttpMethod.POST, "/api/solicitacoes/*/cancelamento").hasRole("SOLICITANTE")
				// Fila, aprovação e rejeição (RF09): perfis com a visão do operador.
				.requestMatchers(HttpMethod.GET, "/api/solicitacoes")
					.hasAnyRole("OPERADOR", "ENCARREGADO", "ADMINISTRADOR")
				.requestMatchers(HttpMethod.POST, "/api/solicitacoes/*/aprovacao", "/api/solicitacoes/*/rejeicao")
					.hasAnyRole("OPERADOR", "ENCARREGADO", "ADMINISTRADOR")
				// Retirada (RF10): mesma regra.
				.requestMatchers(HttpMethod.POST, "/api/solicitacoes/*/retirada")
					.hasAnyRole("OPERADOR", "ENCARREGADO", "ADMINISTRADOR")
				// Devolução (RF11): mesma regra.
				.requestMatchers(HttpMethod.POST, "/api/emprestimos/*/devolucao")
					.hasAnyRole("OPERADOR", "ENCARREGADO", "ADMINISTRADOR")
				// Indicadores do painel (RF20): o dashboard é do operador.
				.requestMatchers(HttpMethod.GET, "/api/painel/**")
					.hasAnyRole("OPERADOR", "ENCARREGADO", "ADMINISTRADOR")
				.anyRequest().authenticated())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(erroSegurancaHandler)
				.accessDeniedHandler(erroSegurancaHandler))
			.addFilterBefore(new TokenAuthenticationFilter(authService), UsernamePasswordAuthenticationFilter.class)
			.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${equiptrack.cors.origens-permitidas}") List<String> origensPermitidas) {
		CorsConfiguration configuracao = new CorsConfiguration();
		configuracao.setAllowedOrigins(origensPermitidas);
		configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuracao.setAllowedHeaders(List.of("Authorization", "Content-Type"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuracao);
		return source;
	}

}
