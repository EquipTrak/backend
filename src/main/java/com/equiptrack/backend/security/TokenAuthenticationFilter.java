package com.equiptrack.backend.security;

import com.equiptrack.backend.auth.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class TokenAuthenticationFilter extends OncePerRequestFilter {

	private static final String PREFIXO_BEARER = "Bearer ";

	private final AuthService authService;

	public TokenAuthenticationFilter(AuthService authService) {
		this.authService = authService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(PREFIXO_BEARER)) {
			authService.autenticar(header.substring(PREFIXO_BEARER.length())).ifPresent(usuario -> {
				var autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.perfil().name()));
				SecurityContextHolder.getContext()
					.setAuthentication(new UsernamePasswordAuthenticationToken(usuario, null, autoridades));
			});
		}
		filterChain.doFilter(request, response);
	}

}
