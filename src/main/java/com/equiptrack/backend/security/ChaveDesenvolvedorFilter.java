package com.equiptrack.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Autentica os desenvolvedores que gerenciam usuários pela API. A chave vem da variável
 * EQUIPTRACK_CHAVE_DESENVOLVEDOR e é enviada no cabeçalho {@value #CABECALHO}. Sem a variável,
 * ninguém recebe o papel DESENVOLVEDOR e as rotas de usuários ficam fechadas.
 */
public class ChaveDesenvolvedorFilter extends OncePerRequestFilter {

	public static final String CABECALHO = "X-Chave-Desenvolvedor";
	public static final String PAPEL = "DESENVOLVEDOR";

	private static final Logger log = LoggerFactory.getLogger(ChaveDesenvolvedorFilter.class);

	private final byte[] chave;

	public ChaveDesenvolvedorFilter(String chave) {
		this.chave = chave == null || chave.isBlank() ? null : chave.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String enviada = request.getHeader(CABECALHO);
		// isEqual compara em tempo constante, para o tempo de resposta não revelar a chave aos poucos.
		if (chave != null && enviada != null
				&& MessageDigest.isEqual(chave, enviada.getBytes(StandardCharsets.UTF_8))) {
			var autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + PAPEL));
			SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(PAPEL, null, autoridades));
		}
		else if (enviada != null) {
			// Rastro de tentativa com chave errada (ou rota fechada por falta de chave configurada).
			log.warn("Chave de desenvolvedor recusada em {} {} a partir de {}", request.getMethod(),
					request.getRequestURI(), request.getRemoteAddr());
		}
		filterChain.doFilter(request, response);
	}

}
